/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Immutable identity for a fluid stack; the amount is deliberately excluded. */
public final class FluidInputKey {

    private final Fluid fluid;
    @Nullable
    private final NBTTagCompound tag;

    private FluidInputKey(Fluid fluid, @Nullable NBTTagCompound tag) {
        this.fluid = fluid;
        this.tag = tag;
    }

    @Nullable
    public static FluidInputKey of(@Nullable FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return null;
        return new FluidInputKey(stack.getFluid(), stack.tag == null ? null : (NBTTagCompound) stack.tag.copy());
    }

    public FluidStack createStack(int amount) {
        return new FluidStack(fluid, amount, tag);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FluidInputKey)) return false;
        FluidInputKey that = (FluidInputKey) other;
        return fluid == that.fluid && (tag == null ? that.tag == null : tag.equals(that.tag));
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(fluid);
        return 31 * result + (tag == null ? 0 : tag.hashCode());
    }
}
