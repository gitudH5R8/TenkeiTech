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
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.networking.crafting.ICraftingPatternDetails;

@Pseudo
@Mixin(targets = "com.myname.wildcardpattern.crafting.WildcardPatternGenerator", remap = false)
public interface WildcardPatternGeneratorInvoker {

    @Invoker("generateAllDetails")
    static List<ICraftingPatternDetails> tenkeitech$generateAllDetails(ItemStack pattern, World world) {
        throw new AssertionError();
    }

    @Invoker("getGeneratedPatternId")
    static String tenkeitech$getGeneratedPatternId(ItemStack pattern) {
        throw new AssertionError();
    }
}
