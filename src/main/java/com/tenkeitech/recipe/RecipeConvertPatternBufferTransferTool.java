/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;

/** One-input conversion deliberately discards all stored configuration, including CUT payloads. */
public final class RecipeConvertPatternBufferTransferTool extends ShapelessRecipes {

    public RecipeConvertPatternBufferTransferTool(Item input, Item output) {
        super(new ItemStack(output), Collections.singletonList(new ItemStack(input)));
    }
}
