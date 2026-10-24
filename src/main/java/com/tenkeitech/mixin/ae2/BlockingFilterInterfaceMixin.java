/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.OptionalInt;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.gtnhlib.capability.item.ItemSink;
import com.gtnewhorizon.gtnhlib.item.ItemStackPredicate;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.blockingfilter.BlockingFilter;
import com.tenkeitech.blockingfilter.BlockingFilterHost;
import com.tenkeitech.blockingfilter.BlockingFilterState;

import appeng.api.AEApi;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorFluidHandler;
import appeng.util.inv.ItemSlot;

@Mixin(value = DualityInterface.class, remap = false)
public abstract class BlockingFilterInterfaceMixin implements BlockingFilterHost {

    @Shadow
    @Final
    private IInterfaceHost iHost;
    @Shadow
    @Final
    private UpgradeInventory upgrades;
    @Shadow
    private long busyCache;
    @Unique
    private final BlockingFilterState tenkeitech$filterState = new BlockingFilterState();

    @Override
    public BlockingFilter tenkeitech$blockingFilter() {
        return tenkeitech$filterState.get(iHost, upgrades);
    }

    @Inject(method = "onChangeInventory", at = @At("HEAD"), require = 1)
    private void tenkeitech$invalidate(IInventory inv, int slot, InvOperation operation, ItemStack removed,
        ItemStack added, CallbackInfo ci) {
        if (inv == upgrades) {
            tenkeitech$filterState.invalidate();
            busyCache = Long.MIN_VALUE;
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void tenkeitech$read(NBTTagCompound tag, CallbackInfo ci) {
        tenkeitech$filterState.invalidate();
        busyCache = Long.MIN_VALUE;
    }

    @Inject(method = "tileHasOnlyIgnoredItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$items(InventoryAdaptor adaptor, CallbackInfoReturnable<Boolean> cir) {
        BlockingFilter filter = tenkeitech$blockingFilter();
        if (filter.empty()) return;
        for (ItemSlot slot : adaptor) {
            ItemStack stack = slot.getItemStack();
            if (stack != null && !AEApi.instance()
                .registries()
                .blockingModeIgnoreItem()
                .isIgnored(stack) && !filter.ignores(stack)) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }

    @WrapOperation(
        method = "inventoryCountsAsEmpty",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/gtnhlib/capability/item/ItemSink;getStoredItemsInSink(Lcom/gtnewhorizon/gtnhlib/item/ItemStackPredicate;)Ljava/util/OptionalInt;"),
        require = 1)
    private OptionalInt tenkeitech$sink(ItemSink sink, ItemStackPredicate predicate, Operation<OptionalInt> original) {
        BlockingFilter filter = tenkeitech$blockingFilter();
        if (filter.empty()) return original.call(sink, predicate);
        return original.call(sink, predicate.and(stack -> !filter.ignores(stack.toStackFast())));
    }

    @WrapOperation(
        method = "inventoryCountsAsEmpty",
        at = @At(value = "INVOKE", target = "Lappeng/util/inv/AdaptorFluidHandler;containsFluid()Z"),
        require = 1)
    private boolean tenkeitech$gtFluid(AdaptorFluidHandler adaptor, Operation<Boolean> original) {
        BlockingFilter filter = tenkeitech$blockingFilter();
        return filter.empty() ? original.call(adaptor) : filter.hasBlockingPacket(adaptor);
    }

    @ModifyExpressionValue(
        method = "inventoryCountsAsEmpty",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraftforge/fluids/FluidTankInfo;fluid:Lnet/minecraftforge/fluids/FluidStack;"),
        require = 1)
    private FluidStack tenkeitech$interfaceFluid(FluidStack fluid) {
        return tenkeitech$blockingFilter().ignores(fluid) ? null : fluid;
    }
}
