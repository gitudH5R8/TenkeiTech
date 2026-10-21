/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.minecraft;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.universalwand.UniversalWandRightClick;

/** Include the native block's handled result in a pending wand click without changing activation order. */
@Mixin(ItemInWorldManager.class)
public abstract class UniversalWandBlockInteractionMixin {

    @WrapMethod(method = "activateBlockOrUseItem")
    private boolean tenkeitech$recordInteractionChanges(EntityPlayer player, World world, ItemStack stack, int x, int y,
        int z, int side, float hitX, float hitY, float hitZ, Operation<Boolean> original) {
        if (!UniversalWandRightClick.isPending(player) || !world.blockExists(x, y, z))
            return original.call(player, world, stack, x, y, z, side, hitX, hitY, hitZ);
        Block before = world.getBlock(x, y, z);
        int metadata = world.getBlockMetadata(x, y, z);
        try {
            return original.call(player, world, stack, x, y, z, side, hitX, hitY, hitZ);
        } finally {
            // Forge event handlers can change the target even if a later handler makes the item return false.
            if (world.blockExists(x, y, z)) UniversalWandRightClick
                .handled(player, world.getBlock(x, y, z) != before || world.getBlockMetadata(x, y, z) != metadata);
        }
    }

    @WrapOperation(
        method = "activateBlockOrUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onBlockActivated(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z"))
    private boolean tenkeitech$recordBlockActivation(Block block, World world, int x, int y, int z, EntityPlayer player,
        int side, float hitX, float hitY, float hitZ, Operation<Boolean> original) {
        return UniversalWandRightClick
            .handled(player, original.call(block, world, x, y, z, player, side, hitX, hitY, hitZ));
    }

    @WrapOperation(
        method = "activateBlockOrUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;tryPlaceItemIntoWorld(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFF)Z"))
    private boolean tenkeitech$recordPlacement(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        int side, float hitX, float hitY, float hitZ, Operation<Boolean> original) {
        // The result includes Forge placement protection/rollback and can belong to the temporary offhand slot.
        return UniversalWandRightClick
            .handled(player, original.call(stack, player, world, x, y, z, side, hitX, hitY, hitZ));
    }

    @Inject(method = "tryUseItem", at = @At("RETURN"))
    private void tenkeitech$recordItemUse(EntityPlayer player, World world, ItemStack stack,
        CallbackInfoReturnable<Boolean> cir) {
        UniversalWandRightClick.handled(player, cir.getReturnValue());
    }
}
