/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/** Stable, version-independent identity for one wireless AE target face. */
public final class WirelessAeTargetLocation {

    private static final String NBT_DIMENSION = "dimension";
    private static final String NBT_X = "x";
    private static final String NBT_Y = "y";
    private static final String NBT_Z = "z";
    private static final String NBT_SIDE = "side";

    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final ForgeDirection side;

    public WirelessAeTargetLocation(int dimensionId, int x, int y, int z, ForgeDirection side) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    public int dimensionId() {
        return dimensionId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public ForgeDirection side() {
        return side;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_DIMENSION, dimensionId);
        tag.setInteger(NBT_X, x);
        tag.setInteger(NBT_Y, y);
        tag.setInteger(NBT_Z, z);
        tag.setInteger(NBT_SIDE, side.ordinal());
        return tag;
    }

    public static WirelessAeTargetLocation readFromNBT(NBTTagCompound tag) {
        if (tag == null) return null;
        if (!tag.hasKey(NBT_DIMENSION) || !tag.hasKey(NBT_X)
            || !tag.hasKey(NBT_Y)
            || !tag.hasKey(NBT_Z)
            || !tag.hasKey(NBT_SIDE)) {
            return null;
        }
        int y = tag.getInteger(NBT_Y);
        int sideOrdinal = tag.getInteger(NBT_SIDE);
        if (y < 0 || y > 255 || sideOrdinal < 0 || sideOrdinal >= ForgeDirection.values().length) return null;
        return new WirelessAeTargetLocation(
            tag.getInteger(NBT_DIMENSION),
            tag.getInteger(NBT_X),
            y,
            tag.getInteger(NBT_Z),
            ForgeDirection.getOrientation(sideOrdinal));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WirelessAeTargetLocation location)) return false;
        return dimensionId == location.dimensionId && x == location.x
            && y == location.y
            && z == location.z
            && side == location.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, x, y, z, side);
    }

    @Override
    public String toString() {
        return "dim " + dimensionId + " @ " + x + ", " + y + ", " + z + " / " + side.name();
    }
}
