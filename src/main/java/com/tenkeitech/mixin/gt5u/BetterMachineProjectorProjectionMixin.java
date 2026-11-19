/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.machineprojector.BetterMachineProjectorProjection;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

/** Active only for the detached controller inside BetterMachineProjectorProjection.collect. */
@Mixin(value = MTEEnhancedMultiBlockBase.class, remap = false)
public abstract class BetterMachineProjectorProjectionMixin {

    @Inject(method = "buildPiece", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$captureProjection(String piece, ItemStack trigger, boolean hints, int a, int b, int c,
        CallbackInfoReturnable<Boolean> callback) {
        if (BetterMachineProjectorProjection
            .capture((MTEEnhancedMultiBlockBase<?>) (Object) this, piece, trigger, hints, a, b, c))
            callback.setReturnValue(true);
    }
}
