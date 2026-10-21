/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.tenkeitech.ModMetaTileEntities;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.autotemplate.TemplateType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;

/** Restores the reusable shape or mold after a generated vanilla shapeless binding recipe crafts. */
public final class RecipeBindAutoTemplateInput {

    /** Restores the reusable template before SlotCrafting decrements every occupied input slot. */
    @SubscribeEvent
    public void onItemCrafted(ItemCraftedEvent event) {
        if (event == null || event.crafting == null || event.craftMatrix == null) return;
        Ingredients ingredients = findIngredients(event.craftMatrix);
        if (ingredients == null || !isAutoTemplateInput(event.crafting)) return;
        TemplateType craftedType = event.crafting.hasTagCompound() ? TemplateType.fromId(
            event.crafting.getTagCompound()
                .getInteger("TemplateType"))
            : TemplateType.NONE;
        if (craftedType != ingredients.templateType || ingredients.template.stackSize == Integer.MAX_VALUE) return;
        if (ingredients.template.getItem()
            .hasContainerItem(ingredients.template)) {
            ItemStack container = ingredients.template.getItem()
                .getContainerItem(ingredients.template);
            if (craftedType.matchesTemplate(container)) return;
        }
        ingredients.template.stackSize++;
    }

    @Nullable
    private static Ingredients findIngredients(IInventory inventory) {
        if (inventory == null) return null;
        ItemStack machine = null;
        ItemStack template = null;
        TemplateType templateType = TemplateType.NONE;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null) continue;
            if (stack.stackSize <= 0) return null;
            if (isAutoTemplateInput(stack)) {
                if (machine != null) return null;
                machine = stack;
                continue;
            }
            TemplateType candidateType = TemplateType.fromTemplateStack(stack);
            if (candidateType == TemplateType.NONE || template != null) return null;
            template = stack;
            templateType = candidateType;
        }
        return machine == null || template == null || !AutoTemplateRecipeRegistry.hasExposedPatterns(templateType)
            ? null
            : new Ingredients(machine, template, templateType);
    }

    private static boolean isAutoTemplateInput(ItemStack stack) {
        ItemStack registered = ModMetaTileEntities.autoTemplateInput;
        return stack != null && registered != null
            && stack.getItem() == registered.getItem()
            && stack.getItemDamage() == registered.getItemDamage();
    }

    private static final class Ingredients {

        private final ItemStack machine;
        private final ItemStack template;
        private final TemplateType templateType;

        private Ingredients(ItemStack machine, ItemStack template, TemplateType templateType) {
            this.machine = machine;
            this.template = template;
            this.templateType = templateType;
        }
    }
}
