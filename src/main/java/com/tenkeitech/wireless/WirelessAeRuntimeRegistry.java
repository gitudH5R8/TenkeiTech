/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.tenkeitech.machine.MTEWirelessNetworkCore;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Server-scoped indexes for loaded cores and their dimensional target blocks. */
public final class WirelessAeRuntimeRegistry {

    private static final Map<MinecraftServer, ServerState> SERVER_STATES = new WeakHashMap<>();

    private WirelessAeRuntimeRegistry() {}

    public static synchronized void registerCore(MTEWirelessNetworkCore core, UUID frequency) {
        ServerState state = stateFor(core, true);
        if (state == null || frequency == null) return;
        state.coresByFrequency.computeIfAbsent(frequency, ignored -> newWeakCoreSet())
            .add(core);
    }

    public static synchronized void unregisterCore(MTEWirelessNetworkCore core, UUID frequency) {
        ServerState state = stateFor(core, false);
        if (state == null || frequency == null) return;
        Set<MTEWirelessNetworkCore> cores = state.coresByFrequency.get(frequency);
        if (cores != null) {
            cores.remove(core);
            if (cores.isEmpty()) state.coresByFrequency.remove(frequency);
        }
        discardEmptyState(state);
    }

    public static synchronized boolean isCoreLoaded(World referenceWorld, WirelessAeWorldData.NetworkRecord network) {
        return findLoadedCore(referenceWorld, network) != null;
    }

    @Nullable
    public static synchronized MTEWirelessNetworkCore findLoadedCore(World referenceWorld,
        WirelessAeWorldData.NetworkRecord network) {
        if (network == null) return null;
        ServerState state = stateFor(referenceWorld, false);
        if (state == null) return null;
        World loadedCoreWorld = WirelessAeWorldResolver.getLoadedWorld(referenceWorld, network.dimensionId());
        if (loadedCoreWorld == null) return null;
        Set<MTEWirelessNetworkCore> cores = state.coresByFrequency.get(network.frequency());
        if (cores == null || cores.isEmpty()) return null;
        for (MTEWirelessNetworkCore core : new ArrayList<>(cores)) {
            if (isMatchingLoadedCore(core, loadedCoreWorld, network)) return core;
        }
        return null;
    }

    public static synchronized void register(MTEWirelessNetworkCore core, WirelessAeTargetLocation target) {
        ServerState state = stateFor(core, true);
        if (state == null || target == null) return;
        state.coresByTarget.computeIfAbsent(TargetBlockKey.of(target), ignored -> newWeakCoreSet())
            .add(core);
    }

    public static synchronized void unregister(MTEWirelessNetworkCore core, WirelessAeTargetLocation target) {
        ServerState state = stateFor(core, false);
        if (state == null || target == null) return;
        TargetBlockKey key = TargetBlockKey.of(target);
        Set<MTEWirelessNetworkCore> cores = state.coresByTarget.get(key);
        if (cores != null) {
            cores.remove(core);
            if (cores.isEmpty()) state.coresByTarget.remove(key);
        }
        discardEmptyState(state);
    }

    public static synchronized int clearDestroyedTarget(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return 0;
        ServerState state = stateFor(world, false);
        if (state == null) return 0;

        Set<MTEWirelessNetworkCore> cores = state.coresByTarget
            .remove(new TargetBlockKey(world.provider.dimensionId, x, y, z));
        if (cores == null || cores.isEmpty()) {
            discardEmptyState(state);
            return 0;
        }

        int cleared = 0;
        for (MTEWirelessNetworkCore core : new ArrayList<>(cores)) {
            if (core != null && core.clearDestroyedTarget(world, x, y, z)) cleared++;
        }
        discardEmptyState(state);
        return cleared;
    }

    /** Drops runtime references into an unloading dimension without changing persistent membership. */
    public static synchronized void disconnectTargetWorld(World world) {
        if (world == null || world.isRemote) return;
        ServerState state = stateFor(world, false);
        if (state == null) return;

        int dimensionId = world.provider.dimensionId;
        Set<MTEWirelessNetworkCore> cores = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<TargetBlockKey, Set<MTEWirelessNetworkCore>> entry : state.coresByTarget.entrySet()) {
            if (entry.getKey().dimensionId == dimensionId) cores.addAll(entry.getValue());
        }
        for (MTEWirelessNetworkCore core : cores) {
            if (core != null) core.disconnectRuntimeTargetsInDimension(dimensionId);
        }
    }

    private static boolean isMatchingLoadedCore(MTEWirelessNetworkCore core, World loadedCoreWorld,
        WirelessAeWorldData.NetworkRecord network) {
        if (core == null) return false;
        IGregTechTileEntity base = core.getBaseMetaTileEntity();
        World coreWorld = base == null ? null : base.getWorld();
        return base != null && base.isServerSide()
            && coreWorld == loadedCoreWorld
            && coreWorld.provider.dimensionId == network.dimensionId()
            && base.getXCoord() == network.x()
            && base.getYCoord() == network.y()
            && base.getZCoord() == network.z()
            && core.matchesFrequency(
                network.frequency()
                    .getMostSignificantBits(),
                network.frequency()
                    .getLeastSignificantBits());
    }

    private static Set<MTEWirelessNetworkCore> newWeakCoreSet() {
        return Collections.newSetFromMap(new WeakHashMap<>());
    }

    @Nullable
    private static ServerState stateFor(MTEWirelessNetworkCore core, boolean create) {
        if (core == null) return null;
        IGregTechTileEntity base = core.getBaseMetaTileEntity();
        return base == null ? null : stateFor(base.getWorld(), create);
    }

    @Nullable
    private static ServerState stateFor(World world, boolean create) {
        if (world == null || world.isRemote) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        return create ? SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState()) : SERVER_STATES.get(server);
    }

    private static void discardEmptyState(ServerState state) {
        if (!state.coresByFrequency.isEmpty() || !state.coresByTarget.isEmpty()) return;
        SERVER_STATES.values()
            .removeIf(candidate -> candidate == state);
    }

    private static final class ServerState {

        private final Map<UUID, Set<MTEWirelessNetworkCore>> coresByFrequency = new HashMap<>();
        private final Map<TargetBlockKey, Set<MTEWirelessNetworkCore>> coresByTarget = new HashMap<>();
    }

    private static final class TargetBlockKey {

        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;

        private TargetBlockKey(int dimensionId, int x, int y, int z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static TargetBlockKey of(WirelessAeTargetLocation target) {
            return new TargetBlockKey(target.dimensionId(), target.x(), target.y(), target.z());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TargetBlockKey key)) return false;
            return dimensionId == key.dimensionId && x == key.x && y == key.y && z == key.z;
        }

        @Override
        public int hashCode() {
            int result = dimensionId;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
