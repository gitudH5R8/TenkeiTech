/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.enderio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;

/** Uses the public native staff interaction, target UI and travel protocol. */
public final class UniversalWandTravelIntegration {

    private UniversalWandTravelIntegration() {}

    @SideOnly(Side.CLIENT)
    public static void use(ItemStack stack, World world, EntityPlayer player) {
        EnderIO.itemTeleportStaff.onItemRightClick(stack, world, player);
    }
}
