/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.nee;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;
import com.tenkeitech.patternmode.PatternRecipeMapData;

import codechicken.nei.recipe.IRecipeHandler;
import gregtech.nei.GTNEIDefaultHandler;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler", remap = false)
public abstract class PatternModeTransferMixin {

    @Inject(method = "packRecipe", at = @At("RETURN"))
    private void tenkeitech$tagRecipe(IRecipeHandler recipe, int recipeIndex, int multiplier,
        CallbackInfoReturnable<PacketNEIPatternRecipe> callback) {
        if (recipe instanceof GTNEIDefaultHandler handler && handler.getRecipeMap() != null
            && callback.getReturnValue() instanceof PatternModePacketAccessor packet
            && packet.tenkeitech$input() != null) {
            PatternRecipeMapData.write(packet.tenkeitech$input(), handler.getRecipeMap().unlocalizedName);
        }
    }
}
