/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.glodblock.github.util.DualityFluidInterface;
import com.tenkeitech.autoextract.AutoExtractionHost;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.helpers.IInterfaceHost;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEMonitorPassThrough;

@Mixin(value = DualityFluidInterface.class, remap = false)
public abstract class AutoExtractionFluidInterfaceMixin {

    @Shadow
    @Final
    private IInterfaceHost iHost;
    @Shadow
    @Final
    private BaseActionSource mySource;
    @Shadow
    @Final
    private Map<IAEStackType<?>, MEMonitorPassThrough<?>> monitorMap;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void tenkeitech$init(AENetworkProxy proxy, IInterfaceHost host, CallbackInfo ci) {
        ((AutoExtractionHost) host.getInterfaceDuality()).tenkeitech$extraction()
            .installMonitors(monitorMap, mySource);
    }

    @Redirect(
        method = "usePlan",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/IMEInventory;injectItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;"),
        require = 1)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStack tenkeitech$stored(IMEInventory inventory, IAEStack input, Actionable mode,
        BaseActionSource source) {
        return ((AutoExtractionHost) iHost.getInterfaceDuality()).tenkeitech$extraction()
            .inject(inventory, input, mode, source);
    }

    @Redirect(
        method = "fill",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/IMEMonitor;injectItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;"),
        require = 2)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStack tenkeitech$filled(IMEMonitor inventory, IAEStack input, Actionable mode, BaseActionSource source) {
        return ((AutoExtractionHost) iHost.getInterfaceDuality()).tenkeitech$extraction()
            .inject(inventory, input, mode, source);
    }
}
