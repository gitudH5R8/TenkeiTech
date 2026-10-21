/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.client;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.client.UniversalWandTravelFallback;

/** Bracket native right-click packets without cancelling or replaying any interaction. */
@Mixin(Minecraft.class)
public abstract class UniversalWandRightClickMixin {

    @Inject(method = "func_147121_ag", at = @At("HEAD"))
    private void tenkeitech$beginWandClick(CallbackInfo ci) {
        UniversalWandTravelFallback.beginClick();
    }

    @Inject(method = "func_147121_ag", at = @At("RETURN"))
    private void tenkeitech$finishWandClick(CallbackInfo ci) {
        UniversalWandTravelFallback.finishClick();
    }
}
