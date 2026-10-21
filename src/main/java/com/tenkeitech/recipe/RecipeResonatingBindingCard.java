/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.tenkeitech.ModItems;
import com.tenkeitech.resonating.ResonatingBindingCardData;
import com.tenkeitech.resonating.ResonatingPatternData;

/** Applies two positional banks without consuming or modifying the card. */
public final class RecipeResonatingBindingCard implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return result(grid, world) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        return result(grid, null);
    }

    private ItemStack result(InventoryCrafting grid, World world) {
        ItemStack pattern = null, card = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack == null) continue;
            if (stack.stackSize <= 0) return null;
            if ((stack.getItem() == ModItems.RESONATING_PATTERN || RecipeResonatingPattern.supportedSource(stack))
                && pattern == null) pattern = stack;
            else if (stack.getItem() == ModItems.RESONATING_BINDING_CARD && card == null) card = stack;
            else return null;
        }
        if (pattern == null || card == null || card.stackSize != 1 || !ResonatingBindingCardData.valid(card))
            return null;
        boolean converting = pattern.getItem() != ModItems.RESONATING_PATTERN;
        ItemStack output = converting ? RecipeResonatingPattern.convert(pattern, world) : pattern.copy();
        if (output == null
            || !(ModItems.RESONATING_PATTERN.getPatternForItem(output, world) instanceof ResonatingPatternData details))
            return null;
        output.stackSize = 1;
        int item = 0, fluid = 0, applied = 0;
        for (int slot = 0; slot < details.getAEInputs().length; slot++) {
            var input = details.getAEInputs()[slot];
            if (input == null) continue;
            boolean isFluid = input.isFluid();
            var target = ResonatingBindingCardData.target(card, isFluid, isFluid ? fluid++ : item++);
            if (target != null) {
                ResonatingPatternData.setTarget(output, details, slot, target);
                applied++;
            }
        }
        return !converting && applied == 0 ? null : output;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
