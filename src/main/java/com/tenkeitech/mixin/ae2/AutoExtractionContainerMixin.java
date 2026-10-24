/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.autoextract.AutoExtractionContainer;
import com.tenkeitech.autoextract.AutoExtractionHost;
import com.tenkeitech.autoextract.AutoExtractionState;
import com.tenkeitech.autoextract.AutoExtractionView;

import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.IUpgradeableHost;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.util.Platform;

@Mixin(value = ContainerInterface.class, remap = false)
public abstract class AutoExtractionContainerMixin extends ContainerUpgradeable implements AutoExtractionContainer {

    @Shadow
    @Final
    private DualityInterface myDuality;
    @Unique
    private ObjectSyncHandler<AutoExtractionView> tenkeitech$view;
    @Unique
    private ActionHandler<Long> tenkeitech$cancel;
    @Unique
    private final AtomicLong tenkeitech$requestedCancel = new AtomicLong();

    protected AutoExtractionContainerMixin(InventoryPlayer inventory, IUpgradeableHost host) {
        super(inventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void tenkeitech$init(InventoryPlayer inventory, IInterfaceHost host, CallbackInfo ci) {
        tenkeitech$view = ((ContainerInterface) (Object) this).getSyncManager()
            .root()
            .objectS2C("tenkeitech_auto_extraction", AutoExtractionView.CODEC, AutoExtractionView.EMPTY);
        tenkeitech$cancel = ((ContainerInterface) (Object) this).getSyncManager()
            .root()
            .actionC2S("tenkeitech_auto_extraction_cancel", StreamCodecs.longValue())
            .onServerAction(token -> tenkeitech$requestedCancel.compareAndSet(0, token));
    }

    @Inject(method = "detectAndSendChanges()V", remap = true, at = @At("HEAD"), require = 1)
    private void tenkeitech$sync(CallbackInfo ci) {
        if (Platform.isServer() && tenkeitech$view != null) {
            AutoExtractionState state = ((AutoExtractionHost) myDuality).tenkeitech$extraction();
            long requested = tenkeitech$requestedCancel.getAndSet(0);
            ContainerInterface container = (ContainerInterface) (Object) this;
            // Apply on the normal server container tick, never on the packet thread.
            if (requested != 0 && container.getPlayerInv().player.openContainer == container
                && container.isValidContainer()
                && container.canInteractWith(container.getPlayerInv().player)
                && hasAccess(SecurityPermissions.BUILD, false)) state.cancel(requested);
            AutoExtractionView view = state.view();
            if (tenkeitech$view.get() != view) tenkeitech$view.set(view);
        }
    }

    @Override
    public AutoExtractionView tenkeitech$extractionView() {
        return tenkeitech$view == null ? AutoExtractionView.EMPTY : tenkeitech$view.get();
    }

    @Override
    public void tenkeitech$cancelExtraction() {
        AutoExtractionView view = tenkeitech$extractionView();
        if (Platform.isClient() && tenkeitech$cancel != null
            && view.present()
            && view.locked()
            && !view.quarantined()
            && view.taskId() != 0) tenkeitech$cancel.send(view.taskId());
    }
}
