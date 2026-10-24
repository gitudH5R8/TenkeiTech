/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.blockingfilter.BlockingFilterState;
import com.tenkeitech.resonating.ResonatingInterfaceHosts;

import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;

@Mixin(value = UpgradeInventory.class, remap = false)
public abstract class BlockingFilterUpgradeMixin {

    @Shadow
    @Final
    private IAEAppEngInventory parent;

    @Inject(
        method = "isItemValidForSlot(ILnet/minecraft/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = true,
        require = 1)
    private void tenkeitech$filter(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (BlockingFilterState.card(stack)) cir.setReturnValue(
            parent instanceof DualityInterface duality && duality.getHost() instanceof IInterfaceHost host
                && ResonatingInterfaceHosts.supported(host)
                && BlockingFilterState.canInstall((IInventory) this, slot, stack));
    }
}
