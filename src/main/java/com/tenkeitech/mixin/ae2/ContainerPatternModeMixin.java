/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.patternmode.PatternModeEncodingContext;
import com.tenkeitech.patternmode.PatternModeTerminal;

import appeng.container.implementations.ContainerPatternTerm;

@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class ContainerPatternModeMixin implements PatternModeTerminal {

    @Unique
    private final PatternModeEncodingContext tenkeitech$modeContext = new PatternModeEncodingContext();

    @Override
    public PatternModeEncodingContext tenkeitech$patternModeContext() {
        return tenkeitech$modeContext;
    }

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/storage/ITerminalHost;Z)V",
        at = @At("RETURN"))
    private void tenkeitech$attach(CallbackInfo callback) {
        ContainerPatternTerm container = (ContainerPatternTerm) (Object) this;
        if (!container.getPlayerInv().player.worldObj.isRemote) {
            tenkeitech$modeContext.attach(container.getPatternTerminal());
        }
    }

}
