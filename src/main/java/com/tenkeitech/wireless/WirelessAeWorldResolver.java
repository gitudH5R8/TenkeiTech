/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import javax.annotation.Nullable;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** Resolves server worlds without loading a dimension or a chunk. */
public final class WirelessAeWorldResolver {

    private WirelessAeWorldResolver() {}

    @Nullable
    public static World getLoadedWorld(World referenceWorld, int dimensionId) {
        if (referenceWorld == null || referenceWorld.isRemote) return null;
        if (referenceWorld.provider.dimensionId == dimensionId) return referenceWorld;
        WorldServer loadedWorld = DimensionManager.getWorld(dimensionId);
        return loadedWorld == null || loadedWorld.isRemote ? null : loadedWorld;
    }
}
