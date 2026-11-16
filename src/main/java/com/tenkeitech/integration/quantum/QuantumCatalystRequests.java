/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.quantum;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenkeitech.machine.MTESuperCraftingInputME;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.ContainerNull;
import appeng.crafting.CraftingLink;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import gregtech.GTLoggers;

/** Bounded, provider-owned real items and native requester links; never edits the parent order. */
public final class QuantumCatalystRequests {

    private static final String NBT_KEY = "quantumCatalystRequests";
    private static final int LIMIT = 64;
    private static final int MAX_DEPTH = 9;
    private static final long RETRY_TICKS = 200;

    private final MTESuperCraftingInputME host;
    private final List<Request> requests = new ArrayList<>();
    private IGrid attachedGrid;

    public QuantumCatalystRequests(MTESuperCraftingInputME host) {
        this.host = host;
    }

    public static boolean isChild(CraftingCPUCluster cpu) {
        return cpu instanceof QuantumCpuSource source && !source.tenkeitech$quantumOrder()
            .ancestry(cpu)
            .isEmpty();
    }

    private void dirty() {
        if (host.getBaseMetaTileEntity() != null) host.getBaseMetaTileEntity()
            .markDirty();
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left != null && right != null && left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    public boolean push(CraftingCPUCluster parent, ICraftingPatternDetails pattern, MEInventoryCrafting table,
        Operation<Boolean> original) {
        List<ItemStack> missing = host.getMissingAutomaticItemCatalysts(pattern);
        if (missing.isEmpty()) return original.call(host, pattern, table);
        ICraftingLink parentLink = parent.getLastCraftingLink();
        if (parentLink == null || parentLink.isCanceled() || parentLink.isDone() || !host.isActive()) return false;
        BaseActionSource source = originalSource(parent);
        if (source == null) return false;
        List<Request> needed = new ArrayList<>();
        for (ItemStack catalyst : missing) {
            Request request = find(parentLink.getCraftingID(), catalyst);
            if (request == null) {
                List<ItemStack> ancestors = ((QuantumCpuSource) parent).tenkeitech$quantumOrder()
                    .ancestry(parent);
                if (requests.size() >= LIMIT) return false;
                request = new Request(parentLink.getCraftingID(), catalyst);
                request.ancestry.addAll(ancestors);
                request.ancestry.add(request.item.copy());
                requests.add(request);
                dirty();
            }
            request.parentLink = parentLink;
            request.parentCpu = parent;
            if (request.abandoned || request.uncertain) return false;
            advance(request, parent, source);
            if (!request.held) return false;
            needed.add(request);
        }
        MEInventoryCrafting augmented = new MEInventoryCrafting(
            new ContainerNull(),
            table.getSizeInventory() + needed.size(),
            1);
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            IAEStack<?> input = table.getAEStackInSlot(slot);
            if (input != null) augmented.setInventorySlotContents(slot, input.copy());
        }
        for (int index = 0; index < needed.size(); index++) {
            augmented
                .setInventorySlotContents(table.getSizeInventory() + index, AEItemStack.create(needed.get(index).item));
        }
        if (!original.call(host, pattern, augmented)) return false;
        // pushPattern installs exactly these physical items transactionally before returning true.
        for (Request request : needed) {
            request.held = false;
            requests.remove(request);
        }
        dirty();
        for (Request request : needed) if (request.notice != null) notice(request, source, "installed");
        return true;
    }

    private Request find(String parent, ItemStack item) {
        for (Request request : requests) if (request.parent.equals(parent) && same(request.item, item)) return request;
        return null;
    }

    /** Uses AE's public source API; an offline player must never fall back to machine permissions. */
    private static BaseActionSource originalSource(ICraftingCPU cpu) {
        BaseActionSource source = cpu.getCurrentJobSource();
        if (source instanceof PlayerSource player)
            return onlinePlayerSource(player.player.getCommandSenderName(), player.via);
        if (source != null) return source;
        source = cpu.getActionSource();
        String playerName = cpu.getSourcePlayer();
        if (playerName == null || playerName.isEmpty()) return source;
        return onlinePlayerSource(
            playerName,
            source instanceof MachineSource machine ? machine.via
                : source instanceof PlayerSource player ? player.via : null);
    }

    private static PlayerSource onlinePlayerSource(String name, IActionHost via) {
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP player = server == null ? null
            : server.getConfigurationManager()
                .func_152612_a(name);
        return player == null ? null : new PlayerSource(player, via);
    }

    private void advance(Request request, CraftingCPUCluster parent, BaseActionSource source) {
        if (request.held) return;
        long now = CraftingCPUCluster.getServerTick();
        if (request.link != null) {
            if (!request.link.isCanceled() && !request.link.isDone()) return;
            request.link = null;
            request.retryAfter = now + RETRY_TICKS;
            dirty();
        }
        if (now < request.retryAfter) return;
        try {
            IGrid grid = parent.getGrid();
            if (grid == null || grid != host.getProxy()
                .getGrid()) return;
            // AE registers this cache under ICraftingGrid, not its implementation class.
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            if (!(crafting instanceof CraftingGridCache cache)) return;
            if (request.calculation == null) {
                IAEItemStack extracted = Platform.poweredExtraction(
                    host.getProxy()
                        .getEnergy(),
                    host.getProxy()
                        .getStorage()
                        .getItemInventory(),
                    AEItemStack.create(request.item),
                    source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    request.held = true;
                    dirty();
                    return;
                }
                if (request.ancestry.size() > MAX_DEPTH || repeatsAncestor(request)) {
                    request.retryAfter = now + RETRY_TICKS;
                    notice(request, source, "limit");
                    return;
                }
                request.calculationGrid = grid;
                request.calculation = cache.beginCraftingJob(
                    host.getBaseMetaTileEntity()
                        .getWorld(),
                    grid,
                    source,
                    AEItemStack.create(request.item),
                    null);
                // Replanning a waiting order is not a recovery; keep its last failure notice.
                if (request.notice == null) notice(request, source, "started");
                return;
            }
            if (!request.calculation.isDone()) return;
            ICraftingJob job = request.calculation.get();
            request.calculation = null;
            request.retryAfter = now + RETRY_TICKS;
            if (request.calculationGrid != grid) return;
            if (job == null || job.isSimulation()) {
                notice(request, source, "missing");
                return;
            }
            if (job.getByteTotal() < 0 || !(job.getOutput() instanceof IAEItemStack output)
                || output.getStackSize() != 1
                || !same(output.getItemStack(), request.item)) {
                notice(request, source, "failed");
                return;
            }
            CraftingCPUCluster target = ((QuantumCpu) parent).tenkeitech$childCpu();
            if (target == null || !target.isActive() || target.getGrid() != grid) {
                notice(request, source, "quantum_unavailable");
                return;
            }
            if (!job.supportsCPUCluster(target)) {
                notice(request, source, "failed");
                return;
            }
            // A throwing third-party submission has unknown commit state. Never blindly submit it twice.
            request.uncertain = true;
            dirty();
            List<ItemStack> previous = QuantumOrderContext.enter(request.ancestry);
            try {
                request.link = cache.submitJob(job, host, target, false, source);
            } finally {
                QuantumOrderContext.enter(previous);
            }
            request.uncertain = false;
            dirty();
            notice(request, source, request.link == null ? "quantum_unavailable" : "submitted");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            failedCalculation(request, now);
            notice(request, source, "failed");
        } catch (Exception failure) {
            failedCalculation(request, now);
            if (notice(request, source, request.uncertain ? "uncertain" : "failed"))
                GTLoggers.GT_FML_LOGGER.warn("Could not prepare an independent quantum catalyst order", failure);
        }
    }

    /** Transient status only: retries of the same failure do not spam chat or the log. */
    private static boolean notice(Request request, BaseActionSource source, String state) {
        if (state.equals(request.notice)) return false;
        request.notice = state;
        try {
            String name = request.item.getDisplayName();
            GTLoggers.GT_FML_LOGGER
                .info("[QuantumCatalyst] state={} catalyst={} parent={}", state, name, request.parent);
            if (source instanceof PlayerSource player) player.player
                .addChatMessage(new ChatComponentTranslation("tenkeitech.chat.automatic_catalyst." + state, name));
        } catch (RuntimeException failure) {
            // A notification failure must not turn an accepted item transaction into a rejected push.
            GTLoggers.GT_FML_LOGGER
                .warn("Could not send quantum catalyst status {} for {}", state, request.parent, failure);
        }
        return true;
    }

    private static boolean repeatsAncestor(Request request) {
        for (int index = 0; index + 1 < request.ancestry.size(); index++) {
            if (same(request.ancestry.get(index), request.item)) return true;
        }
        return false;
    }

    private static void failedCalculation(Request request, long now) {
        if (request.calculation != null) request.calculation.cancel(true);
        request.calculation = null;
        request.retryAfter = now + RETRY_TICKS;
    }

    public ImmutableSet<ICraftingLink> links() {
        ImmutableSet.Builder<ICraftingLink> links = ImmutableSet.builder();
        for (Request request : requests) if (request.link != null) links.add(request.link);
        return links.build();
    }

    public IAEStack<?> receive(ICraftingLink link, IAEStack<?> items, Actionable mode) {
        if (!(items instanceof IAEItemStack item) || items.getStackSize() <= 0 || !host.isActive()) return items;
        for (Request request : requests) {
            if (request.link != link || request.held
                || request.abandoned
                || request.uncertain
                || !same(request.item, item.getItemStack())) continue;
            if (mode == Actionable.MODULATE) {
                request.held = true;
                dirty();
            }
            return items.getStackSize() == 1 ? null
                : items.copy()
                    .setStackSize(items.getStackSize() - 1);
        }
        return items;
    }

    public void tick() {
        if (requests.isEmpty()) return;
        long now = CraftingCPUCluster.getServerTick();
        // A live link notices cancel immediately, even while the provider is offline.
        for (Request request : requests)
            if (request.parentLink != null && (request.parentLink.isCanceled() || request.parentLink.isDone()))
                abandon(request);
        if (now % 20 != 0) return;
        try {
            IGrid grid = host.getProxy()
                .getGrid();
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            if (!(crafting instanceof CraftingGridCache cache)) return;
            if (attachedGrid != grid) {
                for (Request request : requests) if (request.link instanceof CraftingLink link) cache.addLink(link);
                if (attachedGrid != null) {
                    for (Request request : requests) {
                        request.parentLink = null;
                        request.parentCpu = null;
                        request.missingSince = now;
                    }
                }
                attachedGrid = grid;
            }
            for (Request request : requests) if (request.parentCpu != null && !cache.hasCpu(request.parentCpu)) {
                request.parentCpu = null;
                request.parentLink = null;
                request.missingSince = now;
            }
            // Live links carry cancel/done notifications. Only restored or moved requests need a CPU scan.
            if (requests.stream()
                .anyMatch(request -> !request.abandoned && request.parentLink == null)) {
                Map<String, CraftingCPUCluster> parents = new java.util.HashMap<>();
                for (var cpu : cache.getCpus()) if (cpu instanceof CraftingCPUCluster cluster) {
                    ICraftingLink link = cluster.getLastCraftingLink();
                    if (link != null) parents.put(link.getCraftingID(), cluster);
                }
                for (Request request : requests) if (request.parentLink == null) {
                    request.parentCpu = parents.get(request.parent);
                    request.parentLink = request.parentCpu == null ? null : request.parentCpu.getLastCraftingLink();
                    if (request.missingSince < 0) request.missingSince = now;
                    if (request.parentLink == null && now - request.missingSince >= RETRY_TICKS) abandon(request);
                }
            }
            Iterator<Request> iterator = requests.iterator();
            while (iterator.hasNext()) {
                Request request = iterator.next();
                if (request.parentLink != null && (request.parentLink.isCanceled() || request.parentLink.isDone()))
                    abandon(request);
                if (!request.abandoned) continue;
                refund(request);
                if (!request.held) {
                    iterator.remove();
                    dirty();
                }
            }
        } catch (GridAccessException ignored) {
            attachedGrid = null;
        }
    }

    private void abandon(Request request) {
        if (request.abandoned) return;
        request.abandoned = true;
        if (request.calculation != null) request.calculation.cancel(true);
        request.calculation = null;
        if (request.link != null) request.link.cancel();
        dirty();
    }

    private void refund(Request request) throws GridAccessException {
        if (!request.held) return;
        IAEItemStack remainder = Platform.poweredInsert(
            host.getProxy()
                .getEnergy(),
            host.getProxy()
                .getStorage()
                .getItemInventory(),
            AEItemStack.create(request.item),
            new MachineSource(host));
        if (remainder == null || remainder.getStackSize() == 0) {
            request.held = false;
            dirty();
        }
    }

    /** Caller drops only returned real items when the block is destroyed. */
    public List<ItemStack> destroy() {
        List<ItemStack> remaining = new ArrayList<>();
        for (Request request : requests) {
            abandon(request);
            try {
                refund(request);
            } catch (GridAccessException ignored) {
                // Unavailable network: preserve the physical item as a block drop.
            }
            if (request.held) remaining.add(request.item.copy());
        }
        requests.clear();
        return remaining;
    }

    public void write(NBTTagCompound tag) {
        NBTTagList saved = new NBTTagList();
        for (Request request : requests) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("parent", request.parent);
            entry.setTag("item", request.item.writeToNBT(new NBTTagCompound()));
            entry.setBoolean("held", request.held);
            entry.setBoolean("abandoned", request.abandoned);
            entry.setBoolean("uncertain", request.uncertain);
            if (request.link != null) {
                NBTTagCompound link = new NBTTagCompound();
                request.link.writeToNBT(link);
                entry.setTag("link", link);
            }
            NBTTagList ancestry = new NBTTagList();
            for (ItemStack item : request.ancestry) ancestry.appendTag(item.writeToNBT(new NBTTagCompound()));
            entry.setTag("ancestry", ancestry);
            saved.appendTag(entry);
        }
        tag.setTag(NBT_KEY, saved);
    }

    public void read(NBTTagCompound tag) {
        for (Request request : requests) {
            if (request.calculation != null) request.calculation.cancel(true);
        }
        requests.clear();
        attachedGrid = null;
        NBTTagList saved = tag.getTagList(NBT_KEY, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < Math.min(saved.tagCount(), LIMIT); index++) {
            NBTTagCompound entry = saved.getCompoundTagAt(index);
            ItemStack item = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            String parent = entry.getString("parent");
            if (item == null || item.stackSize != 1 || parent.isEmpty() || find(parent, item) != null) continue;
            Request request = new Request(parent, item);
            request.held = entry.getBoolean("held");
            request.abandoned = entry.getBoolean("abandoned");
            request.uncertain = entry.getBoolean("uncertain");
            NBTTagList ancestry = entry.getTagList("ancestry", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < Math.min(ancestry.tagCount(), MAX_DEPTH + 1); i++) {
                ItemStack ancestor = ItemStack.loadItemStackFromNBT(ancestry.getCompoundTagAt(i));
                if (ancestor != null) {
                    ancestor.stackSize = 1;
                    request.ancestry.add(ancestor);
                }
            }
            if (request.ancestry.isEmpty()) request.ancestry.add(request.item.copy());
            if (entry.hasKey("link", Constants.NBT.TAG_COMPOUND)) {
                request.link = AEApi.instance()
                    .storage()
                    .loadCraftingLink(entry.getCompoundTag("link"), host);
            }
            requests.add(request);
        }
    }

    private static final class Request {

        final String parent;
        final ItemStack item;
        final List<ItemStack> ancestry = new ArrayList<>();
        ICraftingLink parentLink;
        CraftingCPUCluster parentCpu;
        ICraftingLink link;
        Future<ICraftingJob> calculation;
        IGrid calculationGrid;
        long retryAfter;
        long missingSince = -1;
        boolean held;
        boolean abandoned;
        boolean uncertain;
        String notice;

        Request(String parent, ItemStack item) {
            this.parent = parent;
            this.item = item.copy();
            this.item.stackSize = 1;
        }
    }
}
