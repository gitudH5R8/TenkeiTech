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
import com.tenkeitech.client.UniversalWandNeeButton;
import com.tenkeitech.client.UniversalWandNeeGuideClient;
import com.tenkeitech.client.UniversalWandNeePanel;
import com.tenkeitech.patternupload.PatternUploadButton;
import com.tenkeitech.patternupload.PatternUploadNetwork;
import com.tenkeitech.patternupload.PatternUploadPanel;
import com.tenkeitech.patternupload.PatternUploadRecipeContext;
import com.tenkeitech.patternupload.PatternUploadScreenBridge;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPatternTerm;
import cpw.mods.fml.common.Loader;

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
    private GuiButton tenkeitech$neeSettingsButton;
    @Unique
    private UniversalWandNeePanel tenkeitech$neeSettings;
    @Unique
    private int tenkeitech$overlayMouseButtons;
    @Unique
    private PatternUploadPanel tenkeitech$panel;
    @Unique
    private final PatternUploadRecipeContext tenkeitech$recipe = new PatternUploadRecipeContext();

    protected GuiPatternTermMixin(InventoryPlayer inventoryPlayer, ITerminalHost terminalHost) {
        super(inventoryPlayer, terminalHost);
    }

    @Inject(method = "initGui", at = @At("TAIL"), remap = true)
    private void tenkeitech$addUploadButton(CallbackInfo callback) {
        if (Loader.isModLoaded("neenergistics")) {
            if (tenkeitech$neeSettings == null) tenkeitech$neeSettings = new UniversalWandNeePanel(container);
            // ECO places its buttons in InitGuiEvent.Post and counts hidden buttons too.
            tenkeitech$neeSettingsButton = new UniversalWandNeeButton();
            buttonList.add(tenkeitech$neeSettingsButton);
        }
        if (!Config.enablePatternUpload) return;
        if (tenkeitech$panel == null) tenkeitech$panel = new PatternUploadPanel(container, tenkeitech$recipe);
        tenkeitech$uploadButton = new PatternUploadButton();
        buttonList.add(tenkeitech$uploadButton);
        tenkeitech$positionUploadButton();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$handleUploadButton(GuiButton button, CallbackInfo callback) {
        if (button == tenkeitech$neeSettingsButton) {
            if (Config.universalWandShowNeeTerminalButton && !tenkeitech$isTerminalOverlayOpen())
                tenkeitech$neeSettings.open();
            callback.cancel();
            return;
        }
        if (!Config.enablePatternUpload) return;
        if (button == tenkeitech$uploadButton) {
            if (GuiScreen.isShiftKeyDown()) PatternUploadNetwork.undoLastUpload(container.windowId);
            else tenkeitech$panel.openUpload();
            callback.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void tenkeitech$blockTerminalClicks(int mouseX, int mouseY, int mouseButton, CallbackInfo callback) {
        if (tenkeitech$isTerminalOverlayOpen()) {
            tenkeitech$handleTerminalOverlayClick(mouseX, mouseY, mouseButton);
            callback.cancel();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tenkeitech$positionButtons();
        if (tenkeitech$isTerminalOverlayOpen()) {
            // Do not draw AE, its slots, carried stack, tooltips, or the NEI container draw hooks underneath.
            if (tenkeitech$neeSettings != null && tenkeitech$neeSettings.isOpen())
                tenkeitech$neeSettings.draw(width, height, mouseX, mouseY, partialTicks);
            else tenkeitech$panel.draw(width, height, mouseX, mouseY);
            return;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tenkeitech$overButton(tenkeitech$neeSettingsButton, mouseX, mouseY)) {
            drawHoveringText(UniversalWandNeeGuideClient.tooltip(), mouseX, mouseY);
        }
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
        tenkeitech$positionUploadButton();
        if (tenkeitech$neeSettingsButton == null) return;
        tenkeitech$neeSettingsButton.visible = encodeBtn != null && Config.universalWandShowNeeTerminalButton
            && UniversalWandNeeGuideClient.findWandSlot() >= 0
            && !container.isCraftingMode()
            && !tenkeitech$isTerminalOverlayOpen();
        // Hidden TT must never reserve a position in other mods' layout scans.
        tenkeitech$neeSettingsButton.xPosition = -1000;
        tenkeitech$neeSettingsButton.yPosition = -1000;
        if (!tenkeitech$neeSettingsButton.visible) return;

        int x = encodeBtn.xPosition + 31;
        int y = encodeBtn.yPosition + encodeBtn.height + 2;
        for (Object value : buttonList) {
            if (value instanceof GuiImgButton pinButton && pinButton.getSetting() == Settings.ACTIONS
                && pinButton.getCurrentValue() == ActionItems.PINS) {
                x = pinButton.xPosition;
                y = pinButton.yPosition + pinButton.height + 2;
                break;
            }
        }

        for (int guard = 0; guard < 16; guard++) {
            GuiButton hit = null;
            for (Object value : buttonList) {
                if (value instanceof GuiButton button && button != tenkeitech$neeSettingsButton
                    && button.visible
                    && x < button.xPosition + button.width
                    && button.xPosition < x + 16
                    && y < button.yPosition + button.height
                    && button.yPosition < y + 16) {
                    hit = button;
                    break;
                }
            }
            if (hit == null) {
                tenkeitech$neeSettingsButton.xPosition = x;
                tenkeitech$neeSettingsButton.yPosition = y;
                tenkeitech$neeSettingsButton.displayString = (UniversalWandNeeGuideClient.activeCount() > 0 ? "\u00a7a"
                    : "\u00a77") + "TT";
                return;
            }
            y = hit.yPosition + hit.height + 2;
        }
        // If an unusually crowded column exhausts the bound, leave TT hidden instead of overlapping.
        tenkeitech$neeSettingsButton.visible = false;
    }

    @Unique
    private void tenkeitech$positionUploadButton() {
        if (tenkeitech$panel == null || tenkeitech$uploadButton == null) return;
        tenkeitech$uploadButton.xPosition = encodeBtn.xPosition - tenkeitech$uploadButton.width - 2;
        tenkeitech$uploadButton.yPosition = encodeBtn.yPosition
            + (encodeBtn.height - tenkeitech$uploadButton.height) / 2;
        tenkeitech$uploadButton.visible = tenkeitech$panel.showUploadButton() && !tenkeitech$isTerminalOverlayOpen();
        tenkeitech$uploadButton.enabled = !tenkeitech$panel.isOpen()
            && (tenkeitech$panel.hasPattern() || GuiScreen.isShiftKeyDown());
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (tenkeitech$neeSettings != null && tenkeitech$neeSettings.isOpen()) tenkeitech$neeSettings.key(keyCode);
        else if (!Config.enablePatternUpload) super.keyTyped(character, keyCode);
        else if (keyCode == Keyboard.KEY_U && isCtrlKeyDown() && tenkeitech$panel != null)
            tenkeitech$panel.openSettings();
        else if (tenkeitech$isTerminalOverlayOpen()) tenkeitech$panel.key(character, keyCode);
        else super.keyTyped(character, keyCode);
    }

    @Override
    public void handleKeyboardInput() {
        if (!tenkeitech$isTerminalOverlayOpen()) {
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
        int button = Mouse.getEventButton();
        if (button >= 0 && button < 32
            && !Mouse.getEventButtonState()
            && (tenkeitech$overlayMouseButtons & 1 << button) != 0) {
            tenkeitech$overlayMouseButtons &= ~(1 << button);
            if (tenkeitech$neeSettings != null) tenkeitech$neeSettings.release(button);
            return;
        }
        if (!tenkeitech$isTerminalOverlayOpen()) {
            super.handleMouseInput();
            return;
        }
        if (Mouse.getEventDWheel() != 0 && tenkeitech$panel != null && tenkeitech$panel.isOpen())
            tenkeitech$panel.scroll(Mouse.getEventDWheel());
        if (button < 0 || !Mouse.getEventButtonState()) return;
        if (button < 32) tenkeitech$overlayMouseButtons |= 1 << button;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        tenkeitech$handleTerminalOverlayClick(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!tenkeitech$isTerminalOverlayOpen()) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    public boolean tenkeitech$isTerminalOverlayOpen() {
        return tenkeitech$neeSettings != null && tenkeitech$neeSettings.isOpen()
            || Config.enablePatternUpload && tenkeitech$panel != null && tenkeitech$panel.isOpen();
    }

    @Override
    public void tenkeitech$tickPatternUpload() {
        if (Config.enablePatternUpload && tenkeitech$panel != null) tenkeitech$panel.tick();
    }

    @Override
    public void tenkeitech$handleTerminalOverlayClick(int mouseX, int mouseY, int mouseButton) {
        if (tenkeitech$neeSettings != null && tenkeitech$neeSettings.isOpen())
            tenkeitech$neeSettings.click(mouseX, mouseY, mouseButton);
        else if (Config.enablePatternUpload && tenkeitech$panel != null)
            tenkeitech$panel.click(mouseX, mouseY, mouseButton);
    }

    @Override
    public void tenkeitech$tickNeeSettings() {
        if (tenkeitech$neeSettings != null) tenkeitech$neeSettings.tick();
    }

    @Override
    public void tenkeitech$closeNeeSettings() {
        if (tenkeitech$neeSettings != null) tenkeitech$neeSettings.close();
    }

    @Override
    public void tenkeitech$acceptNeeSettingsReply(
        com.tenkeitech.universalwand.UniversalWandNetwork.NeeSettingsReply reply) {
        if (tenkeitech$neeSettings != null) tenkeitech$neeSettings.accept(reply);
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
