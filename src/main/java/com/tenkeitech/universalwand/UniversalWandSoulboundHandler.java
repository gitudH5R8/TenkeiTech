/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Iterator;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Death drops transfer ownership to PlayerPersisted until the living player has inventory space. */
public final class UniversalWandSoulboundHandler {

    private static final String PENDING = "tenkeitechWandSoulbound";
    private static final int SCHEMA = 1;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrops(PlayerDropsEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP player) || player instanceof FakePlayer
            || player.worldObj.isRemote
            || player.worldObj.getGameRules()
                .getGameRuleBooleanValue("keepInventory"))
            return;
        NBTTagCompound persisted = persisted(player);
        if (persisted.hasKey(PENDING) && !isCurrent(persisted)) {
            TenkeiTech.LOG.warn("Preserving unsupported Universal Wand soulbound data for {}", player.getUniqueID());
            return;
        }
        NBTTagCompound pending = persisted.getCompoundTag(PENDING);
        NBTTagList items = pending.getTagList("items", 10);
        Iterator<EntityItem> drops = event.drops.iterator();
        while (drops.hasNext()) {
            EntityItem drop = drops.next();
            ItemStack stack = drop.getEntityItem();
            if (!isWand(stack)) continue;
            NBTTagCompound item = new NBTTagCompound();
            stack.writeToNBT(item);
            items.appendTag(item);
            pending.setInteger("schemaVersion", SCHEMA);
            pending.setTag("items", items);
            persisted.setTag(PENDING, pending);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            // Only the serialized original is retained; no inventory or world copy remains.
            drops.remove();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP player) restore(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        NBTTagCompound persisted = persisted(player);
        if (persisted.hasKey(PENDING) && !isCurrent(persisted)) {
            TenkeiTech.LOG.warn("Preserving unsupported Universal Wand soulbound data for {}", player.getUniqueID());
        }
        restore(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP player
            && player.ticksExisted % 20 == 0) restore(player);
    }

    private static void restore(EntityPlayerMP player) {
        if (player instanceof FakePlayer || player.worldObj.isRemote || !player.isEntityAlive()) return;
        NBTTagCompound persisted = persisted(player);
        if (!isCurrent(persisted)) return;
        NBTTagCompound pending = persisted.getCompoundTag(PENDING);
        NBTTagList items = pending.getTagList("items", 10);
        boolean changed = false;
        for (int index = 0; index < items.tagCount();) {
            int slot = player.inventory.getFirstEmptyStack();
            if (slot < 0) break;
            ItemStack stack = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(index));
            if (!isWand(stack) || stack.stackSize > stack.getMaxStackSize()) {
                // Invalid entries remain available for inspection instead of being deleted or reinterpreted.
                index++;
                continue;
            }
            player.inventory.setInventorySlotContents(slot, stack);
            items.removeTag(index);
            changed = true;
        }
        if (items.tagCount() == 0) persisted.removeTag(PENDING);
        if (changed) {
            player.inventory.markDirty();
            player.sendContainerToPlayer(player.inventoryContainer);
        }
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        return player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static boolean isCurrent(NBTTagCompound persisted) {
        if (!persisted.hasKey(PENDING, 10)) return false;
        NBTTagCompound pending = persisted.getCompoundTag(PENDING);
        return pending.getInteger("schemaVersion") == SCHEMA && pending.getTag("items") instanceof NBTTagList items
            && (items.tagCount() == 0 || items.func_150303_d() == 10);
    }

    private static boolean isWand(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemUniversalWand;
    }
}
