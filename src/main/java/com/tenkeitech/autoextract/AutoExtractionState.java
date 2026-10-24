/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.tenkeitech.ModItems;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.resonating.ResonatingInterfaceHosts;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.MEPassThrough;
import appeng.util.Platform;

/** One recipe's promises and already extracted physical output, owned by one interface. */
public final class AutoExtractionState {

    private static final String KEY = "tenkeitechAutoExtraction";
    private static final int MAX_OUTPUTS = 127;
    private static final int ITEM_TRANSFER = 4096;
    private static final int FLUID_TRANSFER = 1_000_000;
    private static final int LOCAL_BUDGET = 8;
    private static final AtomicLong TASK_IDS = new AtomicLong();

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> returning = new ArrayList<>();
    private IInterfaceHost host;
    private ItemStack pattern;
    private Plan pending;
    private NBTBase invalid;
    private NBTTagCompound inFlight;
    private boolean quarantined;
    private boolean loggedDrop;
    private int cursor;
    private int returnDepth;
    private long lastTick = Long.MIN_VALUE;
    private long lastWarning = Long.MIN_VALUE;
    private AutoExtractionView view;
    private long taskId;

    public void locate(IInterfaceHost host) {
        this.host = host;
    }

    public boolean supported() {
        return host != null && ResonatingInterfaceHosts.supported(host);
    }

    public static boolean card(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() == ModItems.AUTO_EXTRACTION_CARD;
    }

    public boolean present() {
        if (!supported()) return false;
        for (ItemStack stack : host.getInterfaceDuality()
            .getUpgrades()) if (card(stack)) return true;
        return false;
    }

    public boolean installed() {
        return extractionDirection() != null;
    }

    private ForgeDirection extractionDirection() {
        if (!supported()) return null;
        int count = 0;
        ForgeDirection direction = null;
        for (ItemStack stack : host.getInterfaceDuality()
            .getUpgrades()) {
            if (card(stack)) {
                count += stack.stackSize;
                direction = AutoExtractionCardData.direction(stack);
            } else if (stack != null && stack.getItem() instanceof IUpgradeModule module
                && module.getType(stack) == Upgrades.LOCK_CRAFTING) return null;
        }
        return count == 1 ? direction : null;
    }

    public boolean busy() {
        return invalid != null || quarantined || pending != null || !entries.isEmpty();
    }

    public boolean hasWork() {
        return busy() || !returning.isEmpty();
    }

    /** Cancel only the displayed task; retained physical outputs remain owned by this interface. */
    public boolean cancel(long expectedTask) {
        if (!installed() || expectedTask == 0
            || expectedTask != taskId
            || pending != null
            || inFlight != null
            || quarantined
            || invalid != null) return false;
        int buffers = 0;
        for (Entry entry : entries) if (entry.buffer > 0) buffers++;
        if (returning.size() + buffers > MAX_OUTPUTS) return false;
        for (Entry entry : entries) if (entry.buffer > 0) returning.add(new Entry(entry.identity, 0, entry.buffer));
        entries.clear();
        pattern = null;
        taskId = 0;
        changed();
        return true;
    }

    /** Immutable, read-only GUI snapshot; rebuild only after task or card changes. */
    public AutoExtractionView view() {
        boolean present = present();
        if (view == null || view.present() != present)
            view = new AutoExtractionView(present, busy(), quarantined || invalid != null, taskId, remaining());
        return view;
    }

    /** Do not retain an AE details instance or CPU inventory across the push call. */
    public boolean begin(ICraftingPatternDetails details, boolean smart) {
        if (pending != null) return false;
        if (!installed() || invalid != null
            || quarantined
            || details == null
            || details.isCraftable()
            || details.isInputOnly()) return false;
        ItemStack source = details.getPattern();
        if (source == null || busy() && (!smart || !ItemStack.areItemStacksEqual(pattern, source))) return false;
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0 || outputs.length > MAX_OUTPUTS) return false;
        List<IAEStackType<?>> supportedTypes = Arrays.asList(host.getSupportedStackTypes());
        List<Entry> added = new ArrayList<>();
        for (IAEStack<?> output : outputs) {
            if (output == null) continue;
            if ((!output.isItem() && !output.isFluid()) || output.getStackSize() <= 0) return false;
            // Ordinary ME interfaces cannot return fluids; only supported outputs become task targets.
            if (!supportedTypes.contains(output.getStackType())) continue;
            Entry same = find(added, output);
            if (same == null) added.add(new Entry(output, output.getStackSize(), 0));
            else {
                if (Long.MAX_VALUE - same.owed < output.getStackSize()) return false;
                same.owed += output.getStackSize();
            }
        }
        if (added.isEmpty()) return false;
        if (busy()) {
            if (added.size() != entries.size()) return false;
            for (Entry add : added) {
                Entry old = find(entries, add.identity);
                if (old == null || Long.MAX_VALUE - old.owed < add.owed) return false;
            }
        }
        pending = new Plan(source.copy(), added);
        changed();
        return true;
    }

    public void finish(boolean accepted) {
        Plan plan = pending;
        pending = null;
        if (plan == null) return;
        if (accepted) {
            if (pattern == null) {
                pattern = plan.pattern;
                taskId = TASK_IDS.incrementAndGet();
            }
            for (Entry add : plan.outputs) {
                Entry old = find(entries, add.identity);
                long outstanding = add.owed - add.buffer; // Reentrant returns during a synchronous push.
                if (old == null) entries.add(new Entry(add.identity, outstanding, 0));
                else old.owed += outstanding;
            }
        }
        changed();
        cleanup();
    }

    public void failedPush(Throwable cause) {
        if (pending != null && !quarantined)
            quarantine(new IllegalStateException("Native pattern push did not finish", cause));
    }

    public boolean pending() {
        return pending != null;
    }

    public boolean repeating() {
        return pending != null && !entries.isEmpty();
    }

    /** Detached snapshots for native lock/Waila readers; never expose the mutable completion ledger. */
    public List<IAEStack<?>> remaining() {
        List<IAEStack<?>> result = new ArrayList<>();
        for (Entry entry : entries) {
            long amount = Math.max(entry.owed, entry.buffer);
            if (amount > 0) result.add(
                entry.identity.copy()
                    .setStackSize(amount));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> T poweredInsert(IEnergySource energy, IMEInventory<T> inventory, T input,
        BaseActionSource source) {
        if (!busy() && pending == null) return Platform.poweredInsert(energy, inventory, input, source);
        IMEInventory<T> boundary = new MEPassThrough<T>(inventory, (IAEStackType<T>) input.getStackType()) {

            @Override
            public T injectItems(T material, Actionable mode, BaseActionSource actionSource) {
                return AutoExtractionState.this.inject(inventory, material, mode, actionSource);
            }
        };
        return Platform.poweredInsert(energy, boundary, input, source);
    }

    /** Replaces only an audited host's own pass-through monitors, retaining native grid-change handling. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void installMonitors(Map<IAEStackType<?>, MEMonitorPassThrough<?>> monitors, BaseActionSource source) {
        if (!supported()) return;
        for (var entry : monitors.entrySet()) {
            InterfaceReturnMonitor monitor = new InterfaceReturnMonitor(entry.getKey(), this);
            monitor.setChangeSource(source);
            entry.setValue(monitor);
        }
    }

    /** Counts the actual accepted quantity at this interface boundary, not grid-wide insertion notifications. */
    public <T extends IAEStack<T>> T inject(IMEInventory<T> inventory, T input, Actionable mode,
        BaseActionSource source) {
        if (input == null || mode != Actionable.MODULATE || !tracks(input))
            return inventory.injectItems(input, mode, source);
        T request = input.copy();
        boolean outer = returnDepth++ == 0;
        try {
            if (outer) markTransfer("return", request, request.getStackSize());
            T left = inventory.injectItems(input, mode, source);
            long remaining = left == null ? 0 : left.getStackSize();
            if (remaining < 0 || remaining > request.getStackSize() || left != null && !left.isSameType(request))
                throw new IllegalStateException("Invalid insertion remainder");
            if (outer) {
                inFlight = null;
                returned(request, request.getStackSize() - remaining);
            }
            return left;
        } catch (RuntimeException exception) {
            if (outer) quarantine(exception);
            throw exception;
        } finally {
            returnDepth--;
        }
    }

    public void returned(IAEStack<?> identity, long amount) {
        if (amount <= 0 || quarantined || invalid != null) return;
        Entry old = find(entries, identity);
        long credited = old == null ? 0 : Math.min(amount, old.owed);
        if (old != null) old.owed -= credited;
        long extra = amount - credited;
        if (pending != null && extra > 0) {
            Entry future = find(pending.outputs, identity);
            if (future != null) future.buffer += Math.min(extra, future.owed - future.buffer);
        }
        if (credited > 0 || extra > 0 && pending != null) changed();
        cleanup();
    }

    private boolean tracks(IAEStack<?> identity) {
        return !quarantined && invalid == null
            && (find(entries, identity) != null || find(returning, identity) != null
                || pending != null && find(pending.outputs, identity) != null);
    }

    /** Returns actual transfer progress for AE's scheduler within this interface's step limit. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean tick(long tick, AENetworkProxy proxy, BaseActionSource source) {
        if (!hasWork() || quarantined || invalid != null || lastTick == tick || !proxy.isActive()) return false;
        lastTick = tick;
        boolean progressed = false;
        ForgeDirection inputSide = extractionDirection();
        for (int step = 0; step < LOCAL_BUDGET; step++) {
            // Drain cancelled tasks' real buffers before acquiring more physical output.
            // This bounds retained buffers even when many tasks are cancelled while ME is full.
            List<Entry> work = returning.isEmpty() ? entries : returning;
            if (work.isEmpty()) break;
            Entry entry = work.get(Math.floorMod(cursor++, work.size()));
            try {
                IMEInventory destination = proxy.getStorage()
                    .getMEMonitor(entry.identity.getStackType());
                if (entry.buffer > 0) {
                    long amount = poweredAmount(proxy, entry.identity, entry.buffer);
                    if (amount == 0) continue;
                    IAEStack request = entry.identity.copy()
                        .setStackSize(amount);
                    IAEStack left = inject(destination, request, Actionable.MODULATE, source);
                    long stored = amount - (left == null ? 0 : left.getStackSize());
                    entry.buffer -= stored;
                    if (stored > 0) {
                        progressed = true;
                        changed();
                        proxy.getEnergy()
                            .extractAEPower(
                                (double) stored / entry.identity.getAmountPerUnit(),
                                Actionable.MODULATE,
                                PowerMultiplier.CONFIG);
                    }
                } else if (entry.owed > 0 && inputSide != null) {
                    ForgeDirection side = ForgeDirection.VALID_DIRECTIONS[Math
                        .floorMod(entry.sideCursor++, ForgeDirection.VALID_DIRECTIONS.length)];
                    ForgeDirection accessSide = inputSide == ForgeDirection.UNKNOWN ? side.getOpposite() : inputSide;
                    TileEntity tile = host.getTileEntity();
                    int x = tile.xCoord + side.offsetX, y = tile.yCoord + side.offsetY, z = tile.zCoord + side.offsetZ;
                    if (!tile.getWorldObj()
                        .blockExists(x, y, z)) continue;
                    TileEntity target = tile.getWorldObj()
                        .getTileEntity(x, y, z);
                    if (target == null || target.isInvalid()
                        || networkHost(target)
                        || networkHost(Platform.getPartFromTE(target, accessSide))) continue;
                    var registry = AEApi.instance()
                        .registries()
                        .externalStorage();
                    var handler = registry.getHandler(target, accessSide, entry.identity.getStackType(), source);
                    IMEInventory external = handler == null ? null
                        : handler.getInventory(target, accessSide, entry.identity.getStackType(), source);
                    if (external == null) continue;
                    long amount = poweredAmount(
                        proxy,
                        entry.identity,
                        Math.min(entry.owed, entry.identity.isFluid() ? FLUID_TRANSFER : ITEM_TRANSFER));
                    if (amount == 0) continue;
                    IAEStack request = entry.identity.copy()
                        .setStackSize(amount);
                    IAEStack leftover = (IAEStack) destination.injectItems(request.copy(), Actionable.SIMULATE, source);
                    long space = leftover == null ? amount : amount - leftover.getStackSize();
                    if (space <= 0 || space > amount || leftover != null && !leftover.isSameType(request)) continue;
                    request.setStackSize(space);
                    markTransfer("extract", request, space);
                    IAEStack actual = (IAEStack) external.extractItems(request, Actionable.MODULATE, source);
                    if (actual != null && (!actual.isSameType(entry.identity) || actual.getStackSize() < 0
                        || actual.getStackSize() > space)) throw new IllegalStateException("Invalid extracted output");
                    entry.buffer = actual == null ? 0 : actual.getStackSize();
                    progressed |= entry.buffer > 0;
                    inFlight = null;
                    changed();
                }
            } catch (GridAccessException exception) {
                break;
            } catch (RuntimeException exception) {
                if (inFlight != null && !quarantined) quarantine(exception);
                else if (lastWarning == Long.MIN_VALUE || tick - lastWarning >= 200) {
                    lastWarning = tick;
                    TenkeiTech.LOG.warn("Auto extraction lookup failed at " + location(), exception);
                }
                if (quarantined) break;
            }
            cleanup();
        }
        return progressed;
    }

    private long poweredAmount(AENetworkProxy proxy, IAEStack<?> material, long amount) throws GridAccessException {
        double available = proxy.getEnergy()
            .extractAEPower((double) amount / material.getAmountPerUnit(), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Math.min(amount, Math.max(0L, (long) (available * material.getAmountPerUnit())));
    }

    private static boolean networkHost(Object target) {
        // GT's common base tile implements IActionHost even for ordinary, non-AE machines.
        // A proxy also identifies disconnected AE devices whose actionable node is temporarily absent.
        return target instanceof IInterfaceHost || target instanceof IGridProxyable grid && grid.getProxy() != null
            || target instanceof IActionHost action && action.getActionableNode() != null;
    }

    private void markTransfer(String operation, IAEStack<?> material, long amount) {
        inFlight = new NBTTagCompound();
        inFlight.setString("operation", operation);
        inFlight.setTag(
            "material",
            material.copy()
                .setStackSize(1)
                .toNBTGeneric());
        inFlight.setLong("amount", amount);
        changed();
    }

    private void quarantine(RuntimeException exception) {
        quarantined = true;
        changed();
        NBTTagCompound snapshot = new NBTTagCompound();
        write(snapshot);
        TenkeiTech.LOG.error("Auto extraction quarantined at " + location() + ": " + snapshot, exception);
    }

    private String location() {
        if (host == null || host.getTileEntity() == null) return "unknown interface";
        TileEntity tile = host.getTileEntity();
        return host.getClass()
            .getName() + " "
            + tile.xCoord
            + ","
            + tile.yCoord
            + ","
            + tile.zCoord
            + " input="
            + extractionDirection();
    }

    private void changed() {
        view = null;
        if (host != null && host.getTileEntity() != null
            && host.getTileEntity()
                .getWorldObj() != null)
            host.saveChanges();
    }

    private void cleanup() {
        if (quarantined || invalid != null) return;
        if (returning.removeIf(entry -> entry.buffer == 0)) changed();
        if (pending != null || entries.isEmpty()) return;
        for (Entry entry : entries) if (entry.owed != 0 || entry.buffer != 0) return;
        entries.clear();
        pattern = null;
        taskId = 0;
        changed();
    }

    public void write(NBTTagCompound root) {
        if (invalid != null) {
            root.setTag(KEY, invalid.copy());
            return;
        }
        if (!hasWork()) {
            root.removeTag(KEY);
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setBoolean("quarantined", quarantined);
        if (pattern != null) tag.setTag("pattern", pattern.writeToNBT(new NBTTagCompound()));
        if (inFlight != null) tag.setTag("inFlight", inFlight.copy());
        if (pending != null) {
            NBTTagCompound dispatching = new NBTTagCompound();
            dispatching.setTag("pattern", pending.pattern.writeToNBT(new NBTTagCompound()));
            NBTTagList promised = new NBTTagList();
            for (Entry output : pending.outputs) {
                NBTTagCompound record = new NBTTagCompound();
                record.setTag("material", output.identity.toNBTGeneric());
                record.setLong("promised", output.owed);
                record.setLong("returnedDuringPush", output.buffer);
                promised.appendTag(record);
            }
            dispatching.setTag("outputs", promised);
            tag.setTag("dispatching", dispatching);
        }
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setTag("material", entry.identity.toNBTGeneric());
            saved.setLong("owed", entry.owed);
            saved.setLong("buffer", entry.buffer);
            list.appendTag(saved);
        }
        tag.setTag("entries", list);
        if (!returning.isEmpty()) {
            NBTTagList retained = new NBTTagList();
            for (Entry entry : returning) {
                NBTTagCompound saved = new NBTTagCompound();
                saved.setTag("material", entry.identity.toNBTGeneric());
                saved.setLong("buffer", entry.buffer);
                retained.appendTag(saved);
            }
            tag.setTag("returning", retained);
        }
        root.setTag(KEY, tag);
    }

    public void read(NBTTagCompound root) {
        view = null;
        entries.clear();
        returning.clear();
        pattern = null;
        taskId = 0;
        pending = null;
        invalid = null;
        inFlight = null;
        quarantined = false;
        if (!root.hasKey(KEY)) return;
        NBTTagCompound tag = root.getCompoundTag(KEY);
        try {
            if (!root.hasKey(KEY, 10) || tag.getInteger("schema") != 1 || !tag.hasKey("quarantined", 1))
                throw new IllegalArgumentException("Unsupported extraction schema");
            if (tag.hasKey("dispatching")) throw new IllegalArgumentException("Unfinished native pattern push");
            pattern = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("pattern"));
            NBTTagList list = tag.getTagList("entries", 10);
            if (!tag.hasKey("entries", 9) || ((NBTTagList) tag.getTag("entries")).tagCount() != list.tagCount()
                || list.tagCount() > MAX_OUTPUTS
                || list.tagCount() > 0 && (pattern == null || pattern.stackSize != 1)
                || list.tagCount() == 0 && tag.hasKey("pattern"))
                throw new IllegalArgumentException("Invalid extraction task");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound saved = list.getCompoundTagAt(i);
                IAEStack<?> identity = IAEStack.fromNBTGeneric(saved.getCompoundTag("material"));
                long owed = saved.getLong("owed"), buffer = saved.getLong("buffer");
                if (identity == null || identity.getStackSize() != 1
                    || !identity.isItem() && !identity.isFluid()
                    || !saved.hasKey("owed", 4)
                    || !saved.hasKey("buffer", 4)
                    || owed < 0
                    || buffer < 0
                    || buffer > (identity.isFluid() ? FLUID_TRANSFER : ITEM_TRANSFER)
                    || find(entries, identity) != null) throw new IllegalArgumentException("Invalid extraction entry");
                entries.add(new Entry(identity, owed, buffer));
            }
            if (tag.hasKey("returning")) {
                NBTTagList retained = tag.getTagList("returning", 10);
                if (!tag.hasKey("returning", 9) || retained.tagCount() == 0 || retained.tagCount() > MAX_OUTPUTS)
                    throw new IllegalArgumentException("Invalid retained extraction buffers");
                for (int i = 0; i < retained.tagCount(); i++) {
                    NBTTagCompound saved = retained.getCompoundTagAt(i);
                    IAEStack<?> identity = IAEStack.fromNBTGeneric(saved.getCompoundTag("material"));
                    long buffer = saved.getLong("buffer");
                    if (identity == null || identity.getStackSize() != 1
                        || !identity.isItem() && !identity.isFluid()
                        || !saved.hasKey("buffer", 4)
                        || buffer <= 0
                        || buffer > (identity.isFluid() ? FLUID_TRANSFER : ITEM_TRANSFER)
                        || find(returning, identity) != null)
                        throw new IllegalArgumentException("Invalid retained output");
                    returning.add(new Entry(identity, 0, buffer));
                }
            }
            if (entries.isEmpty() && returning.isEmpty()) throw new IllegalArgumentException("Empty extraction state");
            if (!entries.isEmpty()) taskId = TASK_IDS.incrementAndGet();
            quarantined = tag.getBoolean("quarantined") || tag.hasKey("inFlight");
            if (tag.hasKey("inFlight")) inFlight = (NBTTagCompound) tag.getCompoundTag("inFlight")
                .copy();
            cleanup();
            if (quarantined) TenkeiTech.LOG.error("Loaded quarantined auto extraction at " + location() + ": " + tag);
        } catch (RuntimeException exception) {
            entries.clear();
            returning.clear();
            taskId = 0;
            invalid = root.getTag(KEY)
                .copy();
            TenkeiTech.LOG.error("Invalid auto extraction retained at " + location() + ": " + invalid, exception);
        }
    }

    public void addDrops(List<ItemStack> drops) {
        if (quarantined || invalid != null) {
            if (!loggedDrop) {
                NBTTagCompound snapshot = new NBTTagCompound();
                write(snapshot);
                TenkeiTech.LOG.error("Quarantined auto extraction drop query at " + location() + ": " + snapshot);
                loggedDrop = true;
            }
            return;
        }
        addDrops(entries, drops);
        addDrops(returning, drops);
    }

    private static void addDrops(List<Entry> buffers, List<ItemStack> drops) {
        for (Entry entry : buffers) {
            long left = entry.buffer;
            if (entry.identity.isFluid() && left > 0) {
                var packet = Platform.stackConvertPacket(
                    entry.identity.copy()
                        .setStackSize(left));
                if (packet == null) throw new IllegalStateException("Cannot preserve extracted fluid");
                drops.add(packet.getItemStack());
            } else while (left > 0) {
                ItemStack stack = ((IAEItemStack) entry.identity).getItemStack();
                stack.stackSize = (int) Math.min(left, Math.max(1, stack.getMaxStackSize()));
                drops.add(stack);
                left -= stack.stackSize;
            }
        }
    }

    private static Entry find(List<Entry> list, IAEStack<?> identity) {
        for (Entry entry : list) if (entry.identity.isSameType(identity)) return entry;
        return null;
    }

    private static final class Entry {

        private final IAEStack<?> identity;
        private long owed;
        private long buffer;
        private int sideCursor;

        private Entry(IAEStack<?> identity, long owed, long buffer) {
            this.identity = identity.copy()
                .setStackSize(1);
            this.owed = owed;
            this.buffer = buffer;
        }
    }

    private static final class Plan {

        private final ItemStack pattern;
        private final List<Entry> outputs;

        private Plan(ItemStack pattern, List<Entry> outputs) {
            this.pattern = pattern;
            this.pattern.stackSize = 1;
            this.outputs = outputs;
        }
    }
}
