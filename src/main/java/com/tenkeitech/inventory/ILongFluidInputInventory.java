/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;

/**
 * Long quantity contract for inventories whose fluid amounts cannot be represented by a FluidStack int.
 *
 * <p>
 * The regular GT5U inventory API remains the compatibility/search API. Implementations of this contract are
 * consulted only after GT5U has selected the active dual-input inventory and a recipe.
 * </p>
 */
public interface ILongFluidInputInventory {

    /** Returns whether this inventory is the authoritative source for the recipe's every fluid input. */
    boolean ownsAllFluidInputs(GTRecipe recipe);

    /** Returns the authoritative amount for one fluid identity. */
    long getLongFluidAmount(FluidStack identity);

    /** Consumes an already validated amount from one fluid identity. */
    void consumeLongFluid(FluidStack identity, long amount);
}
