/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(EntityPlayer.class)
public abstract class UniversalWandPlayerProtectionMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$death(DamageSource source, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.isProtected((EntityPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "setDead", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$remove(CallbackInfo ci) {
        if (UniversalWandProtectionHandler.isProtected((EntityPlayer) (Object) this)) ci.cancel();
    }

}
