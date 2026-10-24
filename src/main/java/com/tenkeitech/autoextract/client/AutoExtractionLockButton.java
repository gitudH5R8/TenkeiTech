/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.google.common.base.Joiner;
import com.tenkeitech.autoextract.AutoExtractionContainer;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;

/** The native mode button with the automatic card's fixed-mode explanation. */
public final class AutoExtractionLockButton extends GuiImgButton {

    private final AutoExtractionContainer container;

    public AutoExtractionLockButton(int x, int y, AutoExtractionContainer container) {
        super(x, y, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
        this.container = container;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (!container.tenkeitech$extractionView()
            .present()) return message;
        return message + '\n'
            + explanation("fixed_return_mode")
            + '\n'
            + explanation("exclusive_lock")
            + '\n'
            + StatCollector.translateToLocal("tenkeitech.autoextract.cancel_hint");
    }

    private static String explanation(String key) {
        return Joiner.on('\n')
            .join(
                Minecraft.getMinecraft().fontRenderer
                    .listFormattedStringToWidth(StatCollector.translateToLocal("tenkeitech.autoextract." + key), 250));
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!container.tenkeitech$extractionView()
            .present()) return super.mousePressed(mc, mouseX, mouseY);
        // Fixed mode stays disabled; only this explicit gesture submits a cancellation action.
        if (visible && mouseX >= xPosition
            && mouseX < xPosition + width
            && mouseY >= yPosition
            && mouseY < yPosition + height
            && GuiScreen.isShiftKeyDown()
            && Mouse.getEventButton() == 0) container.tenkeitech$cancelExtraction();
        return false;
    }
}
