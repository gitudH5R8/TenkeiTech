/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandRightClick;
import com.tenkeitech.universalwand.interaction.WandActionResult;

@Mixin(NetHandlerPlayServer.class)
public abstract class UniversalWandEntityInteractionMixin {

    @Shadow
    public EntityPlayerMP playerEntity;

    @WrapOperation(
        method = "processUseEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;interactWith(Lnet/minecraft/entity/Entity;)Z"))
    private boolean tenkeitech$recordEntityInteraction(EntityPlayerMP player, Entity target,
        Operation<Boolean> original) {
        UniversalWandRightClick.observe(player);
        try {
            return UniversalWandRightClick.handled(player, original.call(player, target));
        } catch (RuntimeException failure) {
            UniversalWandRightClick.record(player, WandActionResult.BLOCKED);
            throw failure;
        }
    }

    @WrapOperation(
        method = "processUseEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;attackTargetEntityWithCurrentItem(Lnet/minecraft/entity/Entity;)V"))
    private void tenkeitech$recordRightClickAttackHandoff(EntityPlayerMP player, Entity target,
        Operation<Void> original) {
        UniversalWandRightClick.nativeHandoff(player);
        original.call(player, target);
    }

    @Inject(method = "processPlayerDigging", at = @At("HEAD"))
    private void tenkeitech$recordRightClickDigHandoff(C07PacketPlayerDigging packet, CallbackInfo ci) {
        if (packet.func_149506_g() == 0) UniversalWandRightClick.nativeHandoff(playerEntity);
    }

    @ModifyConstant(method = "processUseEntity", constant = @Constant(doubleValue = 36D))
    private double tenkeitech$extendEntityValidation(double squaredReach) {
        if (!ItemUniversalWand.hasReachBonus(playerEntity)) return squaredReach;
        double reach = Math.sqrt(squaredReach) + ItemUniversalWand.REACH_BONUS;
        return reach * reach;
    }
}
