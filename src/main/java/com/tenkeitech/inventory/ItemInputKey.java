/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import gregtech.api.util.GTUtility;

/** Immutable item identity with stack size deliberately excluded. */
public final class ItemInputKey {

    private final GTUtility.ItemId id;

    private ItemInputKey(GTUtility.ItemId id) {
        this.id = id;
    }

    @Nullable
    public static ItemInputKey of(@Nullable ItemStack stack) {
        return stack == null || stack.getItem() == null ? null : new ItemInputKey(GTUtility.ItemId.create(stack));
    }

    public ItemStack createStack(int amount) {
        ItemStack result = id.getItemStack();
        result.stackSize = amount;
        return result;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemInputKey && id.equals(((ItemInputKey) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
