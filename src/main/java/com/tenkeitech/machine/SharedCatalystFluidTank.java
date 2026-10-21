/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/** Transactional MUI2 tank view over one machine-wide physical fluid catalyst. */
public final class SharedCatalystFluidTank implements IFluidTank {

    private final MTESuperCraftingInputME machine;
    private final int catalystTank;

    public SharedCatalystFluidTank(MTESuperCraftingInputME machine, int catalystTank) {
        this.machine = machine;
        this.catalystTank = catalystTank;
    }

    @Override
    public FluidStack getFluid() {
        return machine.getSharedFluidCatalysts()
            .get(catalystTank);
    }

    @Override
    public int getFluidAmount() {
        FluidStack fluid = getFluid();
        return fluid == null ? 0 : fluid.amount;
    }

    @Override
    public int getCapacity() {
        return PatternCatalystFluidTank.CAPACITY;
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(getFluid(), getCapacity());
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() == null || resource.amount <= 0) return 0;
        FluidCatalystData data = machine.getSharedFluidCatalysts();
        FluidStack current = data.get(catalystTank);
        if (current != null && !current.isFluidEqual(resource)) return 0;
        int currentAmount = current == null ? 0 : current.amount;
        int filled = Math.min(resource.amount, getCapacity() - currentAmount);
        if (!doFill || filled <= 0) return filled;

        FluidStack result = current == null ? resource.copy() : current.copy();
        result.amount = currentAmount + filled;
        data.set(catalystTank, result);
        return machine.applySharedFluidCatalysts(data) ? filled : 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        FluidCatalystData data = machine.getSharedFluidCatalysts();
        FluidStack current = data.get(catalystTank);
        if (current == null) return null;

        FluidStack drained = current.copy();
        drained.amount = Math.min(maxDrain, current.amount);
        if (!doDrain) return drained;

        current.amount -= drained.amount;
        data.set(catalystTank, current);
        return machine.applySharedFluidCatalysts(data) ? drained : null;
    }
}
