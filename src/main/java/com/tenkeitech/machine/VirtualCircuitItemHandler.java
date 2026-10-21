/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

/** Read-only MUI2 view which syncs the circuit stored by a pattern as an item icon. */
public final class VirtualCircuitItemHandler implements IItemHandlerModifiable {

    private final MTESuperCraftingInputME machine;
    private final int patternSlot;
    private ItemStack clientStack;

    public VirtualCircuitItemHandler(MTESuperCraftingInputME machine, int patternSlot) {
        this.machine = machine;
        this.patternSlot = patternSlot;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        return copy(isServerSide() ? machine.getPatternVirtualCircuitStack(patternSlot) : clientStack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        if (!isServerSide()) clientStack = displayCopy(stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        checkSlot(slot);
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        return null;
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return false;
    }

    @Override
    public boolean isSlotFromInventory(int slot, IInventory inventory, int inventorySlot) {
        checkSlot(slot);
        return false;
    }

    private boolean isServerSide() {
        return machine.getBaseMetaTileEntity() != null && machine.getBaseMetaTileEntity()
            .isServerSide();
    }

    private static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = copy(stack);
        if (copy != null) copy.stackSize = 1;
        return copy;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static void checkSlot(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException("Virtual circuit slot " + slot);
    }
}
