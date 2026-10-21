/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.patternupload.PatternUploadScreenBridge;

import appeng.client.gui.implementations.GuiPatternTermEx;

/** Stops the extended terminal's scrollbar from receiving clicks through the upload overlay. */
@Mixin(value = GuiPatternTermEx.class, remap = false)
public abstract class GuiPatternTermExMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$blockExtendedTerminalClicks(int mouseX, int mouseY, int mouseButton,
        CallbackInfo callback) {
        PatternUploadScreenBridge bridge = (PatternUploadScreenBridge) this;
        if (!bridge.tenkeitech$isPatternUploadOverlayOpen()) return;
        bridge.tenkeitech$handlePatternUploadOverlayClick(mouseX, mouseY, mouseButton);
        callback.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$blockExtendedTerminalDrag(int mouseX, int mouseY, int clickedMouseButton,
        long timeSinceLastClick, CallbackInfo callback) {
        PatternUploadScreenBridge bridge = (PatternUploadScreenBridge) this;
        if (bridge.tenkeitech$isPatternUploadOverlayOpen()) callback.cancel();
    }

    @Inject(
        method = "handleMouseInput",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false),
        cancellable = true,
        remap = true)
    private void tenkeitech$blockExtendedTerminalScrolling(CallbackInfo callback) {
        PatternUploadScreenBridge bridge = (PatternUploadScreenBridge) this;
        if (bridge.tenkeitech$isPatternUploadOverlayOpen()) callback.cancel();
    }
}
