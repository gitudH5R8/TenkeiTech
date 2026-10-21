/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.tenkeitech.inventory.ILongItemInputInventory;
import com.tenkeitech.inventory.LongItemStore;

import gregtech.common.tileentities.machines.IDualInputInventory;

/** Machine-wide item storage backed by one long entry per item identity. */
public final class SharedItemInputPool implements IDualInputInventory, ILongItemInputInventory {

    private final LongItemStore store = new LongItemStore();

    @Override
    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    public synchronized boolean insert(ItemStack source) {
        return source != null && source.stackSize > 0 && insert(source, source.stackSize);
    }

    public synchronized boolean insert(ItemStack source, long amount) {
        return source != null && source.getItem() != null && store.insert(source, amount);
    }

    public synchronized boolean insertAll(Iterable<ItemStack> stacks) {
        if (stacks == null) return false;
        java.util.ArrayList<ItemStack> pending = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) return false;
            pending.add(stack.copy());
        }
        for (ItemStack stack : pending) insert(stack);
        return true;
    }

    @Override
    public synchronized ItemStack[] getItemInputs() {
        return store.getItemInputs(this);
    }

    @Override
    public synchronized net.minecraftforge.fluids.FluidStack[] getFluidInputs() {
        return new net.minecraftforge.fluids.FluidStack[0];
    }

    public synchronized List<ItemStack> snapshot() {
        return Collections.unmodifiableList(store.snapshot());
    }

    public synchronized List<LongItemStore.Snapshot> longSnapshot() {
        return store.longSnapshot();
    }

    public synchronized NBTTagList writeToNBT() {
        return store.writeToNBT();
    }

    public synchronized void readFromNBT(NBTTagList list) {
        store.readFromNBT(list);
    }

    public synchronized void copyFrom(SharedItemInputPool source) {
        if (source == this) return;
        store.copyFrom(source == null ? null : source.store);
    }

    public synchronized void writeToNBT(NBTTagCompound parent, String key) {
        parent.setTag(key, writeToNBT());
    }

    public synchronized void readFromNBT(NBTTagCompound parent, String key) {
        readFromNBT(parent.getTagList(key, Constants.NBT.TAG_COMPOUND));
    }

    public synchronized boolean observeBackingChanges() {
        return store.observeBackingChanges();
    }

    public synchronized void acknowledgeBackingAmounts() {
        store.acknowledgeBackingAmounts();
    }

    @Override
    public synchronized List<LongItemStore.Snapshot> longItemSnapshot() {
        return store.longSnapshot();
    }

    @Override
    public synchronized long getLongItemAmount(ItemStack identity) {
        return store.getAmount(identity);
    }

    @Override
    public synchronized void consumeLongItem(ItemStack identity, long amount) {
        if (!store.consume(identity, amount)) throw new IllegalStateException("Insufficient long item input");
    }
}
