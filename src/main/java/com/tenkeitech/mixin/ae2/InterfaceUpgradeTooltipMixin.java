/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.tenkeitech.client.InterfaceUpgradeTooltip;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.container.implementations.ContainerUpgradeable;

@Mixin(value = GuiUpgradeable.class, remap = false)
public abstract class InterfaceUpgradeTooltipMixin {

    @Shadow
    @Final
    protected ContainerUpgradeable cvb;

    @ModifyArg(
        method = "handleUpgradeSlotTooltip(II)V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/implementations/GuiUpgradeable;drawTooltip(II[Ljava/lang/String;)V"),
        index = 2,
        require = 1)
    private String[] tenkeitech$accepted(String[] lines) {
        return InterfaceUpgradeTooltip.appendAccepted(cvb.getUpgradeable(), lines);
    }
}
