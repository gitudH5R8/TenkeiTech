/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;

import appeng.client.gui.widgets.GuiImgButton;

/** AE terminal button background with a text label, without impersonating an AE action. */
public final class UniversalWandNeeButton extends GuiImgButton {

    public UniversalWandNeeButton() {
        super(-1000, -1000, null, null);
        id = 0x5E02;
        displayString = "TT";
        visible = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);
        if (visible) drawCenteredString(mc.fontRenderer, displayString, xPosition + width / 2, yPosition + 4, 0xFFFFFF);
    }

    @Override
    public String getMessage() {
        // GuiPatternTermMixin owns the full tooltip, including its config hint.
        return null;
    }
}
