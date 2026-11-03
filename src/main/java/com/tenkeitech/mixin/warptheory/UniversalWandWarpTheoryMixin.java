/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.warptheory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandWarpHandler;

import shukaro.warptheory.handlers.IWarpEvent;

@Pseudo
@Mixin(targets = "shukaro.warptheory.handlers.WarpEventHandler", remap = false)
public abstract class UniversalWandWarpTheoryMixin {

    @WrapOperation(
        method = "livingUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lshukaro/warptheory/handlers/IWarpEvent;doEvent(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Z"),
        require = 1)
    private boolean tenkeitech$suppressWarp(IWarpEvent event, World world, EntityPlayer player,
        Operation<Boolean> original) {
        // The event has already been dequeued, so suppressed effects cannot accumulate for later replay.
        if (UniversalWandWarpHandler.suppressEvent(player)) return false;
        return original.call(event, world, player);
    }
}
