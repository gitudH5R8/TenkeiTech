/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.eco;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.integration.quantum.QuantumDispatch;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider", remap = false)
public abstract class ECONativeProviderMixin {

    @WrapOperation(
        method = "pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"),
        require = 1)
    private boolean tenkeitech$pushCatalysts(ICraftingMedium medium, ICraftingPatternDetails pattern,
        InventoryCrafting table, Operation<Boolean> original) {
        return QuantumDispatch.push(medium, pattern, table, original);
    }
}
