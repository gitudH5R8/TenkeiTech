/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;

/** Keep the active inventory consistent with the shared GTNL recipe cache for every machine. */
@Pseudo
@Mixin(targets = "com.science.gtnl.utils.recipes.GTNLProcessingLogic", remap = false)
public abstract class ProcessingLogicRecipeCacheMixin extends ProcessingLogic {

    @Inject(method = "getCurrentRecipeMap", at = @At("RETURN"), require = 0)
    private void tenkeitech$discardMissingRecipeCache(CallbackInfoReturnable<RecipeMap<?>> callback) {
        if (activeDualInv != null && dualInvWithPatternToRecipeCache.get(activeDualInv) == null) {
            activeDualInv = null;
        }
    }
}
