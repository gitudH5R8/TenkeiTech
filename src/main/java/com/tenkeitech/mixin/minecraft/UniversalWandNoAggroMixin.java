/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(EntityLiving.class)
public abstract class UniversalWandNoAggroMixin {

    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$target(EntityLivingBase target, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.ignoresTarget(target)) ci.cancel();
    }
}
