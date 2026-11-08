/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.universalwand.UniversalWandAccelerationHandler;
import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(World.class)
public abstract class UniversalWandRemovalMixin {

    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"), remap = false)
    private void tenkeitech$removeDestroyedAcceleration(int x, int y, int z, Chunk chunk, Block previous,
        Block replacement, int flags, CallbackInfo ci) {
        UniversalWandAccelerationHandler.onBlockChanged((World) (Object) this, x, y, z, replacement);
    }

    // World removal also serves logout, respawn and third-party dimension transfers.
    @WrapMethod(method = { "removeEntity", "removePlayerEntityDangerously" })
    private void tenkeitech$normalRemoval(Entity entity, Operation<Void> original) {
        UniversalWandProtectionHandler.beginRemoval(entity);
        try {
            original.call(entity);
        } finally {
            UniversalWandProtectionHandler.endRemoval();
        }
    }
}
