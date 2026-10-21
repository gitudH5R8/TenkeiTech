/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.universalwand.UniversalWandUtilityHandler;

@Mixin(EntityPlayerMP.class)
public abstract class UniversalWandFlightMixin {

    @Inject(method = "sendPlayerAbilities", at = @At("HEAD"))
    private void tenkeitech$observeExternalFlight(CallbackInfo ci) {
        UniversalWandUtilityHandler.observeAbilitySync((EntityPlayerMP) (Object) this);
    }
}
