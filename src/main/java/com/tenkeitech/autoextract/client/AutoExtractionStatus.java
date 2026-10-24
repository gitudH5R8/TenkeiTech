/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import com.tenkeitech.autoextract.AutoExtractionContainer;
import com.tenkeitech.autoextract.AutoExtractionView;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.ITooltip;

/** Non-interactive title text with AE's native hover tooltip. */
public final class AutoExtractionStatus extends GuiButton implements ITooltip {

    private final AutoExtractionContainer container;

    public AutoExtractionStatus(int x, int y, AutoExtractionContainer container) {
        super(0, x, y, 0, 9, "");
        this.container = container;
        this.enabled = false;
        this.visible = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        AutoExtractionView view = container.tenkeitech$extractionView();
        visible = view.present() || view.locked();
        if (!visible) return;
        displayString = StatCollector
            .translateToLocal(view.locked() ? "tenkeitech.autoextract.locked" : "tenkeitech.autoextract.unlocked");
        width = mc.fontRenderer.getStringWidth(displayString);
        mc.fontRenderer.drawString(displayString, xPosition, yPosition, view.locked() ? 0xFF5555 : 0x008800);
    }

    @Override
    public String getMessage() {
        AutoExtractionView view = container.tenkeitech$extractionView();
        StringBuilder message = new StringBuilder(StatCollector.translateToLocal("tenkeitech.autoextract.remaining"));
        if (view.quarantined()) message.append('\n')
            .append(StatCollector.translateToLocal("tenkeitech.autoextract.quarantined"));
        boolean empty = true;
        for (IAEStack<?> stack : view.remaining()) {
            empty = false;
            message.append('\n')
                .append(
                    StatCollector.translateToLocalFormatted(
                        stack.isFluid() ? "tenkeitech.autoextract.remaining_fluid"
                            : "tenkeitech.autoextract.remaining_item",
                        stack.getDisplayName(),
                        Long.toString(stack.getStackSize())));
        }
        if (empty && !view.quarantined()) message.append('\n')
            .append(
                StatCollector.translateToLocal(
                    view.locked() ? "tenkeitech.autoextract.waiting" : "tenkeitech.autoextract.none"));
        return message.toString();
    }

    @Override
    public int xPos() {
        return xPosition;
    }

    @Override
    public int yPos() {
        return yPosition;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
