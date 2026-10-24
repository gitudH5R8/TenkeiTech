/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.blockingfilter.BlockingFilterState;

import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;

@Mixin(value = SlotRestrictedInput.class, remap = false)
public abstract class BlockingFilterSlotMixin {

    @Shadow
    @Final
    private PlacableItemType which;

    // Retain all native enabled/container/inventory/edit checks before accepting our standalone module.
    @Inject(
        method = "isItemValid(Lnet/minecraft/item/ItemStack;)Z",
        at = @At(
            value = "FIELD",
            target = "Lappeng/container/slot/SlotRestrictedInput;which:Lappeng/container/slot/SlotRestrictedInput$PlacableItemType;",
            ordinal = 0,
            remap = false),
        cancellable = true,
        remap = true,
        require = 1)
    private void tenkeitech$filter(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (which == PlacableItemType.UPGRADES && BlockingFilterState.card(stack)) cir.setReturnValue(true);
    }
}
