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

import com.tenkeitech.autoextract.AutoExtractionHost;
import com.tenkeitech.autoextract.AutoExtractionState;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;

@Mixin(value = UpgradeInventory.class, remap = false)
public abstract class AutoExtractionUpgradeMixin {

    @Shadow
    @Final
    private IAEAppEngInventory parent;

    // This is an IInventory override: its name must follow Minecraft's production SRG mapping.
    @Inject(
        method = "isItemValidForSlot(ILnet/minecraft/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = true,
        require = 1)
    private void tenkeitech$card(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (AutoExtractionState.card(stack)) {
            if (!(parent instanceof AutoExtractionHost host) || !host.tenkeitech$extraction()
                .supported()) {
                cir.setReturnValue(false);
                return;
            }
            // The native LOCK_CRAFTING maximum enforces one card and both installation orders.
        } else if (stack != null && stack.getItem() instanceof IUpgradeModule module
            && module.getType(stack) == Upgrades.LOCK_CRAFTING
            && parent instanceof AutoExtractionHost host
            && (host.tenkeitech$extraction()
                .present()
                || host.tenkeitech$extraction()
                    .busy())) {
                        cir.setReturnValue(false);
                    }
    }
}
