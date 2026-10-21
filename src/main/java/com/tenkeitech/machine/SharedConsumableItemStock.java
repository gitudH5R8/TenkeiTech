/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.tenkeitech.inventory.ILongItemInputInventory;
import com.tenkeitech.inventory.LongInputExposure;
import com.tenkeitech.inventory.LongItemStore;

import gregtech.GTLoggers;
import gregtech.api.util.GTUtility;

/**
 * Configured targets and machine-owned backing stacks for the super pattern buffer's shared consumable stock.
 */
public final class SharedConsumableItemStock implements ILongItemInputInventory {

    public static final int SLOT_COUNT = 8;
    public static final int DEFAULT_TARGET_AMOUNT = 64;

    private static final String NBT_SLOTS = "slots";
    private static final String NBT_RECOVERY = "recovery";
    private static final String NBT_SLOT = "slot";
    private static final String NBT_CONFIG = "config";
    private static final String NBT_TARGET = "target";
    private static final String NBT_STORED = "stored";
    private static final String NBT_STORED_AMOUNT = "storedAmount";
    private static final String NBT_STACK = "stack";

    private final ItemStack[] configs = new ItemStack[SLOT_COUNT];
    private final long[] targets = new long[SLOT_COUNT];
    private final ItemStack[] stored = new ItemStack[SLOT_COUNT];
    private final List<ItemStack>[] backings = createBackingLists();
    private final long[] storedAmounts = new long[SLOT_COUNT];
    private final long[] observedAmounts = new long[SLOT_COUNT];
    private final List<ItemStack> recovery = new ArrayList<>();
    private final List<Long> recoveryAmounts = new ArrayList<>();

    @Nullable
    public ItemStack getConfig(int slot) {
        checkSlot(slot);
        return copy(configs[slot]);
    }

    @Nullable
    public ItemStack getStoredCopy(int slot) {
        checkSlot(slot);
        pruneSlot(slot);
        return copy(stored[slot]);
    }

    @Nullable
    ItemStack getStoredBacking(int slot) {
        checkSlot(slot);
        pruneSlot(slot);
        return stored[slot];
    }

    public long getTarget(int slot) {
        checkSlot(slot);
        return targets[slot];
    }

    public long getStoredAmount(int slot) {
        checkSlot(slot);
        reconcileSlot(slot);
        return storedAmounts[slot];
    }

    public boolean hasConfiguration() {
        for (ItemStack config : configs) if (config != null) return true;
        return false;
    }

    public boolean isCompletelyEmpty() {
        return !hasConfiguration() && isStoredEmpty() && recoveryAmounts.isEmpty();
    }

    public boolean isStoredEmpty() {
        reconcileStored();
        for (long amount : storedAmounts) if (amount > 0) return false;
        pruneRecovery();
        return recoveryAmounts.isEmpty();
    }

    boolean hasConsumableInputs() {
        reconcileStored();
        for (long amount : storedAmounts) if (amount > 0) return true;
        return false;
    }

    long getConsumableAmount(ItemStack identity) {
        if (!isValid(identity)) return 0;
        reconcileStored();
        long amount = 0;
        for (int slot = 0; slot < stored.length; slot++) {
            if (storedAmounts[slot] > 0 && GTUtility.areStacksEqual(configs[slot], identity)) {
                amount = saturatedAdd(amount, storedAmounts[slot]);
            }
        }
        return amount;
    }

    public boolean containsConfig(ItemStack candidate, int exceptSlot) {
        if (!isValid(candidate)) return false;
        for (int slot = 0; slot < configs.length; slot++) {
            if (slot != exceptSlot && configs[slot] != null && GTUtility.areStacksEqual(configs[slot], candidate)) {
                return true;
            }
        }
        return false;
    }

    void setConfig(int slot, @Nullable ItemStack config) {
        checkSlot(slot);
        if (storedAmounts[slot] > 0)
            throw new IllegalStateException("Stored items must be refunded before changing config");
        configs[slot] = normalizeConfig(config);
        targets[slot] = configs[slot] == null ? 0 : DEFAULT_TARGET_AMOUNT;
        backings[slot].clear();
        stored[slot] = null;
        observedAmounts[slot] = 0;
    }

    void setTarget(int slot, long target) {
        checkSlot(slot);
        if (configs[slot] == null || target <= 0) throw new IllegalArgumentException("Invalid stock target");
        targets[slot] = target;
    }

    boolean addExtracted(int slot, ItemStack extracted, long amount) {
        checkSlot(slot);
        reconcileSlot(slot);
        if (!isValid(extracted) || amount <= 0
            || configs[slot] == null
            || !GTUtility.areStacksEqual(configs[slot], extracted)
            || storedAmounts[slot] > Long.MAX_VALUE - amount) {
            return false;
        }
        storedAmounts[slot] += amount;
        refreshBacking(slot);
        return true;
    }

    void setStoredAmount(int slot, long amount) {
        checkSlot(slot);
        storedAmounts[slot] = Math.max(0, amount);
        refreshBacking(slot);
    }

    public ItemStack[] getConsumableInputs() {
        reconcileStored();
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> slotBackings : backings) result.addAll(slotBackings);
        return result.toArray(new ItemStack[0]);
    }

    @Override
    public List<LongItemStore.Snapshot> longItemSnapshot() {
        reconcileStored();
        List<LongItemStore.Snapshot> result = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (configs[slot] != null && storedAmounts[slot] > 0) {
                result.add(new LongItemStore.Snapshot(configs[slot], storedAmounts[slot]));
            }
        }
        return result;
    }

    @Override
    public long getLongItemAmount(ItemStack identity) {
        return getConsumableAmount(identity);
    }

    @Override
    public void consumeLongItem(ItemStack identity, long amount) {
        if (amount <= 0) return;
        reconcileStored();
        long available = getConsumableAmount(identity);
        if (available < amount) throw new IllegalStateException("Insufficient shared item stock");
        long remaining = amount;
        for (int slot = 0; slot < SLOT_COUNT && remaining > 0; slot++) {
            if (storedAmounts[slot] <= 0 || !GTUtility.areStacksEqual(configs[slot], identity)) continue;
            long consumed = Math.min(remaining, storedAmounts[slot]);
            storedAmounts[slot] -= consumed;
            remaining -= consumed;
            refreshBacking(slot);
        }
        if (remaining > 0) throw new IllegalStateException("Shared item stock changed during consumption");
    }

    int getRecoveryCount() {
        pruneRecovery();
        return recovery.size();
    }

    ItemStack getRecoveryIdentity(int index) {
        pruneRecovery();
        return copy(recovery.get(index));
    }

    long getRecoveryAmount(int index) {
        pruneRecovery();
        return recoveryAmounts.get(index);
    }

    void setRecoveryAmount(int index, long amount) {
        pruneRecovery();
        if (amount <= 0) {
            recovery.remove(index);
            recoveryAmounts.remove(index);
        } else {
            recoveryAmounts.set(index, amount);
        }
    }

    /** Detects GT's direct in-place subtraction from the shared backing stacks. */
    public boolean observeBackingChanges() {
        boolean changed = false;
        for (int slot = 0; slot < stored.length; slot++) changed |= reconcileSlot(slot);
        return changed;
    }

    void acknowledgeBackingAmounts() {
        for (int slot = 0; slot < stored.length; slot++) refreshBacking(slot);
    }

    public NBTTagCompound writeToNBT(boolean includeStored) {
        reconcileStored();
        pruneRecovery();
        NBTTagCompound result = new NBTTagCompound();
        NBTTagList slots = new NBTTagList();
        for (int slot = 0; slot < configs.length; slot++) {
            ItemStack config = configs[slot];
            if (config == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(NBT_SLOT, slot);
            entry.setTag(NBT_CONFIG, GTUtility.saveItem(config));
            entry.setLong(NBT_TARGET, targets[slot]);
            if (includeStored && storedAmounts[slot] > 0) {
                ItemStack identity = configs[slot].copy();
                identity.stackSize = 1;
                entry.setTag(NBT_STORED, GTUtility.saveItem(identity));
                entry.setLong(NBT_STORED_AMOUNT, storedAmounts[slot]);
            }
            slots.appendTag(entry);
        }
        result.setTag(NBT_SLOTS, slots);
        if (includeStored && !recoveryAmounts.isEmpty()) {
            NBTTagList recoveryList = new NBTTagList();
            for (int index = 0; index < recovery.size(); index++) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag(NBT_STACK, GTUtility.saveItem(recovery.get(index)));
                entry.setLong(NBT_STORED_AMOUNT, recoveryAmounts.get(index));
                recoveryList.appendTag(entry);
            }
            result.setTag(NBT_RECOVERY, recoveryList);
        }
        acknowledgeBackingAmounts();
        return result;
    }

    /**
     * Reads either persistent data or a configuration-only transfer payload. Strict mode rejects the entire payload
     * instead of salvaging valid entries.
     */
    public boolean readFromNBT(NBTTagCompound nbt, boolean includeStored, boolean strict) {
        ItemStack[] nextConfigs = new ItemStack[SLOT_COUNT];
        long[] nextTargets = new long[SLOT_COUNT];
        ItemStack[] nextStored = new ItemStack[SLOT_COUNT];
        long[] nextStoredAmounts = new long[SLOT_COUNT];
        List<ItemStack> nextRecovery = new ArrayList<>();
        List<Long> nextRecoveryAmounts = new ArrayList<>();
        boolean valid = nbt != null && hasCompoundList(nbt, NBT_SLOTS);
        if (!valid && strict) return false;

        NBTTagList slots = nbt == null ? new NBTTagList() : nbt.getTagList(NBT_SLOTS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < slots.tagCount(); index++) {
            NBTTagCompound entry = slots.getCompoundTagAt(index);
            boolean hasStored = entry.hasKey(NBT_STORED);
            boolean storedTagValid = !hasStored;
            ItemStack savedStored = null;
            long savedStoredAmount = 0;
            if (hasStored && includeStored && entry.hasKey(NBT_STORED, Constants.NBT.TAG_COMPOUND)) {
                savedStored = GTUtility.loadItem(entry.getCompoundTag(NBT_STORED));
                savedStoredAmount = entry.hasKey(NBT_STORED_AMOUNT, Constants.NBT.TAG_LONG)
                    ? entry.getLong(NBT_STORED_AMOUNT)
                    : 0;
                storedTagValid = isValid(savedStored) && savedStoredAmount > 0;
            }
            int slot = entry.getInteger(NBT_SLOT);
            ItemStack config = entry.hasKey(NBT_CONFIG, Constants.NBT.TAG_COMPOUND)
                ? normalizeConfig(GTUtility.loadItem(entry.getCompoundTag(NBT_CONFIG)))
                : null;
            long target = readPositiveLong(entry, NBT_TARGET);
            boolean entryValid = entry.hasKey(NBT_SLOT, Constants.NBT.TAG_INT) && slot >= 0
                && slot < SLOT_COUNT
                && config != null
                && target > 0
                && nextConfigs[slot] == null
                && !containsConfig(nextConfigs, config)
                && storedTagValid;
            if (entryValid && includeStored && savedStored != null) {
                entryValid = isValid(savedStored) && GTUtility.areStacksEqual(config, savedStored);
            }
            if (!entryValid) {
                valid = false;
                if (strict) return false;
                if (isValid(savedStored)) {
                    addRecovery(nextRecovery, nextRecoveryAmounts, savedStored, savedStoredAmount);
                }
                GTLoggers.GT_FML_LOGGER.warn("Ignoring invalid shared consumable stock entry {}", index);
                continue;
            }
            nextConfigs[slot] = config;
            nextTargets[slot] = target;
            if (includeStored && savedStored != null) {
                nextStored[slot] = savedStored.copy();
                nextStoredAmounts[slot] = savedStoredAmount;
            }
        }

        if (includeStored && nbt != null && nbt.hasKey(NBT_RECOVERY)) {
            if (!hasCompoundList(nbt, NBT_RECOVERY)) {
                valid = false;
                if (strict) return false;
            } else {
                NBTTagList recoveryList = nbt.getTagList(NBT_RECOVERY, Constants.NBT.TAG_COMPOUND);
                for (int index = 0; index < recoveryList.tagCount(); index++) {
                    NBTTagCompound entry = recoveryList.getCompoundTagAt(index);
                    ItemStack stack = entry.hasKey(NBT_STACK, Constants.NBT.TAG_COMPOUND)
                        ? GTUtility.loadItem(entry.getCompoundTag(NBT_STACK))
                        : null;
                    long amount = entry.hasKey(NBT_STORED_AMOUNT, Constants.NBT.TAG_LONG)
                        ? entry.getLong(NBT_STORED_AMOUNT)
                        : 0;
                    if (isValid(stack) && amount > 0) {
                        addRecovery(nextRecovery, nextRecoveryAmounts, stack, amount);
                    } else {
                        valid = false;
                        if (strict) return false;
                    }
                }
            }
        }

        System.arraycopy(nextConfigs, 0, configs, 0, SLOT_COUNT);
        System.arraycopy(nextTargets, 0, targets, 0, SLOT_COUNT);
        System.arraycopy(nextStored, 0, stored, 0, SLOT_COUNT);
        System.arraycopy(nextStoredAmounts, 0, storedAmounts, 0, SLOT_COUNT);
        clearBackingLists();
        recovery.clear();
        recovery.addAll(nextRecovery);
        recoveryAmounts.clear();
        recoveryAmounts.addAll(nextRecoveryAmounts);
        acknowledgeBackingAmounts();
        return valid;
    }

    public void clear() {
        Arrays.fill(configs, null);
        Arrays.fill(targets, 0);
        Arrays.fill(stored, null);
        Arrays.fill(storedAmounts, 0);
        Arrays.fill(observedAmounts, 0);
        clearBackingLists();
        recovery.clear();
        recoveryAmounts.clear();
    }

    private void reconcileStored() {
        for (int slot = 0; slot < stored.length; slot++) reconcileSlot(slot);
    }

    private void pruneSlot(int slot) {
        reconcileSlot(slot);
    }

    private boolean reconcileSlot(int slot) {
        long current = 0;
        for (ItemStack backing : backings[slot]) current += Math.max(0, backing.stackSize);
        long consumed = Math.max(0, observedAmounts[slot] - current);
        if (consumed > 0) storedAmounts[slot] = Math.max(0, storedAmounts[slot] - consumed);
        refreshBacking(slot);
        return consumed > 0;
    }

    private void refreshBacking(int slot) {
        if (storedAmounts[slot] <= 0 || configs[slot] == null) {
            storedAmounts[slot] = 0;
            backings[slot].clear();
            stored[slot] = null;
            observedAmounts[slot] = 0;
            return;
        }
        List<ItemStack> slotBackings = backings[slot];
        int segmentCount = LongInputExposure.segmentCount(storedAmounts[slot]);
        while (slotBackings.size() > segmentCount) slotBackings.remove(slotBackings.size() - 1);
        while (slotBackings.size() < segmentCount) slotBackings.add(configs[slot].copy());
        observedAmounts[slot] = 0;
        for (int index = 0; index < slotBackings.size(); index++) {
            ItemStack backing = slotBackings.get(index);
            if (!GTUtility.areStacksEqual(backing, configs[slot])) {
                backing = configs[slot].copy();
                slotBackings.set(index, backing);
            }
            backing.stackSize = LongInputExposure.segmentAmount(storedAmounts[slot], index);
            observedAmounts[slot] += backing.stackSize;
        }
        stored[slot] = slotBackings.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack>[] createBackingLists() {
        List<ItemStack>[] result = new List[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) result[slot] = new ArrayList<>();
        return result;
    }

    private void clearBackingLists() {
        for (List<ItemStack> slotBackings : backings) slotBackings.clear();
    }

    private static long readPositiveLong(NBTTagCompound nbt, String key) {
        if (nbt.hasKey(key, Constants.NBT.TAG_LONG)) return nbt.getLong(key);
        return 0;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private void pruneRecovery() {
        for (int index = recovery.size() - 1; index >= 0; index--) {
            if (!isValid(recovery.get(index)) || recoveryAmounts.get(index) <= 0) {
                recovery.remove(index);
                recoveryAmounts.remove(index);
            }
        }
    }

    private static void addRecovery(List<ItemStack> recovery, List<Long> amounts, ItemStack source, long amount) {
        if (amount <= 0) return;
        ItemStack copy = source.copy();
        copy.stackSize = 1;
        for (int index = 0; index < recovery.size(); index++) {
            if (GTUtility.areStacksEqual(recovery.get(index), copy)) {
                amounts.set(index, saturatedAdd(amounts.get(index), amount));
                return;
            }
        }
        recovery.add(copy);
        amounts.add(amount);
    }

    private static boolean containsConfig(ItemStack[] configs, ItemStack candidate) {
        for (ItemStack config : configs) {
            if (config != null && GTUtility.areStacksEqual(config, candidate)) return true;
        }
        return false;
    }

    @Nullable
    private static ItemStack normalizeConfig(@Nullable ItemStack stack) {
        if (!isValid(stack)) return null;
        ItemStack result = stack.copy();
        result.stackSize = 1;
        return result;
    }

    @Nullable
    private static ItemStack copy(@Nullable ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static boolean isValid(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.stackSize > 0;
    }

    private static boolean hasCompoundList(NBTTagCompound nbt, String key) {
        if (!nbt.hasKey(key, Constants.NBT.TAG_LIST)) return false;
        int elementType = ((NBTTagList) nbt.getTag(key)).func_150303_d();
        return elementType == Constants.NBT.TAG_END || elementType == Constants.NBT.TAG_COMPOUND;
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IndexOutOfBoundsException("Shared consumable slot " + slot);
    }
}
