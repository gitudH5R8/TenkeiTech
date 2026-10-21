/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.wildcardpattern;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Implemented on Wildcard Pattern items by an optional Mixin when that mod is present. */
public interface WildcardPatternItemBridge {

    List<ICraftingPatternDetails> tenkeitech$getExpandedPatternDetails(ItemStack pattern, World world);

    String tenkeitech$getExpandedPatternId(ICraftingPatternDetails details);
}
