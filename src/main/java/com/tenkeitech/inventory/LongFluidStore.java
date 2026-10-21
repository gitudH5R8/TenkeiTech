/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

/**
 * Long fluid storage with a bounded number of FluidStack compatibility segments per identity.
 *
 * <p>
 * The backing stack is kept for old GT5U code. It is never the source of truth once long-aware processing is
 * active.
 * </p>
 */
public final class LongFluidStore {

    private static final String NBT_FLUID = "fluid";
    private static final String NBT_AMOUNT = "amount";

    public static final class Snapshot {

        private final FluidStack identity;
        private final long amount;

        private Snapshot(FluidStack identity, long amount) {
            this.identity = identity;
            this.amount = amount;
        }

        public FluidStack getIdentity() {
            return identity.copy();
        }

        public long getAmount() {
            return amount;
        }
    }

    private static final class BackingStack extends FluidStack {

        @Nullable
        private ILongFluidInputInventory owner;

        private BackingStack(FluidStack identity, int amount) {
            super(identity, amount);
        }
    }

    private static final class Entry {

        private final FluidInputKey key;
        private final List<BackingStack> backings = new ArrayList<>();
        private long observedAmount;
        private long amount;

        private Entry(FluidInputKey key, long amount) {
            this.key = key;
            this.amount = amount;
            refreshBacking();
        }

        private void refreshBacking() {
            int segmentCount = LongInputExposure.segmentCount(amount);
            while (backings.size() > segmentCount) backings.remove(backings.size() - 1).owner = null;
            while (backings.size() < segmentCount) backings.add(new BackingStack(key.createStack(1), 1));
            observedAmount = 0;
            for (int index = 0; index < backings.size(); index++) {
                BackingStack backing = backings.get(index);
                backing.amount = LongInputExposure.segmentAmount(amount, index);
                observedAmount += backing.amount;
            }
        }
    }

    private final Map<FluidInputKey, Entry> entries = new LinkedHashMap<>();

    public synchronized boolean isEmpty() {
        reconcileBackingAmounts();
        pruneEmpty();
        return entries.isEmpty();
    }

    public synchronized boolean insert(FluidStack identity, long amount) {
        reconcileBackingAmounts();
        FluidInputKey key = FluidInputKey.of(identity);
        if (key == null || amount <= 0 || amount > Long.MAX_VALUE) return false;
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(key, amount);
            entries.put(key, entry);
            return true;
        }
        if (Long.MAX_VALUE - entry.amount < amount) return false;
        entry.amount += amount;
        entry.refreshBacking();
        return true;
    }

    public synchronized long getAmount(@Nullable FluidStack identity) {
        reconcileBackingAmounts();
        FluidInputKey key = FluidInputKey.of(identity);
        Entry entry = key == null ? null : entries.get(key);
        return entry == null ? 0 : entry.amount;
    }

    public synchronized boolean consume(FluidStack identity, long amount) {
        if (amount <= 0) return true;
        reconcileBackingAmounts();
        FluidInputKey key = FluidInputKey.of(identity);
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null || entry.amount < amount) return false;
        entry.amount -= amount;
        if (entry.amount == 0) {
            clearOwnerReferences(entry);
            entries.remove(key);
        } else entry.refreshBacking();
        return true;
    }

    /** Consumes at most the requested amount and returns the amount actually removed. */
    public synchronized long consumeUpTo(FluidStack identity, long amount) {
        if (amount <= 0) return 0;
        reconcileBackingAmounts();
        FluidInputKey key = FluidInputKey.of(identity);
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null) return 0;
        long consumed = Math.min(amount, entry.amount);
        entry.amount -= consumed;
        if (entry.amount == 0) {
            clearOwnerReferences(entry);
            entries.remove(key);
        } else entry.refreshBacking();
        return consumed;
    }

    public synchronized boolean contains(@Nullable FluidStack identity) {
        reconcileBackingAmounts();
        FluidInputKey key = FluidInputKey.of(identity);
        return key != null && entries.containsKey(key);
    }

    /** Returns bounded backing references for legacy GT5U recipe search. */
    public synchronized FluidStack[] getFluidInputs() {
        return getFluidInputs(null);
    }

    /** Returns bounded backing references annotated with their authoritative inventory. */
    public synchronized FluidStack[] getFluidInputs(@Nullable ILongFluidInputInventory owner) {
        reconcileBackingAmounts();
        pruneEmpty();
        List<FluidStack> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            for (BackingStack backing : entry.backings) {
                backing.owner = owner;
                result.add(backing);
            }
        }
        return result.toArray(new FluidStack[0]);
    }

    @Nullable
    public static ILongFluidInputInventory getOwner(@Nullable FluidStack stack) {
        return stack instanceof BackingStack ? ((BackingStack) stack).owner : null;
    }

    public synchronized List<FluidStack> snapshot() {
        reconcileBackingAmounts();
        pruneEmpty();
        List<FluidStack> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) for (BackingStack backing : entry.backings) result.add(backing.copy());
        return result;
    }

    public synchronized List<Snapshot> longSnapshot() {
        reconcileBackingAmounts();
        pruneEmpty();
        List<Snapshot> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) result.add(new Snapshot(entry.key.createStack(1), entry.amount));
        return result;
    }

    public synchronized void copyFrom(LongFluidStore source) {
        if (source == this) return;
        clearOwnerReferences();
        entries.clear();
        if (source == null) return;
        for (Snapshot snapshot : source.longSnapshot()) insert(snapshot.identity, snapshot.amount);
    }

    /** Detects legacy GT5U's in-place decrement of the bounded backing stack. */
    public synchronized boolean observeBackingChanges() {
        return reconcileBackingAmounts();
    }

    /** Synchronizes the compatibility window after a long-aware commit. */
    public synchronized void acknowledgeBackingAmounts() {
        for (Entry entry : entries.values()) entry.refreshBacking();
    }

    public synchronized NBTTagList writeToNBT() {
        reconcileBackingAmounts();
        pruneEmpty();
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries.values()) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setTag(
                NBT_FLUID,
                entry.key.createStack(1)
                    .writeToNBT(new NBTTagCompound()));
            saved.setLong(NBT_AMOUNT, entry.amount);
            list.appendTag(saved);
        }
        return list;
    }

    /** Reads explicit fluid identities and authoritative long amounts. */
    public synchronized void readFromNBT(@Nullable NBTTagList list) {
        clearOwnerReferences();
        entries.clear();
        if (list == null) return;
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound saved = list.getCompoundTagAt(index);
            if (!saved.hasKey(NBT_FLUID, Constants.NBT.TAG_COMPOUND)
                || !saved.hasKey(NBT_AMOUNT, Constants.NBT.TAG_LONG)) continue;
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(saved.getCompoundTag(NBT_FLUID));
            long amount = saved.getLong(NBT_AMOUNT);
            if (fluid != null && amount > 0) insert(fluid, amount);
        }
        acknowledgeBackingAmounts();
    }

    private boolean reconcileBackingAmounts() {
        boolean changed = false;
        Iterator<Map.Entry<FluidInputKey, Entry>> iterator = entries.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next()
                .getValue();
            long current = 0;
            for (BackingStack backing : entry.backings) current += Math.max(0, backing.amount);
            if (current != entry.observedAmount) {
                long consumed = entry.observedAmount - current;
                if (consumed > 0) entry.amount = Math.max(0, entry.amount - consumed);
                else if (Long.MAX_VALUE - entry.amount < current - entry.observedAmount) entry.amount = Long.MAX_VALUE;
                else entry.amount += current - entry.observedAmount;
                entry.observedAmount = current;
                changed = true;
            }
            if (entry.amount <= 0) {
                clearOwnerReferences(entry);
                iterator.remove();
            } else if (entry.backings.size() != LongInputExposure.segmentCount(entry.amount)
                || current != LongInputExposure.visibleAmount(entry.amount)) {
                    entry.refreshBacking();
                    changed = true;
                }
        }
        return changed;
    }

    private void pruneEmpty() {
        Iterator<Map.Entry<FluidInputKey, Entry>> iterator = entries.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next()
                .getValue();
            if (entry.amount <= 0) {
                clearOwnerReferences(entry);
                iterator.remove();
            }
        }
    }

    private void clearOwnerReferences() {
        for (Entry entry : entries.values()) clearOwnerReferences(entry);
    }

    private static void clearOwnerReferences(Entry entry) {
        for (BackingStack backing : entry.backings) backing.owner = null;
    }

}
