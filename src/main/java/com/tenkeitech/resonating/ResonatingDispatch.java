/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.tenkeitech.ModItems;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.resonating.ResonatingPatternData.Target;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.MEInventoryCrafting;

/** One accepted dispatch is owned by its interface; uncertain transfers stop and are recorded in the game log. */
public final class ResonatingDispatch {

    private static final String KEY = "tenkeitechResonatingDispatch";
    private final List<Entry> entries = new ArrayList<>();
    private ItemStack pattern;
    private boolean quarantined;
    private NBTTagCompound invalidRecovery;
    private appeng.helpers.IInterfaceHost logHost;
    private boolean loggedDropQuery;
    private int cursor;
    private long lastLookupWarning = -200;
    private long lastTransferTick = Long.MIN_VALUE;
    private boolean completeCapacityRequired;
    private boolean started;
    private static long budgetTick = Long.MIN_VALUE;
    private static int attempts;

    public void locate(appeng.helpers.IInterfaceHost host) {
        logHost = host;
    }

    private String logContext() {
        var host = logHost;
        if (host == null) return "interface location unavailable";
        var tile = host.getTileEntity();
        if (tile == null) return "interface location unavailable";
        var world = tile.getWorldObj();
        return "host=" + host.getClass()
            .getName()
            + ", dimension="
            + (world == null ? "unavailable" : world.provider.dimensionId)
            + ", position="
            + tile.xCoord
            + ","
            + tile.yCoord
            + ","
            + tile.zCoord
            + ", side="
            + (host instanceof appeng.parts.AEBasePart part ? part.getSide() : "block");
    }

    private void logSnapshot(String reason) {
        NBTTagCompound saved = new NBTTagCompound();
        write(saved);
        TenkeiTech.LOG.error("Resonating dispatch: {}; {}; data={}", reason, logContext(), saved);
    }

    private static final class Entry {

        final Target target;
        final IAEStack<?> identity;
        final int inputIndex;
        long amount;
        boolean unknown;
        long unknownRequest;

        Entry(int inputIndex, Target target, IAEStack<?> identity, long amount) {
            this.inputIndex = inputIndex;
            this.target = target;
            this.identity = identity.copy();
            this.identity.setStackSize(1);
            this.amount = amount;
        }
    }

    public boolean busy() {
        return quarantined || invalidRecovery != null || !entries.isEmpty();
    }

    public boolean targetUnreachable(BaseActionSource source) {
        if (pattern == null || quarantined || invalidRecovery != null) return false;
        try {
            for (Entry entry : entries) {
                if (ResonatingTargetAccess.inventory(entry.target, pattern, source, entry.identity) == null)
                    return true;
            }
        } catch (RuntimeException exception) {
            return true;
        }
        return false;
    }

    public boolean accept(ResonatingPatternData details, MEInventoryCrafting table, BaseActionSource source,
        boolean blocking, boolean requireCompleteCapacity) {
        if (busy()) return false;
        List<IAEStack<?>> remaining = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            IAEStack<?> actual = table.getAEStackInSlot(i);
            if (actual == null) continue;
            if (!ResonatingPatternData.supportedMaterial(actual) || actual.getStackSize()
                > (actual.isFluid() ? ResonatingPatternData.MAX_FLUID_AMOUNT : ResonatingPatternData.MAX_AMOUNT)
                    * (long) ResonatingPatternData.MAX_INPUTS)
                return false;
            remaining.add(actual.copy());
        }
        List<Entry> next = new ArrayList<>();
        for (int i = 0; i < details.getAEInputs().length; i++) {
            IAEStack<?> expected = details.getAEInputs()[i];
            if (expected == null) continue;
            Target target = details.target(i);
            if (target == null) return false;
            var inventory = ResonatingTargetAccess.inventory(target, details.getPattern(), source, expected);
            if (inventory == null) return false;
            if (blocking && ResonatingTargetAccess.blocked(target, expected)) return false;
            long needed = expected.getStackSize();
            for (IAEStack<?> actual : remaining) {
                if (!actual.isSameType(expected)) continue;
                long take = Math.min(needed, actual.getStackSize());
                needed -= take;
                actual.decStackSize(take);
            }
            if (needed != 0) return false;
            next.add(new Entry(i, target, expected, expected.getStackSize()));
        }
        for (IAEStack<?> actual : remaining) if (actual.getStackSize() != 0) return false;
        if (next.isEmpty()) return false;
        if (requireCompleteCapacity) {
            List<Target> targets = new ArrayList<>();
            List<IAEStack<?>> materials = new ArrayList<>();
            for (Entry entry : next) {
                var inventory = ResonatingTargetAccess
                    .inventory(entry.target, details.getPattern(), source, entry.identity);
                if (inventory == null) return false;
                IAEStack<?> request = entry.identity.copy();
                request.setStackSize(entry.amount);
                IAEStack<?> remainder = (IAEStack<?>) inventory.injectItems(request, Actionable.SIMULATE, source);
                if (remainder != null && remainder.getStackSize() != 0) return false;
                IAEStack<?> material = entry.identity.copy();
                material.setStackSize(entry.amount);
                targets.add(entry.target);
                materials.add(material);
            }
            if (!ResonatingCapacityCheck.fits(targets, materials, details.getPattern(), source)) return false;
        }
        pattern = details.getPattern()
            .copy();
        pattern.stackSize = 1;
        completeCapacityRequired = requireCompleteCapacity;
        started = false;
        entries.addAll(next);
        return true;
    }

    /** Runs on the server thread, with local and whole-server attempt budgets. */
    public boolean tick(long tick, IEnergySource energy, BaseActionSource source) {
        if (quarantined || invalidRecovery != null || entries.isEmpty()) return false;
        if (completeCapacityRequired && !started) {
            List<Target> targets = new ArrayList<>();
            List<IAEStack<?>> materials = new ArrayList<>();
            try {
                double total = 0;
                for (Entry entry : entries) {
                    var inventory = ResonatingTargetAccess.inventory(entry.target, pattern, source, entry.identity);
                    if (inventory == null) return false;
                    IAEStack<?> request = entry.identity.copy();
                    request.setStackSize(entry.amount);
                    IAEStack<?> remainder = (IAEStack<?>) inventory.injectItems(request, Actionable.SIMULATE, source);
                    if (remainder != null && remainder.getStackSize() != 0) return false;
                    IAEStack<?> material = entry.identity.copy();
                    material.setStackSize(entry.amount);
                    targets.add(entry.target);
                    materials.add(material);
                    total += energyCost(entry.identity, entry.amount);
                }
                if (!ResonatingCapacityCheck.fits(targets, materials, pattern, source)
                    || energy.extractAEPower(total, Actionable.SIMULATE, PowerMultiplier.CONFIG) < total) return false;
            } catch (RuntimeException exception) {
                if (tick - lastLookupWarning >= 200) {
                    lastLookupWarning = tick;
                    TenkeiTech.LOG.warn("Resonating capacity or energy preflight failed; " + logContext(), exception);
                }
                return false;
            }
        }
        if (budgetTick != tick) {
            budgetTick = tick;
            attempts = 0;
        }
        // Reserve an entire bounded batch before starting; a server budget must not split its input slots.
        if (lastTransferTick == tick || attempts + entries.size() > 128) return false;
        lastTransferTick = tick;
        boolean changed = false;
        int visits = entries.size();
        while (visits-- > 0 && !entries.isEmpty()) {
            attempts++;
            cursor = Math.floorMod(cursor, entries.size());
            Entry entry = entries.get(cursor);
            try {
                var inventory = ResonatingTargetAccess.inventory(entry.target, pattern, source, entry.identity);
                if (inventory == null) {
                    cursor++;
                    continue;
                }
                IAEStack<?> request = entry.identity.copy();
                long limit = entry.amount;
                double available = energy
                    .extractAEPower(energyCost(entry.identity, limit), Actionable.SIMULATE, PowerMultiplier.CONFIG);
                long requested = available >= energyCost(entry.identity, limit) ? limit
                    : Math.min(limit, Math.max(0L, (long) (available * entry.identity.getAmountPerUnit())));
                if (requested == 0) {
                    cursor++;
                    continue;
                }
                request.setStackSize(requested);
                // Once modulation starts an exception cannot prove how many items the external inventory accepted.
                entry.unknown = true;
                entry.unknownRequest = requested;
                IAEStack<?> remainder = (IAEStack<?>) inventory.injectItems(request, Actionable.MODULATE, source);
                long left = remainder == null ? 0 : remainder.getStackSize();
                if (left < 0 || left > requested || remainder != null && !remainder.isSameType(entry.identity))
                    throw new IllegalStateException("Invalid remote insertion remainder");
                entry.unknown = false;
                entry.unknownRequest = 0;
                if (left != requested) started = true;
                entry.amount -= requested - left;
                changed |= left != requested;
                if (entry.amount == 0) entries.remove(cursor);
                else cursor++;
                // Deduct known acceptance before charging: a failed charge must never resend delivered items.
                if (left != requested) energy.extractAEPower(
                    energyCost(entry.identity, requested - left),
                    Actionable.MODULATE,
                    PowerMultiplier.CONFIG);
            } catch (RuntimeException exception) {
                if (entry.unknown) {
                    quarantined = true;
                    changed = true;
                    TenkeiTech.LOG.error(
                        "Unknown resonating transfer; " + logContext()
                            + "; input="
                            + entry.inputIndex
                            + "; target="
                            + entry.target.write()
                            + "; material="
                            + entry.identity.toNBTGeneric()
                            + "; remaining="
                            + entry.amount
                            + "; requested="
                            + entry.unknownRequest,
                        exception);
                    logSnapshot("transfer quarantined; automatic retry and refund disabled");
                } else if (tick - lastLookupWarning >= 200) {
                    lastLookupWarning = tick;
                    TenkeiTech.LOG.warn(
                        "Resonating target lookup or energy charge failed; " + logContext()
                            + "; target="
                            + entry.target.write()
                            + "; material="
                            + entry.identity.toNBTGeneric()
                            + "; remaining="
                            + entry.amount,
                        exception);
                }
                break;
            }
        }
        if (entries.isEmpty()) pattern = null;
        return changed;
    }

    public void write(NBTTagCompound root) {
        if (!busy()) {
            root.removeTag(KEY);
            return;
        }
        if (invalidRecovery != null) {
            root.setTag(KEY, invalidRecovery.copy());
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 2);
        tag.setBoolean("quarantined", quarantined);
        tag.setBoolean("completeCapacityRequired", completeCapacityRequired);
        tag.setBoolean("started", started);
        if (pattern != null) tag.setTag("pattern", pattern.writeToNBT(new NBTTagCompound()));
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setInteger("inputIndex", entry.inputIndex);
            saved.setTag("target", entry.target.write());
            saved.setTag("material", entry.identity.toNBTGeneric());
            saved.setLong("amount", entry.amount);
            saved.setBoolean("unknown", entry.unknown);
            if (entry.unknown) saved.setLong("unknownRequest", entry.unknownRequest);
            list.appendTag(saved);
        }
        tag.setTag("entries", list);
        root.setTag(KEY, tag);
    }

    public void read(NBTTagCompound root) {
        entries.clear();
        pattern = null;
        quarantined = false;
        invalidRecovery = null;
        loggedDropQuery = false;
        cursor = 0;
        completeCapacityRequired = false;
        started = false;
        if (!root.hasKey(KEY, 10)) return;
        NBTTagCompound tag = root.getCompoundTag(KEY);
        completeCapacityRequired = tag.getBoolean("completeCapacityRequired");
        started = tag.getBoolean("started");
        NBTTagList list = tag.getTagList("entries", 10);
        pattern = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("pattern"));
        ResonatingPatternData details = pattern != null && pattern.getItem() == ModItems.RESONATING_PATTERN
            && ModItems.RESONATING_PATTERN.getPatternForItem(pattern, null) instanceof ResonatingPatternData routed
                ? routed
                : null;
        boolean[] accounted = new boolean[ResonatingPatternData.MAX_INPUTS];
        boolean valid = tag.getInteger("schema") == 2 && tag.hasKey("started", 1)
            && list.tagCount() > 0
            && list.tagCount() <= ResonatingPatternData.MAX_INPUTS
            && pattern != null
            && pattern.getItem() == ModItems.RESONATING_PATTERN
            && details != null;
        for (int i = 0; valid && i < list.tagCount(); i++) {
            NBTTagCompound saved = list.getCompoundTagAt(i);
            Target target = Target.read(saved.getCompoundTag("target"));
            IAEStack<?> item = readMaterial(saved);
            long amount = saved.getLong("amount");
            int inputIndex = saved.getInteger("inputIndex");
            if (!saved.hasKey("inputIndex", 3) || inputIndex < 0
                || inputIndex >= details.getAEInputs().length
                || accounted[inputIndex]
                || details.getAEInputs()[inputIndex] == null) {
                valid = false;
                break;
            }
            accounted[inputIndex] = true;
            var expected = details.getAEInputs()[inputIndex];
            var expectedTarget = details.target(inputIndex);
            if (target == null || item == null
                || item.getStackSize() != 1
                || amount <= 0
                || !ResonatingPatternData.supportedInput(
                    item.copy()
                        .setStackSize(amount))
                || amount > expected.getStackSize()
                || !expected.isSameType(item)
                || expectedTarget == null
                || !expectedTarget.write()
                    .equals(target.write())) {
                valid = false;
                break;
            }
            Entry entry = new Entry(inputIndex, target, item, amount);
            entry.unknown = saved.getBoolean("unknown");
            entry.unknownRequest = saved.getLong("unknownRequest");
            if (entry.unknown && (entry.unknownRequest <= 0 || entry.unknownRequest > amount)) {
                valid = false;
                break;
            }
            entries.add(entry);
        }
        if (!valid) {
            entries.clear();
            invalidRecovery = (NBTTagCompound) tag.copy();
            logSnapshot("unsupported or damaged saved dispatch; original data retained and execution disabled");
        } else {
            quarantined = tag.getBoolean("quarantined");
            for (Entry entry : entries) quarantined |= entry.unknown;
            if (quarantined) logSnapshot("loaded quarantined dispatch; automatic retry and refund disabled");
        }
    }

    public void addDrops(List<ItemStack> drops) {
        if (!busy()) return;
        if (invalidRecovery != null || quarantined) {
            if (!loggedDropQuery) {
                logSnapshot("drop query for quarantined dispatch; no record item or uncertain material is dropped");
                loggedDropQuery = true;
            }
            return;
        }
        for (Entry entry : entries) {
            long left = entry.amount;
            while (left > 0) {
                if (entry.identity.isFluid()) {
                    IAEStack<?> fluid = entry.identity.copy();
                    fluid.setStackSize(left);
                    var packet = appeng.util.Platform.stackConvertPacket(fluid);
                    if (packet == null) throw new IllegalStateException("Cannot preserve resonating fluid drop");
                    drops.add(packet.getItemStack());
                    break;
                }
                ItemStack dropped = ((IAEItemStack) entry.identity).getItemStack();
                dropped.stackSize = (int) Math.min(Math.max(1, dropped.getMaxStackSize()), left);
                drops.add(dropped);
                left -= dropped.stackSize;
            }
        }
        // addDrops may be queried without removal. Like AE's own inventory drops it must not clear live state.
    }

    private static double energyCost(IAEStack<?> material, long amount) {
        return (double) amount / material.getAmountPerUnit();
    }

    private static IAEStack<?> readMaterial(NBTTagCompound saved) {
        try {
            return IAEStack.fromNBTGeneric(saved.getCompoundTag("material"));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
