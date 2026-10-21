/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tenkeitech.machine.MTEWirelessNetworkCore;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Resolves only already-loaded AE targets and never requests or loads a chunk. */
public final class WirelessAeTargetResolver {

    private WirelessAeTargetResolver() {}

    /** Resolves a GT ME hatch for sneak-place binding while explicitly excluding wireless network cores. */
    @Nullable
    public static WirelessAeTargetLocation resolveLoadedGtMeHatch(World world, int x, int y, int z) {
        TileEntity tile = getLoadedTile(world, x, y, z);
        IGridHost gridHost = getGtMeHatchGridHost(tile);
        if (gridHost == null) return null;

        IGridNode node = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        if (node != null) {
            return new WirelessAeTargetLocation(world.provider.dimensionId, x, y, z, ForgeDirection.UNKNOWN);
        }

        IGridNode uniqueNode = null;
        ForgeDirection uniqueSide = null;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IGridNode sideNode = gridHost.getGridNode(side);
            if (sideNode == null) continue;
            if (uniqueNode != null && uniqueNode != sideNode) return null;
            uniqueNode = sideNode;
            if (uniqueSide == null) uniqueSide = side;
        }
        return uniqueNode == null ? null
            : new WirelessAeTargetLocation(world.provider.dimensionId, x, y, z, uniqueSide);
    }

    public static boolean isLoadedGtMeHatch(World world, int x, int y, int z) {
        return getGtMeHatchGridHost(getLoadedTile(world, x, y, z)) != null;
    }

    @Nullable
    public static IGridNode resolveLoaded(World referenceWorld, WirelessAeTargetLocation location) {
        if (location == null) return null;
        World targetWorld = WirelessAeWorldResolver.getLoadedWorld(referenceWorld, location.dimensionId());
        if (targetWorld == null) return null;
        TileEntity tile = getLoadedTile(targetWorld, location.x(), location.y(), location.z());
        if (tile == null) return null;

        if (tile instanceof IPartHost partHost) {
            IPart part = partHost.getPart(location.side());
            IGridNode partNode = part == null ? null : part.getGridNode();
            if (partNode != null) return partNode;
        }

        if (tile instanceof IGridHost gridHost) {
            IGridNode node = gridHost.getGridNode(location.side());
            if (node == null && location.side() != net.minecraftforge.common.util.ForgeDirection.UNKNOWN) {
                node = gridHost.getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
            }
            return node;
        }
        return null;
    }

    public static boolean isLoadedTargetMissingOrReplaced(World referenceWorld, WirelessAeTargetLocation location,
        IGridNode expectedNode) {
        if (location == null || expectedNode == null) return false;
        World targetWorld = WirelessAeWorldResolver.getLoadedWorld(referenceWorld, location.dimensionId());
        if (targetWorld == null || !targetWorld.getChunkProvider()
            .chunkExists(location.x() >> 4, location.z() >> 4)) {
            return false;
        }
        return resolveLoaded(targetWorld, location) != expectedNode;
    }

    @Nullable
    private static TileEntity getLoadedTile(World world, int x, int y, int z) {
        if (world == null || !world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) {
            return null;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile == null || tile.isInvalid() ? null : tile;
    }

    @Nullable
    private static IGridHost getGtMeHatchGridHost(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity gtTile) || !(gtTile.getMetaTileEntity() instanceof MTEHatch hatch)
            || hatch instanceof MTEWirelessNetworkCore) {
            return null;
        }
        return hatch instanceof IGridHost gridHost ? gridHost : null;
    }
}
