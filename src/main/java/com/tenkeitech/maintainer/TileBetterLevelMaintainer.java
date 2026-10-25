/*
 * SPDX-FileCopyrightText: 2026 Aedial
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 * Adapted from AE2-PowerTools, be56992b0ab8e64cf254f603dad22cca2931aa6b.
 */
package com.tenkeitech.maintainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableSet;
import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

/** Owns targets and requester links; all real crafting inventory remains with AE and its providers. */
public final class TileBetterLevelMaintainer extends TileEntity implements IGridProxyable, ICraftingRequester {

    public static final int CAPACITY = Config.MAX_MAINTAINER_ENTRIES;
    public static final String ITEM_SETTINGS = "tenkeitechMaintainerSettings";
    private static final Comparator<ICraftingCPU> PREFERRED_CPU_ORDER = Comparator
        .comparingInt((ICraftingCPU cpu) -> cpu.getCraftingAllowMode() == CraftingAllow.ONLY_NONPLAYER ? 0 : 1)
        .thenComparingInt(ICraftingCPU::getCoProcessors)
        .thenComparingLong(ICraftingCPU::getAvailableStorage)
        .thenComparing(ICraftingCPU::getName);
    private MaintainerEntry[] entries = new MaintainerEntry[Config.maintainerMaxEntries];
    private final List<MaintainerEntry> operations = new ArrayList<>();
    private final MaintainerProbe probe = new MaintainerProbe();
    private AENetworkProxy proxy;
    private final MachineSource source = new MachineSource(this);
    private String owner = "";
    private long nextId = 1;
    private boolean initialized;
    private boolean clientActive;
    private boolean lastActive;
    private boolean readOnly;
    private NBTTagCompound unknownSchema;
    private NBTTagCompound retainedItemSettings;
    private long settleUntil;
    private IGrid connectedGrid;
    private int cursor;

    public MaintainerEntry entry(int slot) {
        return slot >= 0 && slot < entries.length ? entries[slot] : null;
    }

    public int slotCapacity() {
        return entries.length;
    }

    static boolean permitsAutomaticRequests(ICraftingCPU cpu) {
        CraftingAllow mode = cpu.getCraftingAllowMode();
        return mode == CraftingAllow.ONLY_NONPLAYER || mode == CraftingAllow.ALLOW_ALL;
    }

    private void ensureCapacity(int minimum) {
        if (minimum > entries.length)
            entries = Arrays.copyOf(entries, Math.min(CAPACITY, Math.max(minimum, entries.length * 2)));
    }

    public boolean entryLimitReached() {
        int count = 0;
        for (MaintainerEntry entry : entries) if (entry != null && ++count >= Config.maintainerMaxEntries) return true;
        return false;
    }

    public int openSlots() {
        ensureCapacity(Config.maintainerMaxEntries);
        int last = -1;
        for (int i = 0; i < entries.length; i++) if (entries[i] != null) last = i;
        return Math.max(last + 1, Math.min(Config.maintainerMaxEntries, Math.max(18, ((last + 1) / 3 + 1) * 3)));
    }

    public void placed(EntityPlayer player) {
        owner = player.getUniqueID()
            .toString();
        getProxy().setOwner(player);
        markDirty();
    }

    public NBTTagCompound itemSettings() {
        if (retainedItemSettings != null) return (NBTTagCompound) retainedItemSettings.copy();
        if (unknownSchema != null) return (NBTTagCompound) unknownSchema.copy();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("tenkeitechMaintainerSchema", 1);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < entries.length; i++) if (entries[i] != null) {
            NBTTagCompound saved = entries[i].settings();
            saved.setInteger("slot", i);
            list.appendTag(saved);
        }
        tag.setTag("entries", list);
        return tag;
    }

    public void restoreItemSettings(NBTTagCompound tag) {
        if (worldObj == null || worldObj.isRemote || initialized || readOnly) return;
        if (tag.getInteger("tenkeitechMaintainerSchema") != 1) {
            retainedItemSettings = (NBTTagCompound) tag.copy();
            readOnly = true;
            markDirty();
            return;
        }
        NBTTagList list = tag.getTagList("entries", 10);
        long now = worldObj.getTotalWorldTime();
        for (int i = 0; i < Math.min(list.tagCount(), CAPACITY); i++) {
            NBTTagCompound saved = list.getCompoundTagAt(i);
            int slot = saved.getInteger("slot");
            if (slot < 0 || slot >= CAPACITY || entry(slot) != null) continue;
            try {
                IAEStack<?> material = IAEStack.fromNBTGeneric(saved.getCompoundTag("material"));
                if (!MaintainerEntry.supported(material)) continue;
                boolean duplicate = false;
                for (MaintainerEntry entry : entries) if (entry != null && entry.material.isSameType(material)) {
                    duplicate = true;
                    break;
                }
                long target = saved.getLong("target"), batch = saved.getLong("batch");
                int seconds = saved.getInteger("seconds");
                if (duplicate || target < 0 || batch < 1 || seconds < 1 || seconds > 86400) continue;
                MaintainerEntry entry = new MaintainerEntry();
                entry.id = nextId++;
                entry.material = material.setStackSize(1);
                entry.target = target;
                entry.batch = batch;
                entry.seconds = seconds;
                entry.enabled = saved.getBoolean("enabled");
                entry.nextCheck = now + seconds * 20L;
                entry.uncertain = saved.getBoolean("uncertain");
                if (entry.uncertain) entry.uncertainRequest = (NBTTagCompound) saved.getCompoundTag("uncertainRequest")
                    .copy();
                entry.state = entry.uncertain ? MaintainerEntry.State.ERROR
                    : entry.enabled ? MaintainerEntry.State.IDLE : MaintainerEntry.State.DISABLED;
                entry.error = entry.uncertain ? "uncertain" : "";
                ensureCapacity(slot + 1);
                entries[slot] = entry;
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.warn("Could not restore carried maintainer setting in slot {}", slot, exception);
            }
        }
        markDirty();
    }

    public boolean canUse(EntityPlayer player) {
        if (player == null || player.worldObj != worldObj
            || isInvalid()
            || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this
            || player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) > 64) return false;
        if (worldObj.isRemote) return true;
        if (!getProxy().isActive()) return owner.equals(
            player.getUniqueID()
                .toString());
        try {
            return getProxy().getSecurity()
                .hasPermission(player, SecurityPermissions.BUILD)
                && getProxy().getSecurity()
                    .hasPermission(player, SecurityPermissions.CRAFT);
        } catch (GridAccessException exception) {
            return false;
        }
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        ensureCapacity(Config.maintainerMaxEntries);
        if (!initialized) {
            initialized = true;
            getProxy().onReady();
        }
        long now = worldObj.getTotalWorldTime();
        boolean active = getProxy().isActive();
        if (active != lastActive) {
            lastActive = active;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        IGrid grid = null;
        try {
            if (active && !getProxy().getPath()
                .isNetworkBooting()) grid = getProxy().getGrid();
        } catch (GridAccessException ignored) {}
        if (grid != connectedGrid) {
            cancelCalculations();
            connectedGrid = grid;
            settleUntil = now + 100;
        }
        boolean waiting = grid == null || now < settleUntil;
        probe.network(!active ? 0 : waiting ? 1 : 2, now);
        if (waiting || readOnly) return;

        long workStartedAt = System.nanoTime();
        boolean worked = !operations.isEmpty();
        try {
            for (int i = operations.size() - 1; i >= 0; i--) {
                MaintainerEntry entry = operations.get(i);
                if (entry.link != null && (entry.terminal || entry.link.isDone() || entry.link.isCanceled()))
                    finish(entry, now);
                if (entry.future != null || entry.plan != null) process(entry, now);
                if (!entry.busy()) operations.remove(i);
            }
            // A fixed cursor bounds storage lookups independently of the configured entry count.
            for (int n = 0; n < 16; n++) {
                MaintainerEntry entry = entries[cursor];
                cursor = (cursor + 1) % entries.length;
                if (entry == null || entry.material == null) continue;
                if (now - entry.stockChecked >= 20) {
                    worked = true;
                    try {
                        entry.current = stored(entry.material);
                    } catch (GridAccessException | RuntimeException ignored) {}
                    entry.stockChecked = now;
                }
                if (!entry.enabled || entry.busy() || entry.uncertain) continue;
                if (entry.nextCheck <= now) {
                    worked = true;
                    check(entry, now);
                }
            }
        } finally {
            if (worked) probe.record(System.nanoTime() - workStartedAt);
        }
    }

    public NBTTagCompound probeSnapshot() {
        return probe.snapshot(this);
    }

    private void check(MaintainerEntry entry, long now) {
        try {
            IAEStack<?> available = networkStack(entry.material);
            entry.current = storedCount(available);
            ICraftingGrid crafting = getProxy().getCrafting();
            if (available == null || !available.isCraftable()) {
                fail(entry, "no_recipe", now);
                return;
            }
            if (entry.current >= entry.target) {
                entry.state = MaintainerEntry.State.IDLE;
                entry.error = "";
                entry.nextCheck = now + entry.seconds * 20L;
                markDirty();
                return;
            }
            if (activeCount() >= Config.maintainerActiveJobs) {
                entry.state = MaintainerEntry.State.SCHEDULED;
                entry.nextCheck = now + 20;
                return;
            }
            boolean free = false;
            for (ICraftingCPU cpu : crafting.getCpus()) {
                if (!cpu.isBusy() && permitsAutomaticRequests(cpu)) free = true;
            }
            if (!free) {
                entry.state = MaintainerEntry.State.CPU;
                entry.error = "";
                entry.nextCheck = now + 20;
                return;
            }
            for (MaintainerEntry other : operations) if (other.future != null) return;
            IGrid grid = getProxy().getGrid();
            if (!MaintainerCalculations.available(grid)) {
                entry.state = MaintainerEntry.State.SCHEDULED;
                entry.nextCheck = now + 20;
                return;
            }
            entry.future = crafting.beginCraftingJob(
                worldObj,
                grid,
                source,
                entry.material.copy()
                    .setStackSize(entry.batch),
                null);
            MaintainerCalculations.track(grid, entry.future);
            if (!operations.contains(entry)) operations.add(entry);
            entry.calculationGrid = grid;
            entry.calculationStarted = now;
            entry.state = MaintainerEntry.State.CALCULATING;
            entry.error = "";
        } catch (Exception exception) {
            fail(entry, "calculation", now);
            TenkeiTech.LOG.warn("Maintainer could not plan a crafting request", exception);
        }
    }

    private void process(MaintainerEntry entry, long now) {
        boolean submitting = false;
        try {
            if (entry.plan != null && now - entry.calculationStarted > Config.maintainerCalculationTimeout + 200L) {
                fail(entry, "timeout", now);
                return;
            }
            if (entry.calculationGrid != getProxy().getGrid()) {
                discardCalculation(entry);
                entry.nextCheck = now + 100;
                return;
            }
            if (entry.future != null) {
                if (!entry.future.isDone()) {
                    if (now - entry.calculationStarted > Config.maintainerCalculationTimeout)
                        fail(entry, "timeout", now);
                    return;
                }
                entry.plan = entry.future.get();
                entry.future = null;
                if (entry.plan == null || entry.plan.isSimulation()) {
                    fail(entry, "resources", now);
                    return;
                }
            }
            if (entry.plan == null || now < entry.retryAt) return;
            entry.current = stored(entry.material);
            if (entry.current >= entry.target) {
                discardCalculation(entry);
                entry.state = MaintainerEntry.State.IDLE;
                entry.nextCheck = now + entry.seconds * 20L;
                markDirty();
                return;
            }
            ICraftingGrid crafting = getProxy().getCrafting();
            boolean preferNonPlayerCpus = Config.maintainerPreferNonPlayerCpus;
            ICraftingCPU selected = null;
            boolean fitting = false;
            boolean free = false;
            for (ICraftingCPU cpu : crafting.getCpus()) {
                if (!permitsAutomaticRequests(cpu)) continue;
                if (cpu.getAvailableStorage() >= entry.plan.getByteTotal()) {
                    fitting = true;
                    if (!cpu.isBusy()) {
                        free = true;
                        if (preferNonPlayerCpus && entry.plan.supportsCPUCluster(cpu)
                            && (selected == null || PREFERRED_CPU_ORDER.compare(cpu, selected) < 0)) selected = cpu;
                    }
                }
            }
            if (!fitting) {
                fail(entry, "cpu_size", now);
                return;
            }
            if (!free) {
                entry.state = MaintainerEntry.State.CPU;
                entry.retryAt = now + 20;
                return;
            }
            if (preferNonPlayerCpus && selected == null) {
                fail(entry, "submission", now);
                return;
            }
            // getCpus exposes active CPUs; permission, capacity and compatibility are checked above.
            // A null target keeps AE's native selection when the preference is disabled.
            submitting = true;
            entry.link = crafting.submitJob(entry.plan, this, selected, false, source);
            submitting = false;
            entry.plan = null;
            entry.calculationGrid = null;
            if (entry.link == null) fail(entry, "submission", now);
            else {
                entry.terminal = false;
                entry.state = MaintainerEntry.State.RUNNING;
                entry.error = "";
                markDirty();
            }
        } catch (Exception exception) {
            if (submitting) {
                quarantine(
                    entry,
                    entry.material.copy()
                        .setStackSize(entry.batch),
                    "submission",
                    exception);
                discardCalculation(entry);
                return;
            }
            fail(entry, "calculation", now);
            TenkeiTech.LOG.warn("Maintainer request failed", exception);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStack<?> networkStack(IAEStack<?> material) throws GridAccessException {
        IMEMonitor inventory = getProxy().getStorage()
            .getMEMonitor(material.getStackType());
        return inventory == null ? null
            : (IAEStack<?>) inventory.getStorageList()
                .findPrecise(material);
    }

    private static long storedCount(IAEStack<?> available) {
        return available == null ? 0 : Math.max(0, available.getStackSize());
    }

    private long stored(IAEStack<?> material) throws GridAccessException {
        return storedCount(networkStack(material));
    }

    boolean hasPattern(IAEStack<?> material) throws GridAccessException {
        // This AE version's getCraftingFor accepts IAEStack but only handles item stacks.
        // The native monitor exposes craftability for both items and zero-stock fluids.
        IAEStack<?> available = networkStack(material);
        return available != null && available.isCraftable();
    }

    private int activeCount() {
        int count = 0;
        for (MaintainerEntry entry : operations) if (entry.busy()) count++;
        return count;
    }

    private void fail(MaintainerEntry entry, String error, long now) {
        discardCalculation(entry);
        entry.error = error;
        entry.state = MaintainerEntry.State.ERROR;
        entry.nextCheck = now + entry.seconds * 20L;
        markDirty();
    }

    private void finish(MaintainerEntry entry, long now) {
        boolean cancelled = entry.link.isCanceled();
        entry.link = null;
        entry.terminal = false;
        entry.state = entry.enabled ? MaintainerEntry.State.IDLE : MaintainerEntry.State.DISABLED;
        entry.error = cancelled ? "cancelled" : "";
        entry.nextCheck = now + entry.seconds * 20L;
        if (entry.uncertain) {
            entry.state = MaintainerEntry.State.ERROR;
            entry.error = "uncertain";
        }
        markDirty();
    }

    public boolean edit(int slot, long expectedId, int revision, IAEStack<?> material, long target, long batch,
        int seconds, boolean enabled) {
        if (readOnly || slot < 0 || slot >= CAPACITY || target < 0 || batch < 1 || seconds < 1 || seconds > 86400)
            return false;
        MaintainerEntry old = entry(slot);
        if (!matches(old, expectedId, revision) || old != null && (old.uncertain || old.revision == Integer.MAX_VALUE))
            return false;
        if (material != null && !MaintainerEntry.supported(material)) return false;
        if (old == null && material != null && entryLimitReached()) return false;
        for (int i = 0; i < entries.length; i++) {
            MaintainerEntry other = entries[i];
            if (i != slot && other != null
                && other.material != null
                && material != null
                && other.material.isSameType(material)) return false;
        }
        boolean changed = old != null && (material == null || !old.material.isSameType(material) || !enabled);
        if (changed) cancel(old);
        if (material == null) {
            if (slot < entries.length) entries[slot] = null;
        } else {
            MaintainerEntry entry = old == null ? new MaintainerEntry() : old;
            if (old == null) {
                if (nextId == Long.MAX_VALUE) return false;
                entry.id = nextId++;
            }
            // A calculated plan belongs to the old numerical configuration, never the edited one.
            discardCalculation(entry);
            entry.material = material.copy()
                .setStackSize(1);
            entry.target = target;
            entry.batch = batch;
            entry.seconds = seconds;
            entry.enabled = enabled;
            entry.revision++;
            entry.nextCheck = worldObj.getTotalWorldTime() + 20;
            entry.error = "";
            entry.state = !enabled ? MaintainerEntry.State.DISABLED
                : entry.link != null ? MaintainerEntry.State.RUNNING : MaintainerEntry.State.IDLE;
            if (enabled && entry.link == null && getProxy().isActive()) try {
                if (!hasPattern(entry.material)) {
                    entry.state = MaintainerEntry.State.ERROR;
                    entry.error = "no_recipe";
                    entry.nextCheck = worldObj.getTotalWorldTime() + entry.seconds * 20L;
                }
            } catch (GridAccessException | RuntimeException ignored) {}
            ensureCapacity(slot + 1);
            entries[slot] = entry;
        }
        markDirty();
        return true;
    }

    static boolean matches(MaintainerEntry entry, long id, int revision) {
        return entry == null ? id == 0 && revision == 0 : entry.id == id && entry.revision == revision;
    }

    public void runNow(int slot, long id, int revision) {
        MaintainerEntry entry = entry(slot);
        if (!readOnly && entry != null
            && matches(entry, id, revision)
            && entry.enabled
            && !entry.busy()
            && !entry.uncertain) {
            entry.nextCheck = 0;
        }
    }

    private void cancel(MaintainerEntry entry) {
        discardCalculation(entry);
        if (entry.link != null) entry.link.cancel();
        entry.link = null;
        entry.terminal = false;
    }

    private void discardCalculation(MaintainerEntry entry) {
        if (entry.future != null) entry.future.cancel(true);
        entry.future = null;
        entry.plan = null;
        entry.calculationGrid = null;
    }

    private void cancelCalculations() {
        for (int i = operations.size() - 1; i >= 0; i--) {
            MaintainerEntry entry = operations.get(i);
            discardCalculation(entry);
            if (!entry.busy()) operations.remove(i);
        }
    }

    public void destroyed() {
        for (MaintainerEntry entry : entries) if (entry != null) {
            if (entry.uncertain) TenkeiTech.LOG
                .error("Destroyed maintainer has an unresolved quarantine record at {},{},{}", xCoord, yCoord, zCoord);
            cancel(entry);
        }
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        ImmutableSet.Builder<ICraftingLink> links = ImmutableSet.builder();
        for (MaintainerEntry entry : operations) if (entry.link != null) links.add(entry.link);
        return links.build();
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack<?> injectCraftedItems(ICraftingLink link, IAEStack<?> items, Actionable mode) {
        if (items == null) return null;
        MaintainerEntry entry = null;
        for (MaintainerEntry candidate : operations) if (candidate.link == link) entry = candidate;
        if (entry == null || entry.uncertain || !getProxy().isActive() || !MaintainerEntry.supported(items))
            return items;
        try {
            IMEMonitor inventory = getProxy().getStorage()
                .getMEMonitor(items.getStackType());
            if (inventory == null) return items;
            IAEStack<?> remainder = Platform
                .poweredInsert(getProxy().getEnergy(), inventory, items.copy(), source, mode);
            if (remainder != null && (!items.isSameType(remainder) || remainder.getStackSize() < 0
                || remainder.getStackSize() > items.getStackSize()))
                throw new IllegalStateException("Invalid ME remainder");
            if (mode == Actionable.MODULATE) {
                MaintainerEntry.State state = remainder != null && remainder.getStackSize() > 0
                    ? MaintainerEntry.State.OUTPUT
                    : MaintainerEntry.State.RUNNING;
                if (entry.state != state) {
                    entry.state = state;
                    markDirty();
                }
            }
            return remainder;
        } catch (GridAccessException exception) {
            return items;
        } catch (RuntimeException exception) {
            if (mode == Actionable.MODULATE) {
                quarantine(entry, items, "output", exception);
                // Acknowledge this ambiguous delivery to the CPU so cancellation cannot replay it.
                // This is an audit record, not a claim that any particular quantity reached storage.
                return null;
            }
            return items;
        }
    }

    private void quarantine(MaintainerEntry entry, IAEStack<?> request, String phase, Exception exception) {
        entry.uncertain = true;
        entry.uncertainRequest = request.toNBTGeneric();
        entry.uncertainRequest.setString("tenkeitechPhase", phase);
        entry.error = "uncertain";
        entry.state = MaintainerEntry.State.ERROR;
        markDirty();
        try {
            MaintainerQuarantine.record(this, entry);
        } catch (RuntimeException ledgerFailure) {
            TenkeiTech.LOG.error(
                "Could not duplicate maintainer quarantine credentials into world ledger; tile retains them",
                ledgerFailure);
        }
        TenkeiTech.LOG.error("Maintainer {} commit is unknown; automatic repetition disabled", phase, exception);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        for (MaintainerEntry entry : operations) if (entry.link == link) entry.terminal = true;
        markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (unknownSchema != null) {
            for (Object key : unknownSchema.func_150296_c()) {
                String name = (String) key;
                if (!name.equals("x") && !name.equals("y") && !name.equals("z") && !name.equals("id")) tag.setTag(
                    name,
                    unknownSchema.getTag(name)
                        .copy());
            }
            return;
        }
        getProxy().writeToNBT(tag);
        tag.setInteger("tenkeitechMaintainerSchema", 1);
        tag.setString("owner", owner);
        if (retainedItemSettings != null) tag.setTag(ITEM_SETTINGS, retainedItemSettings.copy());
        tag.setLong("nextId", nextId);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < entries.length; i++) if (entries[i] != null) {
            NBTTagCompound saved = entries[i].save();
            saved.setInteger("slot", i);
            list.appendTag(saved);
        }
        tag.setTag("entries", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        getProxy().readFromNBT(tag);
        readOnly = tag.getInteger("tenkeitechMaintainerSchema") != 1;
        owner = tag.getString("owner");
        if (readOnly) {
            unknownSchema = (NBTTagCompound) tag.copy();
            TenkeiTech.LOG.warn("Unknown TenkeiTech maintainer schema; original data retained read-only");
            return;
        }
        if (tag.hasKey(ITEM_SETTINGS, 10)) {
            retainedItemSettings = (NBTTagCompound) tag.getCompoundTag(ITEM_SETTINGS)
                .copy();
            readOnly = true;
            return;
        }
        nextId = Math.max(1, tag.getLong("nextId"));
        NBTTagList list = tag.getTagList("entries", 10);
        Set<Long> ids = new HashSet<>();
        Set<String> links = new HashSet<>();
        for (int i = 0; i < Math.min(list.tagCount(), CAPACITY); i++) {
            NBTTagCompound saved = list.getCompoundTagAt(i);
            int slot = saved.getInteger("slot");
            long id = saved.getLong("id");
            if (slot < 0 || slot >= CAPACITY || entry(slot) != null || id <= 0 || !ids.add(id)) continue;
            try {
                MaintainerEntry entry = new MaintainerEntry();
                entry.id = id;
                entry.revision = Math.max(0, saved.getInteger("revision"));
                entry.material = IAEStack.fromNBTGeneric(saved.getCompoundTag("material"));
                if (!MaintainerEntry.supported(entry.material)) continue;
                entry.material.setStackSize(1);
                entry.target = Math.max(0, saved.getLong("target"));
                entry.batch = Math.max(1, saved.getLong("batch"));
                entry.seconds = Math.max(1, Math.min(86400, saved.getInteger("seconds")));
                entry.enabled = saved.getBoolean("enabled");
                entry.nextCheck = Math.max(0, saved.getLong("nextCheck"));
                entry.uncertain = saved.getBoolean("uncertain");
                entry.uncertainRequest = saved.getCompoundTag("uncertainRequest");
                if (saved.hasKey("link", 10)) {
                    ICraftingLink link = AEApi.instance()
                        .storage()
                        .loadCraftingLink(saved.getCompoundTag("link"), this);
                    if (link != null && links.add(link.getCraftingID())) {
                        entry.link = link;
                        operations.add(entry);
                    }
                }
                entry.state = entry.uncertain ? MaintainerEntry.State.ERROR
                    : entry.link != null ? MaintainerEntry.State.RUNNING
                        : entry.enabled ? MaintainerEntry.State.IDLE : MaintainerEntry.State.DISABLED;
                entry.error = entry.uncertain ? "uncertain" : "";
                ensureCapacity(slot + 1);
                entries[slot] = entry;
                nextId = Math.max(nextId, id == Long.MAX_VALUE ? Long.MAX_VALUE : id + 1);
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.warn("Could not restore maintainer entry {}", slot, exception);
            }
        }
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "proxy", new ItemStack(MaintainerRegistration.BLOCK), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.range(ForgeDirection.DOWN, ForgeDirection.EAST));
            proxy.setIdlePowerUsage(1);
        }
        return proxy;
    }

    public boolean active() {
        return worldObj != null && worldObj.isRemote ? clientActive : getProxy().isActive();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection direction) {
        return AECableType.SMART;
    }

    @Override
    public void gridChanged() {
        connectedGrid = null;
    }

    @Override
    public void securityBreak() {
        if (worldObj != null && !worldObj.isRemote) worldObj.func_147480_a(xCoord, yCoord, zCoord, true);
    }

    @Override
    public void onChunkUnload() {
        cancelCalculations();
        initialized = false;
        getProxy().onChunkUnload();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        cancelCalculations();
        initialized = false;
        getProxy().invalidate();
        super.invalidate();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("active", active());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        clientActive = packet.func_148857_g()
            .getBoolean("active");
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
