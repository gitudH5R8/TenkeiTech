/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.mixin.gt5u.BlockOverlayRendererInvoker;

import appeng.api.implementations.tiles.IWirelessAccessPoint;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Reuses GT5U's own machine highlight renderer for the universal wand. */
public final class UniversalWandBlockOverlayRenderer {

    public UniversalWandBlockOverlayRenderer() {}

    @SubscribeEvent(receiveCanceled = true)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (event == null || event.currentItem == null
            || event.target == null
            || event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        ItemStack stack = event.currentItem;
        if (!(stack.getItem() instanceof ItemUniversalWand)) return;
        if (ItemUniversalWand.isBuildingEnabled(stack) && !event.player.isSneaking()) return;
        ItemUniversalWand.GtToolMode mode = ItemUniversalWand.getGtToolMode(stack);
        if (mode == ItemUniversalWand.GtToolMode.OFF) return;
        TileEntity tile = event.player.worldObj
            .getTileEntity(event.target.blockX, event.target.blockY, event.target.blockZ);
        if (!(tile instanceof IGregTechTileEntity)) {
            int[] cable = ItemUniversalWand.findGtCableOnViewRay(event.player, event.player.worldObj);
            if (cable == null) return;
            tile = event.player.worldObj.getTileEntity(cable[0], cable[1], cable[2]);
            if (!(tile instanceof IGregTechTileEntity)) return;
            MovingObjectPosition cableTarget = new MovingObjectPosition(
                cable[0],
                cable[1],
                cable[2],
                cable[3],
                Vec3.createVectorHelper(
                    cable[0] + cable[4] / 1000.0D,
                    cable[1] + cable[5] / 1000.0D,
                    cable[2] + cable[6] / 1000.0D));
            event = new DrawBlockHighlightEvent(
                event.context,
                event.player,
                cableTarget,
                event.subID,
                event.currentItem,
                event.partialTicks);
        }
        if (!(tile instanceof IGregTechTileEntity)) return;
        BlockOverlayRendererInvoker
            .tenkeitech$drawGrid(event, false, mode == ItemUniversalWand.GtToolMode.WRENCH, event.player.isSneaking());
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null
            || minecraft.renderViewEntity == null
            || !ItemUniversalWand.isHeldBy(minecraft.thePlayer)) return;
        NBTTagCompound binding = ItemUniversalWand.getAeBinding(minecraft.thePlayer.getHeldItem());
        if (!binding.hasKey("dimension", 3) || !binding.hasKey("x", 3)
            || !binding.hasKey("y", 3)
            || !binding.hasKey("z", 3)
            || binding.getInteger("dimension") != minecraft.theWorld.provider.dimensionId) return;
        int x = binding.getInteger("x"), y = binding.getInteger("y"), z = binding.getInteger("z");
        if (!minecraft.theWorld.blockExists(x, y, z) || minecraft.theWorld.isAirBlock(x, y, z)
            || !minecraft.theWorld.getBlock(x, y, z)
                .hasTileEntity(minecraft.theWorld.getBlockMetadata(x, y, z)))
            return;
        TileEntity tile = minecraft.theWorld.getTileEntity(x, y, z);
        if (!(tile instanceof IWirelessAccessPoint) || tile.isInvalid()) return;
        Entity camera = minecraft.renderViewEntity;
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x, y, z, x + 1D, y + 1D, z + 1D)
            .expand(0.003D, 0.003D, 0.003D)
            .getOffsetBoundingBox(-cameraX, -cameraY, -cameraZ);
        // Draw after block highlights so cancellation or custom AE selection shapes cannot hide the binding marker.
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2F);
            RenderGlobal.drawOutlinedBoundingBox(box, 0x38D6E6);
        } finally {
            GL11.glPopAttrib();
        }
    }

}
