/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.ModItems;
import com.tenkeitech.recipe.RecipeResonatingPattern;

import appeng.api.parts.IPatternTerminal;
import appeng.tile.inventory.IAEStackInventory;

/** Use the original decoder and slot layout in all terminals sharing AE's loading API. */
@Mixin(value = IPatternTerminal.class, remap = false)
public interface ResonatingPatternTerminalMixin {

    @Inject(method = "loadPatternFromItem", at = @At("HEAD"), cancellable = true, require = 1)
    default void tenkeitech$loadOriginal(ItemStack stack, World world, IAEStackInventory crafting,
        IAEStackInventory output, CallbackInfo callback) {
        if (stack == null || stack.getItem() != ModItems.RESONATING_PATTERN) return;
        ItemStack restored = RecipeResonatingPattern.restoreForEditing(stack);
        if (restored != null) ((IPatternTerminal) this).loadPatternFromItem(restored, world, crafting, output);
        callback.cancel();
    }
}
