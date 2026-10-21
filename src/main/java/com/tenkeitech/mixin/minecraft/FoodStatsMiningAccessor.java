/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.util.FoodStats;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodStats.class)
public interface FoodStatsMiningAccessor {

    @Accessor("foodExhaustionLevel")
    float tenkeitech$getMiningExhaustion();

    @Accessor("foodExhaustionLevel")
    void tenkeitech$setMiningExhaustion(float exhaustion);
}
