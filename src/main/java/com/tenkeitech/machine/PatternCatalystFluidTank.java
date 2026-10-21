/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/** Transactional MUI2 tank view over one fixed pattern-slot index's physical fluid catalyst. */
public final class PatternCatalystFluidTank implements IFluidTank {

    public static final int CAPACITY = 16_000;

    private final MTESuperCraftingInputME machine;
    private final int patternSlot;
    private final int catalystTank;

    public PatternCatalystFluidTank(MTESuperCraftingInputME machine, int patternSlot, int catalystTank) {
        this.machine = machine;
        this.patternSlot = patternSlot;
        this.catalystTank = catalystTank;
    }

    @Override
    public FluidStack getFluid() {
        PatternAugmentData data = machine.getPatternAugmentData(patternSlot);
        return data == null ? null : data.getFluidCatalyst(catalystTank);
    }

    @Override
    public int getFluidAmount() {
        FluidStack fluid = getFluid();
        return fluid == null ? 0 : fluid.amount;
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(getFluid(), CAPACITY);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() == null || resource.amount <= 0) return 0;
        PatternAugmentData data = machine.getPatternAugmentData(patternSlot);
        if (data == null) return 0;
        FluidStack current = data.getFluidCatalyst(catalystTank);
        if (current != null && !current.isFluidEqual(resource)) return 0;
        int currentAmount = current == null ? 0 : current.amount;
        int filled = Math.min(resource.amount, CAPACITY - currentAmount);
        if (!doFill || filled <= 0) return filled;

        FluidStack result = current == null ? resource.copy() : current.copy();
        result.amount = currentAmount + filled;
        data.setFluidCatalyst(catalystTank, result);
        return machine.applyPatternAugmentData(patternSlot, data) ? filled : 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        PatternAugmentData data = machine.getPatternAugmentData(patternSlot);
        if (data == null) return null;
        FluidStack current = data.getFluidCatalyst(catalystTank);
        if (current == null) return null;

        FluidStack drained = current.copy();
        drained.amount = Math.min(maxDrain, current.amount);
        if (!doDrain) return drained;

        current.amount -= drained.amount;
        data.setFluidCatalyst(catalystTank, current);
        return machine.applyPatternAugmentData(patternSlot, data) ? drained : null;
    }
}
