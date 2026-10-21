/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.nee;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;

import com.github.vfyjxf.nee.config.NEEConfig;
import com.github.vfyjxf.nee.processor.GregTech5RecipeProcessor;
import com.github.vfyjxf.nee.processor.IRecipeProcessor;
import com.github.vfyjxf.nee.processor.RecipeProcessor;
import com.tenkeitech.ModItems;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemPatternCatalystMarker;
import com.tenkeitech.item.ItemUniversalWand;

import codechicken.nei.PositionedStack;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.CondensateType;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTUtility;
import gregtech.nei.FluidDisplayStackMode;
import tectech.recipe.TecTechRecipeMaps;

public final class NEEIntegration {

    private static final NEEIntegration INSTANCE = new NEEIntegration();

    private boolean waitingForNEEConfig;

    private NEEIntegration() {}

    public static void init() {
        if (installProcessor()) return;
        if (!INSTANCE.waitingForNEEConfig) {
            INSTANCE.waitingForNEEConfig = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
        }
    }

    @SubscribeEvent
    public void onNEIConfigsLoaded(NEIConfigsLoadedEvent event) {
        if (!installProcessor()) {
            TenkeiTech.LOG.warn("NEE is loaded, but its GregTech 5 recipe processor was not found");
        }
        waitingForNEEConfig = false;
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private static boolean installProcessor() {
        List<IRecipeProcessor> processors = RecipeProcessor.recipeProcessors;
        for (int index = 0; index < processors.size(); index++) {
            IRecipeProcessor processor = processors.get(index);
            if (processor instanceof TenkeiTechGregTech5RecipeProcessor) return true;
            if (processor instanceof GregTech5RecipeProcessor) {
                processors.add(index, new TenkeiTechGregTech5RecipeProcessor());
                TenkeiTech.LOG.info("Enabled TenkeiTech recipe overlay handling for NEE");
                return true;
            }
        }
        return false;
    }

    private static final class TenkeiTechGregTech5RecipeProcessor extends GregTech5RecipeProcessor {

        private TenkeiTechGregTech5RecipeProcessor() {
            super(true);
        }

        @Override
        public List<PositionedStack> getRecipeInput(IRecipeHandler recipe, int recipeIndex, String identifier) {
            boolean preserveCircuits = isCircuitPreserverActive();
            boolean recordCatalysts = isCatalystRecorderActive();
            RecipeCategory category = RecipeCategory.ALL_RECIPE_CATEGORIES.get(identifier);
            boolean replaceCondensate = category != null
                && category.recipeMap == TecTechRecipeMaps.condensateAssemblingRecipes
                && isCondensateReplacementActive();
            Minecraft minecraft = Minecraft.getMinecraft();
            boolean filterCutterFluids = category != null && isCutterRecipeMap(category.recipeMap)
                && minecraft.thePlayer != null
                && ModItems.isCutterFluidFilterEnabled(minecraft.thePlayer.inventory);
            if (!preserveCircuits && !recordCatalysts && !replaceCondensate && !filterCutterFluids)
                return super.getRecipeInput(recipe, recipeIndex, identifier);

            List<PositionedStack> recipeInputs = new ArrayList<>();
            for (PositionedStack positionedStack : recipe.getIngredientStacks(recipeIndex)) {
                if (positionedStack == null || positionedStack.item == null) continue;
                if (filterCutterFluids && getFluidFromDisplayStack(positionedStack.item) != null) continue;

                PositionedStack copy = positionedStack.copy();
                if (replaceCondensate) replaceCondensateWithSource(copy);
                if (copy.item.stackSize == 0) {
                    if (preserveCircuits && preserveProgrammedCircuit(copy)) {
                        copy.item.stackSize = 1;
                    } else if (isProgrammedCircuit(copy.item)) {
                        if (NEEConfig.includeNonConsumableIngredients) copy.item.stackSize = 1;
                        else continue;
                    } else if (recordCatalysts && isAssemblyLineDataStick(copy.item)) {
                        continue;
                    } else if (recordCatalysts && getFluidFromDisplayStack(copy.item) == null
                        && !(copy.item.getItem() instanceof ItemUniversalWand)
                        && !(copy.item.getItem() instanceof ItemPatternCatalystMarker)) {
                            replaceWithCatalystMarker(copy);
                        } else if (NEEConfig.includeNonConsumableIngredients) {
                            copy.item.stackSize = 1;
                        } else {
                            continue;
                        }
                }
                recipeInputs.add(copy);
            }
            return recipeInputs;
        }

        private static boolean isCircuitPreserverActive() {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft.thePlayer != null
                && ModItems.isTenkeiMagicToolCircuitPreserverEnabled(minecraft.thePlayer.inventory);
        }

        private static boolean isCatalystRecorderActive() {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft.thePlayer != null
                && ModItems.isTenkeiMagicToolCatalystRecorderEnabled(minecraft.thePlayer.inventory);
        }

        private static boolean isCondensateReplacementActive() {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft.thePlayer != null
                && ModItems.isTenkeiMagicToolCondensateReplacementEnabled(minecraft.thePlayer.inventory);
        }

        private static boolean isCutterRecipeMap(gregtech.api.recipe.RecipeMap<?> recipeMap) {
            return recipeMap == RecipeMaps.cutterRecipes || recipeMap == RecipeMaps.cutterFakeRecipes;
        }

        private static void replaceCondensateWithSource(PositionedStack positionedStack) {
            FluidStack displayed = GTUtility.getFluidFromDisplayStack(positionedStack.item);
            if (displayed == null || displayed.getFluid() == null || displayed.amount <= 0) return;

            CondensateType type = CondensateType.getCondensateType(displayed.getFluid());
            if (type == null) return;
            FluidStack source = type.getSourceFluid();
            if (source == null || source.getFluid() == null) return;
            source = source.copy();
            source.amount = displayed.amount;

            ItemStack replacement = GTUtility.getFluidDisplayStack(source, FluidDisplayStackMode.SHOWN);
            positionedStack.item = replacement;
            positionedStack.items = new ItemStack[] { replacement.copy() };
        }

        private static void replaceWithCatalystMarker(PositionedStack positionedStack) {
            ItemStack marker = ModItems.PATTERN_CATALYST_MARKER.createMarker(positionedStack.item);
            positionedStack.item = marker;
            positionedStack.items = new ItemStack[] { marker.copy() };
        }

        private static boolean preserveProgrammedCircuit(PositionedStack positionedStack) {
            if (!isProgrammedCircuit(positionedStack.item)) return false;

            positionedStack.item.stackSize = Math.max(1, positionedStack.item.stackSize);
            if (positionedStack.items != null) {
                for (ItemStack permutation : positionedStack.items) {
                    if (isProgrammedCircuit(permutation)) {
                        permutation.stackSize = Math.max(1, permutation.stackSize);
                    }
                }
            }
            return true;
        }

        private static boolean isProgrammedCircuit(ItemStack stack) {
            return stack != null && GTUtility.isAnyIntegratedCircuit(stack);
        }

        private static boolean isAssemblyLineDataStick(ItemStack stack) {
            if (stack == null || stack.getItem() == null) return false;
            ItemStack identity = stack.copy();
            identity.stackSize = 1;
            return AssemblyLineUtils.isItemDataStick(identity);
        }
    }
}
