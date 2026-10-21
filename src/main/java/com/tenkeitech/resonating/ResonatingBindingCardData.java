/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.resonating.ResonatingPatternData.Target;

/** Two independent, bounded banks of reusable target snapshots. */
public final class ResonatingBindingCardData {

    private static final String KEY = "tenkeitechResonatingCard";
    public static final int SLOTS = ResonatingPatternData.MAX_INPUTS;

    private ResonatingBindingCardData() {}

    public static boolean valid(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound()
            .hasKey(KEY)) return true;
        return stack.getTagCompound()
            .hasKey(KEY, 10)
            && stack.getTagCompound()
                .getCompoundTag(KEY)
                .getInteger("schema") == 1;
    }

    private static NBTTagCompound read(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(KEY) : new NBTTagCompound();
    }

    private static NBTTagCompound edit(ItemStack stack) {
        if (!valid(stack)) throw new IllegalArgumentException("Unsupported binding card");
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (!stack.getTagCompound()
            .hasKey(KEY, 10)) {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("schema", 1);
            stack.getTagCompound()
                .setTag(KEY, data);
        }
        return stack.getTagCompound()
            .getCompoundTag(KEY);
    }

    public static boolean fluid(ItemStack stack) {
        return read(stack).getBoolean("fluid");
    }

    public static int selected(ItemStack stack) {
        return Math.floorMod(read(stack).getInteger(fluid(stack) ? "fluidSelected" : "itemSelected"), SLOTS);
    }

    public static void select(ItemStack stack, int step) {
        edit(stack).setInteger(
            fluid(stack) ? "fluidSelected" : "itemSelected",
            Math.floorMod(selected(stack) + (step < 0 ? -1 : 1), SLOTS));
    }

    public static void toggle(ItemStack stack) {
        edit(stack).setBoolean("fluid", !fluid(stack));
    }

    public static Target target(ItemStack stack, boolean fluid, int slot) {
        if (!valid(stack) || slot < 0 || slot >= SLOTS) return null;
        var data = read(stack).getCompoundTag(fluid ? "fluids" : "items");
        return data.hasKey("target" + slot, 10) ? Target.read(data.getCompoundTag("target" + slot)) : null;
    }

    public static void setTarget(ItemStack stack, Target target) {
        var data = edit(stack);
        String bank = fluid(stack) ? "fluids" : "items";
        if (!data.hasKey(bank, 10)) data.setTag(bank, new NBTTagCompound());
        var targets = data.getCompoundTag(bank);
        String key = "target" + selected(stack);
        if (target == null) targets.removeTag(key);
        else targets.setTag(key, target.write());
    }
}
