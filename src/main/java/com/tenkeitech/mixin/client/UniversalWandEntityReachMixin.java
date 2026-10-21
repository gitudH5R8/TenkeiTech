/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.tenkeitech.item.ItemUniversalWand;

@Mixin(EntityRenderer.class)
public abstract class UniversalWandEntityReachMixin {

    @ModifyConstant(method = "getMouseOver", constant = { @Constant(doubleValue = 3D), @Constant(doubleValue = 6D) })
    private double tenkeitech$extendEntityRay(double reach) {
        return reach
            + (ItemUniversalWand.hasReachBonus(Minecraft.getMinecraft().thePlayer) ? ItemUniversalWand.REACH_BONUS
                : 0D);
    }
}
