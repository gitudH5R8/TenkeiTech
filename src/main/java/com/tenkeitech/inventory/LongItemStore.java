/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import gregtech.api.util.GTUtility;

/** Compact long item storage with a bounded number of backing ItemStacks per identity. */
public final class LongItemStore {

    private static final String NBT_ITEM = "item";
    private static final String NBT_AMOUNT = "amount";

    public static final class Snapshot {

        private final ItemStack identity;
        private final long amount;

        public Snapshot(ItemStack identity, long amount) {
            this.identity = identity;
            this.amount = amount;
        }

        public ItemStack getIdentity() {
            return identity.copy();
        }

        public long getAmount() {
            return amount;
        }
    }

    private static final Map<ItemStack, WeakReference<ILongItemInputInventory>> OWNERS = Collections
        .synchronizedMap(new WeakHashMap<>());

    private static final class Entry {

        private final ItemInputKey key;
        private final ItemStack identity;
        private final List<ItemStack> backings = new ArrayList<>();
        private long amount;
        private long observedAmount;

        private Entry(ItemStack identity, long amount) {
            this.key = ItemInputKey.of(identity);
            this.identity = identity.copy();
            this.identity.stackSize = 1;
            this.amount = amount;
            refresh();
        }

        private void refresh() {
            int segmentCount = LongInputExposure.segmentCount(amount);
            while (backings.size() > segmentCount) {
                OWNERS.remove(backings.remove(backings.size() - 1));
            }
            while (backings.size() < segmentCount) backings.add(identity.copy());
            observedAmount = 0;
            for (int index = 0; index < backings.size(); index++) {
                ItemStack backing = backings.get(index);
                backing.stackSize = LongInputExposure.segmentAmount(amount, index);
                observedAmount += backing.stackSize;
            }
        }
    }

    private final Map<ItemInputKey, Entry> entries = new LinkedHashMap<>();

    public synchronized boolean isEmpty() {
        reconcileBackingAmounts();
        pruneEmpty();
        return entries.isEmpty();
    }

    public synchronized boolean insert(ItemStack identity, long amount) {
        reconcileBackingAmounts();
        ItemInputKey key = ItemInputKey.of(identity);
        if (key == null || amount <= 0) return false;
        Entry entry = entries.get(key);
        if (entry == null) {
            entries.put(key, new Entry(identity, amount));
            return true;
        }
        if (Long.MAX_VALUE - entry.amount < amount) return false;
        entry.amount += amount;
        entry.refresh();
        return true;
    }

    public synchronized long getAmount(@Nullable ItemStack identity) {
        reconcileBackingAmounts();
        ItemInputKey key = ItemInputKey.of(identity);
        Entry entry = key == null ? null : entries.get(key);
        return entry == null ? 0 : entry.amount;
    }

    public synchronized boolean consume(ItemStack identity, long amount) {
        if (amount <= 0) return true;
        reconcileBackingAmounts();
        ItemInputKey key = ItemInputKey.of(identity);
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null || entry.amount < amount) return false;
        entry.amount -= amount;
        if (entry.amount == 0) {
            clearOwnerReferences(entry);
            entries.remove(key);
        } else entry.refresh();
        return true;
    }

    public synchronized ItemStack[] getItemInputs() {
        return getItemInputs(null);
    }

    public synchronized ItemStack[] getItemInputs(@Nullable ILongItemInputInventory owner) {
        reconcileBackingAmounts();
        pruneEmpty();
        List<ItemStack> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            for (ItemStack backing : entry.backings) {
                if (owner == null) OWNERS.remove(backing);
                else OWNERS.put(backing, new WeakReference<>(owner));
                result.add(backing);
            }
        }
        return result.toArray(new ItemStack[0]);
    }

    @Nullable
    public static ILongItemInputInventory getOwner(@Nullable ItemStack stack) {
        if (stack == null) return null;
        WeakReference<ILongItemInputInventory> reference = OWNERS.get(stack);
        return reference == null ? null : reference.get();
    }

    public synchronized List<Snapshot> longSnapshot() {
        reconcileBackingAmounts();
        pruneEmpty();
        List<Snapshot> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) result.add(new Snapshot(entry.identity, entry.amount));
        return Collections.unmodifiableList(result);
    }

    public synchronized List<ItemStack> snapshot() {
        List<ItemStack> result = new ArrayList<>();
        reconcileBackingAmounts();
        pruneEmpty();
        for (Entry entry : entries.values()) for (ItemStack backing : entry.backings) result.add(backing.copy());
        return Collections.unmodifiableList(result);
    }

    public synchronized void copyFrom(LongItemStore source) {
        if (source == this) return;
        clearOwnerReferences();
        entries.clear();
        if (source == null) return;
        for (Snapshot snapshot : source.longSnapshot()) insert(snapshot.getIdentity(), snapshot.getAmount());
    }

    public synchronized boolean observeBackingChanges() {
        return reconcileBackingAmounts();
    }

    public synchronized void acknowledgeBackingAmounts() {
        for (Entry entry : entries.values()) entry.refresh();
    }

    public synchronized NBTTagList writeToNBT() {
        reconcileBackingAmounts();
        pruneEmpty();
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries.values()) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setTag(NBT_ITEM, GTUtility.saveItem(entry.identity));
            saved.setLong(NBT_AMOUNT, entry.amount);
            list.appendTag(saved);
        }
        return list;
    }

    /** Reads explicit item identities and authoritative long amounts. */
    public synchronized void readFromNBT(@Nullable NBTTagList list) {
        clearOwnerReferences();
        entries.clear();
        if (list == null) return;
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound saved = list.getCompoundTagAt(index);
            if (!saved.hasKey(NBT_ITEM, Constants.NBT.TAG_COMPOUND)
                || !saved.hasKey(NBT_AMOUNT, Constants.NBT.TAG_LONG)) continue;
            ItemStack item = GTUtility.loadItem(saved.getCompoundTag(NBT_ITEM));
            long amount = saved.getLong(NBT_AMOUNT);
            if (item != null && item.stackSize > 0 && amount > 0) insert(item, amount);
        }
        acknowledgeBackingAmounts();
    }

    private boolean reconcileBackingAmounts() {
        boolean changed = false;
        Iterator<Map.Entry<ItemInputKey, Entry>> iterator = entries.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next()
                .getValue();
            long current = 0;
            for (ItemStack backing : entry.backings) current += Math.max(0, backing.stackSize);
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
                    entry.refresh();
                    changed = true;
                }
        }
        return changed;
    }

    private void pruneEmpty() {
        Iterator<Map.Entry<ItemInputKey, Entry>> iterator = entries.entrySet()
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
        for (ItemStack backing : entry.backings) OWNERS.remove(backing);
    }
}
