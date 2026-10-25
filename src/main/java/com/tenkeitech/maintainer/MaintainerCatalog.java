/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPostPatternChangeListener;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;

/** Window-local craftable view of AE's shared monitor caches. Inventory counts never enter this catalog. */
final class MaintainerCatalog implements IMEMonitorHandlerReceiver<IAEStack<?>>, ICraftingPostPatternChangeListener {

    private static final int CAPACITY = 4096;
    private final TileBetterLevelMaintainer tile;
    private final Consumer<NBTTagCompound> send;
    private final IAEStack<?>[] materials = new IAEStack<?>[CAPACITY];
    private final NBTTagCompound[] tags = new NBTTagCompound[CAPACITY];
    private final int[] sizes = new int[CAPACITY], seen = new int[CAPACITY];
    private final Map<IAEStack<?>, Integer> indices = new HashMap<>();
    private final Deque<Integer> free = new ArrayDeque<>();
    private final Set<Integer> updates = new LinkedHashSet<>();
    private final Map<IAEStack<?>, IMEMonitor<?>> changes = new LinkedHashMap<>();
    private final Deque<Iterator<? extends IAEStack<?>>> sources = new ArrayDeque<>();
    private ICraftingGrid grid;
    private IMEMonitor<?> items, fluids;
    private boolean initialized, closed, scanRequested, scanOverflow, statusDirty;
    private int version, generation;
    private long scannedAt;

    MaintainerCatalog(TileBetterLevelMaintainer tile, Consumer<NBTTagCompound> send) {
        this.tile = tile;
        this.send = send;
    }

    @Override
    public boolean isValid(Object token) {
        return !closed && (token == items || token == fluids);
    }

    @Override
    public void postChange(IBaseMonitor<IAEStack<?>> monitor, Iterable<IAEStack<?>> change, BaseActionSource source) {
        for (IAEStack<?> material : change) {
            if (!MaintainerEntry.supported(material)) continue;
            if (changes.size() >= 8192) {
                changes.clear();
                scanRequested = true;
                break;
            }
            changes.put(
                material.copy()
                    .setStackSize(1),
                (IMEMonitor<?>) monitor);
        }
    }

    @Override
    public void onListUpdate() {
        scanRequested = true;
    }

    @Override
    public void onPostPatternChange() {
        // AE's alteration event lists new craftables; a rescan also removes disappeared patterns.
        scanRequested = true;
    }

    void resend(int clientVersion) {
        if (!initialized || clientVersion == version) return;
        resetClient();
        for (int i = 0; i < CAPACITY; i++) if (materials[i] != null) updates.add(i);
        statusDirty = true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void attach(IMEMonitor<?> monitor) {
        if (monitor != null) ((IMEMonitor) monitor).addListener(this, monitor);
    }

    private void detach() {
        if (grid instanceof CraftingGridCache cache) cache.removePostPatternChangeListeners(this);
        if (items != null) items.removeListener(this);
        if (fluids != null) fluids.removeListener(this);
    }

    private void refreshConnection(long now) {
        ICraftingGrid currentGrid = null;
        IMEMonitor<?> currentItems = null, currentFluids = null;
        try {
            if (tile.active()) {
                currentGrid = tile.getProxy()
                    .getCrafting();
                currentItems = tile.getProxy()
                    .getStorage()
                    .getItemInventory();
                currentFluids = tile.getProxy()
                    .getStorage()
                    .getFluidInventory();
            }
        } catch (GridAccessException ignored) {}
        if (initialized && grid == currentGrid && items == currentItems && fluids == currentFluids) return;
        detach();
        grid = currentGrid;
        items = currentItems;
        fluids = currentFluids;
        attach(items);
        attach(fluids);
        if (grid instanceof CraftingGridCache cache) cache.addPostPatternChangeListeners(this);
        Arrays.fill(materials, null);
        Arrays.fill(tags, null);
        Arrays.fill(seen, 0);
        indices.clear();
        free.clear();
        for (int i = 0; i < CAPACITY; i++) free.add(i);
        updates.clear();
        changes.clear();
        sources.clear();
        initialized = true;
        version++;
        scanRequested = true;
        scannedAt = now - 20;
        statusDirty = true;
        resetClient();
    }

    private void resetClient() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("kind", 3);
        tag.setInteger("version", version);
        send.accept(tag);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IAEStack<?>[] snapshot(IItemList<?> list) {
        // Do not retain a live native sorted iterator across server ticks.
        IAEStack<?>[] all = (IAEStack<?>[]) ((IItemList) list).toArray(new IAEStack[0]);
        return Arrays.stream(all)
            .filter(IAEStack::isCraftable)
            .toArray(IAEStack<?>[]::new);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IAEStack<?> find(IItemList<?> list, IAEStack<?> material) {
        return list == null ? null : (IAEStack<?>) ((IItemList) list).findPrecise(material);
    }

    private void remove(int index) {
        if (materials[index] == null) return;
        indices.remove(materials[index]);
        materials[index] = null;
        tags[index] = null;
        sizes[index] = 0;
        free.add(index);
        updates.add(index);
    }

    private void update(IAEStack<?> material, boolean craftable) {
        Integer index = indices.get(material);
        if (!craftable) {
            if (index != null) remove(index);
            return;
        }
        if (index != null) {
            seen[index] = generation;
            return;
        }
        if (free.isEmpty()) {
            scanOverflow = true;
            return;
        }
        try {
            IAEStack<?> normalized = material.copy()
                .setStackSize(1);
            NBTTagCompound tag = normalized.toNBTGeneric();
            int size = tag.toString()
                .length();
            if (size > 8192) return;
            index = free.remove();
            materials[index] = normalized;
            tags[index] = tag;
            sizes[index] = size;
            seen[index] = generation;
            indices.put(normalized, index);
            updates.add(index);
        } catch (IllegalArgumentException ignored) {}
    }

    void tick() {
        if (closed) return;
        long now = tile.getWorldObj()
            .getTotalWorldTime();
        refreshConnection(now);
        boolean start = scanRequested && sources.isEmpty() && now - scannedAt >= 20;
        Map<IMEMonitor<?>, IItemList<?>> lists = new IdentityHashMap<>();
        if (start || !sources.isEmpty() || !changes.isEmpty()) {
            if (items != null) lists.put(items, items.getStorageList());
            if (fluids != null) lists.put(fluids, fluids.getStorageList());
        }
        if (start) {
            // Retain the displayed cache while rescanning, and finish this scan before starting another.
            scanRequested = false;
            scanOverflow = false;
            scannedAt = now;
            generation++;
            for (IItemList<?> list : lists.values()) sources.add(
                Arrays.asList(snapshot(list))
                    .iterator());
            statusDirty = true;
        }
        long deadline = System.nanoTime() + 2000000;
        long scanDeadline = changes.isEmpty() ? deadline : deadline - 1000000;
        int scanLimit = changes.isEmpty() ? 256 : 128;
        int processed = 0;
        while (!sources.isEmpty() && processed < scanLimit && (processed == 0 || System.nanoTime() < scanDeadline)) {
            Iterator<? extends IAEStack<?>> source = sources.peek();
            if (!source.hasNext()) {
                sources.remove();
                if (sources.isEmpty()) {
                    for (int i = 0; i < CAPACITY; i++) if (materials[i] != null && seen[i] != generation) remove(i);
                    if (scanOverflow && !free.isEmpty()) scanRequested = true;
                    statusDirty = true;
                }
                continue;
            }
            IAEStack<?> material = source.next();
            if (MaintainerEntry.supported(material)) {
                IAEStack<?> current = find(
                    lists.get(material.getStackType() == ITEM_STACK_TYPE ? items : fluids),
                    material);
                update(material, current != null && current.isCraftable());
            }
            processed++;
        }
        Iterator<Map.Entry<IAEStack<?>, IMEMonitor<?>>> changed = changes.entrySet()
            .iterator();
        int processedChanges = 0;
        while (changed.hasNext() && processedChanges < 128 && (processedChanges == 0 || System.nanoTime() < deadline)) {
            Map.Entry<IAEStack<?>, IMEMonitor<?>> next = changed.next();
            IAEStack<?> current = find(lists.get(next.getValue()), next.getKey());
            update(next.getKey(), current != null && current.isCraftable());
            changed.remove();
            processedChanges++;
        }
        for (int sent = 0; sent < 2 && (!updates.isEmpty() || statusDirty); sent++) sendPage();
    }

    private void sendPage() {
        NBTTagList entries = new NBTTagList();
        int characters = 0;
        Iterator<Integer> iterator = updates.iterator();
        while (iterator.hasNext() && entries.tagCount() < 128) {
            int index = iterator.next();
            if (characters + sizes[index] + 64 > 16384) break;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("index", index);
            if (tags[index] != null) entry.setTag("material", tags[index]);
            entries.appendTag(entry);
            characters += sizes[index] + 64;
            iterator.remove();
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("kind", 1);
        tag.setInteger("version", version);
        tag.setBoolean("complete", sources.isEmpty() && updates.isEmpty());
        tag.setTag("materials", entries);
        send.accept(tag);
        statusDirty = false;
    }

    void close() {
        closed = true;
        detach();
        grid = null;
        items = fluids = null;
        sources.clear();
        changes.clear();
        updates.clear();
        indices.clear();
        Arrays.fill(materials, null);
        Arrays.fill(tags, null);
    }
}
