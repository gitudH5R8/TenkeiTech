/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.tenkeitech.ModItems;
import com.tenkeitech.resonating.ResonatingPatternData;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.items.misc.ItemEncodedPattern;
import appeng.items.misc.ItemEncodedUltimatePattern;

/** An encoded item-processing pattern plus pearl converts; crafting alone restores the original pattern. */
public final class RecipeResonatingPattern implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        ItemStack source = source(grid);
        if (source == null) return false;
        if (source.getItem() == ModItems.RESONATING_PATTERN) return restoredItem(source) != null;
        return convert(source, world) != null;
    }

    /** Shared conversion validation for pearl crafting and reusable binding-card crafting. */
    public static ItemStack convert(ItemStack source, World world) {
        if (!supportedSource(source)) return null;
        if (!source.hasTagCompound()) return null;
        try {
            var details = ((ICraftingPatternItem) source.getItem()).getPatternForItem(source.copy(), world);
            if (details == null || details.isCraftable()
                || details.isInputOnly()
                || details.canSubstitute()
                || source.getTagCompound()
                    .getBoolean("crafting"))
                return null;
            for (var input : details.getAEInputs()) {
                if (input != null && !ResonatingPatternData.supportedInput(input)) return null;
            }
            for (var output : details.getAEOutputs()) {
                if (output != null && !ResonatingPatternData.supportedMaterial(output)) return null;
            }
            ItemStack result = new ItemStack(ModItems.RESONATING_PATTERN);
            NBTTagCompound nbt = (NBTTagCompound) source.getTagCompound()
                .copy();
            NBTTagCompound route = new NBTTagCompound();
            route.setInteger("schema", 1);
            route.setTag(
                "sourceItem",
                new ItemStack(source.getItem(), 1, source.getItemDamage()).writeToNBT(new NBTTagCompound()));
            nbt.setTag(ResonatingPatternData.ROUTING, route);
            result.setTagCompound(nbt);
            return ModItems.RESONATING_PATTERN.getPatternForItem(result, world) != null ? result : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ItemStack source(InventoryCrafting grid) {
        ItemStack source = null;
        int pearl = 0;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack == null) continue;
            if (stack.getItem() == Items.ender_pearl) {
                if (++pearl > 1) return null;
            } else if (source == null && (stack.getItem() == ModItems.RESONATING_PATTERN || supportedSource(stack)))
                source = stack;
            else return null;
        }
        return source != null && pearl == (source.getItem() == ModItems.RESONATING_PATTERN ? 0 : 1) ? source : null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack source = source(grid);
        if (source == null || !source.hasTagCompound()) return null;
        boolean restore = source.getItem() == ModItems.RESONATING_PATTERN;
        if (!restore) return convert(source, null);
        ItemStack output = restoredItem(source);
        if (output == null) return null;
        NBTTagCompound nbt = (NBTTagCompound) source.getTagCompound()
            .copy();
        nbt.removeTag(ResonatingPatternData.ROUTING);
        output.setTagCompound(nbt);
        return output;
    }

    public static boolean supportedSource(ItemStack stack) {
        return stack != null && (stack.getItem()
            .getClass() == ItemEncodedPattern.class
            || stack.getItem()
                .getClass() == ItemEncodedUltimatePattern.class
            || stack.getItem()
                .getClass() == com.glodblock.github.common.item.ItemFluidEncodedPattern.class);
    }

    /** Original encoded item identity, without recipe or routing data. */
    public static ItemStack restoredItem(ItemStack source) {
        if (!source.hasTagCompound()) return null;
        NBTTagCompound route = source.getTagCompound()
            .getCompoundTag(ResonatingPatternData.ROUTING);
        if (route.getInteger("schema") != 1) return null;
        if (!route.hasKey("sourceItem", 10)) return null;
        ItemStack restored = ItemStack.loadItemStackFromNBT(route.getCompoundTag("sourceItem"));
        if (!supportedSource(restored) || restored.stackSize != 1) return null;
        restored.setTagCompound(null);
        return restored;
    }

    /** A detached original pattern, suitable for terminal loading without losing sparse slots. */
    public static ItemStack restoreForEditing(ItemStack source) {
        if (source == null || source.getItem() != ModItems.RESONATING_PATTERN) return source;
        ItemStack restored = restoredItem(source);
        if (restored == null) return null;
        NBTTagCompound data = (NBTTagCompound) source.getTagCompound()
            .copy();
        data.removeTag(ResonatingPatternData.ROUTING);
        restored.setTagCompound(data);
        return restored;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
