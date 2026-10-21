/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One pending fallback per player; actual server outcomes veto the client travel action. */
public final class UniversalWandRightClick {

    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();

    public static void request(EntityPlayerMP player, int id) {
        REQUESTS.put(player.getUniqueID(), new Request(player, id));
    }

    public static boolean isPending(EntityPlayer player) {
        Request request = REQUESTS.get(player.getUniqueID());
        return request != null && request.player == player;
    }

    public static boolean handled(EntityPlayer player, boolean changed) {
        if (changed && player instanceof EntityPlayerMP) {
            Request request = REQUESTS.get(player.getUniqueID());
            if (request != null && request.player == player) request.handled = true;
        }
        return changed;
    }

    /** Sent after every vanilla/custom use packet belonging to this click, on the same connection. */
    public static void finish(EntityPlayerMP player, int id) {
        Request request = REQUESTS.get(player.getUniqueID());
        if (request == null || request.player != player || request.id != id) return;
        request.ready = true;
        complete(player);
    }

    /** Building calls this when its existing main-thread job completes; no additional tick is scheduled. */
    public static void complete(EntityPlayerMP player) {
        Request request = REQUESTS.get(player.getUniqueID());
        if (request == null || request.player != player
            || !request.ready
            || UniversalWandBuildingHandler.hasPendingAction(player)
            || !REQUESTS.remove(player.getUniqueID(), request)) return;
        ItemStack stack = player.getHeldItem();
        if (request.handled || System.nanoTime() > request.deadline
            || player.isDead
            || player.worldObj == null
            || !player.worldObj.playerEntities.contains(player)
            || player.openContainer != player.inventoryContainer
            || stack == null
            || !(stack.getItem() instanceof ItemUniversalWand)
            || !ItemUniversalWand.isTeleportEnabled(stack)
            || !Loader.isModLoaded("EnderIO")) return;
        UniversalWandNetwork.allowTravelFallback(player, request.id);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Missing completion markers expire without granting travel. This is cleanup, not an action timer.
        long now = System.nanoTime();
        REQUESTS.entrySet()
            .removeIf(entry -> now > entry.getValue().deadline);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.player.getUniqueID());
    }

    public static void clearAll() {
        REQUESTS.clear();
    }

    private static final class Request {

        final EntityPlayerMP player;
        final int id;
        final long deadline = System.nanoTime() + 2_000_000_000L;
        volatile boolean handled;
        volatile boolean ready;

        Request(EntityPlayerMP player, int id) {
            this.player = player;
            this.id = id;
        }
    }
}
