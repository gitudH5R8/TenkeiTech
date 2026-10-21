/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.function.LongSupplier;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/** Phantom fluid-identity view for one shared consumable refill slot. */
public final class SharedConsumableConfigFluidTank implements IFluidTank {

    private final MTESuperCraftingInputME machine;
    private final int slot;
    private final LongSupplier target;

    public SharedConsumableConfigFluidTank(MTESuperCraftingInputME machine, int slot, LongSupplier target) {
        this.machine = machine;
        this.slot = slot;
        this.target = target;
    }

    @Override
    public FluidStack getFluid() {
        FluidStack fluid = machine.getSharedConsumableFluidConfig(slot);
        if (fluid != null) fluid.amount = getDisplayAmount();
        return fluid;
    }

    @Override
    public int getFluidAmount() {
        return getFluid() == null ? 0 : getDisplayAmount();
    }

    @Override
    public int getCapacity() {
        return getDisplayAmount();
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(getFluid(), getCapacity());
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (!machine.isValidSharedConsumableFluidConfig(slot, resource)) return 0;
        return !doFill || machine.setSharedConsumableFluidConfig(slot, resource) ? 1 : 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        FluidStack current = getFluid();
        if (current == null || maxDrain <= 0) return null;
        if (!doDrain || machine.setSharedConsumableFluidConfig(slot, null)) return current;
        return null;
    }

    private int getDisplayAmount() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, target.getAsLong()));
    }
}
