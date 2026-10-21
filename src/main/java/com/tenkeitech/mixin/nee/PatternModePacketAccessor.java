/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.nee;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe", remap = false)
public interface PatternModePacketAccessor {

    @Accessor("input")
    NBTTagCompound tenkeitech$input();
}
