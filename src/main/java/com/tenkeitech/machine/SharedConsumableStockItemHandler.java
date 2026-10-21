/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

/** Read-only MUI2 view of the machine-owned shared consumable backing stacks. */
public final class SharedConsumableStockItemHandler implements IItemHandlerModifiable {

    private final MTESuperCraftingInputME machine;
    private final ItemStack[] clientStacks = new ItemStack[SharedConsumableItemStock.SLOT_COUNT];

    public SharedConsumableStockItemHandler(MTESuperCraftingInputME machine) {
        this.machine = machine;
    }

    @Override
    public int getSlots() {
        return SharedConsumableItemStock.SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        return copy(isServerSide() ? machine.getSharedConsumableStored(slot) : clientStacks[slot]);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        if (!isServerSide()) clientStacks[slot] = copy(stack);
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
        return Integer.MAX_VALUE;
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

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SharedConsumableItemStock.SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Shared consumable stock slot " + slot);
        }
    }
}
