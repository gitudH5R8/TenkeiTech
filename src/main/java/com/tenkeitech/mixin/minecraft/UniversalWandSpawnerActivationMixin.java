/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(MobSpawnerBaseLogic.class)
public abstract class UniversalWandSpawnerActivationMixin {

    @WrapOperation(
        method = "isActivated",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getClosestPlayer(DDDD)Lnet/minecraft/entity/player/EntityPlayer;"))
    private EntityPlayer tenkeitech$activationPlayer(World world, double x, double y, double z, double range,
        Operation<EntityPlayer> original) {
        EntityPlayer closest = original.call(world, x, y, z, range);
        if (world.isRemote || !UniversalWandProtectionHandler.ignoresTarget(closest)) return closest;
        EntityPlayer result = null;
        double distance = range < 0 ? Double.POSITIVE_INFINITY : range * range;
        for (Object value : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) value;
            if (player.isDead || UniversalWandProtectionHandler.ignoresTarget(player)) continue;
            double candidate = player.getDistanceSq(x, y, z);
            if (candidate < distance) {
                distance = candidate;
                result = player;
            }
        }
        return result;
    }
}
