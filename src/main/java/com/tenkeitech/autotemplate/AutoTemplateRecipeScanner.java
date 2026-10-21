/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.tenkeitech.TenkeiTech;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Builds a deterministic, strictly filtered catalog from one GT recipe map. */
public final class AutoTemplateRecipeScanner {

    private static final int GUARANTEED_OUTPUT_CHANCE = 10_000;
    private static final int PRIORITIES_PER_TEMPLATE = 1 << 16;

    private AutoTemplateRecipeScanner() {}

    public static TemplateRecipeCatalog scan(TemplateType templateType) {
        if (templateType == null || templateType == TemplateType.NONE) return null;
        if (!templateType.isConfigured()) {
            TenkeiTech.LOG.warn("[AutoTemplateME] Template {} is not configured; exposing no patterns", templateType);
            return TemplateRecipeCatalog.empty(templateType);
        }

        RecipeMap<?> recipeMap = templateType.getRecipeMap();
        if (recipeMap == null) return TemplateRecipeCatalog.empty(templateType);

        ScanContext context = new ScanContext(templateType);
        List<VirtualPattern> candidates = new ArrayList<VirtualPattern>();
        for (GTRecipe recipe : recipeMap.getAllRecipes()) {
            VirtualPattern pattern;
            if (templateType.getInputKind() == InputKind.INGOT) pattern = context.scanItemRecipe(recipe);
            else pattern = context.scanSolidifier(recipe);
            if (pattern != null) candidates.add(pattern);
        }

        Map<PatternKey, VirtualPattern> accepted = new LinkedHashMap<PatternKey, VirtualPattern>();
        for (VirtualPattern pattern : candidates) {
            VirtualPattern existing = accepted.get(pattern.getKey());
            if (existing == null || pattern.getRecipeEUt() < existing.getRecipeEUt())
                accepted.put(pattern.getKey(), pattern);
        }
        List<VirtualPattern> patterns = new ArrayList<VirtualPattern>(accepted.values());
        patterns.sort(
            Comparator.comparing(
                pattern -> pattern.getKey()
                    .toString()));
        if (patterns.size() >= PRIORITIES_PER_TEMPLATE) {
            throw new IllegalStateException("Auto-template catalog is too large to assign unique AE priorities");
        }
        int priorityBase = Integer.MIN_VALUE + templateType.getStableId() * PRIORITIES_PER_TEMPLATE;
        for (int index = 0; index < patterns.size(); index++) {
            patterns.get(index)
                .getDetails()
                .setPriority(priorityBase + index);
        }
        return TemplateRecipeCatalog.of(templateType, patterns);
    }

    private static final class ScanContext {

        private final TemplateType templateType;
        private final Set<String> warnedReasons = new HashSet<String>();

        private ScanContext(TemplateType templateType) {
            this.templateType = templateType;
        }

        private VirtualPattern scanItemRecipe(GTRecipe recipe) {
            if (!commonRecipeChecks(recipe)) return null;
            List<ItemStack> items = presentItems(recipe.mInputs);
            List<FluidStack> fluids = presentFluids(recipe.mFluidInputs);
            if (hasInvalidItemAmount(recipe.mInputs) || hasInvalidFluidAmount(recipe.mFluidInputs)) {
                return reject("INVALID_INPUT_AMOUNT", recipe);
            }
            List<ItemStack> templates = new ArrayList<ItemStack>();
            List<ItemStack> consumed = new ArrayList<ItemStack>();
            for (ItemStack item : items) {
                if (item.stackSize == 0) templates.add(item);
                else consumed.add(item);
            }
            if (templates.size() != 1) return reject("TEMPLATE_COUNT", recipe);
            ItemStack expectedTemplate = templateType.getTemplateStack();
            if (!sameTemplate(expectedTemplate, templates.get(0))) return reject("TEMPLATE_MISMATCH", recipe);
            if (consumed.size() != 1) return reject("CONSUMED_ITEM_COUNT", recipe);
            if (!fluids.isEmpty()) return reject("FLUID_INPUTS_NOT_SUPPORTED", recipe);

            List<ItemStack> outputs = presentItems(recipe.mOutputs);
            if (hasInvalidItemAmount(recipe.mOutputs) || outputs.size() != 1) {
                return reject("ITEM_OUTPUT_COUNT", recipe);
            }
            if (!presentFluids(recipe.mFluidOutputs).isEmpty()) return reject("FLUID_OUTPUTS_NOT_SUPPORTED", recipe);
            if (!guaranteedOutput(recipe)) return reject("PROBABILITY_OUTPUT", recipe);

            ItemStack input = consumed.get(0);
            // RecipeMap inputs may be representatives of ore-dictionary ingredients.
            // Resolve a precise AE input only after validating the original material.
            if (!hasPrefix(input, OrePrefixes.ingot)) {
                return reject("INPUT_IS_NOT_INGOT", recipe);
            }
            if (input.stackSize <= 0 || outputs.get(0).stackSize <= 0) return reject("INVALID_AMOUNT", recipe);
            ItemStack output = outputs.get(0);
            OrePrefixes outputPrefix = templateType.getExpectedOutputPrefix();
            if (templateType.isCircuitMode() && !hasPrefix(output, outputPrefix)) {
                return reject("OUTPUT_PREFIX_MISMATCH", recipe);
            }
            if (!hasSameMaterial(input, OrePrefixes.ingot, output, outputPrefix)) {
                return reject("OUTPUT_MATERIAL_MISMATCH", recipe);
            }
            ItemStack preferredInput = preferredIngotInput(input);
            if (preferredInput == null) return reject("INVALID_PREFERRED_INPUT", recipe);
            if (!hasSameMaterial(input, OrePrefixes.ingot, preferredInput, OrePrefixes.ingot)) {
                return reject("PREFERRED_INPUT_MATERIAL_MISMATCH", recipe);
            }
            if (!GTUtility.areStacksEqual(input, preferredInput, false)) {
                // Shared Forge membership alone does not prove that this recipe accepts
                // substitution. Check the native matcher and the uncached RecipeMap index.
                if (!recipe
                    .isRecipeInputEqual(false, new FluidStack[0], preferredInput.copy(), expectedTemplate.copy())) {
                    return reject("PREFERRED_INPUT_NOT_ACCEPTED", recipe);
                }
                if (templateType.getRecipeMap()
                    .findRecipeQuery()
                    .items(preferredInput.copy(), expectedTemplate.copy())
                    .filter(candidate -> candidate == recipe)
                    .find() == null) {
                    return reject("PREFERRED_INPUT_NOT_DISCOVERABLE", recipe);
                }
            }
            // Outputs keep the recipe's actual identity, including IC2 dense plates.
            return createItemPattern(preferredInput, output, recipe);
        }

        private VirtualPattern scanSolidifier(GTRecipe recipe) {
            if (!commonRecipeChecks(recipe)) return null;
            List<ItemStack> items = presentItems(recipe.mInputs);
            List<FluidStack> fluids = presentFluids(recipe.mFluidInputs);
            if (hasInvalidItemAmount(recipe.mInputs) || hasInvalidFluidAmount(recipe.mFluidInputs)) {
                return reject("INVALID_INPUT_AMOUNT", recipe);
            }
            List<ItemStack> templates = new ArrayList<ItemStack>();
            List<ItemStack> consumed = new ArrayList<ItemStack>();
            for (ItemStack item : items) {
                if (item.stackSize == 0) templates.add(item);
                else consumed.add(item);
            }
            if (templates.size() != 1) return reject("TEMPLATE_COUNT", recipe);
            ItemStack expectedTemplate = templateType.getTemplateStack();
            if (!sameTemplate(expectedTemplate, templates.get(0))) return reject("TEMPLATE_MISMATCH", recipe);
            if (!consumed.isEmpty()) return reject("CONSUMED_ITEM_NOT_SUPPORTED", recipe);
            if (fluids.size() != 1) return reject("FLUID_INPUT_COUNT", recipe);
            if (fluids.get(0).amount <= 0) return reject("INVALID_FLUID_AMOUNT", recipe);

            List<ItemStack> outputs = presentItems(recipe.mOutputs);
            if (hasInvalidItemAmount(recipe.mOutputs) || outputs.size() != 1) {
                return reject("ITEM_OUTPUT_COUNT", recipe);
            }
            if (!presentFluids(recipe.mFluidOutputs).isEmpty()) return reject("FLUID_OUTPUTS_NOT_SUPPORTED", recipe);
            if (!guaranteedOutput(recipe)) return reject("PROBABILITY_OUTPUT", recipe);
            if (outputs.get(0).stackSize <= 0) return reject("INVALID_AMOUNT", recipe);
            if (!hasSameMaterial(fluids.get(0), outputs.get(0))) {
                return reject("OUTPUT_MATERIAL_MISMATCH", recipe);
            }
            return createFluidPattern(fluids.get(0), outputs.get(0), recipe);
        }

        private boolean commonRecipeChecks(GTRecipe recipe) {
            if (recipe == null) {
                reject("NULL_RECIPE", null);
                return false;
            }
            if (!recipe.mEnabled) {
                reject("DISABLED_RECIPE", recipe);
                return false;
            }
            if (recipe.mFakeRecipe) {
                reject("FAKE_RECIPE", recipe);
                return false;
            }
            if (recipe.mEUt <= 0) {
                reject("INVALID_EUT", recipe);
                return false;
            }
            return true;
        }

        private VirtualPattern createFluidPattern(FluidStack input, ItemStack output, GTRecipe recipe) {
            try {
                TemplateInputKey inputKey = TemplateInputKey.forFluid(input);
                TemplateOutputKey outputKey = TemplateOutputKey.forItem(output);
                PatternKey key = new PatternKey(templateType, inputKey, outputKey);
                return VirtualPattern.forFluid(key, input, output, recipe.mEUt, GTUtility.getTier(recipe.mEUt));
            } catch (RuntimeException exception) {
                return reject("INVALID_FLUID_PATTERN", recipe);
            }
        }

        private VirtualPattern createItemPattern(ItemStack input, ItemStack output, GTRecipe recipe) {
            try {
                TemplateInputKey inputKey = TemplateInputKey.forItem(input);
                TemplateOutputKey outputKey = TemplateOutputKey.forItem(output);
                PatternKey key = new PatternKey(templateType, inputKey, outputKey);
                return VirtualPattern.forItem(key, input, output, recipe.mEUt, GTUtility.getTier(recipe.mEUt));
            } catch (RuntimeException exception) {
                return reject("INVALID_ITEM_PATTERN", recipe);
            }
        }

        private boolean guaranteedOutput(GTRecipe recipe) {
            try {
                return recipe.getOutputChance(0) == GUARANTEED_OUTPUT_CHANCE;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private VirtualPattern reject(String reason, GTRecipe recipe) {
            if (warnedReasons.add(reason)) {
                TenkeiTech.LOG.warn(
                    "[AutoTemplateME] Skipping unsupported recipe: template={} reason={} recipe={}",
                    templateType,
                    reason,
                    recipe);
            }
            return null;
        }

    }

    private static List<ItemStack> presentItems(ItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) return Collections.emptyList();
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (ItemStack stack : stacks) {
            if (stack != null) result.add(stack);
        }
        return result;
    }

    private static ItemStack preferredIngotInput(ItemStack stack) {
        // Preserve blacklisted ingredients, amounts and NBT. Do not modify the recipe.
        ItemStack unified = GTOreDictUnificator.get(true, stack.copy(), true);
        if (unified == null || unified.stackSize != stack.stackSize) return null;

        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (GTOreDictUnificator.isBlacklisted(stack)
            || data != null && (data.mBlackListed || data.hasValidPrefixMaterialData())) return unified;

        // GT's ignoreTinkerConstruct skips associations, so get(stack) returns the original.
        // Resolve one unambiguous Forge identity against a registered GT preferred item instead.
        ItemStack preferred = preferredOreItem(stack);
        if (preferred == null) return unified;
        ItemStack resolved = preferred.copy();
        resolved.stackSize = stack.stackSize;
        resolved.setTagCompound(
            stack.copy()
                .getTagCompound());
        return resolved;
    }

    private static ItemStack preferredOreItem(ItemStack stack) {
        OrePrefixes.ParsedOreDictName identity = null;
        for (OrePrefixes.ParsedOreDictName parsed : OrePrefixes.detectPrefix(stack)) {
            if (parsed == null || parsed.prefix == null || !isConcreteMaterialName(parsed.material)) continue;
            if (identity != null && (identity.prefix != parsed.prefix || !identity.material.equals(parsed.material)))
                return null;
            identity = parsed;
        }
        if (identity == null || !identity.prefix.isUnifiable()) return null;
        Materials material = Materials.get(identity.material);
        if (material == Materials._NULL || !identity.material.equals(material.mName)) return null;

        ItemStack preferred = GTOreDictUnificator.get(identity.prefix, material, 1);
        ItemData preferredData = GTOreDictUnificator.getAssociation(preferred);
        // get(prefix, material) can fall back to the first Forge ore. Require a real GT association.
        if (preferredData == null || !preferredData.hasValidPrefixMaterialData()
            || preferredData.mPrefix != identity.prefix
            || preferredData.mMaterial.mMaterial != material) return null;
        return preferred;
    }

    private static List<FluidStack> presentFluids(FluidStack[] stacks) {
        if (stacks == null || stacks.length == 0) return Collections.emptyList();
        List<FluidStack> result = new ArrayList<FluidStack>();
        for (FluidStack stack : stacks) {
            if (stack != null) result.add(stack);
        }
        return result;
    }

    private static boolean hasInvalidItemAmount(ItemStack[] stacks) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack != null && stack.stackSize < 0) return true;
        }
        return false;
    }

    private static boolean hasInvalidFluidAmount(FluidStack[] stacks) {
        if (stacks == null) return false;
        for (FluidStack stack : stacks) {
            if (stack != null && (stack.getFluid() == null || stack.amount <= 0)) return true;
        }
        return false;
    }

    private static boolean hasPrefix(ItemStack stack, OrePrefixes expectedPrefix) {
        if (stack == null || expectedPrefix == null) return false;
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null && data.hasValidPrefixData() && data.mPrefix == expectedPrefix) return true;
        for (OrePrefixes.ParsedOreDictName parsedName : OrePrefixes.detectPrefix(stack)) {
            if (parsedName != null && parsedName.prefix == expectedPrefix) return true;
        }
        return false;
    }

    private static boolean hasSameMaterial(ItemStack first, OrePrefixes firstPrefix, ItemStack second,
        OrePrefixes secondPrefix) {
        String firstMaterial = materialName(first, firstPrefix);
        return firstMaterial != null && firstMaterial.equals(materialName(second, secondPrefix));
    }

    private static boolean hasSameMaterial(FluidStack input, ItemStack output) {
        String outputMaterial = materialName(output, null);
        if (input == null || input.getFluid() == null || outputMaterial == null) return false;
        Materials material = Materials.getGtMaterialFromFluid(input.getFluid());
        if (material != null && material != Materials._NULL) {
            return isConcreteMaterialName(material.mName) && material.mName.equals(outputMaterial);
        }

        // BartWorks registers its fluids separately from GT's fluid-to-material map.
        // Use registered fluid identity instead of inferring its Werkstoff from a name.
        Werkstoff moltenMaterial = WerkstoffLoader.molten.inverse()
            .get(input.getFluid());
        Werkstoff fluidMaterial = WerkstoffLoader.fluids.inverse()
            .get(input.getFluid());
        if (moltenMaterial != null || fluidMaterial != null) {
            return (moltenMaterial == null || outputMaterial.equals(moltenMaterial.getVarName()))
                && (fluidMaterial == null || outputMaterial.equals(fluidMaterial.getVarName()));
        }

        // Addon fluids need not have a GT material association. Only accept a complete
        // conventional registry name; never trim material qualifiers such as "annealed".
        String fluidName = input.getFluid()
            .getName();
        String expectedName = outputMaterial.toLowerCase(Locale.ROOT);
        return expectedName.equals(fluidName) || ("molten." + expectedName).equals(fluidName)
            || ("fluid." + expectedName).equals(fluidName);
    }

    private static String materialName(ItemStack stack, OrePrefixes expectedPrefix) {
        if (stack == null) return null;
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null && data.hasValidPrefixMaterialData() && data.mMaterial.mMaterial != Materials._NULL) {
            // GT retains the first valid association. A later Forge registration can
            // add another valid form without replacing that primary prefix/material.
            if (expectedPrefix != null && !hasPrefix(stack, expectedPrefix)) return null;
            String name = data.mMaterial.mMaterial.mName;
            // The primary association distinguishes PigIron/CastIron from Iron even
            // though all three are additionally registered under AnyIron.
            if (isConcreteMaterialName(name)) return name;
        }

        Set<String> materials = new HashSet<String>();
        for (OrePrefixes.ParsedOreDictName parsedName : OrePrefixes.detectPrefix(stack)) {
            // Physical extruder templates have no fixed output prefix; accept any parsed
            // prefix, but still require the same nonempty material name as the input ingot.
            if (parsedName != null && parsedName.prefix != null
                && (expectedPrefix == null || parsedName.prefix == expectedPrefix)
                && isConcreteMaterialName(parsedName.material)) {
                materials.add(parsedName.material);
            }
        }
        // Without a primary association, ambiguous concrete names cannot prove identity.
        return materials.size() == 1 ? materials.iterator()
            .next() : null;
    }

    private static boolean isConcreteMaterialName(String name) {
        return name != null && !name.isEmpty() && !name.equals("_NULL") && !name.startsWith("Any");
    }

    private static boolean sameTemplate(ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null) return false;
        ItemStack expectedCopy = expected.copy();
        ItemStack actualCopy = actual.copy();
        expectedCopy.stackSize = 0;
        actualCopy.stackSize = 0;
        return GTUtility.areStacksEqual(expectedCopy, actualCopy);
    }
}
