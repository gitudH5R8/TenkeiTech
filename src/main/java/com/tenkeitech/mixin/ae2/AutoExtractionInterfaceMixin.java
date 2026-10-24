/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.autoextract.AutoExtractionHost;
import com.tenkeitech.autoextract.AutoExtractionState;
import com.tenkeitech.resonating.ResonatingHost;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.ScheduledReason;

/** Native interfaces retain their normal paths unless this host owns an extraction card or unfinished task. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class AutoExtractionInterfaceMixin implements AutoExtractionHost {

    @Shadow
    @Final
    protected AENetworkProxy gridProxy;
    @Shadow
    @Final
    private BaseActionSource mySource;
    @Shadow
    @Final
    private ConfigManager cm;
    @Shadow
    @Final
    private Map<IAEStackType<?>, MEMonitorPassThrough<?>> monitorMap;
    @Shadow
    private ScheduledReason scheduledReason;

    @Shadow
    protected abstract boolean hasItemsToSend();

    @Shadow
    protected abstract boolean hasWorkToDo();

    @Shadow
    public abstract void resetCraftingLock();

    @Unique
    private final AutoExtractionState tenkeitech$autoExtraction = new AutoExtractionState();

    @Override
    public AutoExtractionState tenkeitech$extraction() {
        return tenkeitech$autoExtraction;
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void tenkeitech$init(AENetworkProxy proxy, IInterfaceHost host, CallbackInfo ci) {
        tenkeitech$autoExtraction.locate(host);
        tenkeitech$autoExtraction.installMonitors(monitorMap, mySource);
    }

    @Unique
    private void tenkeitech$enforce() {
        if (!tenkeitech$autoExtraction.present()) return;
        resetCraftingLock();
        if (cm.getSetting(Settings.BLOCK) != YesNo.YES) cm.putSetting(Settings.BLOCK, YesNo.YES);
        if (cm.getSetting(Settings.LOCK_CRAFTING_MODE) != LockCraftingMode.LOCK_UNTIL_RESULT)
            cm.putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
    }

    @Inject(method = "updateSetting", at = @At("RETURN"), require = 1)
    private void tenkeitech$setting(IConfigManager manager, Enum setting, Enum value, CallbackInfo ci) {
        tenkeitech$enforce();
    }

    @Inject(method = "onChangeInventory", at = @At("RETURN"), require = 1)
    private void tenkeitech$upgrade(IInventory inventory, int slot, InvOperation operation, ItemStack removed,
        ItemStack added, CallbackInfo ci) {
        tenkeitech$enforce();
        if (tenkeitech$autoExtraction.hasWork() && gridProxy.isActive()) {
            try {
                gridProxy.getTick()
                    .alertDevice(gridProxy.getNode());
            } catch (GridAccessException exception) { /* The task remains persisted. */ }
        }
    }

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$blocking(CallbackInfoReturnable<Boolean> cir) {
        if (tenkeitech$autoExtraction.present()) cir.setReturnValue(true);
    }

    @Inject(method = "getCraftingLockedReason", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$locked(CallbackInfoReturnable<LockCraftingMode> cir) {
        if (tenkeitech$autoExtraction.busy()) cir.setReturnValue(LockCraftingMode.LOCK_UNTIL_RESULT);
        else if (tenkeitech$autoExtraction.present()) cir.setReturnValue(LockCraftingMode.NONE);
    }

    @Inject(method = "getUnlockStacks", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$remaining(CallbackInfoReturnable<List<IAEStack<?>>> cir) {
        if (tenkeitech$autoExtraction.present() || tenkeitech$autoExtraction.busy())
            cir.setReturnValue(tenkeitech$autoExtraction.remaining());
    }

    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true, require = 1)
    private void tenkeitech$busy(CallbackInfoReturnable<Boolean> cir) {
        if (!tenkeitech$autoExtraction.present() && !tenkeitech$autoExtraction.busy()) return;
        // Let the dispatch hook report owned inputs first, regardless of HEAD injection order.
        if (((ResonatingHost) (Object) this).tenkeitech$dispatchBusy()) return;
        boolean busy = !tenkeitech$autoExtraction.installed() || hasItemsToSend()
            || tenkeitech$autoExtraction.busy() && cm.getSetting(Settings.SMART_BLOCK) != YesNo.YES;
        if (busy) scheduledReason = ScheduledReason.LOCK_MODE;
        // Destination blocking belongs in each push path: remote recipes must not test the local output chest.
        cir.setReturnValue(busy);
    }

    @Redirect(
        method = "pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/helpers/DualityInterface;getCraftingLockedReason()Lappeng/api/config/LockCraftingMode;"),
        require = 1)
    private LockCraftingMode tenkeitech$acceptedRecipeLock(DualityInterface duality) {
        // The common push gateway has already checked the full recipe and reserved bounded output counts.
        return tenkeitech$autoExtraction.pending() ? LockCraftingMode.NONE : duality.getCraftingLockedReason();
    }

    @Redirect(
        method = "pushPattern",
        at = @At(value = "INVOKE", target = "Lappeng/helpers/DualityInterface;isBlocking()Z"),
        require = 1)
    private boolean tenkeitech$repeatBlocking(DualityInterface duality) {
        if (tenkeitech$autoExtraction.present()) {
            // begin() compared the complete encoded pattern and output identities before allowing this repeat.
            return !tenkeitech$autoExtraction.repeating();
        }
        return cm.getSetting(Settings.BLOCK) == YesNo.YES;
    }

    @Redirect(
        method = "pushPattern",
        at = @At(value = "INVOKE", target = "Lappeng/helpers/DualityInterface;isSmartBlocking()Z"),
        require = 1)
    private boolean tenkeitech$recipeIdentity(DualityInterface duality) {
        // Active card requests use the complete recipe comparison above rather than the native last-input hash.
        return !tenkeitech$autoExtraction.present() && cm.getSetting(Settings.SMART_BLOCK) == YesNo.YES;
    }

    @Redirect(
        method = "onPushPatternSuccess",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ConfigManager;getSetting(Lappeng/api/config/Settings;)Ljava/lang/Enum;"),
        require = 1)
    private Enum<?> tenkeitech$localLock(ConfigManager manager, Settings setting) {
        if (setting == Settings.LOCK_CRAFTING_MODE
            && (tenkeitech$autoExtraction.present() || tenkeitech$autoExtraction.busy())) return LockCraftingMode.NONE;
        return manager.getSetting(setting);
    }

    @WrapMethod(method = "pushPattern", remap = false)
    private boolean tenkeitech$accepted(ICraftingPatternDetails details, InventoryCrafting table,
        Operation<Boolean> original) {
        if ((tenkeitech$autoExtraction.present() || tenkeitech$autoExtraction.busy())
            && !tenkeitech$autoExtraction.begin(details, cm.getSetting(Settings.SMART_BLOCK) == YesNo.YES)) {
            scheduledReason = ScheduledReason.LOCK_MODE;
            return false;
        }
        boolean accepted;
        try {
            accepted = original.call(details, table);
        } catch (RuntimeException | Error exception) {
            tenkeitech$autoExtraction.failedPush(exception);
            throw exception;
        }
        tenkeitech$autoExtraction.finish(accepted);
        if (tenkeitech$autoExtraction.hasWork() && gridProxy.isActive()) {
            try {
                gridProxy.getTick()
                    .alertDevice(gridProxy.getNode());
            } catch (GridAccessException exception) { /* Keep the accepted promise. */ }
        }
        return accepted;
    }

    @Inject(method = "hasWorkToDo", at = @At("RETURN"), cancellable = true, require = 1)
    private void tenkeitech$work(CallbackInfoReturnable<Boolean> cir) {
        if (tenkeitech$autoExtraction.hasWork()) cir.setReturnValue(true);
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true, require = 1)
    private void tenkeitech$tick(IGridNode node, int elapsed, CallbackInfoReturnable<TickRateModulation> cir) {
        boolean progressed = tenkeitech$autoExtraction.tick(
            net.minecraft.server.MinecraftServer.getServer()
                .getTickCounter(),
            gridProxy,
            mySource);
        if (progressed) {
            // Match native storage transfers: stay responsive while working, then sleep only when all work is done.
            // The shared work query also retains native pending inputs and resonating dispatch work.
            cir.setReturnValue(hasWorkToDo() ? TickRateModulation.URGENT : TickRateModulation.SLEEP);
        } else if (tenkeitech$autoExtraction.hasWork() && cir.getReturnValue() == TickRateModulation.SLEEP)
            cir.setReturnValue(TickRateModulation.SLOWER);
    }

    @Redirect(
        method = "usePlan",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;poweredInsert(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;"),
        require = 1)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStack tenkeitech$returnStored(IEnergySource energy, IMEInventory inventory, IAEStack input,
        BaseActionSource source) {
        return tenkeitech$autoExtraction.poweredInsert(energy, inventory, input, source);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void tenkeitech$write(NBTTagCompound tag, CallbackInfo ci) {
        tenkeitech$autoExtraction.write(tag);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void tenkeitech$read(NBTTagCompound tag, CallbackInfo ci) {
        tenkeitech$autoExtraction.read(tag);
        tenkeitech$enforce();
    }

    @Inject(method = "addDrops", at = @At("RETURN"), require = 1)
    private void tenkeitech$drops(List<ItemStack> drops, CallbackInfo ci) {
        tenkeitech$autoExtraction.addDrops(drops);
    }
}
