/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Direction selection uses occupied block cells, independent of a machine's model or mutable bounds. */
@SideOnly(Side.CLIENT)
public final class AutoExtractionCardTarget {

    private AutoExtractionCardTarget() {}

    public static MovingObjectPosition trace(Minecraft mc) {
        if (mc.theWorld == null || mc.renderViewEntity == null || mc.playerController == null) return null;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
            return null;
        Vec3 start = mc.renderViewEntity.getPosition(1F);
        Vec3 look = mc.renderViewEntity.getLook(1F);
        double reach = Math.min(128D, mc.playerController.getBlockReachDistance());
        if (look == null || !Double.isFinite(reach) || reach <= 0) return null;
        Vec3 end = start.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        int x = MathHelper.floor_double(start.xCoord), y = MathHelper.floor_double(start.yCoord),
            z = MathHelper.floor_double(start.zCoord);
        int dx = step(look.xCoord), dy = step(look.yCoord), dz = step(look.zCoord);
        double tx = boundary(start.xCoord, x, look.xCoord, dx);
        double ty = boundary(start.yCoord, y, look.yCoord, dy);
        double tz = boundary(start.zCoord, z, look.zCoord, dz);
        double sx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / look.xCoord);
        double sy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / look.yCoord);
        double sz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / look.zCoord);
        for (int visited = 0; visited < 512; visited++) {
            if (!mc.theWorld.blockExists(x, y, z)) return null;
            var block = mc.theWorld.getBlock(x, y, z);
            if (!block.isAir(mc.theWorld, x, y, z)
                && block.canCollideCheck(mc.theWorld.getBlockMetadata(x, y, z), false)) {
                MovingObjectPosition hit = AxisAlignedBB.getBoundingBox(x, y, z, x + 1D, y + 1D, z + 1D)
                    .calculateIntercept(start, end);
                if (hit != null) return new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
            }
            if (tx <= ty && tx <= tz) {
                if (tx > reach) break;
                x += dx;
                tx += sx;
            } else if (ty <= tz) {
                if (ty > reach) break;
                y += dy;
                ty += sy;
            } else {
                if (tz > reach) break;
                z += dz;
                tz += sz;
            }
        }
        return null;
    }

    private static int step(double value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    private static double boundary(double start, int cell, double look, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY : (cell + (step > 0 ? 1D : 0D) - start) / look;
    }
}
