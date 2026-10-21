/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.tenkeitech.gui.WirelessMeHatchGuiSupport;

import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui;

/** GUI-only hook which adds TenkeiTech's side dock to fixed GT5U ME hatch screens. */
@Mixin(value = MTETieredMachineBlockBaseGui.class, remap = false)
public abstract class MTETieredMachineBlockBaseGuiMixin {

    @Shadow
    @Final
    protected MTETieredMachineBlock machine;

    @Inject(method = "createBottomLeftCornerFlow", at = @At("RETURN"))
    private void tenkeitech$attachWirelessMeSideDock(ModularPanel panel, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> callback) {
        WirelessMeHatchGuiSupport.attachDefaultSideDock(machine, panel, syncManager);
    }
}
