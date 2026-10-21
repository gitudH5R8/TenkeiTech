/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.universalwand.UniversalWandUtilityHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Draws the speed notice through Forge's public HUD event API. */
public final class UniversalWandFlightSpeedHudRenderer {

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        long remaining = UniversalWandUtilityHandler.getClientSpeedNoticeRemaining();
        long accelerationRemaining = UniversalWandUtilityHandler.getClientAccelerationNoticeRemaining();
        boolean acceleration = accelerationRemaining > 0;
        if (acceleration) remaining = accelerationRemaining;
        if (remaining == 0 || minecraft.thePlayer == null) return;
        float speed = UniversalWandUtilityHandler.getClientSpeed(minecraft.thePlayer);
        if (!acceleration && speed <= 0) return;
        int rate = UniversalWandUtilityHandler.getClientAccelerationRate();
        String text = acceleration
            ? StatCollector.translateToLocalFormatted(
                "tenkeitech.universal_wand.acceleration_changed",
                rate == 0 ? StatCollector.translateToLocal("tenkeitech.gui.universal_wand.acceleration_off")
                    : "x" + rate)
            : StatCollector.translateToLocalFormatted(
                "tenkeitech.universal_wand.flight_speed_changed",
                String.format(java.util.Locale.ROOT, "%.1f", speed / 0.05F));
        int alpha = (int) Math.min(255, remaining * 255 / 500_000_000L);
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        int y = height - 71 + (minecraft.playerController.shouldDrawHUD() ? 0 : 14);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        minecraft.fontRenderer.drawStringWithShadow(
            text,
            (width - minecraft.fontRenderer.getStringWidth(text)) / 2,
            y,
            0xFFFFFF | alpha << 24);
        GL11.glPopAttrib();
    }
}
