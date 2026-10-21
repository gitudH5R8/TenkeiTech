/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.tenkeitech.item.ItemUniversalWand;

@Mixin(NetHandlerPlayServer.class)
public abstract class UniversalWandEntityInteractionMixin {

    @Shadow
    public EntityPlayerMP playerEntity;

    @ModifyConstant(method = "processUseEntity", constant = @Constant(doubleValue = 36D))
    private double tenkeitech$extendEntityValidation(double squaredReach) {
        if (!ItemUniversalWand.hasReachBonus(playerEntity)) return squaredReach;
        double reach = Math.sqrt(squaredReach) + ItemUniversalWand.REACH_BONUS;
        return reach * reach;
    }
}
