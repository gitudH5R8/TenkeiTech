/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tenkeitech.ModItems;
import com.tenkeitech.patternmode.PatternModeEncodingContext;
import com.tenkeitech.recipe.RecipeResonatingPattern;
import com.tenkeitech.resonating.ResonatingPatternData;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternEncodingHelper;

@Mixin(value = PatternEncodingHelper.class, remap = false)
public abstract class PatternEncodingModeMixin {

    @Redirect(
        method = "encode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/IInventory;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
            ordinal = 1,
            remap = true))
    private static void tenkeitech$writeEncodedPattern(IInventory inventory, int slot, ItemStack pattern,
        IPatternTerminal terminal, IEnergySource energy, IMEMonitor<IAEItemStack> monitor, BaseActionSource source,
        String author, World world) {
        ItemStack previous = inventory.getStackInSlot(slot);
        if (previous != null && previous.getItem() == ModItems.RESONATING_PATTERN
            && !terminal.isCraftingRecipe()
            && !(pattern.getItem() instanceof appeng.items.misc.ItemTunnelPattern)) {
            ItemStack original = RecipeResonatingPattern.restoredItem(previous);
            // Standard AE item patterns cannot decode newly added fluid inputs or outputs.
            if (original != null && original.getItem()
                .getClass() == appeng.items.misc.ItemEncodedPattern.class) {
                for (String key : new String[] { "in", "out" }) {
                    var entries = pattern.getTagCompound()
                        .getTagList(key, 10);
                    for (int i = 0; i < entries.tagCount(); i++) {
                        var material = appeng.util.Platform.readStackNBT(entries.getCompoundTagAt(i));
                        if (material != null && material.isFluid()) original = null;
                    }
                }
            }
            if (original != null) {
                original.setTagCompound(pattern.getTagCompound());
                original.getTagCompound()
                    .removeTag(ResonatingPatternData.ROUTING);
                pattern = original;
            }
        }
        if (!world.isRemote) PatternModeEncodingContext.encoded(terminal, pattern);
        inventory.setInventorySlotContents(slot, pattern);
    }

    @Redirect(
        method = "encode",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/parts/IPatternTerminal$PatternEncodeListener;onEncoded(Lappeng/api/parts/IPatternTerminal;Lnet/minecraft/item/ItemStack;)V"),
        require = 1)
    private static void tenkeitech$notifyStoredPattern(IPatternTerminal.PatternEncodeListener listener,
        IPatternTerminal terminal, ItemStack pattern) {
        listener.onEncoded(
            terminal,
            terminal.getInventoryByName("pattern")
                .getStackInSlot(1));
    }
}
