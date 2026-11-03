/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.warptheory;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.tenkeitech.universalwand.UniversalWandWarpHandler;

import shukaro.warptheory.handlers.WarpCommand;

@Pseudo
@Mixin(value = WarpCommand.class, remap = false)
public abstract class UniversalWandWarpTheoryCommandMixin {

    @Inject(
        method = "processCommand(Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
        at = { @At(
            value = "INVOKE",
            target = "Lshukaro/warptheory/handlers/IMultiWarpEvent;doEvent(Ljava/lang/String;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Z",
            remap = false),
            @At(
                value = "INVOKE",
                target = "Lshukaro/warptheory/handlers/IWarpEvent;doEvent(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Z",
                remap = false) },
        cancellable = true,
        remap = true,
        require = 2)
    private void tenkeitech$suppressCommandWarp(ICommandSender sender, String[] args, CallbackInfo ci,
        @Local EntityPlayer player) {
        if (UniversalWandWarpHandler.suppressEvent(player)) ci.cancel();
    }
}
