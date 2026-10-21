/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.enderio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.item.ItemUniversalWand;

import crazypants.enderio.api.teleport.TravelSource;

@Pseudo
@Mixin(targets = "crazypants.enderio.teleport.TravelController", remap = false)
public abstract class UniversalWandTeleportStaffMixin {

    @Inject(method = "getTravelSource", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$selectTeleportSource(EntityPlayer player, ItemStack stack,
        CallbackInfoReturnable<TravelSource> cir) {
        if (stack != null && stack.getItem() instanceof ItemUniversalWand && ItemUniversalWand.isTeleportEnabled(stack))
            cir.setReturnValue(TravelSource.TELEPORT_STAFF);
    }

    @Inject(method = "validatePacketTravelEvent", at = @At("RETURN"), cancellable = true)
    private void tenkeitech$acceptEnabledWand(net.minecraft.entity.player.EntityPlayerMP player, int x, int y, int z,
        int power, boolean conserveMotion, TravelSource source, CallbackInfoReturnable<String> cir) {
        // Only replace the final concrete-item rejection, after native distance and power checks.
        if (!"not staff".equals(cir.getReturnValue())
            || source != TravelSource.TELEPORT_STAFF && source != TravelSource.TELEPORT_STAFF_BLINK) return;
        ItemStack stack = player.getHeldItem();
        if (stack != null && stack.getItem() instanceof ItemUniversalWand && ItemUniversalWand.isTeleportEnabled(stack))
            cir.setReturnValue(null);
    }
}
