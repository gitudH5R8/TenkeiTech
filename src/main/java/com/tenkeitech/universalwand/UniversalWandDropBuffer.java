/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.inventory.ItemInputKey;

/** Aggregated counts are separate from the legal-sized stack used as an identity template. */
final class UniversalWandDropBuffer {

    private static final class Drop {

        final ItemStack template;
        long count;
        long quarantinedCount;

        Drop(ItemStack template) {
            this.template = template;
        }
    }

    private final Map<ItemInputKey, Drop> drops = new LinkedHashMap<>();

    void collect(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return;
        add(stack, stack.stackSize);
    }

    private void add(ItemStack stack, long count) {
        add(stack, count, false);
    }

    private void add(ItemStack stack, long count, boolean quarantined) {
        if (count <= 0 || stack == null || stack.getItem() == null) return;
        ItemStack template = stack.copy();
        template.stackSize = 1;
        ItemInputKey key = ItemInputKey.of(template);
        Drop drop = drops.computeIfAbsent(key, ignored -> new Drop(template));
        if (quarantined) drop.quarantinedCount = Math.addExact(drop.quarantinedCount, count);
        else drop.count = Math.addExact(drop.count, count);
    }

    boolean isEmpty() {
        return drops.isEmpty();
    }

    void distribute(EntityPlayerMP player, UniversalWandStorage.Destination destination) {
        for (Drop drop : drops.values()) {
            if (drop.count <= 0) continue;
            if (destination.aePriority) {
                try {
                    drop.count = UniversalWandStorage.insert(player, destination, drop.template, drop.count);
                } catch (UniversalWandStorage.UnknownInsertionOutcome failure) {
                    // A throwing MODULATE call cannot prove how much the network already consumed.
                    drop.quarantinedCount = Math.addExact(drop.quarantinedCount, drop.count);
                    drop.count = 0;
                    TenkeiTech.LOG.error(
                        "Universal Wand AE insertion outcome unknown; retained a quarantined recovery entry",
                        failure);
                    player.addChatMessage(
                        new net.minecraft.util.ChatComponentTranslation("tenkeitech.universal_wand.ae_unknown"));
                    continue;
                }
            }
            if (destination.inventory && !player.isDead && drop.count > 0) {
                insertIntoInventory(player, drop);
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
            }
        }
        drops.values()
            .removeIf(drop -> drop.count <= 0 && drop.quarantinedCount <= 0);
    }

    private static void insertIntoInventory(EntityPlayerMP player, Drop drop) {
        ItemStack template = drop.template;
        int limit = Math.min(
            player.inventory.getInventoryStackLimit(),
            template.isStackable() ? Math.min(64, template.getMaxStackSize()) : 1);
        if (limit <= 0) return;
        // Use the same main slots as vanilla, without its creative-mode discard-on-full shortcut.
        for (int pass = 0; pass < 2 && drop.count > 0; pass++) {
            for (int slot = 0; slot < player.inventory.mainInventory.length && drop.count > 0; slot++) {
                ItemStack current = player.inventory.mainInventory[slot];
                if (pass == 0) {
                    if (!template.isStackable() || current == null
                        || current.stackSize >= limit
                        || !ItemInputKey.of(template)
                            .equals(ItemInputKey.of(current)))
                        continue;
                    int amount = (int) Math.min(drop.count, (long) limit - current.stackSize);
                    current.stackSize += amount;
                    drop.count -= amount;
                    current.animationsToGo = 5;
                } else if (current == null) {
                    ItemStack inserted = template.copy();
                    inserted.stackSize = (int) Math.min(drop.count, (long) limit);
                    player.inventory.mainInventory[slot] = inserted;
                    drop.count -= inserted.stackSize;
                    inserted.animationsToGo = 5;
                }
            }
        }
    }

    /** Retains unissued counts when the destination is unavailable; issued stacks are never retried. */
    boolean flush(World world, double x, double y, double z) {
        if (isEmpty()) return true;
        if (world == null || world.isRemote
            || world.restoringBlockSnapshots
            || !world.getChunkProvider()
                .chunkExists((int) Math.floor(x / 16D), (int) Math.floor(z / 16D)))
            return false;
        Iterator<Drop> iterator = drops.values()
            .iterator();
        while (iterator.hasNext()) {
            Drop drop = iterator.next();
            while (drop.count > 0) {
                ItemStack stack = drop.template.copy();
                // Vanilla stack counts are saved as bytes; also honor smaller item-specific limits.
                int limit = stack.isStackable() ? Math.min(64, stack.getMaxStackSize()) : 1;
                if (limit <= 0) return false;
                int amount = (int) Math.min((long) limit, drop.count);
                stack.stackSize = amount;
                EntityItem entity = new EntityItem(world, x, y, z, stack);
                entity.delayBeforeCanPickup = 10;
                entity.motionX = 0D;
                entity.motionZ = 0D;
                try {
                    // A loaded-world event veto may mean another mod already took the items.
                    world.spawnEntityInWorld(entity);
                } catch (RuntimeException failure) {
                    if (entity.addedToChunk) drop.count -= amount;
                    throw failure;
                }
                drop.count -= amount;
            }
            if (drop.quarantinedCount <= 0) iterator.remove();
        }
        return isEmpty();
    }

    NBTTagList writeToNbt() {
        NBTTagList entries = new NBTTagList();
        for (Drop drop : drops.values()) {
            if (drop.count > 0) entries.appendTag(recoveryEntry(drop, drop.count, false));
            if (drop.quarantinedCount > 0) entries.appendTag(recoveryEntry(drop, drop.quarantinedCount, true));
        }
        return entries;
    }

    private static NBTTagCompound recoveryEntry(Drop drop, long count, boolean quarantined) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setTag("item", drop.template.writeToNBT(new NBTTagCompound()));
        entry.setLong("count", count);
        if (quarantined) entry.setBoolean("quarantined", true);
        return entry;
    }

    void readFromNbt(NBTTagList entries) {
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            long count = entry.getLong("count");
            add(stack, count, entry.getBoolean("quarantined"));
        }
    }
}
