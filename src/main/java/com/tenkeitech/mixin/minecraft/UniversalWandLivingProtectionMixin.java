/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(EntityLivingBase.class)
public abstract class UniversalWandLivingProtectionMixin {

    @Shadow
    protected boolean dead;

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void tenkeitech$validHealth(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        float health = cir.getReturnValue();
        if (UniversalWandProtectionHandler.isProtected(entity) && (!Float.isFinite(health) || health <= 0))
            cir.setReturnValue(entity.getMaxHealth());
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float tenkeitech$health(float value) {
        return UniversalWandProtectionHandler.isProtected((EntityLivingBase) (Object) this)
            ? UniversalWandProtectionHandler.protectedHealth((EntityPlayer) (Object) this, value)
            : value;
    }

    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void tenkeitech$maximum(CallbackInfoReturnable<Float> cir) {
        if (UniversalWandProtectionHandler.isProtected((EntityLivingBase) (Object) this)) cir.setReturnValue(
            UniversalWandProtectionHandler.validMaximum((EntityPlayer) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$death(DamageSource source, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.isProtected((EntityLivingBase) (Object) this)) {
            dead = false;
            ci.cancel();
        }
    }

    @Inject(method = "onDeathUpdate", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$deathUpdate(CallbackInfo ci) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        if (UniversalWandProtectionHandler.isProtected(entity)) {
            dead = false;
            entity.deathTime = 0;
            entity.setHealth(entity.getMaxHealth());
            ci.cancel();
        }
    }

    @Inject(method = "addPotionEffect", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$potion(PotionEffect effect, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.isProtected((EntityLivingBase) (Object) this)
            && UniversalWandProtectionHandler.isNegative(effect)) ci.cancel();
    }

    @Inject(method = "setRevengeTarget", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$revenge(EntityLivingBase target, CallbackInfo ci) {
        if (UniversalWandProtectionHandler.ignoresTarget(target)) ci.cancel();
    }
}
