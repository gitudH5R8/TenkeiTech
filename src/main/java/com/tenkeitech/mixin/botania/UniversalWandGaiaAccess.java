/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.botania;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "vazkii.botania.common.entity.EntityDoppleganger", remap = false)
public abstract class UniversalWandGaiaAccess implements com.tenkeitech.universalwand.UniversalWandGaiaAccess {

    @Invoker(value = "getInvulTime", remap = false)
    public abstract int tenkeitech$getGaiaInvulnerability();

    @Invoker(value = "setInvulTime", remap = false)
    public abstract void tenkeitech$setGaiaInvulnerability(int ticks);
}
