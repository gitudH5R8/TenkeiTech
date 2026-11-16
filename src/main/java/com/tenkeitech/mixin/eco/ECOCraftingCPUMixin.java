/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.eco;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.integration.quantum.QuantumDispatch;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", remap = false)
public abstract class ECOCraftingCPUMixin {

    @Shadow
    public abstract ICraftingCPU nativeCPU();

    @WrapMethod(method = "tickCraftingLogic")
    private void tenkeitech$bindDispatch(IEnergyGrid energy, @Coerce Object crafting, Operation<Void> original) {
        ICraftingCPU identity = nativeCPU();
        CraftingCPUCluster previous = QuantumDispatch.enter(identity instanceof CraftingCPUCluster cpu ? cpu : null);
        try {
            original.call(energy, crafting);
        } finally {
            QuantumDispatch.enter(previous);
        }
    }
}
