/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.recipe.RecipeMap;

public final class PatternRecipeMapData {

    public static final String KEY = "tenkeitechPatternMode";
    public static final String INVALID = "<invalid>";

    private PatternRecipeMapData() {}

    public static String read(ItemStack pattern) {
        return pattern == null ? "" : read(pattern.getTagCompound());
    }

    public static String read(NBTTagCompound root) {
        if (root == null || !root.hasKey(KEY)) return "";
        if (!root.hasKey(KEY, 10)) return INVALID;
        NBTTagCompound data = root.getCompoundTag(KEY);
        if (!data.hasKey("schemaVersion", 3) || data.getInteger("schemaVersion") != 1 || !data.hasKey("recipeMap", 8))
            return INVALID;
        String id = data.getString("recipeMap");
        return validId(id) ? id : INVALID;
    }

    public static boolean validId(String id) {
        return id != null && !id.isEmpty()
            && id.length() <= 256
            && !INVALID.equals(id)
            && id.chars()
                .noneMatch(Character::isISOControl);
    }

    public static boolean registered(String id) {
        return validId(id) && RecipeMap.ALL_RECIPE_MAPS.containsKey(id);
    }

    public static void write(NBTTagCompound root, String id) {
        if (!registered(id)) return;
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schemaVersion", 1);
        data.setString("recipeMap", id);
        root.setTag(KEY, data);
    }
}
