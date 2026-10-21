/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import gregtech.GTLoggers;

/** Sparse persistent storage for machine-wide, physically held fluid catalysts. */
public final class FluidCatalystData {

    public static final int TANK_COUNT = 9;

    private static final String NBT_TANK = "tank";
    private static final String NBT_FLUID = "fluid";

    private final FluidStack[] catalysts = new FluidStack[TANK_COUNT];

    public FluidCatalystData() {}

    public FluidCatalystData(FluidCatalystData source) {
        copyFrom(source);
    }

    public FluidCatalystData copy() {
        return new FluidCatalystData(this);
    }

    public void copyFrom(FluidCatalystData source) {
        for (int i = 0; i < TANK_COUNT; i++) {
            set(i, source.catalysts[i]);
        }
    }

    @Nullable
    public FluidStack get(int index) {
        checkIndex(index);
        FluidStack catalyst = catalysts[index];
        return catalyst == null ? null : catalyst.copy();
    }

    public void set(int index, @Nullable FluidStack catalyst) {
        checkIndex(index);
        catalysts[index] = isValid(catalyst) ? catalyst.copy() : null;
    }

    public boolean isEmpty() {
        for (FluidStack catalyst : catalysts) {
            if (isValid(catalyst)) return false;
        }
        return true;
    }

    public NBTTagList writeToNBT() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidStack catalyst = catalysts[i];
            if (!isValid(catalyst)) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(NBT_TANK, i);
            entry.setTag(NBT_FLUID, catalyst.writeToNBT(new NBTTagCompound()));
            list.appendTag(entry);
        }
        return list;
    }

    public static FluidCatalystData readFromNBT(NBTTagList list) {
        FluidCatalystData data = new FluidCatalystData();
        boolean[] occupied = new boolean[TANK_COUNT];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int index = entry.getInteger(NBT_TANK);
            if (!isValidIndex(index)) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Ignoring out-of-range shared fluid catalyst index {} in TenkeiTech super pattern buffer",
                    index);
                continue;
            }
            if (occupied[index]) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Ignoring duplicate shared fluid catalyst index {} in TenkeiTech super pattern buffer",
                    index);
                continue;
            }
            FluidStack catalyst = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag(NBT_FLUID));
            if (!isValid(catalyst)) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Ignoring invalid shared fluid catalyst at index {} in TenkeiTech super pattern buffer: {}",
                    index,
                    entry);
                continue;
            }
            data.set(index, catalyst);
            occupied[index] = true;
        }
        return data;
    }

    public static FluidCatalystData readFromNBT(NBTTagCompound nbt, String key) {
        return readFromNBT(nbt.getTagList(key, Constants.NBT.TAG_COMPOUND));
    }

    private static boolean isValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean isValidIndex(int index) {
        return index >= 0 && index < TANK_COUNT;
    }

    private static void checkIndex(int index) {
        if (!isValidIndex(index)) {
            throw new IndexOutOfBoundsException("Catalyst tank " + index + " is outside 0.." + (TANK_COUNT - 1));
        }
    }
}
