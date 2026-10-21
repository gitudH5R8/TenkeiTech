/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityLivingBase.class)
public interface UniversalWandLivingAccess {

    @Accessor("dead")
    boolean tenkeitech$isDeathCommitted();

    @Accessor("dead")
    void tenkeitech$setDeathCommitted(boolean dead);

    @Accessor("recentlyHit")
    void tenkeitech$setRecentlyHit(int ticks);

    @Accessor("attackingPlayer")
    void tenkeitech$setAttackingPlayer(EntityPlayer player);

    @Invoker("dropFewItems")
    void tenkeitech$dropFewItems(boolean recentlyHit, int looting);

    @Invoker("dropEquipment")
    void tenkeitech$dropEquipment(boolean recentlyHit, int looting);

    @Invoker("dropRareDrop")
    void tenkeitech$dropRareDrop(int bonus);

    @Invoker("func_146066_aG")
    boolean tenkeitech$canDropLoot();
}
