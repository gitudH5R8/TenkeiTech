/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.util.List;

import net.minecraft.item.ItemStack;

/** Long quantity contract for item inventories consumed by GT5U processing. */
public interface ILongItemInputInventory {

    /** Returns compact logical item entries. Stack sizes are authoritative long quantities. */
    List<LongItemStore.Snapshot> longItemSnapshot();

    /** Returns the authoritative amount for one exact item identity. */
    long getLongItemAmount(ItemStack identity);

    /** Consumes an already validated amount from one exact item identity. */
    void consumeLongItem(ItemStack identity, long amount);
}
