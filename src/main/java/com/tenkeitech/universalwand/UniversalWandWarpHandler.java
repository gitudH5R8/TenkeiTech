/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.Loader;

/** Queried before selected warp effects run; no potion or persistent player state is owned here. */
public final class UniversalWandWarpHandler {

    private UniversalWandWarpHandler() {}

    public static boolean isSuppressed(EntityPlayer player) {
        if (player == null || player.worldObj == null
            || player.worldObj.isRemote
            || player.inventory == null
            || player.isDead
            || player.getHealth() <= 0
            || !UniversalWandFeature.WARP_SUPPRESSION.allowed()) return false;
        if (player instanceof EntityPlayerMP serverPlayer && serverPlayer.theItemInWorldManager == null) return false;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (matches(player.inventory.getStackInSlot(slot))) return true;
        }
        return Loader.isModLoaded("backhand") && (matches(UniversalWandBackhandIntegration.getOffhandItem(player))
            || matches(UniversalWandBackhandIntegration.getMainhandItem(player)));
    }

    private static boolean matches(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemUniversalWand
            && ItemUniversalWand.isWarpSuppressionEnabled(stack);
    }

    public static boolean suppressEvent(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || !isSuppressed(player)) return false;
        notifySuppressed(serverPlayer);
        return true;
    }

    public static void notifySuppressed(EntityPlayerMP player) {
        player.addChatMessage(
            new ChatComponentTranslation("tenkeitech.universalwand.warp_suppressed").setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.DARK_PURPLE)
                    .setItalic(true)));
    }
}
