/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

@Mixin(World.class)
public abstract class UniversalWandRemovalMixin {

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
