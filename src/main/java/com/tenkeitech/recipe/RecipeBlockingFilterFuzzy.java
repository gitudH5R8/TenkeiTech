/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import java.util.Arrays;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.ModItems;

import appeng.api.AEApi;

/** Native shapeless ingredients remain visible to NEI; only the result copies source configuration. */
public final class RecipeBlockingFilterFuzzy extends ShapelessRecipes {

    public RecipeBlockingFilterFuzzy() {
        super(
            new ItemStack(ModItems.BLOCKING_FILTER_CARD_FUZZY),
            Arrays.asList(
                new ItemStack(ModItems.BLOCKING_FILTER_CARD_PRECISE),
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cardFuzzy()
                    .maybeStack(1)
                    .get()));
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        if (!matches(grid, null)) return null;
        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            ItemStack source = grid.getStackInSlot(slot);
            if (source == null || source.getItem() != ModItems.BLOCKING_FILTER_CARD_PRECISE) continue;
            ItemStack result = getRecipeOutput().copy();
            if (source.hasTagCompound()) result.setTagCompound(
                (NBTTagCompound) source.getTagCompound()
                    .copy());
            return result;
        }
        return null;
    }
}
