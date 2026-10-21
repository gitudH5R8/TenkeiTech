/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.implementations.ContainerPatternTerm;

/** Last transferred recipe category lives for this terminal GUI until another transfer replaces it. */
public final class PatternUploadRecipeContext {

    private String category = "";
    private String label = "";

    public void transferred(String id, String name) {
        clear();
        if (PatternUploadPreferences.validText(id) && PatternUploadPreferences.validText(name)) {
            category = id;
            label = name;
        }
    }

    public void clear() {
        category = "";
        label = "";
    }

    public String rememberedCategory() {
        return category;
    }

    public String category(ContainerPatternTerm container, World world) {
        ItemStack source = source(container);
        if (category.isEmpty() || source == null || !(source.getItem() instanceof ICraftingPatternItem item)) return "";
        try {
            ICraftingPatternDetails details = item.getPatternForItem(source, world);
            return details == null ? "" : category;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public String label() {
        return label;
    }

    public static boolean isCraftingPattern(ContainerPatternTerm container, World world) {
        ItemStack source = source(container);
        if (source == null || !(source.getItem() instanceof ICraftingPatternItem item)) return false;
        try {
            ICraftingPatternDetails details = item.getPatternForItem(source, world);
            return details != null && details.isCraftable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static ItemStack source(ContainerPatternTerm container) {
        net.minecraft.inventory.IInventory inventory = container.getPatternTerminal()
            .getInventoryByName("pattern");
        return inventory == null || inventory.getSizeInventory() < 2 ? null : inventory.getStackInSlot(1);
    }

}
