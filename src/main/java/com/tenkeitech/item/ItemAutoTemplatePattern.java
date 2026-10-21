/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.autotemplate.PatternKey;
import com.tenkeitech.autotemplate.VirtualPattern;
import com.tenkeitech.autotemplate.VirtualPatternDetails;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Hidden, non-creative synthetic pattern item used to persist virtual auto-template patterns. */
public final class ItemAutoTemplatePattern extends Item implements appeng.api.implementations.ICraftingPatternItem {

    public ItemAutoTemplatePattern() {
        setUnlocalizedName(TenkeiTech.MOD_ID + ".auto_template_pattern");
        setTextureName("minecraft:paper");
        setMaxStackSize(1);
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        PatternKey key = VirtualPatternDetails.readPatternKey(stack);
        if (key == null) return null;
        VirtualPattern pattern = AutoTemplateRecipeRegistry.find(key.getTemplateType(), key);
        return pattern == null ? null : pattern.getDetails();
    }
}
