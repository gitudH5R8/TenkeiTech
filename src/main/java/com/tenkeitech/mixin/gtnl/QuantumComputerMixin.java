/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gtnl;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.tenkeitech.integration.gtnl.QuantumComputerAccess;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiblock.QuantumComputer", remap = false)
public abstract class QuantumComputerMixin implements QuantumComputerAccess {

    @Shadow
    public abstract void forEachCPU(Consumer<CraftingCPUCluster> consumer);

    @Override
    public CraftingCPUCluster tenkeitech$idleCpu() {
        CraftingCPUCluster[] selected = new CraftingCPUCluster[1];
        forEachCPU(cpu -> { if (selected[0] == null && cpu.isActive() && !cpu.isBusy()) selected[0] = cpu; });
        return selected[0];
    }
}
