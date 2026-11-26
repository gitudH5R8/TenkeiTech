/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.Loader;

/** One introduction per player save; settings remain owned by each wand. */
public final class UniversalWandNeeGuide {

    private static final String SHOWN = "tenkeitechWandNeeGuideShown";

    private UniversalWandNeeGuide() {}

    public static void onCarry(EntityPlayerMP player) {
        if (player instanceof FakePlayer || !player.isEntityAlive()) return;
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(SHOWN) || !Loader.isModLoaded("neenergistics")) return;
        UniversalWandNetwork.sendNeeGuide(player);
        persisted.setBoolean(SHOWN, true);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
}
