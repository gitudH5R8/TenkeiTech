/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import java.util.ArrayList;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ForgeHooks;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;

@Mixin(value = ForgeHooks.class, remap = false)
public abstract class UniversalWandLivingDropsMixin {

    @WrapMethod(method = "onLivingDrops", remap = false)
    private static boolean tenkeitech$directDeathDrops(EntityLivingBase victim, DamageSource source,
        ArrayList<EntityItem> drops, int looting, boolean recentlyHit, int specialDropValue,
        Operation<Boolean> original) {
        try (UniversalWandMiningHandler.LivingDropScope scope = UniversalWandMiningHandler
            .beginLivingDrops(victim, source)) {
            return original.call(victim, source, drops, looting, recentlyHit, specialDropValue);
        }
    }

    @WrapMethod(method = "onLivingDeath", remap = false)
    private static boolean tenkeitech$directDeathEventDrops(EntityLivingBase victim, DamageSource source,
        Operation<Boolean> original) {
        try (UniversalWandMiningHandler.LivingDropScope scope = UniversalWandMiningHandler
            .beginLivingDrops(victim, source)) {
            return original.call(victim, source);
        }
    }
}
