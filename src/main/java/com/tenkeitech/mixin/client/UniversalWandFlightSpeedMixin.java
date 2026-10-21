/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tenkeitech.universalwand.UniversalWandUtilityHandler;

@Mixin(EntityPlayer.class)
public abstract class UniversalWandFlightSpeedMixin {

    @Redirect(
        method = "moveEntityWithHeading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerCapabilities;getFlySpeed()F"))
    private float tenkeitech$useConfirmedFlightSpeed(PlayerCapabilities capabilities) {
        float speed = UniversalWandUtilityHandler.getClientSpeed((EntityPlayer) (Object) this);
        return speed > 0 ? speed : capabilities.getFlySpeed();
    }
}
