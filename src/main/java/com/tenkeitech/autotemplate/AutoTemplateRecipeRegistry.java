/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.recipe.AutoTemplateBindingRecipe;

import cpw.mods.fml.common.FMLCommonHandler;

/** Process-wide read-only catalog cache; machine instances only retain their template and user configuration. */
public final class AutoTemplateRecipeRegistry {

    private static final Map<TemplateType, TemplateRecipeCatalog> CATALOGS = new EnumMap<TemplateType, TemplateRecipeCatalog>(
        TemplateType.class);
    private static final List<IRecipe> GENERATED_RECIPES = new ArrayList<IRecipe>();
    private static volatile boolean initialized;
    private static long revision;

    private AutoTemplateRecipeRegistry() {}

    private static synchronized void initialize() {
        if (initialized) return;
        EnumMap<TemplateType, TemplateRecipeCatalog> rebuilt = new EnumMap<TemplateType, TemplateRecipeCatalog>(
            TemplateType.class);
        for (TemplateType type : TemplateType.values()) {
            if (type == TemplateType.NONE) continue;
            TemplateRecipeCatalog catalog = AutoTemplateRecipeScanner.scan(type);
            rebuilt.put(type, catalog);
            int patternCount = catalog == null ? 0
                : catalog.getPatterns()
                    .size();
            if (patternCount > 0) {
                VirtualPattern sample = catalog.getPatterns()
                    .get(0);
                com.tenkeitech.autotemplate.VirtualPatternDetails details = sample.getDetails();
                appeng.api.storage.data.IAEStack<?>[] inputs = details.getCondensedAEInputs();
                appeng.api.storage.data.IAEStack<?>[] outputs = details.getCondensedAEOutputs();
                TenkeiTech.LOG.info(
                    "[AutoTemplateME] Catalog template={} patterns={} sample={} input={} inputAmount={} inputType={} output={} outputAmount={} outputType={}",
                    type,
                    patternCount,
                    sample.getKey(),
                    inputs.length == 0 || inputs[0] == null ? "<none>" : inputs[0].getDisplayName(),
                    inputs.length == 0 || inputs[0] == null ? 0 : inputs[0].getStackSize(),
                    inputs.length == 0 || inputs[0] == null ? "<none>" : inputs[0].getStackType(),
                    outputs.length == 0 || outputs[0] == null ? "<none>" : outputs[0].getDisplayName(),
                    outputs.length == 0 || outputs[0] == null ? 0 : outputs[0].getStackSize(),
                    outputs.length == 0 || outputs[0] == null ? "<none>" : outputs[0].getStackType());
            }
        }
        CATALOGS.clear();
        CATALOGS.putAll(rebuilt);
        registerBindingRecipes(rebuilt);
        initialized = true;
        revision++;
    }

    /** Replaces the generated real crafting recipes after every Catalog rebuild. */
    private static void registerBindingRecipes(Map<TemplateType, TemplateRecipeCatalog> catalogs) {
        List<IRecipe> recipeList = CraftingManager.getInstance()
            .getRecipeList();
        recipeList.removeAll(GENERATED_RECIPES);
        GENERATED_RECIPES.clear();
        AutoTemplateCatalogSync.clearSnapshot();
        for (TemplateType type : TemplateType.values()) {
            if (!type.isPhysicalTemplate()) continue;
            TemplateRecipeCatalog catalog = catalogs.get(type);
            if (catalog == null || catalog.getPatterns()
                .isEmpty()) continue;
            IRecipe recipe = new AutoTemplateBindingRecipe(type);
            recipeList.add(recipe);
            GENERATED_RECIPES.add(recipe);
        }
    }

    /** Remapping also occurs on exit: do not scan until a world is loaded with the new identities. */
    public static synchronized void invalidate() {
        initialized = false;
        AutoTemplateCatalogSync.clearSnapshot();
        CATALOGS.clear();
        CraftingManager.getInstance()
            .getRecipeList()
            .removeAll(GENERATED_RECIPES);
        GENERATED_RECIPES.clear();
    }

    /** All dimensions and players share the catalog; subsequent world loads reuse it. */
    public static synchronized void initializeForWorld() {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer()) initialize();
    }

    /** Publishes a fully decoded server snapshot on the remote client's main thread. */
    public static synchronized void installServerCatalog(Map<TemplateType, TemplateRecipeCatalog> catalogs) {
        registerBindingRecipes(catalogs);
        CATALOGS.clear();
        CATALOGS.putAll(catalogs);
        initialized = true;
    }

    public static synchronized long getRevision() {
        return revision;
    }

    public static synchronized TemplateRecipeCatalog get(TemplateType type) {
        if (type == null || type == TemplateType.NONE) return null;
        return CATALOGS.get(type);
    }

    public static VirtualPattern find(TemplateType type, PatternKey key) {
        TemplateRecipeCatalog catalog = get(type);
        return catalog == null ? null : catalog.find(key);
    }

    public static boolean hasExposedPatterns(TemplateType type) {
        TemplateRecipeCatalog catalog = get(type);
        return catalog != null && !catalog.getPatterns()
            .isEmpty();
    }

    public static synchronized Map<TemplateType, TemplateRecipeCatalog> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<TemplateType, TemplateRecipeCatalog>(CATALOGS));
    }
}
