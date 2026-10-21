/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.wildcardpattern;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.tenkeitech.integration.wildcardpattern.WildcardPatternItemBridge;

import appeng.api.networking.crafting.ICraftingPatternDetails;

@Pseudo
@Mixin(
    targets = { "com.myname.wildcardpattern.item.ItemWildcardPattern",
        "com.myname.wildcardpattern.item.ItemCompositeWildcardPattern" },
    remap = false)
public abstract class WildcardPatternItemMixin implements WildcardPatternItemBridge {

    @Override
    public List<ICraftingPatternDetails> tenkeitech$getExpandedPatternDetails(ItemStack pattern, World world) {
        return WildcardPatternGeneratorInvoker.tenkeitech$generateAllDetails(pattern, world);
    }

    @Override
    public String tenkeitech$getExpandedPatternId(ICraftingPatternDetails details) {
        return details == null ? ""
            : WildcardPatternGeneratorInvoker.tenkeitech$getGeneratedPatternId(details.getPattern());
    }
}
