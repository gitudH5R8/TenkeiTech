/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.item.ItemUniversalWand;

import gregtech.common.GTClient;

/** Keeps GT's native full-block pipe selection scoped to the local player's current wand mode. */
@Mixin(value = GTClient.class, remap = false)
public abstract class UniversalWandPipeSelectionMixin {

    @Inject(method = "shouldHeldItemForceFullBlockBB", at = @At("RETURN"), cancellable = true)
    private static void tenkeitech$selectPipesWithWand(CallbackInfoReturnable<Boolean> cir) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (ItemUniversalWand.isHeldBy(player)
            && ItemUniversalWand.getGtToolMode(player.getHeldItem()) != ItemUniversalWand.GtToolMode.OFF) {
            cir.setReturnValue(true);
        }
    }
}
