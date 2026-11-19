/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;

import com.gtnewhorizon.structurelib.SortedRegistry;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.util.InventoryIterable;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.tenkeitech.TenkeiTech;

/** Native extraction and ordered writeback. Unknown insertion results are isolated rather than replayed. */
public final class BetterMachineProjectorStorage implements IItemSource {

    private static final String RETURNS = "tenkeitechBetterMachineProjectorReturns";
    private final EntityPlayerMP player;
    private final IItemSource nativeSource;
    private final List<Target> preferred = new ArrayList<>();

    public BetterMachineProjectorStorage(EntityPlayerMP player) {
        this.player = player;
        nativeSource = IItemSource.fromPlayer(player);
    }

    @Override
    public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
        Map<ItemStack, Integer> taken = nativeSource.take(predicate, simulate, count);
        BetterMachineProjectorBuild.recordExtraction(
            this,
            count,
            taken.values()
                .stream()
                .mapToLong(Integer::longValue)
                .sum(),
            simulate);
        return taken;
    }

    @Override
    public boolean takeOne(ItemStack stack, boolean simulate) {
        if (!simulate) rememberSource(stack);
        boolean taken = nativeSource.takeOne(stack, simulate);
        BetterMachineProjectorBuild.recordExtraction(this, 1, taken ? 1 : 0, simulate);
        return taken;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void rememberSource(ItemStack requested) {
        preferred.clear();
        SortedRegistry<?> registry = SortedRegistry.getRegistry("inventoryProviders");
        if (registry == null) return;
        for (Object value : registry.getPlayerOrdering(player)) {
            if (!(value instanceof InventoryUtility.InventoryProvider provider)) continue;
            Iterable<ItemStack> inventory = provider.getInventory(player);
            if (inventory == null) continue;
            for (ItemStack source : inventory) {
                if (source == null) continue;
                if (source.isItemEqual(requested) && ItemStack.areItemStackTagsEqual(source, requested)) {
                    // Even when replacement materials came from the backpack, return external storage first.
                    if (inventory instanceof InventoryIterable<?>iterable
                        && iterable.getInventory() != player.inventory)
                        preferred.add(new Target(iterable.getInventory()));
                    return;
                }
                Iterator<? extends InventoryUtility.ItemStackExtractor> extractors = InventoryUtility
                    .getStackExtractors(player);
                while (extractors.hasNext()) {
                    InventoryUtility.ItemStackExtractor extractor = extractors.next();
                    if (!extractor.isValidSource(source, player)) continue;
                    if (extractor instanceof BetterMachineProjectorReturnTarget destination
                        && extractor.isAPIImplemented(InventoryUtility.ItemStackExtractor.APIType.EXTRACT_ONE_STACK)
                        && extractor.getItem(source, requested, true, player) > 0) {
                        preferred.add(new Target(source, destination));
                        return;
                    }
                    if (extractor.isAPIImplemented(InventoryUtility.ItemStackExtractor.APIType.IS_VALID_SOURCE)) break;
                }
            }
        }
    }

    public void give(ItemStack items) {
        if (items != null) giveAll(java.util.Collections.singletonList(items));
    }

    public void giveAll(List<ItemStack> items) {
        NBTTagList records = records(player);
        List<NBTTagCompound> added = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.stackSize <= 0) continue;
            ItemStack remaining = item.copy();
            while (remaining.stackSize > 0) {
                ItemStack portion = remaining
                    .splitStack(Math.min(remaining.stackSize, Math.min(64, Math.max(1, remaining.getMaxStackSize()))));
                NBTTagCompound record = new NBTTagCompound();
                record.setTag("items", portion.writeToNBT(new NBTTagCompound()));
                records.appendTag(record);
                added.add(record);
            }
        }
        persisted(player).setTag(RETURNS, records);
        try {
            for (NBTTagCompound record : added) drain(record, true);
        } finally {
            compact(player);
        }
    }

    private void drain(NBTTagCompound record, boolean configuredSources) {
        if (record.getBoolean("uncertain")) return;
        ItemStack remainder = ItemStack.loadItemStackFromNBT(record.getCompoundTag("items"));
        if (remainder == null || remainder.stackSize <= 0) {
            record.setBoolean("done", true);
            return;
        }
        try {
            if (configuredSources) {
                for (Target target : preferred) {
                    remainder = target.insert(player, remainder);
                    saveRemainder(record, remainder);
                    if (remainder == null || remainder.stackSize <= 0) return;
                }
                remainder = returnConfigured(remainder, record);
                if (remainder == null || remainder.stackSize <= 0) return;
            }
            insert(player.inventory, remainder, player);
            saveRemainder(record, remainder);
            while (remainder.stackSize > 0) {
                ItemStack drop = remainder.copy();
                drop.stackSize = Math.min(drop.getMaxStackSize(), remainder.stackSize);
                int dropping = drop.stackSize;
                EntityItem entity = new EntityItem(player.worldObj, player.posX, player.posY + 0.5D, player.posZ, drop);
                entity.delayBeforeCanPickup = 10;
                if (!player.worldObj.spawnEntityInWorld(entity)) return;
                remainder.stackSize -= dropping;
                saveRemainder(record, remainder);
            }
        } catch (RuntimeException error) {
            record.setBoolean("uncertain", true);
            TenkeiTech.LOG.error(
                "Machine projector writeback has unknown result for player {}. Return record retained without automatic replay.",
                player.getUniqueID(),
                error);
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.better_machine_projector.message.return_uncertain"));
            throw error;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ItemStack returnConfigured(ItemStack remainder, NBTTagCompound record) {
        SortedRegistry<?> registry = SortedRegistry.getRegistry("inventoryProviders");
        if (registry == null) return remainder;
        for (Object value : registry.getPlayerOrdering(player)) {
            if (!(value instanceof InventoryUtility.InventoryProvider provider)) continue;
            Iterable<ItemStack> inventory = provider.getInventory(player);
            if (inventory == null) continue;
            for (ItemStack source : inventory) {
                if (source == null) continue;
                Iterator<? extends InventoryUtility.ItemStackExtractor> extractors = InventoryUtility
                    .getStackExtractors(player);
                while (extractors.hasNext()) {
                    InventoryUtility.ItemStackExtractor extractor = extractors.next();
                    if (!extractor.isValidSource(source, player)) continue;
                    if (extractor instanceof BetterMachineProjectorReturnTarget target) {
                        remainder = target.tenkeitech$returnItems(source, player, remainder);
                        saveRemainder(record, remainder);
                        provider.markDirty(inventory);
                        if (remainder == null || remainder.stackSize <= 0) return null;
                    }
                    if (extractor.isAPIImplemented(InventoryUtility.ItemStackExtractor.APIType.IS_VALID_SOURCE)) break;
                }
            }
            // Still inspect carried storage tools above, but defer the player's own slots to the final fallback.
            if (inventory instanceof InventoryIterable<?>iterable && iterable.getInventory() != player.inventory) {
                insert(iterable.getInventory(), remainder, player);
                saveRemainder(record, remainder);
                provider.markDirty(inventory);
                if (remainder.stackSize <= 0) return null;
            }
        }
        return remainder;
    }

    private static void saveRemainder(NBTTagCompound record, ItemStack remainder) {
        if (remainder == null || remainder.stackSize <= 0) record.setBoolean("done", true);
        else record.setTag("items", remainder.writeToNBT(new NBTTagCompound()));
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        if (!player.getEntityData()
            .hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10))
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static NBTTagList records(EntityPlayer player) {
        return persisted(player).getTagList(RETURNS, 10);
    }

    private static void compact(EntityPlayer player) {
        NBTTagList records = records(player);
        for (int i = records.tagCount() - 1; i >= 0; i--) if (records.getCompoundTagAt(i)
            .getBoolean("done")) records.removeTag(i);
        if (records.tagCount() == 0) persisted(player).removeTag(RETURNS);
    }

    public static void recover(EntityPlayerMP player) {
        NBTTagList records = records(player);
        if (records.tagCount() == 0) return;
        BetterMachineProjectorStorage storage = new BetterMachineProjectorStorage(player);
        int attempted = 0;
        for (int i = 0; i < records.tagCount() && attempted < 16; i++) {
            if (records.getCompoundTagAt(i)
                .getBoolean("uncertain")) continue;
            attempted++;
            try {
                storage.drain(records.getCompoundTagAt(i), false);
            } catch (RuntimeException error) {
                break;
            }
        }
        compact(player);
    }

    private static void insert(IInventory inventory, ItemStack stack, EntityPlayerMP player) {
        int slots = inventory == player.inventory ? player.inventory.mainInventory.length
            : inventory.getSizeInventory();
        for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++)
            for (int slot = 0; slot < slots && stack.stackSize > 0; slot++) {
                if (!inventory.isItemValidForSlot(slot, stack)) continue;
                ItemStack existing = inventory.getStackInSlot(slot);
                if ((pass == 0 && existing == null) || (pass == 1 && existing != null)) continue;
                if (existing != null
                    && (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack))) continue;
                int amount = Math.min(
                    stack.stackSize,
                    Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit())
                        - (existing == null ? 0 : existing.stackSize));
                if (amount <= 0) continue;
                if (existing == null) inventory.setInventorySlotContents(slot, stack.splitStack(amount));
                else {
                    existing.stackSize += amount;
                    stack.stackSize -= amount;
                }
            }
        inventory.markDirty();
    }

    private static final class Target {

        private final IInventory inventory;
        private final ItemStack source;
        private final BetterMachineProjectorReturnTarget target;

        private Target(IInventory inventory) {
            this.inventory = inventory;
            source = null;
            target = null;
        }

        private Target(ItemStack source, BetterMachineProjectorReturnTarget target) {
            this.source = source;
            this.target = target;
            inventory = null;
        }

        private ItemStack insert(EntityPlayerMP player, ItemStack items) {
            if (inventory != null) {
                BetterMachineProjectorStorage.insert(inventory, items, player);
                return items;
            }
            return target.tenkeitech$returnItems(source, player, items);
        }
    }
}
