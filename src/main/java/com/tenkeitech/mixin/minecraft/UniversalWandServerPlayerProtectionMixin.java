/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(EntityPlayerMP.class)
public abstract class UniversalWandServerPlayerProtectionMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$death(DamageSource source, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.isProtected((EntityPlayerMP) (Object) this)) ci.cancel();
    }

}
