/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import com.tenkeitech.Config;
import com.tenkeitech.machine.MTEWirelessNetworkCore;
import com.tenkeitech.machine.MTEWirelessNetworkCore.BindResult;
import com.tenkeitech.wireless.WirelessAeWorldData.NetworkRecord;

/** Bounded next-tick binding for newly placed GT ME hatches. Never loads a chunk or rolls back placement. */
final class WirelessAeSneakPlaceBinder {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ATTEMPTS_PER_TICK = 32;
    private static final int MAX_PENDING_PER_WORLD = 1_024;

    private static final Map<World, LinkedHashMap<BlockPosition, PendingPlacement>> PENDING_BY_WORLD = new WeakHashMap<>();

    private WirelessAeSneakPlaceBinder() {}

    static void enqueue(BlockEvent.PlaceEvent event) {
        if (!Config.enableWirelessAe || !Config.enableWirelessSneakAutoBind
            || event == null
            || event.world == null
            || event.world.isRemote
            || event.player == null
            || !event.player.isSneaking()
            || event.placedBlock == null) {
            return;
        }

        UUID playerId = event.player.getUniqueID();
        WirelessAeWorldData worldData = WirelessAeWorldData.get(event.world);
        UUID favorite = worldData.getFavorite(playerId);
        if (favorite == null || worldData.getAccessibleNetwork(playerId, favorite) == null) return;

        LinkedHashMap<BlockPosition, PendingPlacement> worldPending = PENDING_BY_WORLD
            .computeIfAbsent(event.world, ignored -> new LinkedHashMap<>());
        BlockPosition position = new BlockPosition(event.x, event.y, event.z);
        if (!worldPending.containsKey(position) && worldPending.size() >= MAX_PENDING_PER_WORLD) {
            WirelessAeEventHandler.send(event.player, "tenkeitech.wireless.auto_bind_busy");
            return;
        }
        worldPending.put(
            position,
            new PendingPlacement(
                event.player,
                playerId,
                favorite,
                event.placedBlock,
                event.world.getBlockMetadata(event.x, event.y, event.z),
                event.world.getTotalWorldTime() + 1));
    }

    static void invalidate(World world, int x, int y, int z) {
        LinkedHashMap<BlockPosition, PendingPlacement> worldPending = PENDING_BY_WORLD.get(world);
        if (worldPending == null) return;
        worldPending.remove(new BlockPosition(x, y, z));
        if (worldPending.isEmpty()) PENDING_BY_WORLD.remove(world);
    }

    static void clear(World world) {
        if (world != null) PENDING_BY_WORLD.remove(world);
    }

    static void tick(World world) {
        if (!Config.enableWirelessAe || !Config.enableWirelessSneakAutoBind) {
            clear(world);
            return;
        }
        LinkedHashMap<BlockPosition, PendingPlacement> worldPending = PENDING_BY_WORLD.get(world);
        if (worldPending == null || worldPending.isEmpty()) return;

        long currentTick = world.getTotalWorldTime();
        int attempted = 0;
        Iterator<Map.Entry<BlockPosition, PendingPlacement>> iterator = worldPending.entrySet()
            .iterator();
        while (iterator.hasNext() && attempted < MAX_ATTEMPTS_PER_TICK) {
            Map.Entry<BlockPosition, PendingPlacement> entry = iterator.next();
            PendingPlacement pending = entry.getValue();
            if (currentTick < pending.nextAttemptTick) continue;
            attempted++;

            AttemptResult result = attempt(world, entry.getKey(), pending);
            if (result == AttemptResult.REMOVE) {
                iterator.remove();
                continue;
            }
            if (pending.scheduleRetry(currentTick)) continue;

            EntityPlayer player = pending.getPlayer(world);
            if (player != null) {
                WirelessAeEventHandler.send(
                    player,
                    result == AttemptResult.RETRY_CORE ? "tenkeitech.wireless.auto_bind_core_not_ready"
                        : "tenkeitech.wireless.auto_bind_target_not_ready");
            }
            iterator.remove();
        }
        if (worldPending.isEmpty()) PENDING_BY_WORLD.remove(world);
    }

    private static AttemptResult attempt(World world, BlockPosition position, PendingPlacement pending) {
        if (!world.getChunkProvider()
            .chunkExists(position.x >> 4, position.z >> 4)) {
            return AttemptResult.REMOVE;
        }
        if (world.getBlock(position.x, position.y, position.z) != pending.placedBlock) {
            return AttemptResult.REMOVE;
        }
        if (!pending.placedBlock.hasTileEntity(pending.placedMetadata)) return AttemptResult.REMOVE;
        if (!WirelessAeTargetResolver.isLoadedGtMeHatch(world, position.x, position.y, position.z)) {
            return world.getTileEntity(position.x, position.y, position.z) == null ? AttemptResult.RETRY_TARGET
                : AttemptResult.REMOVE;
        }

        EntityPlayer player = pending.getPlayer(world);
        if (player == null) return AttemptResult.REMOVE;
        WirelessAeWorldData worldData = WirelessAeWorldData.get(world);
        UUID favorite = worldData.getFavorite(pending.playerId);
        if (!pending.frequency.equals(favorite)) return AttemptResult.REMOVE;
        NetworkRecord network = worldData.getAccessibleNetwork(pending.playerId, pending.frequency);
        if (network == null) return AttemptResult.REMOVE;
        if (network.dimensionId() != world.provider.dimensionId && !Config.enableWirelessCrossDimension) {
            WirelessAeEventHandler.send(player, "tenkeitech.wireless.cross_dimension_disabled");
            return AttemptResult.REMOVE;
        }

        WirelessAeTargetLocation target = WirelessAeTargetResolver
            .resolveLoadedGtMeHatch(world, position.x, position.y, position.z);
        if (target == null) return AttemptResult.RETRY_TARGET;

        MTEWirelessNetworkCore core = WirelessAeRuntimeRegistry.findLoadedCore(world, network);
        if (core == null) return AttemptResult.RETRY_CORE;
        BindResult result = core.bindTarget(player, target);
        if (result == BindResult.CORE_OFFLINE) return AttemptResult.RETRY_CORE;
        if (result == BindResult.INVALID_TARGET) return AttemptResult.RETRY_TARGET;
        WirelessAeEventHandler.send(player, WirelessAeEventHandler.getTranslationKey(result));
        return AttemptResult.REMOVE;
    }

    private enum AttemptResult {
        REMOVE,
        RETRY_TARGET,
        RETRY_CORE
    }

    private static final class PendingPlacement {

        private final WeakReference<EntityPlayer> player;
        private final UUID playerId;
        private final UUID frequency;
        private final Block placedBlock;
        private final int placedMetadata;
        private int attempts;
        private long nextAttemptTick;

        private PendingPlacement(EntityPlayer player, UUID playerId, UUID frequency, Block placedBlock,
            int placedMetadata, long nextAttemptTick) {
            this.player = new WeakReference<>(player);
            this.playerId = playerId;
            this.frequency = frequency;
            this.placedBlock = placedBlock;
            this.placedMetadata = placedMetadata;
            this.nextAttemptTick = nextAttemptTick;
        }

        private EntityPlayer getPlayer(World world) {
            EntityPlayer current = player.get();
            return current != null && !current.isDead
                && current.worldObj == world
                && playerId.equals(current.getUniqueID()) ? current : null;
        }

        private boolean scheduleRetry(long currentTick) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) return false;
            nextAttemptTick = currentTick + (1L << attempts);
            return true;
        }
    }

    private static final class BlockPosition {

        private final int x;
        private final int y;
        private final int z;

        private BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockPosition position)) return false;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }
}
