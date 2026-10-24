/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.lang.ref.WeakReference;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tenkeitech.ModItems;
import com.tenkeitech.autoextract.AutoExtractionCardData;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A hand-only direction preview; no target coordinates or predicted card edits are retained. */
@SideOnly(Side.CLIENT)
public final class AutoExtractionCardClient {

    private WeakReference<EntityPlayer> blockClickPlayer = new WeakReference<>(null);
    private int blockClickTick;

    private static boolean held(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() == ModItems.AUTO_EXTRACTION_CARD;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void interact(PlayerInteractEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.world == null || !event.world.isRemote
            || mc.currentScreen != null
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.entityPlayer == null
            || event.entityPlayer != mc.thePlayer
            || !held(event.entityPlayer.getHeldItem())) return;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            blockClickPlayer = new WeakReference<>(event.entityPlayer);
            blockClickTick = event.entityPlayer.ticksExisted;
        } else if (blockClickPlayer.get() == event.entityPlayer && blockClickTick == event.entityPlayer.ticksExisted) {
            // Minecraft follows a cancelled block event with an air event in the same call.
            blockClickPlayer.clear();
            event.setCanceled(true);
            return;
        }
        boolean allowed = !event.isCanceled() && event.useItem != Event.Result.DENY;
        // The client right-click entry checks isCanceled(), not useBlock. Skip local activation and
        // explicitly send one vanilla placement packet so the server still validates the interaction.
        event.setCanceled(true);
        if (!allowed) return;
        MovingObjectPosition hit = AutoExtractionCardTarget.trace(mc);
        if (hit == null) return;
        ItemStack stack = event.entityPlayer.getHeldItem();
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        mc.thePlayer.sendQueue.addToSendQueue(
            new C08PacketPlayerBlockPlacement(
                hit.blockX,
                hit.blockY,
                hit.blockZ,
                hit.sideHit,
                stack,
                (float) (hit.hitVec.xCoord - hit.blockX),
                (float) (hit.hitVec.yCoord - hit.blockY),
                (float) (hit.hitVec.zCoord - hit.blockZ)));
        mc.thePlayer.swingItem();
    }

    @SubscribeEvent
    public void highlight(DrawBlockHighlightEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && held(mc.thePlayer.getHeldItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null
            || mc.renderViewEntity == null
            || mc.currentScreen != null
            || mc.gameSettings.hideGUI) return;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!held(stack)) return;
        ForgeDirection direction = AutoExtractionCardData.direction(stack);
        MovingObjectPosition hit = AutoExtractionCardTarget.trace(mc);
        if (direction == null || hit == null
            || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || hit.sideHit < 0
            || hit.sideHit >= ForgeDirection.VALID_DIRECTIONS.length
            || !mc.theWorld.blockExists(hit.blockX, hit.blockY, hit.blockZ)
            || mc.theWorld.isAirBlock(hit.blockX, hit.blockY, hit.blockZ)) return;
        var view = mc.renderViewEntity;
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * event.partialTicks;
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * event.partialTicks;
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * event.partialTicks;
        boolean bound = direction != ForgeDirection.UNKNOWN;
        BlockFaceOverlay.begin();
        try {
            BlockFaceOverlay
                .face(hit.blockX, hit.blockY, hit.blockZ, bound ? direction.ordinal() : hit.sideHit, x, y, z, bound);
            BlockFaceOverlay.outline(hit.blockX, hit.blockY, hit.blockZ, x, y, z);
        } finally {
            BlockFaceOverlay.end();
        }
    }
}
