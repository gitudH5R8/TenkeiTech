/*
 * SPDX-FileCopyrightText: 2026 Aedial
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 * Probe summary and timing adapted from AE2-PowerTools, be56992b0ab8e64cf254f603dad22cca2931aa6b.
 */
package com.tenkeitech.maintainer;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.GridAccessException;

/** Transient server work samples and a small read-only snapshot; independent of optional overlay APIs. */
final class MaintainerProbe {

    private int networkState;
    private long networkSince = -1, sampleCount, lastNanos, maxNanos, snapshotAt;
    private double averageNanos;
    private NBTTagCompound cached;

    void network(int state, long now) {
        if (networkSince < 0 || networkState != state) networkSince = now;
        networkState = state;
    }

    void record(long nanos) {
        lastNanos = Math.max(0, nanos);
        if (sampleCount < Long.MAX_VALUE) sampleCount++;
        averageNanos += (lastNanos - averageNanos) / sampleCount;
        maxNanos = Math.max(maxNanos, lastNanos);
    }

    NBTTagCompound snapshot(TileBetterLevelMaintainer tile) {
        long now = tile.getWorldObj()
            .getTotalWorldTime();
        // Multiple viewers share one bounded summary every half second.
        if (cached == null || now < snapshotAt || now - snapshotAt >= 10) {
            cached = new NBTTagCompound();
            snapshotAt = now;
            int running = 0, enabled = 0, errors = 0, blocked = 0, busyCpus = 0, cpus = 0;
            for (int i = 0; i < tile.slotCapacity(); i++) {
                MaintainerEntry entry = tile.entry(i);
                if (entry == null) continue;
                if (entry.enabled) enabled++;
                if (entry.link != null && !entry.terminal && !entry.link.isDone() && !entry.link.isCanceled())
                    running++;
                if (entry.state == MaintainerEntry.State.ERROR) errors++;
                if (entry.state == MaintainerEntry.State.OUTPUT) blocked++;
            }
            try {
                for (ICraftingCPU cpu : tile.getProxy()
                    .getCrafting()
                    .getCpus()) {
                    if (!TileBetterLevelMaintainer.permitsAutomaticRequests(cpu)) continue;
                    cpus++;
                    if (cpu.isBusy()) busyCpus++;
                }
            } catch (GridAccessException ignored) {}
            cached.setInteger("running", running);
            cached.setInteger("enabled", enabled);
            cached.setInteger("errors", errors);
            cached.setInteger("blocked", blocked);
            cached.setInteger("busyCpus", busyCpus);
            cached.setInteger("cpus", cpus);
            cached.setInteger("network", networkState);
            cached.setLong("elapsed", networkSince < 0 ? 0 : Math.max(0, now - networkSince));
            cached.setBoolean("sample", sampleCount > 0);
            cached.setLong("last", lastNanos);
            cached.setLong("average", (long) averageNanos);
            cached.setLong("max", maxNanos);
        }
        return (NBTTagCompound) cached.copy();
    }
}
