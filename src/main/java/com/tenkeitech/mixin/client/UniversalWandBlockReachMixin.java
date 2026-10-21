/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.item.ItemUniversalWand;

@Mixin(PlayerControllerMP.class)
public abstract class UniversalWandBlockReachMixin {

    @Inject(method = "getBlockReachDistance", at = @At("RETURN"), cancellable = true)
    private void tenkeitech$extendBlockReach(CallbackInfoReturnable<Float> cir) {
        if (ItemUniversalWand.hasReachBonus(Minecraft.getMinecraft().thePlayer))
            cir.setReturnValue(cir.getReturnValue() + (float) ItemUniversalWand.REACH_BONUS);
    }
}
