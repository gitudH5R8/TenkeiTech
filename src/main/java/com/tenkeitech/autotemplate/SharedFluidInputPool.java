/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import com.tenkeitech.inventory.ILongFluidInputInventory;
import com.tenkeitech.inventory.LongFluidStore;

import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.IDualInputInventory;

/** Fluid pool for one fixed-template input group. */
public final class SharedFluidInputPool implements IDualInputInventory, ILongFluidInputInventory {

    private final LongFluidStore store = new LongFluidStore();

    @Override
    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    public synchronized boolean insert(FluidStack source) {
        if (source == null || source.getFluid() == null || source.amount <= 0) return false;
        return insert(source, source.amount);
    }

    public synchronized boolean insert(FluidStack source, long amount) {
        return source != null && source.getFluid() != null && store.insert(source, amount);
    }

    public synchronized boolean insertAll(Iterable<FluidStack> stacks) {
        if (stacks == null) return false;
        List<FluidStack> pending = new ArrayList<FluidStack>();
        for (FluidStack stack : stacks) {
            if (stack == null || stack.getFluid() == null || stack.amount <= 0) return false;
            pending.add(stack.copy());
        }
        for (FluidStack stack : pending) insert(stack);
        return true;
    }

    @Override
    public synchronized net.minecraft.item.ItemStack[] getItemInputs() {
        return new net.minecraft.item.ItemStack[0];
    }

    @Override
    public synchronized FluidStack[] getFluidInputs() {
        return store.getFluidInputs(this);
    }

    public synchronized List<FluidStack> snapshot() {
        return Collections.unmodifiableList(store.snapshot());
    }

    public synchronized List<LongFluidStore.Snapshot> longSnapshot() {
        return store.longSnapshot();
    }

    public synchronized NBTTagList writeToNBT() {
        return store.writeToNBT();
    }

    public synchronized void readFromNBT(NBTTagList list) {
        store.readFromNBT(list);
    }

    public synchronized void copyFrom(SharedFluidInputPool source) {
        if (source == this) return;
        store.copyFrom(source == null ? null : source.store);
    }

    public synchronized void writeToNBT(NBTTagCompound parent, String key) {
        parent.setTag(key, writeToNBT());
    }

    public synchronized void readFromNBT(NBTTagCompound parent, String key) {
        readFromNBT(parent.getTagList(key, Constants.NBT.TAG_COMPOUND));
    }

    /** Detects GT's direct in-place consumption of the backing stacks. */
    public synchronized boolean observeBackingChanges() {
        return store.observeBackingChanges();
    }

    @Override
    public synchronized boolean ownsAllFluidInputs(GTRecipe recipe) {
        if (recipe == null || recipe.mFluidInputs == null || recipe.mFluidInputs.length == 0) return false;
        for (FluidStack input : recipe.mFluidInputs) {
            if (input != null && input.amount > 0 && !store.contains(input)) return false;
        }
        return true;
    }

    @Override
    public synchronized long getLongFluidAmount(FluidStack identity) {
        return store.getAmount(identity);
    }

    @Override
    public synchronized void consumeLongFluid(FluidStack identity, long amount) {
        if (!store.consume(identity, amount)) throw new IllegalStateException("Insufficient long fluid input");
    }
}
