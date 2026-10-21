/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import java.util.Arrays;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;

import com.tenkeitech.ModItems;
import com.tenkeitech.ModMetaTileEntities;

import appeng.api.AEApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;

/** Consumes a memory card while leaving the exact buffer stack in the crafting grid. */
public final class RecipeCraftPatternBufferCutTool extends ShapelessRecipes {

    public RecipeCraftPatternBufferCutTool() {
        super(
            new ItemStack(ModItems.PATTERN_BUFFER_CUT_TOOL),
            Arrays.asList(
                AEApi.instance()
                    .definitions()
                    .items()
                    .memoryCard()
                    .maybeStack(1)
                    .get(),
                ModMetaTileEntities.superPatternBuffer.copy()));
    }

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        if (!super.matches(inventory, world)) return false;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && (stack.stackSize <= 0 || stack.stackSize == Integer.MAX_VALUE)) return false;
        }
        return true;
    }

    /** Forge fires this before SlotCrafting consumes one item from each occupied slot. */
    @SubscribeEvent
    public void onItemCrafted(ItemCraftedEvent event) {
        if (event.crafting == null || event.crafting.getItem() != ModItems.PATTERN_BUFFER_CUT_TOOL
            || !(event.craftMatrix instanceof InventoryCrafting inventory)
            || !matches(inventory, null)) return;
        ItemStack buffer = ModMetaTileEntities.superPatternBuffer;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == buffer.getItem()
                && stack.getItemDamage() == buffer.getItemDamage()) {
                stack.stackSize++;
                return;
            }
        }
    }
}
