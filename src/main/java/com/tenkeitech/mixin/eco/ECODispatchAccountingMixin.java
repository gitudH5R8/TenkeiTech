/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.eco;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.integration.quantum.QuantumDispatch;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingDispatchAccounting", remap = false)
public abstract class ECODispatchAccountingMixin {

    @Inject(method = "isFakeCraftingDispatch", at = @At("HEAD"), cancellable = true)
    private static void tenkeitech$physicalCatalyst(@Coerce Object request, @Coerce Object provider,
        CallbackInfoReturnable<Boolean> callback) {
        if (QuantumDispatch.isCatalystOrder()) callback.setReturnValue(false);
    }
}
