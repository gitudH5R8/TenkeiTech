/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.DimensionManager;

import com.tenkeitech.resonating.ResonatingPatternData.Target;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** A shared slot snapshot prevents independent simulations from reserving the same free space twice. */
final class ResonatingCapacityCheck {

    private ResonatingCapacityCheck() {}

    static boolean fits(List<Target> targets, List<IAEStack<?>> materials, ItemStack pattern, BaseActionSource source) {
        Map<Object, IAEStack<?>> fluids = new IdentityHashMap<>();
        Map<Object, Target> fluidTargets = new IdentityHashMap<>();
        List<Target> itemTargets = new java.util.ArrayList<>();
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            IAEStack<?> material = materials.get(i);
            if (material instanceof IAEItemStack item) {
                ItemStack stack = item.getItemStack();
                stack.stackSize = (int) material.getStackSize();
                itemTargets.add(target);
                items.add(stack);
                continue;
            }
            var world = DimensionManager.getWorld(target.dimension);
            if (world == null || !world.blockExists(target.x, target.y, target.z)) return false;
            var tile = world.getTileEntity(target.x, target.y, target.z);
            if (!(tile instanceof net.minecraftforge.fluids.IFluidHandler)) return false;
            var previous = fluids.get(tile);
            // Forge has no per-tank simulated reservation API. Only a same-fluid, same-side aggregate is provable.
            if (previous != null) {
                if (!previous.isSameType(material) || fluidTargets.get(tile).side != target.side) return false;
                previous.incStackSize(material.getStackSize());
            } else {
                fluids.put(tile, material.copy());
                fluidTargets.put(tile, target);
            }
        }
        for (var entry : fluids.entrySet()) {
            var target = fluidTargets.get(entry.getKey());
            var inventory = ResonatingTargetAccess.inventory(target, pattern, source, entry.getValue());
            if (inventory == null) return false;
            IAEStack<?> remainder = (IAEStack<?>) inventory.injectItems(
                entry.getValue()
                    .copy(),
                Actionable.SIMULATE,
                source);
            if (remainder != null && remainder.getStackSize() != 0) return false;
        }
        return fitsItems(itemTargets, items);
    }

    private static boolean fitsItems(List<Target> targets, List<ItemStack> materials) {
        Map<IInventory, ItemStack[]> snapshots = new IdentityHashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            var world = DimensionManager.getWorld(target.dimension);
            if (world == null || !world.blockExists(target.x, target.y, target.z)) return false;
            var tile = world.getTileEntity(target.x, target.y, target.z);
            // Unknown external storage cannot provide a combined, side-aware capacity reservation.
            if (!(tile instanceof IInventory inventory)) return false;
            ItemStack[] snapshot = snapshots.get(inventory);
            if (snapshot == null) {
                int size = inventory.getSizeInventory();
                if (size <= 0 || size > 4096) return false;
                snapshot = new ItemStack[size];
                for (int slot = 0; slot < size; slot++) {
                    ItemStack current = inventory.getStackInSlot(slot);
                    snapshot[slot] = current == null ? null : current.copy();
                }
                snapshots.put(inventory, snapshot);
            }
            int[] slots;
            if (inventory instanceof ISidedInventory sided) slots = sided.getAccessibleSlotsFromSide(target.side);
            else {
                slots = new int[snapshot.length];
                for (int slot = 0; slot < slots.length; slot++) slots[slot] = slot;
            }
            if (slots == null || slots.length > 4096) return false;
            ItemStack input = materials.get(index);
            long left = input.stackSize;
            // Fill existing matching stacks before reserving empty slots.
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int slot : slots) {
                    if (slot < 0 || slot >= snapshot.length
                        || !inventory.isItemValidForSlot(slot, input)
                        || inventory instanceof ISidedInventory sided && !sided.canInsertItem(slot, input, target.side))
                        continue;
                    ItemStack current = snapshot[slot];
                    if ((pass == 0) != (current != null)) continue;
                    if (current != null
                        && (!current.isItemEqual(input) || !ItemStack.areItemStackTagsEqual(current, input))) continue;
                    int limit = Math.min(input.getMaxStackSize(), inventory.getInventoryStackLimit());
                    int count = current == null ? 0 : current.stackSize;
                    int amount = (int) Math.min(left, Math.max(0, limit - count));
                    if (amount == 0) continue;
                    if (current == null) {
                        current = input.copy();
                        current.stackSize = 0;
                        snapshot[slot] = current;
                    }
                    current.stackSize += amount;
                    left -= amount;
                    if (left == 0) break;
                }
            }
            if (left != 0) return false;
        }
        return true;
    }
}
