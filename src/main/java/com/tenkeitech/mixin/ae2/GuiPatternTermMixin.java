/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.Collections;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.Config;
import com.tenkeitech.patternupload.PatternUploadNetwork;
import com.tenkeitech.patternupload.PatternUploadPanel;
import com.tenkeitech.patternupload.PatternUploadRecipeContext;
import com.tenkeitech.patternupload.PatternUploadScreenBridge;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPatternTerm;

/** Narrow input/render bridge; the original AE container remains open and authoritative. */
@Mixin(value = GuiPatternTerm.class, remap = false)
public abstract class GuiPatternTermMixin extends GuiMEMonitorable implements PatternUploadScreenBridge {

    @Shadow
    @Final
    private ContainerPatternTerm container;
    @Shadow
    private GuiImgButton encodeBtn;
    @Unique
    private GuiButton tenkeitech$uploadButton;
    @Unique
    private PatternUploadPanel tenkeitech$panel;
    @Unique
    private final PatternUploadRecipeContext tenkeitech$recipe = new PatternUploadRecipeContext();

    protected GuiPatternTermMixin(InventoryPlayer inventoryPlayer, ITerminalHost terminalHost) {
        super(inventoryPlayer, terminalHost);
    }

    @Inject(method = "initGui", at = @At("TAIL"), remap = true)
    private void tenkeitech$addUploadButton(CallbackInfo callback) {
        if (!Config.enablePatternUpload) return;
        if (tenkeitech$panel == null) tenkeitech$panel = new PatternUploadPanel(container, tenkeitech$recipe);
        tenkeitech$uploadButton = new GuiButton(0x5E01, 0, 0, 14, 14, "\u2191");
        buttonList.add(tenkeitech$uploadButton);
        tenkeitech$positionButtons();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$handleUploadButton(GuiButton button, CallbackInfo callback) {
        if (!Config.enablePatternUpload) return;
        if (button == tenkeitech$uploadButton) {
            if (GuiScreen.isShiftKeyDown()) PatternUploadNetwork.undoLastUpload(container.windowId);
            else tenkeitech$panel.openUpload();
            callback.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$blockTerminalClicks(int mouseX, int mouseY, int mouseButton, CallbackInfo callback) {
        if (tenkeitech$isPatternUploadOverlayOpen()) {
            tenkeitech$panel.click(mouseX, mouseY, mouseButton);
            callback.cancel();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tenkeitech$positionButtons();
        if (tenkeitech$isPatternUploadOverlayOpen()) {
            // Do not draw AE, its slots, carried stack, tooltips, or the NEI container draw hooks underneath.
            tenkeitech$panel.draw(width, height, mouseX, mouseY);
            return;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tenkeitech$panel == null) return;
        if (tenkeitech$overButton(tenkeitech$uploadButton, mouseX, mouseY)) {
            drawHoveringText(
                Collections.singletonList(
                    StatCollector.translateToLocal(
                        GuiScreen.isShiftKeyDown() ? "tenkeitech.pattern_upload.undo_button"
                            : "tenkeitech.pattern_upload.button")),
                mouseX,
                mouseY);
        }
    }

    @Unique
    private void tenkeitech$positionButtons() {
        if (tenkeitech$panel == null || tenkeitech$uploadButton == null) return;
        tenkeitech$uploadButton.xPosition = encodeBtn.xPosition - tenkeitech$uploadButton.width - 2;
        tenkeitech$uploadButton.yPosition = encodeBtn.yPosition
            + (encodeBtn.height - tenkeitech$uploadButton.height) / 2;
        tenkeitech$uploadButton.visible = tenkeitech$panel.showUploadButton();
        tenkeitech$uploadButton.enabled = !tenkeitech$panel.isOpen()
            && (tenkeitech$panel.hasPattern() || GuiScreen.isShiftKeyDown());
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (!Config.enablePatternUpload) super.keyTyped(character, keyCode);
        else if (keyCode == Keyboard.KEY_U && isCtrlKeyDown() && tenkeitech$panel != null)
            tenkeitech$panel.openSettings();
        else if (tenkeitech$isPatternUploadOverlayOpen()) tenkeitech$panel.key(character, keyCode);
        else super.keyTyped(character, keyCode);
    }

    @Override
    public void handleKeyboardInput() {
        if (!tenkeitech$isPatternUploadOverlayOpen()) {
            super.handleKeyboardInput();
            return;
        }
        // GuiContainer's NEI hook runs before keyTyped. Bypass it while our text fields own keyboard focus.
        if (Keyboard.getEventKeyState()
            || Keyboard.getEventKey() == 0 && Character.isDefined(Keyboard.getEventCharacter()))
            keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
    }

    @Override
    public void handleMouseInput() {
        if (!tenkeitech$isPatternUploadOverlayOpen()) {
            super.handleMouseInput();
            return;
        }
        if (Mouse.getEventDWheel() != 0) tenkeitech$panel.scroll(Mouse.getEventDWheel());
        int button = Mouse.getEventButton();
        if (button < 0 || !Mouse.getEventButtonState()) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        tenkeitech$panel.click(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!tenkeitech$isPatternUploadOverlayOpen()) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    public boolean tenkeitech$isPatternUploadOverlayOpen() {
        return Config.enablePatternUpload && tenkeitech$panel != null && tenkeitech$panel.isOpen();
    }

    @Override
    public void tenkeitech$tickPatternUpload() {
        if (Config.enablePatternUpload && tenkeitech$panel != null) tenkeitech$panel.tick();
    }

    @Override
    public void tenkeitech$handlePatternUploadOverlayClick(int mouseX, int mouseY, int mouseButton) {
        if (Config.enablePatternUpload && tenkeitech$panel != null) tenkeitech$panel.click(mouseX, mouseY, mouseButton);
    }

    @Override
    public void tenkeitech$acceptPatternUploadPage(PatternUploadNetwork.TargetsPageMessage message) {
        if (Config.enablePatternUpload && tenkeitech$panel != null) tenkeitech$panel.acceptPage(message);
    }

    @Override
    public void tenkeitech$acceptPatternUploadResult(PatternUploadNetwork.UploadResultMessage message) {
        if (Config.enablePatternUpload && tenkeitech$panel != null) tenkeitech$panel.acceptResult(message);
    }

    @Override
    public void tenkeitech$setPatternUploadCategory(String category, String label) {
        if (Config.enablePatternUpload) tenkeitech$recipe.transferred(category, label);
    }

    @Unique
    private static boolean tenkeitech$overButton(GuiButton button, int mouseX, int mouseY) {
        return button != null && button.visible
            && mouseX >= button.xPosition
            && mouseX < button.xPosition + button.width
            && mouseY >= button.yPosition
            && mouseY < button.yPosition + button.height;
    }
}
