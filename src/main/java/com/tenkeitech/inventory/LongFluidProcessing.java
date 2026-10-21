/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;

/** Bridges GT5U's int callback API to an authoritative long fluid inventory. */
public final class LongFluidProcessing {

    private LongFluidProcessing() {}

    public static boolean canHandle(GTRecipe recipe) {
        if (recipe == null) return false;
        if (recipe.mAltFluidInputs != null) {
            for (FluidStack[] alternatives : recipe.mAltFluidInputs) {
                if (alternatives != null && alternatives.length > 0) return false;
            }
        }
        if (recipe.mFluidInputChances != null) {
            for (int chance : recipe.mFluidInputChances) if (chance < 10_000) return false;
        }
        return true;
    }

    public static double calculate(ILongFluidInputInventory inventory, GTRecipe recipe, int limit,
        FluidStack[] legacyFluids, ItemStack[] itemInputs) {
        return calculate(inventory, null, recipe, limit, legacyFluids, itemInputs);
    }

    public static double calculate(ILongFluidInputInventory fluidInventory, ILongItemInputInventory itemInventory,
        GTRecipe recipe, int limit, FluidStack[] legacyFluids, ItemStack[] itemInputs) {
        if (recipe == null || limit <= 0) return 0;
        if (fluidInventory != null && !canHandle(recipe)) return 0;
        if (itemInventory != null && !LongItemProcessing.canHandle(recipe)) return 0;
        int itemLimit = itemInventory == null ? calculateItemLimit(recipe, limit, itemInputs)
            : (int) Math.min(limit, Math.floor(LongItemProcessing.calculate(itemInventory, recipe, limit)));
        Map<FluidInputKey, Long> requirements = aggregateFluidRequirements(recipe);
        long fluidLimit = limit;
        if (!requirements.isEmpty()) {
            if (fluidInventory == null) {
                GTRecipe fluidOnly = recipe.copyShallow();
                fluidOnly.mInputs = new ItemStack[0];
                fluidLimit = (long) Math
                    .floor(fluidOnly.maxParallelCalculatedByInputs(limit, legacyFluids, new ItemStack[0]));
            } else {
                for (Map.Entry<FluidInputKey, Long> requirement : requirements.entrySet()) {
                    FluidStack identity = requirement.getKey()
                        .createStack(1);
                    long required = requirement.getValue();
                    long available = fluidInventory.getLongFluidAmount(identity);
                    if (required <= 0 || available < required) return 0;
                    fluidLimit = Math.min(fluidLimit, available / required);
                }
            }
        }
        return Math.min(itemLimit, Math.min((long) limit, fluidLimit));
    }

    public static void consume(ILongFluidInputInventory inventory, GTRecipe recipe, int parallel,
        FluidStack[] legacyFluids, ItemStack[] itemInputs) {
        consume(inventory, null, recipe, parallel, legacyFluids, itemInputs);
    }

    public static void consume(ILongFluidInputInventory fluidInventory, ILongItemInputInventory itemInventory,
        GTRecipe recipe, int parallel, FluidStack[] legacyFluids, ItemStack[] itemInputs) {
        if (recipe == null || parallel <= 0) return;
        if (fluidInventory != null && !canHandle(recipe)) {
            throw new IllegalStateException("Unsupported long fluid recipe inputs");
        }
        if (itemInventory != null && !LongItemProcessing.canHandle(recipe)) {
            throw new IllegalStateException("Unsupported long item recipe inputs");
        }
        Map<FluidInputKey, Long> requirements = aggregateFluidRequirements(recipe);
        if (fluidInventory != null) {
            for (Map.Entry<FluidInputKey, Long> requirement : requirements.entrySet()) {
                long amount = multiplyExactOrThrow(requirement.getValue(), parallel);
                if (fluidInventory.getLongFluidAmount(
                    requirement.getKey()
                        .createStack(1))
                    < amount) {
                    throw new IllegalStateException("Long fluid inventory changed during recipe calculation");
                }
            }
        } else if (!requirements.isEmpty()) {
            GTRecipe fluidOnly = recipe.copyShallow();
            fluidOnly.mInputs = new ItemStack[0];
            if (fluidOnly.maxParallelCalculatedByInputs(parallel, legacyFluids, new ItemStack[0]) < parallel) {
                throw new IllegalStateException("Legacy fluid inventory changed during recipe calculation");
            }
        }
        if (itemInventory != null && LongItemProcessing.calculate(itemInventory, recipe, parallel) < parallel) {
            throw new IllegalStateException("Long item inventory changed during recipe calculation");
        } else if (itemInventory == null && calculateItemLimit(recipe, parallel, itemInputs) < parallel) {
            throw new IllegalStateException("Legacy item inventory changed during recipe calculation");
        }

        if (fluidInventory != null) {
            for (Map.Entry<FluidInputKey, Long> requirement : requirements.entrySet()) {
                long amount = multiplyExactOrThrow(requirement.getValue(), parallel);
                fluidInventory.consumeLongFluid(
                    requirement.getKey()
                        .createStack(1),
                    amount);
            }
        }
        if (itemInventory != null) {
            LongItemProcessing.consume(itemInventory, recipe, parallel);
        } else if (recipe.mInputs != null && recipe.mInputs.length > 0) {
            // Let GT5U retain its established item matching and in-place item consumption semantics when no long
            // item owner is present.
            GTRecipe itemOnly = recipe.copyShallow();
            itemOnly.mFluidInputs = GTValues.emptyFluidStackArray;
            itemOnly.mAltFluidInputs = new FluidStack[0][];
            itemOnly.mFluidInputChances = new int[0];
            itemOnly.consumeInput(parallel, GTValues.emptyFluidStackArray, itemInputs);
        }
        if (fluidInventory == null && !requirements.isEmpty()) {
            GTRecipe fluidOnly = recipe.copyShallow();
            fluidOnly.mInputs = new ItemStack[0];
            fluidOnly.consumeInput(parallel, legacyFluids, new ItemStack[0]);
        }
    }

    private static int calculateItemLimit(GTRecipe recipe, int limit, ItemStack[] itemInputs) {
        if (recipe.mInputs == null || recipe.mInputs.length == 0) return limit;
        GTRecipe itemOnly = recipe.copyShallow();
        itemOnly.mFluidInputs = GTValues.emptyFluidStackArray;
        itemOnly.mAltFluidInputs = new FluidStack[0][];
        itemOnly.mFluidInputChances = new int[0];
        return (int) Math.min(
            limit,
            Math.max(
                0,
                Math.floor(itemOnly.maxParallelCalculatedByInputs(limit, GTValues.emptyFluidStackArray, itemInputs))));
    }

    private static Map<FluidInputKey, Long> aggregateFluidRequirements(GTRecipe recipe) {
        Map<FluidInputKey, Long> result = new LinkedHashMap<>();
        if (recipe.mFluidInputs == null) return result;
        for (FluidStack input : recipe.mFluidInputs) {
            if (input == null || input.amount <= 0) continue;
            FluidInputKey key = FluidInputKey.of(input);
            if (key == null) continue;
            long previous = result.containsKey(key) ? result.get(key) : 0;
            if (Long.MAX_VALUE - previous < input.amount) throw new ArithmeticException("Fluid recipe input overflow");
            result.put(key, previous + input.amount);
        }
        return result;
    }

    private static long multiplyExactOrThrow(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) throw new ArithmeticException("Fluid consumption overflow");
        return left * right;
    }
}
