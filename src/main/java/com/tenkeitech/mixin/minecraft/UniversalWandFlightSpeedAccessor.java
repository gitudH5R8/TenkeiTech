/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.player.PlayerCapabilities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PlayerCapabilities.setFlySpeed is client-only in Minecraft 1.7.10. */
@Mixin(PlayerCapabilities.class)
public interface UniversalWandFlightSpeedAccessor {

    @Accessor("flySpeed")
    void tenkeitech$setFlySpeed(float speed);
}
