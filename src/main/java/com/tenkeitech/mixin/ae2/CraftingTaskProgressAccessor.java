/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public interface CraftingTaskProgressAccessor {

    @Accessor(value = "value", remap = false)
    long tenkeitech$getValue();

    @Accessor(value = "value", remap = false)
    void tenkeitech$setValue(long value);

    @Invoker(value = "copyDiagnosticSessionsFrom", remap = false)
    void tenkeitech$copyDiagnosticSessionsFrom(CraftingCPUCluster.TaskProgress source);
}
