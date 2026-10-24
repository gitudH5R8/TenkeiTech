/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/** The card owns only the face used to access adjacent inventories, never a location or an output task. */
public final class AutoExtractionCardData {

    private static final String KEY = "tenkeitechAutoExtractionCard";

    private AutoExtractionCardData() {}

    /** UNKNOWN selects each neighbor's face nearest the interface; null means unsupported or damaged data. */
    public static ForgeDirection direction(ItemStack stack) {
        if (stack == null) return null;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(KEY)) return ForgeDirection.UNKNOWN;
        if (!root.hasKey(KEY, 10)) return null;
        NBTTagCompound tag = root.getCompoundTag(KEY);
        if (!tag.hasKey("schema", 3) || tag.getInteger("schema") != 1 || !tag.hasKey("side", 3)) return null;
        int side = tag.getInteger("side");
        return side >= 0 && side < ForgeDirection.VALID_DIRECTIONS.length ? ForgeDirection.getOrientation(side) : null;
    }

    /** Clicking the same direction clears it; another direction replaces it. Other item NBT is retained. */
    public static boolean toggle(ItemStack stack, int side) {
        ForgeDirection current = direction(stack);
        if (current == null || side < 0 || side >= ForgeDirection.VALID_DIRECTIONS.length) return false;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        if (current.ordinal() == side) {
            root.removeTag(KEY);
        } else {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("schema", 1);
            tag.setInteger("side", side);
            root.setTag(KEY, tag);
        }
        stack.setTagCompound(root.hasNoTags() ? null : root);
        return true;
    }
}
