/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.resonating.ResonatingDispatch;
import com.tenkeitech.resonating.ResonatingInterfaceHosts;
import com.tenkeitech.resonating.ResonatingPatternData;

import appeng.api.config.LockCraftingMode;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;

/** Hooks have an effect only for TenkeiTech patterns or their already accepted material. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class ResonatingInterfaceMixin {

    @Shadow
    @Final
    private IInterfaceHost iHost;
    @Shadow
    @Final
    protected AENetworkProxy gridProxy;
    @Shadow
    @Final
    private BaseActionSource mySource;
    @Shadow
    public List<ICraftingPatternDetails> craftingList;
    @Shadow
    private ScheduledReason scheduledReason;
    @Shadow
    private int lastInputHash;

    @Shadow
    protected abstract boolean hasItemsToSend();

    @Shadow
    private boolean isBlocking() {
        throw new AssertionError();
    }

    @Shadow
    private boolean isSmartBlocking() {
        throw new AssertionError();
    }

    @Shadow
    public abstract LockCraftingMode getCraftingLockedReason();

    @Shadow
    public abstract void saveChanges();

    @Shadow
    private void onPushPatternSuccess(TileEntity tile, ForgeDirection side, ICraftingPatternDetails details) {
        throw new AssertionError();
    }

    @Unique
    private final ResonatingDispatch tenkeitech$dispatch = new ResonatingDispatch();

    @Redirect(
        method = "provideCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProviderHelper;addCraftingOption(Lappeng/api/networking/crafting/ICraftingMedium;Lappeng/api/networking/crafting/ICraftingPatternDetails;)V"),
        require = 1)
    private void tenkeitech$publish(ICraftingProviderHelper helper, ICraftingMedium medium,
        ICraftingPatternDetails details) {
        if (!(details instanceof ResonatingPatternData) || ResonatingInterfaceHosts.supported(iHost))
            helper.addCraftingOption(medium, details);
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$push(ICraftingPatternDetails details, InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        if (tenkeitech$dispatch.busy()) {
            scheduledReason = tenkeitech$dispatch.targetUnreachable(mySource)
                ? ScheduledReason.valueOf("TENKEITECH_TARGET_UNREACHABLE")
                : ScheduledReason.SOMETHING_STUCK;
            cir.setReturnValue(false);
            return;
        }
        if (!(details instanceof ResonatingPatternData routed)) return;
        if (!routed.allInputsBound()) {
            scheduledReason = ScheduledReason.valueOf("TENKEITECH_UNBOUND_INPUTS");
            cir.setReturnValue(false);
            return;
        }
        // Unknown addon hosts are rejected, never sent through the local fallback.
        if (!ResonatingInterfaceHosts.supported(iHost) || !gridProxy.isActive()
            || hasItemsToSend()
            || craftingList == null
            || !craftingList.contains(details)
            || getCraftingLockedReason() != LockCraftingMode.NONE
            || !(table instanceof MEInventoryCrafting material)) {
            cir.setReturnValue(false);
            return;
        }
        boolean accepted;
        try {
            for (int input = 0; input < routed.getAEInputs().length; input++) {
                if (routed.getAEInputs()[input] == null) continue;
                if (com.tenkeitech.resonating.ResonatingTargetAccess
                    .inventory(routed.target(input), routed.getPattern(), mySource, routed.getAEInputs()[input])
                    == null) {
                    scheduledReason = ScheduledReason.valueOf("TENKEITECH_TARGET_UNREACHABLE");
                    cir.setReturnValue(false);
                    return;
                }
            }
            accepted = tenkeitech$dispatch.accept(
                routed,
                material,
                mySource,
                isBlocking() && !(isSmartBlocking() && lastInputHash == details.hashCode()),
                isBlocking());
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Rejected resonating input before accepting ownership", exception);
            accepted = false;
        }
        if (accepted) {
            // No external inventory has been mutated yet. All input now belongs to the persistent dispatch.
            try {
                saveChanges();
                onPushPatternSuccess(null, ForgeDirection.UNKNOWN, details);
                gridProxy.getTick()
                    .alertDevice(gridProxy.getNode());
            } catch (RuntimeException | GridAccessException exception) {
                TenkeiTech.LOG
                    .error("Resonating dispatch accepted; retained input despite notification failure", exception);
            }
        } else scheduledReason = ScheduledReason.SOMETHING_STUCK;
        cir.setReturnValue(accepted);
    }

    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$busy(CallbackInfoReturnable<Boolean> cir) {
        if (tenkeitech$dispatch.busy()) {
            scheduledReason = tenkeitech$dispatch.targetUnreachable(mySource)
                ? ScheduledReason.valueOf("TENKEITECH_TARGET_UNREACHABLE")
                : ScheduledReason.SOMETHING_STUCK;
            cir.setReturnValue(true);
            return;
        }
        // A pure remote interface must test remote blocking in pushPattern, not its unrelated neighbours.
        if (ResonatingInterfaceHosts.supported(iHost) && craftingList != null
            && !craftingList.isEmpty()
            && craftingList.stream()
                .allMatch(pattern -> pattern instanceof ResonatingPatternData)) {
            cir.setReturnValue(hasItemsToSend() || getCraftingLockedReason() != LockCraftingMode.NONE);
        }
    }

    @Inject(method = "hasWorkToDo", at = @At("RETURN"), cancellable = true, require = 1)
    private void tenkeitech$work(CallbackInfoReturnable<Boolean> cir) {
        if (tenkeitech$dispatch.busy()) cir.setReturnValue(true);
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true, require = 1)
    private void tenkeitech$tick(IGridNode node, int elapsed, CallbackInfoReturnable<TickRateModulation> cir) {
        if (!tenkeitech$dispatch.busy() || !gridProxy.isActive()) return;
        tenkeitech$dispatch.locate(iHost);
        try {
            if (tenkeitech$dispatch.tick(
                net.minecraft.server.MinecraftServer.getServer()
                    .getTickCounter(),
                gridProxy.getEnergy(),
                mySource)) saveChanges();
        } catch (GridAccessException exception) { /* Keep owned input until the network is available. */ }
        if (tenkeitech$dispatch.busy() && cir.getReturnValue() == TickRateModulation.SLEEP)
            cir.setReturnValue(TickRateModulation.SLOWER);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void tenkeitech$write(NBTTagCompound tag, CallbackInfo ci) {
        tenkeitech$dispatch.write(tag);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void tenkeitech$read(NBTTagCompound tag, CallbackInfo ci) {
        tenkeitech$dispatch.locate(iHost);
        tenkeitech$dispatch.read(tag);
    }

    @Inject(method = "addDrops", at = @At("RETURN"), require = 1)
    private void tenkeitech$drops(List<ItemStack> drops, CallbackInfo ci) {
        tenkeitech$dispatch.locate(iHost);
        tenkeitech$dispatch.addDrops(drops);
    }
}
