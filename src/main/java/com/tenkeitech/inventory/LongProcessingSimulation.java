/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;

/** Isolated long-input state used by GT5U's non-consuming recipe simulations. */
public final class LongProcessingSimulation {

    @Nullable
    private final ILongFluidInputInventory sourceFluidInventory;
    @Nullable
    private final ILongItemInputInventory sourceItemInventory;
    @Nullable
    private SimulatedFluidInventory fluidInventory;
    @Nullable
    private SimulatedItemInventory itemInventory;
    private boolean initialized;

    public LongProcessingSimulation(@Nullable ILongFluidInputInventory fluids,
        @Nullable ILongItemInputInventory items) {
        sourceFluidInventory = fluids;
        sourceItemInventory = items;
    }

    public double calculate(GTRecipe recipe, int limit, FluidStack[] legacyFluids, ItemStack[] legacyItems) {
        initialize(recipe);
        return LongFluidProcessing.calculate(fluidInventory, itemInventory, recipe, limit, legacyFluids, legacyItems);
    }

    public void consume(GTRecipe recipe, int parallel, FluidStack[] legacyFluids, ItemStack[] legacyItems) {
        initialize(recipe);
        LongFluidProcessing.consume(fluidInventory, itemInventory, recipe, parallel, legacyFluids, legacyItems);
    }

    private void initialize(GTRecipe recipe) {
        if (initialized) return;
        fluidInventory = sourceFluidInventory == null ? null
            : new SimulatedFluidInventory(sourceFluidInventory, recipe);
        itemInventory = sourceItemInventory == null ? null : new SimulatedItemInventory(sourceItemInventory);
        initialized = true;
    }

    private static final class SimulatedFluidInventory implements ILongFluidInputInventory {

        private final Map<FluidInputKey, Long> amounts = new LinkedHashMap<>();

        private SimulatedFluidInventory(ILongFluidInputInventory source, GTRecipe recipe) {
            if (recipe == null || recipe.mFluidInputs == null) return;
            for (FluidStack input : recipe.mFluidInputs) {
                FluidInputKey key = FluidInputKey.of(input);
                if (key != null && !amounts.containsKey(key)) amounts.put(key, source.getLongFluidAmount(input));
            }
        }

        @Override
        public boolean ownsAllFluidInputs(GTRecipe recipe) {
            if (recipe == null || recipe.mFluidInputs == null || recipe.mFluidInputs.length == 0) return false;
            for (FluidStack input : recipe.mFluidInputs) {
                if (input != null && input.amount > 0 && getLongFluidAmount(input) <= 0) return false;
            }
            return true;
        }

        @Override
        public long getLongFluidAmount(FluidStack identity) {
            FluidInputKey key = FluidInputKey.of(identity);
            Long amount = key == null ? null : amounts.get(key);
            return amount == null ? 0 : amount;
        }

        @Override
        public void consumeLongFluid(FluidStack identity, long amount) {
            if (amount <= 0) return;
            FluidInputKey key = FluidInputKey.of(identity);
            Long available = key == null ? null : amounts.get(key);
            if (available == null || available < amount) {
                throw new IllegalStateException("Simulated long fluid inventory changed during recipe calculation");
            }
            amounts.put(key, available - amount);
        }
    }

    private static final class SimulatedItemInventory implements ILongItemInputInventory {

        private static final class Entry {

            private final ItemStack identity;
            private long amount;

            private Entry(ItemStack identity, long amount) {
                this.identity = identity.copy();
                this.identity.stackSize = 1;
                this.amount = amount;
            }
        }

        private final Map<ItemInputKey, Entry> entries = new LinkedHashMap<>();

        private SimulatedItemInventory(ILongItemInputInventory source) {
            for (LongItemStore.Snapshot snapshot : source.longItemSnapshot()) {
                ItemStack identity = snapshot.getIdentity();
                ItemInputKey key = ItemInputKey.of(identity);
                if (key == null || snapshot.getAmount() <= 0) continue;
                Entry entry = entries.get(key);
                if (entry == null) entries.put(key, new Entry(identity, snapshot.getAmount()));
                else entry.amount = saturatedAdd(entry.amount, snapshot.getAmount());
            }
        }

        @Override
        public List<LongItemStore.Snapshot> longItemSnapshot() {
            List<LongItemStore.Snapshot> result = new ArrayList<>(entries.size());
            for (Entry entry : entries.values()) {
                if (entry.amount > 0) result.add(new LongItemStore.Snapshot(entry.identity, entry.amount));
            }
            return result;
        }

        @Override
        public long getLongItemAmount(ItemStack identity) {
            ItemInputKey key = ItemInputKey.of(identity);
            Entry entry = key == null ? null : entries.get(key);
            return entry == null ? 0 : entry.amount;
        }

        @Override
        public void consumeLongItem(ItemStack identity, long amount) {
            if (amount <= 0) return;
            ItemInputKey key = ItemInputKey.of(identity);
            Entry entry = key == null ? null : entries.get(key);
            if (entry == null || entry.amount < amount) {
                throw new IllegalStateException("Simulated long item inventory changed during recipe calculation");
            }
            entry.amount -= amount;
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }
}
