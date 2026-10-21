/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.tenkeitech.item.ItemUniversalWand;

/** Coalesces network requests and resolves their targets on the server tick. */
public final class UniversalWandActions {

    public enum Action {
        OPEN_GUI,
        BEDROCK,
        CHAIN_KEY
    }

    private static final class Request {

        final EntityPlayerMP player;
        final int dimension;
        final Action action;
        final int x, y, z;
        int openingDelay = 2;

        Request(EntityPlayerMP player, int dimension, Action action, int x, int y, int z) {
            this.player = player;
            this.dimension = dimension;
            this.action = action;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<Action, Map<UUID, Request>> REQUESTS = new java.util.EnumMap<>(Action.class);

    static {
        for (Action action : Action.values()) REQUESTS.put(action, new ConcurrentHashMap<>());
    }

    private UniversalWandActions() {}

    public static void request(EntityPlayerMP player, Action action, int x, int y, int z) {
        REQUESTS.get(action)
            .put(player.getUniqueID(), new Request(player, player.dimension, action, x, y, z));
    }

    static void tick() {
        for (Map<UUID, Request> requests : REQUESTS.values()) {
            for (Map.Entry<UUID, Request> entry : requests.entrySet()) {
                Request request = entry.getValue();
                // Let inventory swaps queued alongside the GUI request settle before binding its item slot.
                if (request.action == Action.OPEN_GUI && request.openingDelay-- > 0) continue;
                if (!requests.remove(entry.getKey(), request)) continue;
                EntityPlayerMP player = request.player;
                if (player.isDead || player.dimension != request.dimension
                    || player.worldObj == null
                    || !player.worldObj.playerEntities.contains(player)) continue;
                switch (request.action) {
                    case OPEN_GUI -> {
                        if (request.x >= 0 && request.x < 9 && player.inventory.currentItem == request.x)
                            ItemUniversalWand.openSettingsGui(player);
                    }
                    case BEDROCK -> ItemUniversalWand.forceBedrock(player, request.x, request.y, request.z);
                    case CHAIN_KEY -> UniversalWandMiningHandler.setKeyHeld(player, request.x != 0);
                }
            }
        }
    }

    public static MovingObjectPosition rayTrace(EntityPlayerMP player) {
        double reach = player.theItemInWorldManager.getBlockReachDistance();
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        World world = player.worldObj;
        // Ray tracing must not turn an action request into a chunk-loading request.
        for (double distance = 0; distance <= reach; distance += 0.5D) {
            int x = (int) Math.floor(eye.xCoord + look.xCoord * distance);
            int z = (int) Math.floor(eye.zCoord + look.zCoord * distance);
            if (!world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) return null;
        }
        if (!world.getChunkProvider()
            .chunkExists((int) Math.floor(end.xCoord) >> 4, (int) Math.floor(end.zCoord) >> 4)) return null;
        return world.rayTraceBlocks(eye, end);
    }

    public static boolean isTarget(EntityPlayerMP player, int x, int y, int z) {
        MovingObjectPosition hit = rayTrace(player);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == x
            && hit.blockY == y
            && hit.blockZ == z;
    }

    static void clear(UUID player) {
        for (Map<UUID, Request> requests : REQUESTS.values()) requests.remove(player);
    }

    static void applyKeyRequest(EntityPlayerMP player) {
        Request request = REQUESTS.get(Action.CHAIN_KEY)
            .remove(player.getUniqueID());
        if (request != null && request.player == player && request.dimension == player.dimension)
            UniversalWandMiningHandler.setKeyHeld(player, request.x != 0);
    }

    static void clearAll() {
        for (Map<UUID, Request> requests : REQUESTS.values()) requests.clear();
    }
}
