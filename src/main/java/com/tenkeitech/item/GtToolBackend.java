/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTItemStack;

/** Integration boundary for GT tool behavior and future native GT bridges. */
final class GtToolBackend {

    private GtToolBackend() {}

    static boolean handleWrench(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection side, float hitX, float hitY, float hitZ) {
        Boolean nativeResult = invokeNativeGtRightClick(
            GregTechAPI.sWrenchList,
            player,
            world,
            x,
            y,
            z,
            side,
            hitX,
            hitY,
            hitZ);
        if (nativeResult != null) return nativeResult;
        if (player.isSneaking() && ItemUniversalWand.isEnderIOTarget(world, x, y, z)) {
            Boolean enderResult = invokeNativeBlockActivation(player, world, x, y, z, side, hitX, hitY, hitZ);
            if (enderResult != null) return enderResult;
        }
        return ItemUniversalWand.handleGtWrench(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    private static Boolean invokeNativeBlockActivation(EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection side, float hitX, float hitY, float hitZ) {
        if (GregTechAPI.sWrenchList == null) return null;
        Block block = world.getBlock(x, y, z);
        if (block == null) return null;
        for (GTItemStack registered : GregTechAPI.sWrenchList) {
            ItemStack wrench = registered.toStack();
            if (wrench == null || wrench.getItem() == null || wrench.getItem() instanceof ItemUniversalWand) continue;
            ItemStack original = player.getHeldItem();
            int slot = player.inventory.currentItem;
            player.inventory.setInventorySlotContents(slot, wrench);
            try {
                if (block.onBlockActivated(world, x, y, z, player, side.ordinal(), hitX, hitY, hitZ)) return true;
            } finally {
                player.inventory.setInventorySlotContents(slot, original);
            }
        }
        return false;
    }

    static boolean handleWireCutter(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection side, float hitX, float hitY, float hitZ) {
        // EnderIO performs its own conduit/component hit selection during normal
        // block activation. Do not let the GT cutter entry point consume it first.
        if (ItemUniversalWand.isEnderIOTarget(world, x, y, z)) {
            return ItemUniversalWand.handleGtWireCutter(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        }
        Boolean nativeMachineResult = invokeNativeGtRightClick(
            GregTechAPI.sWireCutterList,
            player,
            world,
            x,
            y,
            z,
            side,
            hitX,
            hitY,
            hitZ);
        if (nativeMachineResult != null) return nativeMachineResult;
        Boolean nativeResult = invokeNativeWireCutter(player, world, x, y, z, side, hitX, hitY, hitZ);
        if (nativeResult != null) return nativeResult;
        return ItemUniversalWand.handleGtWireCutter(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    /** Let GT5U execute its complete machine right-click pipeline with a real cutter stack. */
    private static Boolean invokeNativeGtRightClick(java.util.Collection<GTItemStack> toolList, EntityPlayer player,
        World world, int x, int y, int z, ForgeDirection side, float hitX, float hitY, float hitZ) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity gtTile) || toolList == null) return null;
        for (GTItemStack registered : toolList) {
            ItemStack cutter = registered.toStack();
            if (cutter == null || cutter.getItem() == null || cutter.getItem() instanceof ItemUniversalWand) continue;
            ItemStack invocationStack = cutter.copy();
            ItemStack originalStack = player.getHeldItem();
            int slot = player.inventory.currentItem;
            player.inventory.setInventorySlotContents(slot, invocationStack);
            try {
                if (gtTile.onRightclick(player, side, hitX, hitY, hitZ)) return true;
            } finally {
                player.inventory.setInventorySlotContents(slot, originalStack);
            }
        }
        return false;
    }

    /** Execute GT5U's actual MetaBaseItem entry point using a disposable cutter stack. */
    private static Boolean invokeNativeWireCutter(EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection side, float hitX, float hitY, float hitZ) {
        if (GregTechAPI.sWireCutterList == null) return null;
        for (Object candidate : GregTechAPI.sWireCutterList) {
            if (!(candidate instanceof GTItemStack registered)) continue;
            ItemStack cutter = registered.toStack();
            if (cutter == null || cutter.getItem() == null) continue;
            if (cutter.getItem() instanceof ItemUniversalWand) continue;
            ItemStack invocationStack = cutter.copy();
            ItemStack originalStack = player.getHeldItem();
            int slot = player.inventory.currentItem;
            player.inventory.setInventorySlotContents(slot, invocationStack);
            boolean handled;
            try {
                handled = invocationStack.getItem()
                    .onItemUseFirst(invocationStack, player, world, x, y, z, side.ordinal(), hitX, hitY, hitZ);
            } finally {
                player.inventory.setInventorySlotContents(slot, originalStack);
            }
            if (handled) return true;
        }
        return null;
    }
}
