/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTUtility;

/** Stable identity and amount used to group recipe inputs. */
public final class TemplateInputKey {

    private final InputKind kind;
    private final NBTTagCompound identity;
    private final long amount;
    private final int hashCode;

    private TemplateInputKey(InputKind kind, NBTTagCompound identity, long amount) {
        if (kind == null || kind == InputKind.NONE || identity == null || amount <= 0) {
            throw new IllegalArgumentException("Auto-template input key is incomplete");
        }
        this.kind = kind;
        this.identity = (NBTTagCompound) identity.copy();
        this.amount = amount;
        this.hashCode = Objects.hash(kind, this.identity, amount);
    }

    public static TemplateInputKey forItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) throw new IllegalArgumentException("Item input is invalid");
        return forItem(stack, stack.stackSize);
    }

    public static TemplateInputKey forItem(ItemStack stack, long amount) {
        if (stack == null || amount <= 0) throw new IllegalArgumentException("Item input is invalid");
        return new TemplateInputKey(InputKind.INGOT, itemIdentity(stack), amount);
    }

    public static TemplateInputKey forFluid(FluidStack stack) {
        if (stack == null || stack.amount <= 0) throw new IllegalArgumentException("Fluid input is invalid");
        return forFluid(stack, stack.amount);
    }

    public static TemplateInputKey forFluid(FluidStack stack, long amount) {
        if (stack == null || stack.getFluid() == null || amount <= 0) {
            throw new IllegalArgumentException("Fluid input is invalid");
        }
        return new TemplateInputKey(InputKind.FLUID, fluidIdentity(stack), amount);
    }

    static NBTTagCompound itemIdentity(ItemStack stack) {
        return GTUtility.ItemId.create(stack)
            .writeToNBT();
    }

    static NBTTagCompound fluidIdentity(FluidStack stack) {
        NBTTagCompound identity = stack.writeToNBT(new NBTTagCompound());
        identity.removeTag("Amount");
        return identity;
    }

    public InputKind getKind() {
        return kind;
    }

    public long getAmount() {
        return amount;
    }

    public NBTTagCompound getIdentityCopy() {
        return (NBTTagCompound) identity.copy();
    }

    /** Writes this key in a schema-stable form used by synthetic AE pattern items. */
    public void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setString(prefix + "Kind", kind.name());
        tag.setLong(prefix + "Amount", amount);
        tag.setTag(prefix + "Identity", identity.copy());
    }

    public static TemplateInputKey readFromNBT(NBTTagCompound tag, String prefix) {
        if (tag == null || !tag.hasKey(prefix + "Kind") || !tag.hasKey(prefix + "Identity")) return null;
        InputKind parsedKind;
        try {
            parsedKind = InputKind.valueOf(tag.getString(prefix + "Kind"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (parsedKind == InputKind.NONE) return null;
        long parsedAmount = tag.getLong(prefix + "Amount");
        NBTTagCompound parsedIdentity = tag.getCompoundTag(prefix + "Identity");
        if (parsedIdentity == null || parsedAmount <= 0) return null;
        return new TemplateInputKey(parsedKind, parsedIdentity, parsedAmount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TemplateInputKey)) return false;
        TemplateInputKey key = (TemplateInputKey) other;
        return amount == key.amount && kind == key.kind && identity.equals(key.identity);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return kind + ":" + identity + ":" + amount;
    }
}
