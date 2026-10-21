/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.nee;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.Config;
import com.tenkeitech.patternupload.PatternUploadScreenBridge;

import codechicken.nei.recipe.IRecipeHandler;

/** Optional NEE callback after explicit transfer; recipe previews never record a routing hint. */
@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler", remap = false)
public abstract class PatternUploadTransferMixin {

    @Inject(method = "transferRecipe", at = @At("RETURN"))
    private void tenkeitech$rememberCategory(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier,
        CallbackInfoReturnable<Integer> callback) {
        if (Config.enablePatternUpload && gui instanceof PatternUploadScreenBridge bridge) {
            String identifier = recipe.getOverlayIdentifier();
            bridge.tenkeitech$setPatternUploadCategory(
                identifier == null || "null".equals(identifier) ? "" : identifier,
                recipe.getRecipeName());
        }
    }
}
