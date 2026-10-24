/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.blockingfilter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;

/** Only ghost identities belong to the card; this is never a real inventory. */
public final class BlockingFilterConfig extends IAEStackInventory {

    public static final String KEY = "tenkeitechBlockingFilter";
    private final ItemStack card;

    public BlockingFilterConfig(ItemStack card) {
        super(null, 63);
        this.card = card;
        if (supported(card) && card.hasTagCompound()) readFromNBT(
            card.getTagCompound()
                .getCompoundTag(KEY),
            "markers");
    }

    public static boolean supported(ItemStack card) {
        if (!card.hasTagCompound() || !card.getTagCompound()
            .hasKey(KEY)) return true;
        if (!card.getTagCompound()
            .hasKey(KEY, 10)) return false;
        NBTTagCompound tag = card.getTagCompound()
            .getCompoundTag(KEY);
        return tag.hasKey("schema", 3) && tag.getInteger("schema") == 1
            && (!tag.hasKey("markers") || tag.hasKey("markers", 10));
    }

    @Override
    public void putAEStackInSlot(int slot, IAEStack<?> marker) {
        if (!supported(card)) return;
        if (marker != null && !(marker instanceof IAEItemStack) && !(marker instanceof IAEFluidStack)) return;
        IAEStack<?> identity = marker == null ? null
            : marker.copy()
                .setStackSize(1);
        super.putAEStackInSlot(slot, identity);
    }

    @Override
    public void markDirty() {
        if (!supported(card)) return;
        NBTTagCompound root = card.hasTagCompound() ? card.getTagCompound() : new NBTTagCompound();
        NBTTagCompound config = new NBTTagCompound();
        config.setInteger("schema", 1);
        writeToNBT(config, "markers");
        root.setTag(KEY, config);
        card.setTagCompound(root);
    }
}
