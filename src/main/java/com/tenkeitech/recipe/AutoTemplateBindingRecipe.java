/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import java.util.Arrays;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;

import com.tenkeitech.ModMetaTileEntities;
import com.tenkeitech.autotemplate.TemplateType;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;

/** A real vanilla shapeless recipe so crafting tables and NEI discover every valid template binding natively. */
public final class AutoTemplateBindingRecipe extends ShapelessRecipes {

    private final TemplateType templateType;

    public AutoTemplateBindingRecipe(TemplateType templateType) {
        super(
            MTEHatchAutoTemplateInputME.bindTemplateItem(ModMetaTileEntities.autoTemplateInput, templateType),
            Arrays.asList(ModMetaTileEntities.autoTemplateInput.copy(), templateType.getTemplateStack()));
        if (templateType == null || templateType == TemplateType.NONE) {
            throw new IllegalArgumentException("Auto-template binding recipe requires a template type");
        }
        this.templateType = templateType;
    }

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        ItemStack machine = null;
        ItemStack template = null;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack candidate = inventory.getStackInSlot(slot);
            if (candidate == null) continue;
            if (candidate.stackSize <= 0) return false;
            if (isMachine(candidate)) {
                if (machine != null) return false;
                machine = candidate;
            } else if (templateType.matchesTemplate(candidate)) {
                if (template != null) return false;
                template = candidate;
            } else {
                return false;
            }
        }
        return machine != null && template != null;
    }

    /** Preserve the machine's existing NBT while replacing only its template binding. */
    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack machine = null;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack candidate = inventory.getStackInSlot(slot);
            if (!isMachine(candidate)) continue;
            if (machine != null) return null;
            machine = candidate;
        }
        return MTEHatchAutoTemplateInputME.bindTemplateItem(machine, templateType);
    }

    private static boolean isMachine(ItemStack stack) {
        ItemStack registered = ModMetaTileEntities.autoTemplateInput;
        return stack != null && registered != null
            && stack.getItem() == registered.getItem()
            && stack.getItemDamage() == registered.getItemDamage();
    }
}
