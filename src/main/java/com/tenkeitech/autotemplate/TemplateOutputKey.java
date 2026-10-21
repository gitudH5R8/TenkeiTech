/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Stable output identity used to index one deterministic pattern per product row. */
public final class TemplateOutputKey {

    private final NBTTagCompound identity;
    private final long amount;
    private final int hashCode;

    private TemplateOutputKey(NBTTagCompound identity, long amount) {
        if (identity == null || amount <= 0) throw new IllegalArgumentException("Item output key is invalid");
        this.identity = (NBTTagCompound) identity.copy();
        this.amount = amount;
        this.hashCode = Objects.hash(this.identity, amount);
    }

    public static TemplateOutputKey forItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) throw new IllegalArgumentException("Item output is invalid");
        return new TemplateOutputKey(TemplateInputKey.itemIdentity(stack), stack.stackSize);
    }

    public static TemplateOutputKey forItem(ItemStack stack, long amount) {
        if (stack == null || amount <= 0) throw new IllegalArgumentException("Item output is invalid");
        return new TemplateOutputKey(TemplateInputKey.itemIdentity(stack), amount);
    }

    public long getAmount() {
        return amount;
    }

    public NBTTagCompound getIdentityCopy() {
        return (NBTTagCompound) identity.copy();
    }

    /** Writes this key in a schema-stable form used by synthetic AE pattern items. */
    public void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setLong(prefix + "Amount", amount);
        tag.setTag(prefix + "Identity", identity.copy());
    }

    public static TemplateOutputKey readFromNBT(NBTTagCompound tag, String prefix) {
        if (tag == null || !tag.hasKey(prefix + "Identity")) return null;
        long parsedAmount = tag.getLong(prefix + "Amount");
        NBTTagCompound parsedIdentity = tag.getCompoundTag(prefix + "Identity");
        if (parsedIdentity == null || parsedAmount <= 0) return null;
        return new TemplateOutputKey(parsedIdentity, parsedAmount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TemplateOutputKey)) return false;
        TemplateOutputKey key = (TemplateOutputKey) other;
        return amount == key.amount && identity.equals(key.identity);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return identity + ":" + amount;
    }
}
