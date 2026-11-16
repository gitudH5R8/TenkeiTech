/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.quantum;

import net.minecraft.inventory.InventoryCrafting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.machine.MTESuperCraftingInputME;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.inv.MEInventoryCrafting;

/** Restored in finally: nested dispatch must never inherit another order's identity. */
public final class QuantumDispatch {

    private static final ThreadLocal<CraftingCPUCluster> CPU = new ThreadLocal<>();

    private QuantumDispatch() {}

    public static CraftingCPUCluster enter(CraftingCPUCluster cpu) {
        CraftingCPUCluster previous = CPU.get();
        if (cpu == null) CPU.remove();
        else CPU.set(cpu);
        return previous;
    }

    public static boolean isCatalystOrder() {
        return QuantumCatalystRequests.isChild(CPU.get());
    }

    public static boolean push(ICraftingMedium medium, ICraftingPatternDetails pattern, InventoryCrafting table,
        Operation<Boolean> original) {
        CraftingCPUCluster cpu = CPU.get();
        if (cpu instanceof QuantumCpu && medium instanceof MTESuperCraftingInputME buffer
            && !pattern.isCraftable()
            && table instanceof MEInventoryCrafting nativeTable) {
            return buffer.getQuantumCatalysts()
                .push(cpu, pattern, nativeTable, original);
        }
        return original.call(medium, pattern, table);
    }
}
