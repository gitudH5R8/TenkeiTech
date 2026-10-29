/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.tenkeitech.TenkeiTech;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import gregtech.api.GregTechAPI;
import gregtech.nei.GTNEIDefaultHandler;

/** Loaded only by the client when NEI is present. Results live for one upload snapshot. */
final class PatternUploadNeiCategories {

    private PatternUploadNeiCategories() {}

    static Map<Integer, String> lookup(List<PatternUploadNetwork.TargetEntry> entries,
        Map<Integer, Set<String>> exactNames, Map<Integer, Set<String>> categoryIds) {
        boolean complete = true;
        Set<IRecipeHandler> handlers = new LinkedHashSet<>(GuiCraftingRecipe.craftinghandlers);
        handlers.addAll(GuiCraftingRecipe.serialCraftingHandlers);
        Map<Integer, List<String>> machines = new LinkedHashMap<>();
        Map<Integer, Set<String>> machineNames = new LinkedHashMap<>();
        Map<Integer, Set<String>> machineIds = new LinkedHashMap<>();
        for (PatternUploadNetwork.TargetEntry entry : entries) {
            for (int id : entry.machineIds) {
                if (machines.containsKey(id)) continue;
                Set<String> categories = new LinkedHashSet<>();
                Set<String> names = new LinkedHashSet<>();
                Set<String> ids = new LinkedHashSet<>();
                if (id >= 0 && id < GregTechAPI.METATILEENTITIES.length && GregTechAPI.METATILEENTITIES[id] != null) {
                    ItemStack machine = GregTechAPI.METATILEENTITIES[id].getStackForm(1);
                    for (IRecipeHandler handler : handlers) {
                        try {
                            if (!RecipeCatalysts.containsCatalyst(handler, machine)) continue;
                            String key = handler instanceof GTNEIDefaultHandler gt ? gt.getRecipeMap().unlocalizedName
                                : RecipeCatalysts.getRecipeID(handler);
                            String label = handler instanceof GTNEIDefaultHandler ? StatCollector.translateToLocal(key)
                                : handler.getRecipeName();
                            // NEE remembers the overlay ID; catalyst and RecipeMap IDs can be aliases of it.
                            addIdentifier(ids, handler.getOverlayIdentifier());
                            addIdentifier(ids, RecipeCatalysts.getRecipeID(handler));
                            addIdentifier(ids, key);
                            categories.add(label + " (" + key + ")");
                            names.add(label);
                            names.add(key);
                        } catch (RuntimeException exception) {
                            complete = false;
                            TenkeiTech.LOG.warn("Could not read NEI upload categories for machine {}", id, exception);
                        }
                    }
                }
                if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[id] == null)
                    complete = false;
                machines.put(id, new ArrayList<>(categories));
                machineNames.put(id, names);
                machineIds.put(id, ids);
            }
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (PatternUploadNetwork.TargetEntry entry : entries) {
            Set<String> categories = new LinkedHashSet<>();
            Set<String> names = new LinkedHashSet<>();
            Set<String> ids = new LinkedHashSet<>();
            for (int id : entry.machineIds) categories.addAll(machines.get(id));
            for (int id : entry.machineIds) names.addAll(machineNames.get(id));
            for (int id : entry.machineIds) ids.addAll(machineIds.get(id));
            if (complete) exactNames.put(entry.groupIndex, names);
            categoryIds.put(entry.groupIndex, ids);
            result.put(entry.groupIndex, String.join("\n", categories));
        }
        return result;
    }

    private static void addIdentifier(Set<String> ids, String id) {
        if (PatternUploadPreferences.validText(id) && !"null".equals(id)) ids.add(id);
    }
}
