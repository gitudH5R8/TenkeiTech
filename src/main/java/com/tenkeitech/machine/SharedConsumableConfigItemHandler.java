/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

/** Phantom MUI2 handler for shared consumable item identities. */
public final class SharedConsumableConfigItemHandler implements IItemHandlerModifiable {

    private final MTESuperCraftingInputME machine;
    private final ItemStack[] clientStacks = new ItemStack[SharedConsumableItemStock.SLOT_COUNT];

    public SharedConsumableConfigItemHandler(MTESuperCraftingInputME machine) {
        this.machine = machine;
    }

    @Override
    public int getSlots() {
        return SharedConsumableItemStock.SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        checkSlot(slot);
        return copy(isServerSide() ? machine.getSharedConsumableConfig(slot) : clientStacks[slot]);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        ItemStack normalized = normalize(stack);
        if (isServerSide()) machine.setSharedConsumableConfig(slot, normalized);
        else clientStacks[slot] = normalized;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        checkSlot(slot);
        if (!isItemValid(slot, stack)) return stack;
        if (!simulate) setStackInSlot(slot, stack);
        return null;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        ItemStack current = getStackInSlot(slot);
        if (current == null || amount <= 0) return null;
        if (!simulate) setStackInSlot(slot, null);
        return current;
    }

    @Override
    public int getSlotLimit(int slot) {
        checkSlot(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        checkSlot(slot);
        return !isServerSide() ? stack != null && stack.getItem() != null && stack.stackSize > 0
            : machine.isValidSharedConsumableConfig(slot, stack);
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

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SharedConsumableItemStock.SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Shared consumable config slot " + slot);
        }
    }
}
