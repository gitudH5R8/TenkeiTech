/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shared outline and face style for binding tools. Call end in a finally block after begin. */
@SideOnly(Side.CLIENT)
public final class BlockFaceOverlay {

    private BlockFaceOverlay() {}

    public static void begin() {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end() {
        GL11.glPopAttrib();
    }

    public static void outline(int blockX, int blockY, int blockZ, double cameraX, double cameraY, double cameraZ) {
        double x = blockX - cameraX, y = blockY - cameraY, z = blockZ - cameraZ;
        GL11.glLineWidth(2F);
        GL11.glColor4f(0.1F, 0.9F, 1F, 1F);
        RenderGlobal.drawOutlinedBoundingBox(
            AxisAlignedBB.getBoundingBox(x - 0.002, y - 0.002, z - 0.002, x + 1.002, y + 1.002, z + 1.002),
            -1);
    }

    public static void face(int blockX, int blockY, int blockZ, int side, double cameraX, double cameraY,
        double cameraZ, boolean selected) {
        double x = blockX - cameraX, y = blockY - cameraY, z = blockZ - cameraZ;
        double epsilon = 0.003;
        GL11.glColor4f(selected ? 0.1F : 0.15F, selected ? 1F : 0.4F, selected ? 0.1F : 1F, 0.45F);
        GL11.glBegin(GL11.GL_QUADS);
        switch (side) {
            case 0, 1 -> {
                double plane = y + (side == 0 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(x, plane, z);
                GL11.glVertex3d(x + 1, plane, z);
                GL11.glVertex3d(x + 1, plane, z + 1);
                GL11.glVertex3d(x, plane, z + 1);
            }
            case 2, 3 -> {
                double plane = z + (side == 2 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(x, y, plane);
                GL11.glVertex3d(x + 1, y, plane);
                GL11.glVertex3d(x + 1, y + 1, plane);
                GL11.glVertex3d(x, y + 1, plane);
            }
            case 4, 5 -> {
                double plane = x + (side == 4 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(plane, y, z);
                GL11.glVertex3d(plane, y, z + 1);
                GL11.glVertex3d(plane, y + 1, z + 1);
                GL11.glVertex3d(plane, y + 1, z);
            }
            default -> {}
        }
        GL11.glEnd();
    }
}
