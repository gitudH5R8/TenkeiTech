/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandCombatHandler;

@Mixin(EntityPlayer.class)
public abstract class UniversalWandCombatMixin {

    @WrapMethod(method = "attackTargetEntityWithCurrentItem")
    private void tenkeitech$combatScope(Entity target, Operation<Void> original) {
        try (UniversalWandCombatHandler.AttackScope scope = UniversalWandCombatHandler
            .begin((EntityPlayer) (Object) this, target)) {
            original.call(target);
            if (scope != null) scope.complete();
        }
    }

    @WrapOperation(
        method = "attackTargetEntityWithCurrentItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;onLeftClickEntity(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)Z",
            remap = false))
    private boolean tenkeitech$tryStrongAttack(Item item, ItemStack stack, EntityPlayer player, Entity target,
        Operation<Boolean> original) {
        return original.call(item, stack, player, target) || UniversalWandCombatHandler.beforeAttack(player, target);
    }

    @WrapOperation(
        method = "attackTargetEntityWithCurrentItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"))
    private boolean tenkeitech$observeAcceptedAttack(Entity target, DamageSource source, float amount,
        Operation<Boolean> original) {
        boolean accepted = original.call(target, source, amount);
        UniversalWandCombatHandler.observeAttack((EntityPlayer) (Object) this, target, accepted);
        return accepted;
    }
}
