/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.resonating.ResonatingPatternData;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/** A remote-routing pattern must never become an ordinary GT-local processing recipe. */
@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public abstract class ResonatingPatternSafetyMixin {

    @Redirect(
        method = "provideCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProviderHelper;addCraftingOption(Lappeng/api/networking/crafting/ICraftingMedium;Lappeng/api/networking/crafting/ICraftingPatternDetails;)V"),
        require = 1)
    private void tenkeitech$publish(ICraftingProviderHelper helper, ICraftingMedium medium,
        ICraftingPatternDetails details) {
        if (!(details instanceof ResonatingPatternData)) helper.addCraftingOption(medium, details);
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$reject(ICraftingPatternDetails details, InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        if (details instanceof ResonatingPatternData) cir.setReturnValue(false);
    }
}
