/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tenkeitech.Config;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.universalwand.UniversalWandStorage;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Per-player bounded job history. World changes and material refunds stay on the server thread. */
public final class BuildingUndoHistory {

    private static final long KEY_TIMEOUT_NANOS = 750_000_000L;
    private static final Map<UUID, KeyRequest> KEY_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HELD = new HashMap<>();
    private static final Map<UUID, LinkedList<Entry>> HISTORY = new HashMap<>();
    private static final Set<UUID> UNDOING = new HashSet<>();

    private static final class KeyRequest {

        final EntityPlayerMP player;
        final boolean held;

        KeyRequest(EntityPlayerMP player, boolean held) {
            this.player = player;
            this.held = held;
        }
    }

    private static final class Entry {

        final World world;
        final Position angelAnchor;
        final UniversalWandStorage.Destination refundDestination;
        final List<BuildingUndoRecord> records;

        Entry(BuildingContext context, List<BuildingUndoRecord> records,
            UniversalWandStorage.Destination refundDestination) {
            this.world = context.world;
            this.angelAnchor = context.core == BuildingCore.ANGEL && !context.air ? context.origin : null;
            this.refundDestination = refundDestination;
            this.records = new ArrayList<>(records);
        }
    }

    private BuildingUndoHistory() {}

    public static void requestKey(EntityPlayerMP player, boolean held) {
        if (player != null) KEY_REQUESTS.put(player.getUniqueID(), new KeyRequest(player, held));
    }

    public static boolean isActive(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP mp)) return false;
        KeyRequest requested = KEY_REQUESTS.remove(player.getUniqueID());
        if (requested != null) {
            if (requested.player == player && requested.held
                && ItemUniversalWand.isHeldBy(player)
                && ItemUniversalWand.isBuildingEnabled(player.getHeldItem()))
                HELD.put(player.getUniqueID(), System.nanoTime());
            else HELD.remove(player.getUniqueID());
            if (requested.held) updateClient(mp);
        }
        Long last = HELD.get(player.getUniqueID());
        return last != null && System.nanoTime() - last < KEY_TIMEOUT_NANOS;
    }

    public static void tick() {
        HELD.entrySet()
            .removeIf(entry -> System.nanoTime() - entry.getValue() >= KEY_TIMEOUT_NANOS);
        for (KeyRequest request : new ArrayList<>(KEY_REQUESTS.values())) {
            if (request.player.worldObj != null && request.player.worldObj.playerEntities.contains(request.player))
                isActive(request.player);
            else KEY_REQUESTS.remove(request.player.getUniqueID(), request);
        }
        for (LinkedList<Entry> entries : HISTORY.values()) trim(entries);
    }

    private static void trim(LinkedList<Entry> entries) {
        int limit = Math.max(0, Math.min(Config.MAX_WAND_UNDO_HISTORY, Config.universalWandUndoHistory));
        while (entries.size() > limit) entries.removeFirst();
    }

    public static void add(EntityPlayerMP player, BuildingContext context, List<BuildingUndoRecord> records,
        UniversalWandStorage.Destination refundDestination) {
        if (records.isEmpty() || Config.universalWandUndoHistory <= 0) return;
        records.removeIf(record -> record.isObsolete(context.world));
        if (records.isEmpty()) return;
        LinkedList<Entry> entries = HISTORY.computeIfAbsent(player.getUniqueID(), ignored -> new LinkedList<>());
        entries.add(new Entry(context, records, refundDestination));
        trim(entries);
        updateClient(player);
    }

    private static Entry latest(EntityPlayerMP player) {
        LinkedList<Entry> entries = HISTORY.get(player.getUniqueID());
        if (entries == null) return null;
        trim(entries);
        return entries.isEmpty() || entries.getLast().world != player.worldObj ? null : entries.getLast();
    }

    public static void updateClient(EntityPlayerMP player) {
        Entry entry = latest(player);
        Map<Long, Position> positions = new LinkedHashMap<>();
        if (entry != null) {
            for (BuildingUndoRecord record : entry.records) {
                if (record.isObsolete(entry.world)) continue;
                for (Position position : record.getPositions()) {
                    if (positions.size() >= BuildingContext.MAX_BLOCKS) break;
                    positions.putIfAbsent(position.key(), position);
                }
            }
        }
        UniversalWandNetwork.sendUndoPreview(player, new ArrayList<>(positions.values()));
    }

    public static void undo(EntityPlayerMP player, Position clicked) {
        ItemStack wand = player.getHeldItem();
        Entry entry = latest(player);
        if (!isActive(player) || !BuildingUndoRecord.active(player, wand)
            || entry == null
            || !withinRange(entry, clicked)
            || !UNDOING.add(player.getUniqueID())) return;
        List<ItemStack> refunds = new ArrayList<>();
        int restored = 0;
        try {
            ListIterator<BuildingUndoRecord> iterator = entry.records.listIterator(entry.records.size());
            while (iterator.hasPrevious() && player.worldObj == entry.world
                && BuildingUndoRecord.active(player, wand)) {
                BuildingUndoRecord record = iterator.previous();
                if (record.isObsolete(entry.world)) {
                    iterator.remove();
                    continue;
                }
                if (!record.restore(player, wand)) continue;
                iterator.remove();
                restored++;
                com.tenkeitech.universalwand.UniversalWandRightClick.handled(player, true);
                ItemStack refund = record.takeRefund();
                if (refund != null) refunds.add(refund);
            }
        } finally {
            UNDOING.remove(player.getUniqueID());
            LinkedList<Entry> entries = HISTORY.get(player.getUniqueID());
            if (entry.records.isEmpty() && entries != null) entries.remove(entry);
            UniversalWandMiningHandler.refundBuildingMaterials(player, refunds, entry.refundDestination);
            player.inventory.markDirty();
            player.sendContainerToPlayer(player.inventoryContainer);
            updateClient(player);
            if (restored > 0)
                player.worldObj.playSoundEffect(player.posX, player.posY, player.posZ, "mob.endermen.portal", 0.5F, 1F);
        }
    }

    private static boolean withinRange(Entry entry, Position clicked) {
        // Angel placement can be behind a wall, beyond the normal three-block undo trigger radius.
        if (entry.angelAnchor != null && entry.angelAnchor.x == clicked.x
            && entry.angelAnchor.y == clicked.y
            && entry.angelAnchor.z == clicked.z) return true;
        for (BuildingUndoRecord record : entry.records) {
            for (Position position : record.getPositions()) {
                long dx = (long) position.x - clicked.x, dy = (long) position.y - clicked.y,
                    dz = (long) position.z - clicked.z;
                if (dx * dx + dy * dy + dz * dz < 9L) return true;
            }
        }
        return false;
    }

    public static void clear(UUID player) {
        KEY_REQUESTS.remove(player);
        HELD.remove(player);
        HISTORY.remove(player);
        UNDOING.remove(player);
    }

    public static void clearAll() {
        KEY_REQUESTS.clear();
        HELD.clear();
        HISTORY.clear();
        UNDOING.clear();
    }

    public static void unload(World world) {
        if (world.isRemote) return;
        HISTORY.values()
            .forEach(entries -> entries.removeIf(entry -> entry.world == world));
        HISTORY.values()
            .removeIf(List::isEmpty);
        for (Object player : world.playerEntities) clear(((EntityPlayer) player).getUniqueID());
    }
}
