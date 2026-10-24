/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.tenkeitech.item.ItemAutoTemplatePattern;
import com.tenkeitech.item.ItemPatternBufferTransferTool;
import com.tenkeitech.item.ItemPatternCatalystMarker;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.recipe.RecipeBindAutoTemplateInput;
import com.tenkeitech.recipe.RecipeConvertPatternBufferTransferTool;
import com.tenkeitech.recipe.RecipeCraftPatternBufferCutTool;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static final com.tenkeitech.item.ItemResonatingPattern RESONATING_PATTERN = new com.tenkeitech.item.ItemResonatingPattern();
    public static final com.tenkeitech.item.ItemResonatingBindingCard RESONATING_BINDING_CARD = new com.tenkeitech.item.ItemResonatingBindingCard();
    public static final com.tenkeitech.item.ItemAutoExtractionCard AUTO_EXTRACTION_CARD = new com.tenkeitech.item.ItemAutoExtractionCard();
    public static final com.tenkeitech.item.ItemBlockingFilterCard BLOCKING_FILTER_CARD_PRECISE = new com.tenkeitech.item.ItemBlockingFilterCard(
        true);
    public static final com.tenkeitech.item.ItemBlockingFilterCard BLOCKING_FILTER_CARD_FUZZY = new com.tenkeitech.item.ItemBlockingFilterCard(
        false);

    public static final ItemUniversalWand UNIVERSAL_WAND = new ItemUniversalWand();
    public static final Item PATTERN_BUFFER_COPY_TOOL = new ItemPatternBufferTransferTool(
        ItemPatternBufferTransferTool.Mode.COPY);
    public static final Item PATTERN_BUFFER_CUT_TOOL = new ItemPatternBufferTransferTool(
        ItemPatternBufferTransferTool.Mode.CUT);
    /** Internal AE pattern identity used by virtual auto-template processing patterns. */
    public static final ItemAutoTemplatePattern AUTO_TEMPLATE_PATTERN = new ItemAutoTemplatePattern();
    /** Internal encoded-pattern instruction for one automatic item catalyst. */
    public static final ItemPatternCatalystMarker PATTERN_CATALYST_MARKER = new ItemPatternCatalystMarker();
    private static final RecipeBindAutoTemplateInput AUTO_TEMPLATE_BINDING_RECIPE = new RecipeBindAutoTemplateInput();

    private ModItems() {}

    public static void init() {
        GameRegistry.registerItem(RESONATING_PATTERN, "resonating_pattern");
        GameRegistry.registerItem(RESONATING_BINDING_CARD, "resonating_binding_card");
        GameRegistry.registerItem(AUTO_EXTRACTION_CARD, "auto_extraction_card");
        GameRegistry.registerItem(BLOCKING_FILTER_CARD_PRECISE, "blocking_filter_card_precise");
        GameRegistry.registerItem(BLOCKING_FILTER_CARD_FUZZY, "blocking_filter_card_fuzzy");
        cpw.mods.fml.common.registry.EntityRegistry.registerModEntity(
            com.tenkeitech.universalwand.EntityUselessWandItem.class,
            "useless_wand_item",
            0,
            TenkeiTech.MOD_ID,
            64,
            20,
            true);
        GameRegistry.registerItem(UNIVERSAL_WAND, "universal_wand");
        GameRegistry.registerItem(PATTERN_BUFFER_COPY_TOOL, "pattern_buffer_copy_tool");
        GameRegistry.registerItem(PATTERN_BUFFER_CUT_TOOL, "pattern_buffer_cut_tool");
        GameRegistry.registerItem(AUTO_TEMPLATE_PATTERN, "auto_template_pattern");
        GameRegistry.registerItem(PATTERN_CATALYST_MARKER, "pattern_catalyst_marker");
    }

    public static void registerRecipes() {
        GameRegistry.addShapelessRecipe(
            new ItemStack(BLOCKING_FILTER_CARD_PRECISE),
            appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .advCard()
                .maybeStack(1)
                .get(),
            net.minecraft.init.Items.comparator);
        GameRegistry.addRecipe(new com.tenkeitech.recipe.RecipeBlockingFilterFuzzy());
        GameRegistry.addShapelessRecipe(
            new ItemStack(AUTO_EXTRACTION_CARD),
            appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .cardLockCrafting()
                .maybeStack(1)
                .get(),
            appeng.api.AEApi.instance()
                .definitions()
                .parts()
                .importBus()
                .maybeStack(1)
                .get());
        RecipeCraftPatternBufferCutTool cutToolRecipe = new RecipeCraftPatternBufferCutTool();
        GameRegistry.addRecipe(cutToolRecipe);
        FMLCommonHandler.instance()
            .bus()
            .register(cutToolRecipe);
        GameRegistry.addRecipe(new com.tenkeitech.recipe.RecipeResonatingPattern());
        GameRegistry.addRecipe(new com.tenkeitech.recipe.RecipeResonatingBindingCard());
        GameRegistry.addShapelessRecipe(
            new ItemStack(RESONATING_BINDING_CARD),
            appeng.api.AEApi.instance()
                .definitions()
                .items()
                .memoryCard()
                .maybeStack(1)
                .get(),
            net.minecraft.init.Items.ender_pearl);
        GameRegistry
            .addRecipe(new RecipeConvertPatternBufferTransferTool(PATTERN_BUFFER_COPY_TOOL, PATTERN_BUFFER_CUT_TOOL));
        GameRegistry
            .addRecipe(new RecipeConvertPatternBufferTransferTool(PATTERN_BUFFER_CUT_TOOL, PATTERN_BUFFER_COPY_TOOL));
        FMLCommonHandler.instance()
            .bus()
            .register(AUTO_TEMPLATE_BINDING_RECIPE);
    }

    public static boolean isTenkeiMagicToolCircuitPreserverEnabled(InventoryPlayer inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0 && ItemUniversalWand.isCircuitPreserverEnabled(stack)) return true;
        }
        return false;
    }

    public static boolean isTenkeiMagicToolCatalystRecorderEnabled(InventoryPlayer inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0 && ItemUniversalWand.isCatalystRecorderEnabled(stack)) return true;
        }
        return false;
    }

    public static boolean isTenkeiMagicToolCondensateReplacementEnabled(InventoryPlayer inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0 && ItemUniversalWand.isCondensateReplacementEnabled(stack))
                return true;
        }
        return false;
    }

    public static boolean isCutterFluidFilterEnabled(InventoryPlayer inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0 && ItemUniversalWand.isCutterFluidFilterEnabled(stack))
                return true;
        }
        return false;
    }
}
