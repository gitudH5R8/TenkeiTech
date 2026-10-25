/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;

/** Bounded material identities and stable entry revisions make every edit a checked transaction. */
public final class ContainerBetterLevelMaintainer extends Container {

    public final TileBetterLevelMaintainer tile;
    private final EntityPlayer player;
    public long nonce;
    private final MaintainerCatalog catalog;
    private boolean ready;
    private int ticks, page, actions;
    private long actionTick = -1;

    public ContainerBetterLevelMaintainer(EntityPlayer player, TileBetterLevelMaintainer tile) {
        this.player = player;
        this.tile = tile;
        catalog = tile.getWorldObj().isRemote ? null
            : new MaintainerCatalog(tile, tag -> MaintainerNetwork.send((EntityPlayerMP) player, windowId, nonce, tag));
        if (!tile.getWorldObj().isRemote) do {
            nonce = ThreadLocalRandom.current()
                .nextLong();
        } while (nonce == 0);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.canUse(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slot) {
        return null;
    }

    void act(MaintainerNetwork.Action action) {
        long now = tile.getWorldObj()
            .getTotalWorldTime();
        if (actionTick != now) {
            actionTick = now;
            actions = 0;
        }
        if (++actions > 8) return;
        if (action.op == MaintainerNetwork.HELLO) {
            if (!ready && action.nonce == 0) {
                ready = true;
                page = 0;
            }
            return;
        }
        if (!ready || action.nonce != nonce) return;
        if (action.op == MaintainerNetwork.CATALOG) {
            catalog.resend(action.catalogVersion);
            return;
        }
        MaintainerEntry entry = tile.entry(action.slot);
        if (action.op == MaintainerNetwork.MATERIAL) {
            NBTTagCompound result = new NBTTagCompound();
            result.setInteger("kind", 4);
            result.setInteger("request", action.request);
            if (action.selection == -3 && action.slot >= 0
                && action.slot < TileBetterLevelMaintainer.CAPACITY
                && TileBetterLevelMaintainer.matches(entry, action.id, action.revision)) try {
                    IAEStack<?> material = MaintainerNetwork.materialFromBytes(action.material);
                    result.setBoolean("valid", true);
                    if (tile.active()) {
                        result.setBoolean("hasPattern", tile.hasPattern(material));
                        result.setBoolean("known", true);
                    }
                } catch (GridAccessException | IllegalArgumentException ignored) {}
            MaintainerNetwork.send((EntityPlayerMP) player, windowId, nonce, result);
            return;
        }
        boolean success = false;
        if (TileBetterLevelMaintainer.matches(entry, action.id, action.revision)) {
            if (action.op == MaintainerNetwork.RUN) {
                tile.runNow(action.slot, action.id, action.revision);
                success = entry != null;
            } else if (action.op == MaintainerNetwork.TOGGLE && entry != null) {
                success = tile.edit(
                    action.slot,
                    action.id,
                    action.revision,
                    entry.material,
                    entry.target,
                    entry.batch,
                    entry.seconds,
                    action.enabled);
            } else if (action.op == MaintainerNetwork.EDIT) {
                IAEStack<?> material = entry == null ? null : entry.material;
                boolean valid = action.selection == -1 || action.selection == -2;
                if (action.selection == -2) material = null;
                if (action.selection == -3) try {
                    material = MaintainerNetwork.materialFromBytes(action.material);
                    // Recheck at commit too: confirmation may beat the read-only material reply.
                    if (tile.active()) {
                        if (!tile.hasPattern(material)) material = null;
                        valid = true;
                    }
                } catch (GridAccessException | IllegalArgumentException ignored) {}
                if (valid) success = tile.edit(
                    action.slot,
                    action.id,
                    action.revision,
                    material,
                    action.target,
                    action.batch,
                    action.seconds,
                    action.enabled);
            }
        }
        NBTTagCompound result = new NBTTagCompound();
        result.setInteger("kind", 2);
        result.setInteger("op", action.op);
        result.setInteger("request", action.request);
        result.setBoolean("ok", success);
        result.setBoolean(
            "entryLimit",
            !success && action.op == MaintainerNetwork.EDIT && entry == null && tile.entryLimitReached());
        result.setInteger("slot", action.slot);
        MaintainerEntry current = tile.entry(action.slot);
        result.setTag("entry", current == null ? new NBTTagCompound() : current.snapshot(now));
        MaintainerNetwork.send((EntityPlayerMP) player, windowId, nonce, result);
    }

    @Override
    public void detectAndSendChanges() {
        if (!(player instanceof EntityPlayerMP serverPlayer) || !ready || !canInteractWith(player)) return;
        catalog.tick();
        if (++ticks % 2 != 0) return;
        int slots = tile.openSlots();
        if (page * 16 >= slots) page = 0;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("kind", 0);
        tag.setInteger("start", page * 16);
        tag.setInteger("slots", slots);
        tag.setBoolean("active", tile.active());
        NBTTagList list = new NBTTagList();
        int enabled = 0, running = 0, errors = 0, blocked = 0, busyCpus = 0, totalCpus = 0;
        long now = tile.getWorldObj()
            .getTotalWorldTime();
        for (int i = 0; i < tile.slotCapacity(); i++) {
            MaintainerEntry entry = tile.entry(i);
            if (entry != null) {
                if (entry.enabled) enabled++;
                if (entry.busy()) running++;
                if (entry.state == MaintainerEntry.State.ERROR) errors++;
                if (entry.state == MaintainerEntry.State.OUTPUT) blocked++;
            }
            if (i >= page * 16 && i < Math.min(slots, (page + 1) * 16))
                list.appendTag(entry == null ? new NBTTagCompound() : entry.snapshot(now));
        }
        try {
            for (ICraftingCPU cpu : tile.getProxy()
                .getCrafting()
                .getCpus()) {
                if (!TileBetterLevelMaintainer.permitsAutomaticRequests(cpu)) continue;
                totalCpus++;
                if (cpu.isBusy()) busyCpus++;
            }
        } catch (GridAccessException ignored) {}
        tag.setTag("entries", list);
        tag.setInteger("enabled", enabled);
        tag.setInteger("running", running);
        tag.setInteger("errors", errors);
        tag.setInteger("blocked", blocked);
        tag.setInteger("busyCpus", busyCpus);
        tag.setInteger("totalCpus", totalCpus);
        MaintainerNetwork.send(serverPlayer, windowId, nonce, tag);
        page++;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (catalog != null) catalog.close();
        super.onContainerClosed(player);
    }
}
