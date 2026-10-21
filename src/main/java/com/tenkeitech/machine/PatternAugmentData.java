/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import gregtech.GTLoggers;
import gregtech.api.util.GTUtility;

/** Persistent, slot-local configuration which must never be mixed into the consumable input buffers. */
public final class PatternAugmentData {

    public static final int CATALYST_SLOT_COUNT = 9;

    private static final String NBT_ITEM_CATALYSTS = "itemCatalysts";
    private static final String NBT_FLUID_CATALYSTS = "fluidCatalysts";
    private static final String NBT_SLOT = "slot";
    private static final String NBT_TANK = "tank";
    private static final String NBT_STACK = "stack";
    private static final String NBT_FLUID = "fluid";

    private final ItemStack[] itemCatalysts = new ItemStack[CATALYST_SLOT_COUNT];
    private final FluidStack[] fluidCatalysts = new FluidStack[CATALYST_SLOT_COUNT];

    public PatternAugmentData() {}

    public PatternAugmentData(PatternAugmentData source) {
        copyFrom(source);
    }

    public PatternAugmentData copy() {
        return new PatternAugmentData(this);
    }

    public void copyFrom(PatternAugmentData source) {
        for (int i = 0; i < CATALYST_SLOT_COUNT; i++) {
            setItemCatalyst(i, source.itemCatalysts[i]);
            setFluidCatalyst(i, source.fluidCatalysts[i]);
        }
    }

    @Nullable
    public ItemStack getItemCatalyst(int index) {
        checkIndex(index);
        ItemStack catalyst = itemCatalysts[index];
        return catalyst == null ? null : catalyst.copy();
    }

    public void setItemCatalyst(int index, @Nullable ItemStack catalyst) {
        checkIndex(index);
        itemCatalysts[index] = isValid(catalyst) ? catalyst.copy() : null;
    }

    @Nullable
    public FluidStack getFluidCatalyst(int index) {
        checkIndex(index);
        FluidStack catalyst = fluidCatalysts[index];
        return catalyst == null ? null : catalyst.copy();
    }

    public void setFluidCatalyst(int index, @Nullable FluidStack catalyst) {
        checkIndex(index);
        fluidCatalysts[index] = isValid(catalyst) ? catalyst.copy() : null;
    }

    public boolean isEmpty() {
        for (int i = 0; i < CATALYST_SLOT_COUNT; i++) {
            if (isValid(itemCatalysts[i]) || isValid(fluidCatalysts[i])) return false;
        }
        return true;
    }

    public boolean hasItemCatalysts() {
        for (ItemStack catalyst : itemCatalysts) {
            if (isValid(catalyst)) return true;
        }
        return false;
    }

    public boolean hasFluidCatalysts() {
        for (FluidStack catalyst : fluidCatalysts) {
            if (isValid(catalyst)) return true;
        }
        return false;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList itemList = new NBTTagList();
        NBTTagList fluidList = new NBTTagList();
        for (int i = 0; i < CATALYST_SLOT_COUNT; i++) {
            ItemStack itemCatalyst = itemCatalysts[i];
            if (isValid(itemCatalyst)) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger(NBT_SLOT, i);
                entry.setTag(NBT_STACK, GTUtility.saveItem(itemCatalyst));
                itemList.appendTag(entry);
            }

            FluidStack fluidCatalyst = fluidCatalysts[i];
            if (isValid(fluidCatalyst)) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger(NBT_TANK, i);
                entry.setTag(NBT_FLUID, fluidCatalyst.writeToNBT(new NBTTagCompound()));
                fluidList.appendTag(entry);
            }
        }
        if (itemList.tagCount() > 0) nbt.setTag(NBT_ITEM_CATALYSTS, itemList);
        if (fluidList.tagCount() > 0) nbt.setTag(NBT_FLUID_CATALYSTS, fluidList);
        return nbt;
    }

    public static PatternAugmentData readFromNBT(NBTTagCompound nbt, int patternSlot) {
        PatternAugmentData data = new PatternAugmentData();
        boolean[] occupiedItems = new boolean[CATALYST_SLOT_COUNT];
        NBTTagList itemList = nbt.getTagList(NBT_ITEM_CATALYSTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < itemList.tagCount(); i++) {
            NBTTagCompound entry = itemList.getCompoundTagAt(i);
            int index = entry.getInteger(NBT_SLOT);
            if (!isValidIndex(index)) {
                warnInvalidIndex("item", patternSlot, index);
                continue;
            }
            if (occupiedItems[index]) {
                warnDuplicate("item", patternSlot, index);
                continue;
            }
            ItemStack catalyst = GTUtility.loadItem(entry.getCompoundTag(NBT_STACK));
            if (!isValid(catalyst)) {
                warnInvalidStack("item", patternSlot, index, entry);
                continue;
            }
            data.setItemCatalyst(index, catalyst);
            occupiedItems[index] = true;
        }

        boolean[] occupiedFluids = new boolean[CATALYST_SLOT_COUNT];
        NBTTagList fluidList = nbt.getTagList(NBT_FLUID_CATALYSTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fluidList.tagCount(); i++) {
            NBTTagCompound entry = fluidList.getCompoundTagAt(i);
            int index = entry.getInteger(NBT_TANK);
            if (!isValidIndex(index)) {
                warnInvalidIndex("fluid", patternSlot, index);
                continue;
            }
            if (occupiedFluids[index]) {
                warnDuplicate("fluid", patternSlot, index);
                continue;
            }
            FluidStack catalyst = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag(NBT_FLUID));
            if (!isValid(catalyst)) {
                warnInvalidStack("fluid", patternSlot, index, entry);
                continue;
            }
            data.setFluidCatalyst(index, catalyst);
            occupiedFluids[index] = true;
        }
        return data;
    }

    private static boolean isValid(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.stackSize > 0;
    }

    private static boolean isValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() != null && stack.amount > 0;
    }

    private static boolean isValidIndex(int index) {
        return index >= 0 && index < CATALYST_SLOT_COUNT;
    }

    private static void checkIndex(int index) {
        if (!isValidIndex(index)) {
            throw new IndexOutOfBoundsException(
                "Catalyst index " + index + " is outside 0.." + (CATALYST_SLOT_COUNT - 1));
        }
    }

    private static void warnInvalidIndex(String type, int patternSlot, int index) {
        GTLoggers.GT_FML_LOGGER
            .warn("Ignoring out-of-range {} catalyst index {} in TenkeiTech pattern slot {}", type, index, patternSlot);
    }

    private static void warnDuplicate(String type, int patternSlot, int index) {
        GTLoggers.GT_FML_LOGGER
            .warn("Ignoring duplicate {} catalyst index {} in TenkeiTech pattern slot {}", type, index, patternSlot);
    }

    private static void warnInvalidStack(String type, int patternSlot, int index, NBTTagCompound entry) {
        GTLoggers.GT_FML_LOGGER.warn(
            "Ignoring invalid {} catalyst at index {} in TenkeiTech pattern slot {}: {}",
            type,
            index,
            patternSlot,
            entry);
    }
}
