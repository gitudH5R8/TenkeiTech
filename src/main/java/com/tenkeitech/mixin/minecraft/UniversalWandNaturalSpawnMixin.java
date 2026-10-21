/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(SpawnerAnimals.class)
public abstract class UniversalWandNaturalSpawnMixin {

    @WrapOperation(
        method = "findChunksForSpawning",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/WorldServer;playerEntities:Ljava/util/List;"))
    private List<?> tenkeitech$spawnParticipants(WorldServer world, Operation<List<?>> original) {
        List<?> players = original.call(world);
        List<Object> filtered = null;
        for (int index = 0; index < players.size(); index++) {
            Object player = players.get(index);
            if (player instanceof EntityPlayer entityPlayer
                && UniversalWandProtectionHandler.ignoresTarget(entityPlayer)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(players.size());
                    filtered.addAll(players.subList(0, index));
                }
            } else if (filtered != null) {
                filtered.add(player);
            }
        }
        return filtered == null ? players : filtered;
    }
}
