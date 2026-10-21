/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.universalwand.UniversalWandNetwork.AccelerationSyncMessage;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class UniversalWandAccelerationRenderer {

    private static final Map<String, Display> DISPLAYS = new ConcurrentHashMap<>();

    public static void receive(AccelerationSyncMessage message) {
        String key = message.dimension + ":" + message.x + ":" + message.y + ":" + message.z;
        if (message.remaining == 0 || message.level < 1 || message.mode < 0 || message.mode > 2) DISPLAYS.remove(key);
        else DISPLAYS.put(key, new Display(message));
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        DISPLAYS.clear();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        Entity camera = mc.renderViewEntity;
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        long now = System.nanoTime();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            for (Map.Entry<String, Display> entry : DISPLAYS.entrySet()) {
                Display display = entry.getValue();
                double seconds = display.message.remaining / 20D - (now - display.received) / 1_000_000_000D;
                boolean permanent = display.message.mode != 0;
                if (now - display.received > 3_000_000_000L || !permanent && seconds <= 0) {
                    DISPLAYS.remove(entry.getKey(), display);
                    continue;
                }
                AccelerationSyncMessage state = display.message;
                if (state.dimension != mc.theWorld.provider.dimensionId
                    || camera.getDistanceSq(state.x + 0.5, state.y + 0.5, state.z + 0.5) > 4096
                    || !mc.theWorld.getChunkProvider()
                        .chunkExists(state.x >> 4, state.z >> 4)
                    || mc.theWorld.isAirBlock(state.x, state.y, state.z)) continue;
                drawFrame(state, cx, cy, cz, now);
                for (int face = 0; face < 6; face++) {
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(state.x + 0.5 - cx, state.y + 0.5 - cy, state.z + 0.5 - cz);
                        switch (face) {
                            case 0 -> {
                                GL11.glTranslated(0, -0.506, 0);
                                GL11.glRotatef(90, 1, 0, 0);
                            }
                            case 1 -> {
                                GL11.glTranslated(0, 0.506, 0);
                                GL11.glRotatef(-90, 1, 0, 0);
                            }
                            case 2 -> {
                                GL11.glTranslated(0, 0, -0.506);
                                GL11.glRotatef(180, 0, 1, 0);
                            }
                            case 3 -> GL11.glTranslated(0, 0, 0.506);
                            case 4 -> {
                                GL11.glTranslated(-0.506, 0, 0);
                                GL11.glRotatef(-90, 0, 1, 0);
                            }
                            case 5 -> {
                                GL11.glTranslated(0.506, 0, 0);
                                GL11.glRotatef(90, 0, 1, 0);
                            }
                        }
                        GL11.glScalef(0.02F, -0.02F, 0.02F);
                        String speed = "x" + state.level + (permanent ? " \u221e" : "");
                        mc.fontRenderer.drawString(speed, -mc.fontRenderer.getStringWidth(speed) / 2, 0, 0xFFFFFF);
                        float fraction = permanent ? 1 : (float) Math.max(0, Math.min(1, seconds / 30));
                        int fill = -18 + Math.round(36 * fraction);
                        Gui.drawRect(fill, 11, 18, 14, 0xF2303030);
                        Gui.drawRect(
                            -18,
                            11,
                            fill,
                            14,
                            state.throttled ? 0xFF88AA88 : permanent ? 0xFFFFA60D : 0xFF33D9FF);
                        GL11.glColor4f(1, 1, 1, 1);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawFrame(AccelerationSyncMessage state, double cx, double cy, double cz, long now) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        double x = state.x - cx - 0.006, y = state.y - cy - 0.006, z = state.z - cz - 0.006;
        for (int axis = 0; axis < 3; axis++) {
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
                int rgb = java.awt.Color
                    .HSBtoRGB((float) ((now / 1_000_000_000D * 0.15 + axis / 3D + a * 0.12 + b * 0.12) % 1), 0.9F, 1);
                t.setColorOpaque_I(rgb & 0xFFFFFF);
                double[] start = { x, y, z };
                start[(axis + 1) % 3] += a * 1.012;
                start[(axis + 2) % 3] += b * 1.012;
                t.addVertex(start[0], start[1], start[2]);
                start[axis] += 1.012;
                t.addVertex(start[0], start[1], start[2]);
            }
        }
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static final class Display {

        final AccelerationSyncMessage message;
        final long received = System.nanoTime();

        Display(AccelerationSyncMessage message) {
            this.message = message;
        }
    }
}
