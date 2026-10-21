/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.nei;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.tenkeitech.ModItems;
import com.tenkeitech.ModMetaTileEntities;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.autotemplate.TemplateType;

import codechicken.nei.api.API;

/** Client-only NEI filtering for internal items plus variants for real vanilla shapeless binding recipes. */
public final class AutoTemplateNEIIntegration {

    private static boolean initialized;
    private static final Set<TemplateType> REGISTERED_VARIANTS = EnumSet.noneOf(TemplateType.class);

    private AutoTemplateNEIIntegration() {}

    public static void init() {
        if (initialized) return;
        API.hideItem(new ItemStack(ModItems.AUTO_TEMPLATE_PATTERN));
        API.hideItem(new ItemStack(ModItems.PATTERN_CATALYST_MARKER));
        API.addItemFilter(() -> stack -> {
            ItemStack machine = ModMetaTileEntities.autoTemplateInput;
            if (machine == null || stack.getItem() != machine.getItem()
                || stack.getItemDamage() != machine.getItemDamage()
                || !stack.hasTagCompound()
                || !stack.getTagCompound()
                    .hasKey("TemplateType"))
                return true;
            TemplateType type = TemplateType.fromId(
                stack.getTagCompound()
                    .getInteger("TemplateType"));
            return type == TemplateType.NONE || AutoTemplateRecipeRegistry.hasExposedPatterns(type);
        });
        initialized = true;
    }

    public static void refreshCatalog() {
        init();
        for (TemplateType type : TemplateType.values()) {
            if (!type.isPhysicalTemplate() || !AutoTemplateRecipeRegistry.hasExposedPatterns(type)
                || REGISTERED_VARIANTS.contains(type)) continue;
            ItemStack variant = ModMetaTileEntities.autoTemplateInput(type);
            if (variant != null) {
                API.addItemVariant(variant.getItem(), variant);
                REGISTERED_VARIANTS.add(type);
            }
        }
        codechicken.nei.ItemList.updateFilter();
    }
}
