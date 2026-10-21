/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.backhand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import xonin.backhand.api.core.BackhandUtils;
import xonin.backhand.api.core.IBackhandPlayer;

/** Only called while Backhand is loaded; its temporary offhand slot is not the main hand. */
public final class UniversalWandBackhandIntegration {

    private UniversalWandBackhandIntegration() {}

    public static ItemStack getMainhandItem(EntityPlayer player) {
        if (player instanceof IBackhandPlayer backhandPlayer && backhandPlayer.isUsingOffhand())
            return backhandPlayer.getMainhandItem();
        return player.getHeldItem();
    }

    public static ItemStack getOffhandItem(EntityPlayer player) {
        return BackhandUtils.getOffhandItem(player);
    }
}
