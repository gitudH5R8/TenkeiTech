/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;

/** Bounded server-only crop sweeps; no chain input, persistent inventory or chunk loading. */
public final class UniversalWandAutoHarvestHandler {

    private static final int HALF_WIDTH = 8;
    private static final int HALF_HEIGHT = 1;
    private static final int CHECK_INTERVAL = 20;
    private static final int[][] OFFSETS = offsets();
    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static int nextPlayer;

    private UniversalWandAutoHarvestHandler() {}

    private static int[][] offsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -HALF_WIDTH; x < HALF_WIDTH; x++) {
            for (int y = -HALF_HEIGHT; y <= HALF_HEIGHT; y++) {
                for (int z = -HALF_WIDTH; z < HALF_WIDTH; z++) {
                    offsets.add(new int[] { x, y, z });
                }
            }
        }
        offsets.sort(java.util.Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
        return offsets.toArray(new int[0][]);
    }

    private static boolean eligible(EntityPlayerMP player) {
        return !player.isDead && player.worldObj instanceof WorldServer
            && ItemUniversalWand.isHeldBy(player)
            && ItemUniversalWand.isCropHarvestEnabled(player.getHeldItem())
            && !ItemUniversalWand.isProtectionMode(player.getHeldItem());
    }

    static void tick(int searchRemaining, int harvestRemaining, Map<UUID, Integer> searched,
        Map<UUID, Integer> attempted) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        List<EntityPlayerMP> players = new ArrayList<>();
        Set<UUID> active = new HashSet<>();
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP player && eligible(player)) {
                players.add(player);
                active.add(player.getUniqueID());
            }
        }
        SCANS.keySet()
            .retainAll(active);
        if (players.isEmpty()) return;
        Collections.rotate(players, -Math.floorMod(nextPlayer++, players.size()));
        for (EntityPlayerMP player : players) {
            UUID id = player.getUniqueID();
            Scan scan = SCANS.get(id);
            if (scan == null || scan.world != player.worldObj) {
                scan = new Scan(player);
                SCANS.put(id, scan);
            }
            long now = scan.world.getTotalWorldTime();
            int searchBudget = Math.min(
                searchRemaining,
                Math.max(0, Config.universalWandSearchPositionsPerTick - searched.getOrDefault(id, 0)));
            int harvestBudget = Math
                .min(harvestRemaining, Math.max(0, Config.universalWandBlocksPerTick - attempted.getOrDefault(id, 0)));
            ItemStack wand = player.getHeldItem();
            try {
                if (now >= scan.nextCheck && searchBudget > 0) {
                    scan.nextCheck = now + CHECK_INTERVAL;
                    if (scan.cursor == OFFSETS.length || scan.x != MathHelper.floor_double(player.posX)
                        || scan.y != MathHelper.floor_double(player.posY)
                        || scan.z != MathHelper.floor_double(player.posZ)) scan.restart(player);
                    scan.pending.values()
                        .removeIf(pos -> !inRange(player, pos[0], pos[1], pos[2]));
                    while (searchBudget > 0 && scan.cursor < OFFSETS.length) {
                        int[] offset = OFFSETS[scan.cursor++];
                        searchBudget--;
                        searchRemaining--;
                        int x = scan.x + offset[0], y = scan.y + offset[1], z = scan.z + offset[2];
                        if (UniversalWandCropHarvest.isHarvestable(scan.world, x, y, z, wand))
                            scan.pending.putIfAbsent(key(x, y, z), new int[] { x, y, z });
                    }
                }
                while (harvestBudget > 0 && !scan.pending.isEmpty()) {
                    if (!eligible(player) || player.worldObj != scan.world || player.getHeldItem() != wand) break;
                    java.util.Iterator<int[]> pending = scan.pending.values()
                        .iterator();
                    int[] pos = pending.next();
                    pending.remove();
                    int x = pos[0], y = pos[1], z = pos[2];
                    if (!inRange(player, x, y, z) || !UniversalWandCropHarvest.isHarvestable(scan.world, x, y, z, wand))
                        continue;
                    harvestBudget--;
                    harvestRemaining--;
                    if (!player.canPlayerEdit(x, y, z, 1, wand)
                        || server.isBlockProtected((WorldServer) scan.world, x, y, z, player)) continue;
                    try (UniversalWandMiningHandler.HarvestScope ignored = UniversalWandMiningHandler
                        .beginAutoHarvest(player, x, y, z)) {
                        UniversalWandCropHarvest.harvest(player, x, y, z);
                    }
                }
            } catch (RuntimeException failure) {
                // Drop queued attempts after a callback failure; rediscover crops on a later check.
                scan.pending.clear();
                scan.cursor = OFFSETS.length;
                scan.nextCheck = now + CHECK_INTERVAL;
                TenkeiTech.LOG.error("Universal Wand automatic crop harvest stopped after a callback failure", failure);
            }
        }
    }

    private static boolean inRange(EntityPlayerMP player, int x, int y, int z) {
        long dx = (long) x - MathHelper.floor_double(player.posX);
        long dy = (long) y - MathHelper.floor_double(player.posY);
        long dz = (long) z - MathHelper.floor_double(player.posZ);
        return dx >= -HALF_WIDTH && dx < HALF_WIDTH
            && dy >= -HALF_HEIGHT
            && dy <= HALF_HEIGHT
            && dz >= -HALF_WIDTH
            && dz < HALF_WIDTH;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }

    static void clear(UUID id) {
        SCANS.remove(id);
    }

    static void unload(World world) {
        SCANS.values()
            .removeIf(scan -> scan.world == world);
    }

    static void clearAll() {
        SCANS.clear();
        nextPlayer = 0;
    }

    private static final class Scan {

        final World world;
        final Map<Long, int[]> pending = new LinkedHashMap<>();
        int x, y, z, cursor;
        long nextCheck;

        Scan(EntityPlayerMP player) {
            world = player.worldObj;
            restart(player);
        }

        void restart(EntityPlayerMP player) {
            x = MathHelper.floor_double(player.posX);
            y = MathHelper.floor_double(player.posY);
            z = MathHelper.floor_double(player.posZ);
            cursor = 0;
        }
    }
}
