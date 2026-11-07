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
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.UseHoeEvent;

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.WandRightClickRouter;
import com.tenkeitech.universalwand.interaction.WandActionResult;
import com.tenkeitech.universalwand.interaction.WandRightClickContext;
import com.tenkeitech.universalwand.interaction.WandRightClickContext.Phase;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Results from native phases and queued jobs meet here before the router's final phase. */
public final class UniversalWandRightClick {

    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Scope> EXECUTING = new ThreadLocal<>();

    public static void request(EntityPlayerMP player, int id) {
        if (id == 0 || !ItemUniversalWand.isHeldBy(player)) return;
        REQUESTS.put(player.getUniqueID(), new Request(player, id));
    }

    private static Request current(EntityPlayer player) {
        Scope scope = EXECUTING.get();
        if (scope != null && scope.player == player) return scope.request;
        Request request = REQUESTS.get(player.getUniqueID());
        return request != null && request.player == player ? request : null;
    }

    public static boolean isPending(EntityPlayer player) {
        return current(player) != null;
    }

    public static void observe(EntityPlayer player) {
        Request request = current(player);
        if (request != null) request.observed = true;
    }

    /** A native attack/dig packet inside the still-open right click is an explicit handoff, e.g. Backhand. */
    public static void nativeHandoff(EntityPlayer player) {
        Request request = current(player);
        if (request == null || request.ready) return;
        request.observed = true;
        record(request, WandActionResult.NO_EFFECT);
    }

    public static boolean tryAction(EntityPlayer player, int action) {
        Request request = current(player);
        if (request == null) return true;
        long bit = 1L << action;
        if ((request.attempted & bit) != 0) return false;
        request.attempted |= bit;
        return true;
    }

    public static WandActionResult result(EntityPlayer player) {
        Request request = current(player);
        return request == null ? WandActionResult.PASS
            : request.pending > 0 ? request.result.merge(WandActionResult.PENDING) : request.result;
    }

    public static void record(EntityPlayer player, WandActionResult result) {
        Request request = current(player);
        if (request != null) record(request, result);
    }

    private static void record(Request request, WandActionResult result) {
        // Waiting is represented by owned tickets; it must not survive their final result.
        if (result != WandActionResult.PENDING) request.result = request.result.merge(result);
    }

    public static boolean handled(EntityPlayer player, boolean handled) {
        if (handled && player instanceof EntityPlayerMP) record(player, WandActionResult.HANDLED);
        return handled;
    }

    /** A ticket owns its original request, so a late job cannot complete or veto a later click. */
    public static final class Ticket {

        private final Request request;
        private boolean completed;

        private Ticket(Request request) {
            this.request = request;
        }

        public boolean isCurrent(EntityPlayer player) {
            return !completed && request == current(player);
        }
    }

    /** Native callbacks inside an older queued job must report to that job's click too. */
    public static final class Scope implements AutoCloseable {

        private final EntityPlayerMP player;
        private final Request request;
        private final Scope previous;

        private Scope(EntityPlayerMP player, Ticket ticket) {
            this.player = player;
            request = ticket == null ? null : ticket.request;
            previous = EXECUTING.get();
            EXECUTING.set(this);
        }

        @Override
        public void close() {
            if (previous == null) EXECUTING.remove();
            else EXECUTING.set(previous);
        }
    }

    public static Scope executing(EntityPlayerMP player, Ticket ticket) {
        return new Scope(player, ticket);
    }

    public static Ticket defer(EntityPlayerMP player) {
        Request request = current(player);
        if (request == null) return null;
        request.observed = true;
        request.pending++;
        return new Ticket(request);
    }

    public static void complete(Ticket ticket, WandActionResult result) {
        if (ticket == null || ticket.completed) return;
        ticket.completed = true;
        Request request = ticket.request;
        request.pending--;
        record(request, result == WandActionResult.PENDING ? WandActionResult.BLOCKED : result);
        complete(request);
    }

    /** Follows all native/custom use packets belonging to this click on the same connection. */
    public static void finish(EntityPlayerMP player, int id, boolean clientBlocked) {
        Request request = current(player);
        if (request == null || request.id != id) return;
        if (clientBlocked) record(request, WandActionResult.BLOCKED);
        request.ready = true;
        complete(request);
    }

    private static void complete(Request request) {
        EntityPlayerMP player = request.player;
        if (!request.ready || request.pending > 0 || !REQUESTS.remove(player.getUniqueID(), request)) return;
        if (!request.observed || request.result.consumesClick()
            || System.nanoTime() > request.deadline
            || player.isDead
            || player.worldObj != request.world
            || player.dimension != request.dimension
            || !request.world.playerEntities.contains(player)
            || player.openContainer != player.inventoryContainer
            || player.inventory.currentItem != request.slot
            || !ItemStack.areItemStacksEqual(player.getHeldItem(), request.stack)) return;
        WandRightClickRouter.route(WandRightClickContext.air(Phase.COMPLETE, player, player.getHeldItem(), request.id));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.world.isRemote && WandActionResult.fromInteraction(event) == WandActionResult.BLOCKED)
            record(event.entityPlayer, WandActionResult.BLOCKED);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onHoeUse(UseHoeEvent event) {
        if (!event.world.isRemote && (event.isCanceled() || event.getResult() == Event.Result.DENY))
            record(event.entityPlayer, WandActionResult.BLOCKED);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        final World world;
        final ItemStack stack;
        final int id, slot, dimension;
        final long deadline = System.nanoTime() + 2_000_000_000L;
        long attempted;
        WandActionResult result = WandActionResult.PASS;
        int pending;
        boolean ready, observed;

        Request(EntityPlayerMP player, int id) {
            this.player = player;
            this.id = id;
            world = player.worldObj;
            dimension = player.dimension;
            slot = player.inventory.currentItem;
            stack = player.getHeldItem()
                .copy();
        }
    }
}
