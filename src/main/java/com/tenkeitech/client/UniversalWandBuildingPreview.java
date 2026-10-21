/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.UniversalWandKeyHandler;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.universalwand.UniversalWandNetwork.BuildingSupplyMessage;
import com.tenkeitech.universalwand.UniversalWandNetwork.UndoPreviewMessage;
import com.tenkeitech.universalwand.building.BuildingContext;
import com.tenkeitech.universalwand.building.BuildingContext.Position;
import com.tenkeitech.universalwand.building.BuildingJob;
import com.tenkeitech.universalwand.building.BuildingSnapshot;
import com.tenkeitech.universalwand.building.InventorySupplier;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client-only bounded preview. Input remains owned by the native item-use path. */
public final class UniversalWandBuildingPreview {

    private List<BuildingSnapshot> snapshots = Collections.emptyList();
    private BuildingContext context;
    private int age;
    private static final ConcurrentLinkedQueue<UndoPreviewMessage> UNDO_UPDATES = new ConcurrentLinkedQueue<>();
    private List<Position> undoPositions = Collections.emptyList();
    private net.minecraft.world.World undoWorld;
    private static final ConcurrentLinkedQueue<BuildingSupplyMessage> SUPPLY_UPDATES = new ConcurrentLinkedQueue<>();
    private static BuildingSupplyMessage supply;
    private static long supplyReceivedTick;
    private long lastSupplyRequestTick = Long.MIN_VALUE;

    public static void enqueueSupply(BuildingSupplyMessage message) {
        SUPPLY_UPDATES.clear();
        SUPPLY_UPDATES.add(message);
    }

    public static int getSupply(BuildingContext context, ItemStack material) {
        return supply != null && supply.dimension == context.player.dimension
            && supply.x == context.origin.x
            && supply.y == context.origin.y
            && supply.z == context.origin.z
            && ItemStack.areItemStacksEqual(supply.wand, context.wand)
            && InventorySupplier.sameMaterial(supply.material, material)
            && context.world.getTotalWorldTime() - supplyReceivedTick <= 30L ? supply.count : 0;
    }

    public static void enqueueUndo(UndoPreviewMessage message) {
        UNDO_UPDATES.clear();
        UNDO_UPDATES.add(message);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (undoWorld != minecraft.theWorld) {
            undoPositions = Collections.emptyList();
            undoWorld = minecraft.theWorld;
            supply = null;
            SUPPLY_UPDATES.clear();
            lastSupplyRequestTick = Long.MIN_VALUE;
        }
        BuildingSupplyMessage available;
        while ((available = SUPPLY_UPDATES.poll()) != null) {
            if (minecraft.theWorld != null && minecraft.thePlayer != null
                && available.dimension == minecraft.thePlayer.dimension
                && ItemStack.areItemStacksEqual(available.wand, minecraft.thePlayer.getHeldItem())) {
                supply = available;
                supplyReceivedTick = minecraft.theWorld.getTotalWorldTime();
                reset();
            }
        }
        UndoPreviewMessage update;
        while ((update = UNDO_UPDATES.poll()) != null) {
            if (minecraft.theWorld != null && minecraft.thePlayer != null
                && minecraft.thePlayer.dimension == update.dimension) undoPositions = update.positions;
        }
        if (undoEnabled(minecraft)) {
            reset();
            return;
        }
        if (minecraft.thePlayer == null || minecraft.theWorld == null
            || !enabled(minecraft)
            || minecraft.currentScreen != null) {
            reset();
            return;
        }
        MovingObjectPosition hit = minecraft.objectMouseOver;
        boolean air = hit == null || hit.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
        BuildingCore core = ItemUniversalWand.getBuildingCore(minecraft.thePlayer.getHeldItem());
        if (air && core != BuildingCore.ANGEL || !air && hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            reset();
            return;
        }
        if (air) {
            BuildingContext next = BuildingContext.createAir(minecraft.thePlayer, minecraft.thePlayer.getHeldItem());
            if (next == null) {
                reset();
            } else if (context == null || !context.air
                || context.world != next.world
                || context.wand != next.wand
                || context.origin.key() != next.origin.key()
                || context.face != next.face
                || !context.supportsPlacement(context.origin)
                || ++age >= 10) {
                    reset();
                    context = next;
                    updateSnapshots();
                }
            return;
        }
        if (context == null || context.world != minecraft.theWorld
            || context.air
            || context.core != core
            || context.origin.x != hit.blockX
            || context.origin.y != hit.blockY
            || context.origin.z != hit.blockZ
            || context.face.ordinal() != hit.sideHit
            || context.wand != minecraft.thePlayer.getHeldItem()
            || !context.supportsPlacement(context.origin)
            || ++age >= 10) {
            reset();
            context = BuildingContext.create(minecraft.thePlayer, minecraft.thePlayer.getHeldItem(), hit);
            updateSnapshots();
        }
    }

    private void updateSnapshots() {
        if (context == null) return;
        if (context.core == BuildingCore.NORMAL && ItemUniversalWand.isBuildingAeEnabled(context.wand)) {
            long now = context.world.getTotalWorldTime();
            if (lastSupplyRequestTick == Long.MIN_VALUE || now < lastSupplyRequestTick
                || now - lastSupplyRequestTick >= 10L) {
                UniversalWandNetwork.requestBuildingSupply(context);
                lastSupplyRequestTick = now;
            }
        }
        BuildingJob job = new BuildingJob(context);
        if (!job.isSupported()) return;
        job.getSnapshots(BuildingContext.MAX_BLOCKS);
        snapshots = job.getSnapshots();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean undo = undoEnabled(minecraft);
        if (minecraft.renderViewEntity == null || minecraft.thePlayer == null
            || minecraft.currentScreen != null
            || !undo && (context == null || !enabled(minecraft)
                || ItemUniversalWand.getBuildingCore(minecraft.thePlayer.getHeldItem()) != context.core))
            return;
        Entity camera = minecraft.renderViewEntity;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5F);
            List<Position> positions = undo ? undoPositions : new java.util.ArrayList<>();
            if (!undo) for (BuildingSnapshot snapshot : snapshots) positions.add(snapshot.getPosition());
            for (Position destination : positions) {
                if (!BuildingContext.loaded(minecraft.theWorld, destination.x, destination.y, destination.z)) continue;
                AxisAlignedBB box = AxisAlignedBB
                    .getBoundingBox(
                        destination.x,
                        destination.y,
                        destination.z,
                        destination.x + 1D,
                        destination.y + 1D,
                        destination.z + 1D)
                    .expand(0.002D, 0.002D, 0.002D)
                    .getOffsetBoundingBox(-x, -y, -z);
                RenderGlobal.drawOutlinedBoundingBox(
                    box,
                    undo ? 0x00FF00 : context.core == BuildingCore.DESTRUCTION ? 0xEE5555 : 0x55DD88);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean enabled(Minecraft minecraft) {
        return !minecraft.thePlayer.isSneaking() && ItemUniversalWand.isHeldBy(minecraft.thePlayer)
            && ItemUniversalWand.isBuildingEnabled(minecraft.thePlayer.getHeldItem());
    }

    private static boolean undoEnabled(Minecraft minecraft) {
        return minecraft.thePlayer != null && minecraft.theWorld != null
            && ItemUniversalWand.isHeldBy(minecraft.thePlayer)
            && ItemUniversalWand.isBuildingEnabled(minecraft.thePlayer.getHeldItem())
            && UniversalWandKeyHandler.isUndoKeyHeld();
    }

    private void reset() {
        context = null;
        snapshots = Collections.emptyList();
        age = 0;
    }
}
