/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.wildcardpattern;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.integration.wildcardpattern.WildcardPatternCircuitData;

import appeng.api.networking.crafting.ICraftingPatternDetails;

@Pseudo
@Mixin(
    targets = { "com.myname.wildcardpattern.crafting.WildcardPatternGenerator",
        "com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator" },
    remap = false)
public abstract class WildcardPatternCircuitMixin {

    @Inject(method = "createDetailForCurrentStack", at = @At("HEAD"), remap = false)
    private static void tenkeitech$applyConfiguredCircuit(ItemStack pattern, World world,
        CallbackInfoReturnable<ICraftingPatternDetails> callback) {
        WildcardPatternCircuitData.applyToGeneratedPattern(pattern);
    }
}
