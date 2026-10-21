/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.nee;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;
import com.tenkeitech.patternmode.PatternModeTerminal;
import com.tenkeitech.patternmode.PatternRecipeMapData;

import appeng.container.implementations.ContainerPatternTerm;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe$Handler", remap = false)
public abstract class PatternModeReceiveMixin {

    @Inject(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/vfyjxf/nee/network/packet/PacketNEIPatternRecipe$Handler;updateVirtualSlots(Lappeng/container/implementations/ContainerPatternTerm;Lappeng/api/storage/StorageName;Lit/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap;)V",
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void tenkeitech$bindTransferredRecipe(ContainerPatternTerm terminal, PacketNEIPatternRecipe packet,
        CallbackInfo callback) {
        if (packet instanceof PatternModePacketAccessor access
            && terminal instanceof PatternModeTerminal modeTerminal) {
            modeTerminal.tenkeitech$patternModeContext()
                .transferred(terminal, PatternRecipeMapData.read(access.tenkeitech$input()));
        }
    }

    @Inject(
        method = "craftingRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/vfyjxf/nee/network/packet/PacketNEIPatternRecipe$Handler;updateVirtualSlots(Lappeng/container/implementations/ContainerPatternTerm;Lappeng/api/storage/StorageName;Lit/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap;)V",
            shift = At.Shift.AFTER))
    private void tenkeitech$clearAfterCraftingTransfer(ContainerPatternTerm terminal, PacketNEIPatternRecipe packet,
        CallbackInfo callback) {
        if (terminal instanceof PatternModeTerminal modeTerminal) modeTerminal.tenkeitech$patternModeContext()
            .clear();
    }
}
