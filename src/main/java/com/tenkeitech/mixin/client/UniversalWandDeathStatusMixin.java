/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.client;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(EntityLivingBase.class)
public abstract class UniversalWandDeathStatusMixin {

    @Inject(method = "handleHealthUpdate", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$deathStatus(byte status, CallbackInfo ci) {
        if (status == 3 && UniversalWandProtectionHandler.isProtected((EntityLivingBase) (Object) this)) ci.cancel();
    }
}
