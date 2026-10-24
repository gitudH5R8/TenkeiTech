/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.blockingfilter;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.tenkeitech.item.ItemBlockingFilterCard;
import com.tenkeitech.resonating.ResonatingInterfaceHosts;

import appeng.helpers.IInterfaceHost;

/** Upgrade inventory callbacks invalidate this cache; inventory traversal never reparses card NBT. */
public final class BlockingFilterState {

    private BlockingFilter cached;

    public static boolean card(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBlockingFilterCard;
    }

    public static boolean canInstall(IInventory inventory, int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.getSizeInventory() || stack.stackSize <= 0) return false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            // Native inventory validation is also called for a card already occupying this slot.
            if (card(inventory.getStackInSlot(i)) && i != slot) return false;
        }
        return !card(inventory.getStackInSlot(slot)) || inventory.getStackInSlot(slot) == stack;
    }

    public void invalidate() {
        cached = null;
    }

    public BlockingFilter get(IInterfaceHost host, IInventory inventory) {
        if (cached != null) return cached;
        cached = BlockingFilter.EMPTY;
        if (!ResonatingInterfaceHosts.supported(host)) return cached;
        ItemStack found = null;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!card(stack)) continue;
            if (found != null || stack.stackSize != 1 || !BlockingFilterConfig.supported(stack)) return cached;
            found = stack;
        }
        if (found != null) cached = new BlockingFilter(found);
        return cached;
    }
}
