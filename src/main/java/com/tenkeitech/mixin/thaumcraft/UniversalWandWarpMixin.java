/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.thaumcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.tenkeitech.universalwand.UniversalWandWarpHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Pseudo
@Mixin(targets = "thaumcraft.common.lib.WarpEvents", remap = false)
public abstract class UniversalWandWarpMixin {

    // The second roll is reached only after the native probability test and counter update.
    // Zero selects the existing no-effect branch, retaining warp decay and research unlocks below it.
    @ModifyExpressionValue(
        method = "checkWarpEvent",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 1),
        require = 1)
    private static int tenkeitech$skipWarpEffect(int rolled, @Local(argsOnly = true) EntityPlayer player) {
        return UniversalWandWarpHandler.isSuppressed(player) ? 0 : rolled;
    }

    @WrapWithCondition(
        method = "checkWarpEvent",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;sendTo(Lcpw/mods/fml/common/network/simpleimpl/IMessage;Lnet/minecraft/entity/player/EntityPlayerMP;)V",
            ordinal = 0),
        require = 1)
    private static boolean tenkeitech$replaceWarpFeedback(SimpleNetworkWrapper channel, IMessage packet,
        EntityPlayerMP player) {
        if (!UniversalWandWarpHandler.isSuppressed(player)) return true;
        UniversalWandWarpHandler.notifySuppressed(player);
        return false;
    }

    @WrapWithCondition(
        method = "checkWarpEvent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;addChatMessage(Lnet/minecraft/util/IChatComponent;)V",
            remap = true),
        require = 16)
    private static boolean tenkeitech$hideOriginalWarpText(EntityPlayer player, IChatComponent message) {
        return !UniversalWandWarpHandler.isSuppressed(player);
    }
}
