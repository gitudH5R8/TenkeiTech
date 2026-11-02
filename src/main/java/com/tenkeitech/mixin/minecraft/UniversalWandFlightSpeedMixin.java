/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandFlightMotionHandler;
import com.tenkeitech.universalwand.UniversalWandUtilityHandler;

@Mixin(EntityPlayer.class)
public abstract class UniversalWandFlightSpeedMixin {

    @WrapOperation(
        method = "onLivingUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;decrementAnimations()V"),
        require = 1)
    private void tenkeitech$preserveFlightMomentum(InventoryPlayer inventory, Operation<Void> original) {
        UniversalWandFlightMotionHandler.HorizontalMotion motion = UniversalWandFlightMotionHandler
            .capture((EntityPlayer) (Object) this);
        try {
            original.call(inventory);
        } finally {
            if (motion != null) motion.restore();
        }
    }

    @Redirect(
        method = "moveEntityWithHeading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerCapabilities;getFlySpeed()F"))
    private float tenkeitech$useConfirmedFlightSpeed(PlayerCapabilities capabilities) {
        return UniversalWandUtilityHandler
            .resolveClientFlightSpeed((EntityPlayer) (Object) this, capabilities.getFlySpeed());
    }
}
