/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.integration.gtnl.QuantumComputerAccess;
import com.tenkeitech.integration.quantum.QuantumCpu;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Pseudo
@Mixin(targets = "com.science.gtnl.utils.ECraftingCPUCluster", remap = false)
public abstract class QuantumCraftingCPUMixin implements QuantumCpu {

    @Unique
    private QuantumComputerAccess tenkeitech$owner;

    @Inject(method = "setVirtualCPUOwner", at = @At("RETURN"))
    private void tenkeitech$bindOwner(@Coerce Object owner, CallbackInfo callback) {
        tenkeitech$owner = owner instanceof QuantumComputerAccess access ? access : null;
    }

    @Override
    public CraftingCPUCluster tenkeitech$childCpu() {
        return tenkeitech$owner == null ? null : tenkeitech$owner.tenkeitech$idleCpu();
    }
}
