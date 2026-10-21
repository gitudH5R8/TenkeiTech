/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.function.LongSupplier;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/** Read-only MUI2 view of one machine-owned shared consumable backing fluid. */
public final class SharedConsumableStockFluidTank implements IFluidTank {

    private final MTESuperCraftingInputME machine;
    private final int slot;
    private final LongSupplier capacity;

    public SharedConsumableStockFluidTank(MTESuperCraftingInputME machine, int slot, LongSupplier capacity) {
        this.machine = machine;
        this.slot = slot;
        this.capacity = capacity;
    }

    @Override
    public FluidStack getFluid() {
        return machine.getSharedConsumableStoredFluid(slot);
    }

    @Override
    public int getFluidAmount() {
        FluidStack fluid = getFluid();
        return fluid == null ? 0 : fluid.amount;
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, capacity.getAsLong()));
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(getFluid(), getCapacity());
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return null;
    }
}
