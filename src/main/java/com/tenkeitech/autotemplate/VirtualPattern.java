/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Immutable AE-facing recipe data produced by the Phase 1 scanner. */
public final class VirtualPattern {

    private final PatternKey key;
    private final ItemStack itemInput;
    private final FluidStack fluidInput;
    private final ItemStack output;
    private final long recipeEUt;
    private final int recipeTier;
    private final VirtualPatternDetails details;

    private VirtualPattern(PatternKey key, ItemStack itemInput, FluidStack fluidInput, ItemStack output, long recipeEUt,
        int recipeTier) {
        if (key == null || output == null || output.stackSize <= 0 || recipeEUt <= 0 || recipeTier < 0) {
            throw new IllegalArgumentException("Virtual pattern is incomplete");
        }
        if ((itemInput == null) == (fluidInput == null)) {
            throw new IllegalArgumentException("Virtual pattern must have exactly one input kind");
        }
        if (itemInput != null && itemInput.stackSize <= 0) {
            throw new IllegalArgumentException("Virtual item input is invalid");
        }
        if (fluidInput != null && (fluidInput.getFluid() == null || fluidInput.amount <= 0)) {
            throw new IllegalArgumentException("Virtual fluid input is invalid");
        }
        this.key = key;
        this.itemInput = itemInput == null ? null : itemInput.copy();
        this.fluidInput = fluidInput == null ? null : fluidInput.copy();
        this.output = output.copy();
        this.recipeEUt = recipeEUt;
        this.recipeTier = recipeTier;
        this.details = new VirtualPatternDetails(this);
    }

    public static VirtualPattern forItem(PatternKey key, ItemStack input, ItemStack output, long recipeEUt,
        int recipeTier) {
        return new VirtualPattern(key, input, null, output, recipeEUt, recipeTier);
    }

    public static VirtualPattern forFluid(PatternKey key, FluidStack input, ItemStack output, long recipeEUt,
        int recipeTier) {
        return new VirtualPattern(key, null, input, output, recipeEUt, recipeTier);
    }

    public PatternKey getKey() {
        return key;
    }

    public ItemStack getItemInput() {
        return itemInput == null ? null : itemInput.copy();
    }

    public FluidStack getFluidInput() {
        return fluidInput == null ? null : fluidInput.copy();
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public long getRecipeEUt() {
        return recipeEUt;
    }

    public int getRecipeTier() {
        return recipeTier;
    }

    public VirtualPatternDetails getDetails() {
        return details;
    }
}
