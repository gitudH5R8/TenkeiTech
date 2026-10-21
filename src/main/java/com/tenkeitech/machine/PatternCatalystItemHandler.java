/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

import gregtech.api.util.GTUtility;

/** Transactional MUI2 view over one fixed pattern-slot index's physical item catalysts. */
public final class PatternCatalystItemHandler implements IItemHandlerModifiable {

    private final MTESuperCraftingInputME machine;
    private final int patternSlot;
    private final ItemStack[] localStacks = new ItemStack[PatternAugmentData.CATALYST_SLOT_COUNT];
    private final boolean[] pendingChanges = new boolean[PatternAugmentData.CATALYST_SLOT_COUNT];

    public PatternCatalystItemHandler(MTESuperCraftingInputME machine, int patternSlot) {
        this.machine = machine;
        this.patternSlot = patternSlot;
        for (int slot = 0; slot < localStacks.length; slot++) refreshSlot(slot);
    }

    @Override
    public int getSlots() {
        return PatternAugmentData.CATALYST_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        if (isServerSide() && !pendingChanges[slot]) refreshSlot(slot);
        return copy(localStacks[slot]);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        localStacks[slot] = normalize(stack);
        pendingChanges[slot] = true;
        if (isServerSide() && stacksEqual(localStacks[slot], getMachineStack(slot))) pendingChanges[slot] = false;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        ItemStack current = getStackInSlot(slot);
        if (current != null && !GTUtility.areStacksEqual(current, stack)) return stack;
        int currentAmount = current == null ? 0 : current.stackSize;
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        int inserted = Math.min(stack.stackSize, limit - currentAmount);
        if (inserted <= 0) return stack;

        ItemStack remainder = copyWithSize(stack, stack.stackSize - inserted);
        if (simulate) return remainder;

        ItemStack result = current == null ? stack.copy() : current.copy();
        result.stackSize = currentAmount + inserted;
        localStacks[slot] = result;
        pendingChanges[slot] = true;
        return !isServerSide() || commitSlot(slot) ? remainder : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return null;
        ItemStack current = getStackInSlot(slot);
        if (current == null) return null;

        int extractedAmount = Math.min(amount, current.stackSize);
        ItemStack extracted = copyWithSize(current, extractedAmount);
        if (simulate) return extracted;

        current.stackSize -= extractedAmount;
        localStacks[slot] = normalize(current);
        pendingChanges[slot] = true;
        return !isServerSide() || commitSlot(slot) ? extracted : null;
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return stack != null && stack.getItem() != null && stack.stackSize > 0;
    }

    @Override
    public boolean isSlotFromInventory(int slot, IInventory inventory, int inventorySlot) {
        return false;
    }

    public boolean prepareInsertion(int slot, ItemStack inserted) {
        if (!isServerSide()) return true;
        ItemStack current = getStackInSlot(slot);
        if (current != null && GTUtility.areStacksEqual(current, inserted)
            && current.stackSize >= Math.min(getSlotLimit(slot), inserted.getMaxStackSize())) {
            return true;
        }
        return machine.preparePatternCatalystConfiguration(patternSlot);
    }

    public boolean prepareExtraction(int slot) {
        return !isServerSide() || getStackInSlot(slot) == null
            || machine.preparePatternCatalystConfiguration(patternSlot);
    }

    /** Commits a real MUI slot change. Simulation-only set/restore calls never invoke this method. */
    public boolean commitSlot(int slot) {
        checkSlot(slot);
        if (!isServerSide() || !pendingChanges[slot]) return true;
        PatternAugmentData data = machine.getPatternAugmentData(patternSlot);
        if (data == null) {
            refreshSlot(slot);
            return false;
        }
        ItemStack machineStack = data.getItemCatalyst(slot);
        if (stacksEqual(machineStack, localStacks[slot])) {
            pendingChanges[slot] = false;
            return true;
        }
        data.setItemCatalyst(slot, localStacks[slot]);
        boolean applied = machine.applyPatternAugmentData(patternSlot, data);
        refreshSlot(slot);
        return applied;
    }

    private void refreshSlot(int slot) {
        localStacks[slot] = getMachineStack(slot);
        pendingChanges[slot] = false;
    }

    private ItemStack getMachineStack(int slot) {
        PatternAugmentData data = machine.getPatternAugmentData(patternSlot);
        return data == null ? null : data.getItemCatalyst(slot);
    }

    private boolean isServerSide() {
        return machine.getBaseMetaTileEntity() != null && machine.getBaseMetaTileEntity()
            .isServerSide();
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = Math.min(copy.stackSize, Math.min(64, copy.getMaxStackSize()));
        return copy;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static boolean stacksEqual(ItemStack first, ItemStack second) {
        return first == null ? second == null : second != null && ItemStack.areItemStacksEqual(first, second);
    }

    private static ItemStack copyWithSize(ItemStack stack, int amount) {
        if (amount <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = amount;
        return copy;
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= PatternAugmentData.CATALYST_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Catalyst slot " + slot);
        }
    }
}
