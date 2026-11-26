/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Compact AE-style upload button, kept separate from AE action buttons. */
@SideOnly(Side.CLIENT)
public final class PatternUploadButton extends GuiButton {

    public PatternUploadButton() {
        super(0x5E01, 0, 0, 14, 14, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        field_146123_n = mouseX >= xPosition && mouseX < xPosition + width
            && mouseY >= yPosition
            && mouseY < yPosition + height;
        boolean highlighted = enabled && field_146123_n;
        int fill = !enabled ? 0xFF858585 : highlighted ? 0xFFADADAD : 0xFF999999;
        int light = !enabled ? 0xFFA1A1A1 : highlighted ? 0xFFEFEFEF : 0xFFE0E0E0;
        int dark = !enabled ? 0xFF5B5B5B : highlighted ? 0xFF717171 : 0xFF626262;
        boolean pointingDown = GuiScreen.isShiftKeyDown();

        // Gui.drawRect changes texture/blend state and the current color.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, fill);
            drawRect(xPosition, yPosition, xPosition + width - 1, yPosition + 1, light);
            drawRect(xPosition, yPosition + 1, xPosition + 1, yPosition + height - 1, light);
            drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, dark);
            drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, dark);

            // Both the symmetric arrow and its one-pixel shadow stay inside 14x14.
            drawArrow(xPosition + 4, yPosition + 4, enabled ? 0xFF555555 : 0xFF565656, pointingDown);
            drawArrow(xPosition + 3, yPosition + 3, enabled ? 0xFFF4F4F4 : 0xFF646464, pointingDown);
            mouseDragged(mc, mouseX, mouseY);
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawArrow(int x, int y, int color, boolean pointingDown) {
        for (int row = 0; row < 8; row++) {
            int halfWidth = row < 4 ? row + 1 : 1;
            int drawY = y + (pointingDown ? 7 - row : row);
            drawRect(x + 4 - halfWidth, drawY, x + 4 + halfWidth, drawY + 1, color);
        }
    }
}
