/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.inventory.ItemInputKey;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.ILocatable;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;

/** Uses an already loaded wireless access point; never creates nodes, connections or chunk tickets. */
public final class UniversalWandStorage {

    private static final String EXTRACTION_RECOVERY = "tenkeitechWandAeExtractionRecovery";

    public static final class Destination {

        final boolean aePriority;
        final boolean inventory;
        final NBTTagCompound binding;

        public Destination(ItemStack wand) {
            aePriority = ItemUniversalWand.isAePriority(wand);
            // AE priority sends any remainder to the player's inventory before dropping it.
            inventory = ItemUniversalWand.isInventoryCollection(wand) || aePriority;
            binding = ItemUniversalWand.getAeBinding(wand);
        }

        Destination(boolean aePriority, boolean inventory) {
            this(aePriority, inventory, new NBTTagCompound());
        }

        private Destination(boolean aePriority, boolean inventory, NBTTagCompound binding) {
            this.aePriority = aePriority;
            this.inventory = inventory;
            this.binding = (NBTTagCompound) binding.copy();
        }

        public static Destination forBuilding(ItemStack wand) {
            return new Destination(
                ItemUniversalWand.getBuildingCore(wand) == BuildingCore.NORMAL
                    && ItemUniversalWand.isBuildingAeEnabled(wand),
                true,
                ItemUniversalWand.getAeBinding(wand));
        }

        public static Destination forAeReturn(NBTTagCompound binding) {
            return new Destination(true, true, binding);
        }

        boolean collects() {
            return aePriority || inventory;
        }
    }

    private UniversalWandStorage() {}

    static final class UnknownInsertionOutcome extends RuntimeException {

        UnknownInsertionOutcome(Throwable cause) {
            super("AE insertion did not return a trustworthy remainder", cause);
        }
    }

    public static final class UnknownExtractionOutcome extends RuntimeException {

        private UnknownExtractionOutcome(Throwable cause) {
            super("AE extraction did not return a trustworthy acquired stack", cause);
        }
    }

    public static void toggleBinding(EntityPlayerMP player, int x, int y, int z) {
        ItemStack wand = player.getHeldItem();
        if (player.isDead || !ItemUniversalWand.isHeldBy(player)
            || !player.worldObj.blockExists(x, y, z)
            || !UniversalWandActions.isTarget(player, x, y, z)) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_invalid"));
            return;
        }
        TileEntity tile = player.worldObj.getTileEntity(x, y, z);
        if (!(tile instanceof IWirelessAccessPoint point) || tile.isInvalid()) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_invalid"));
            return;
        }
        NBTTagCompound existing = ItemUniversalWand.getAeBinding(wand);
        if (sameLocation(existing, player.dimension, x, y, z) && matchesBinding(tile, existing)) {
            if (UniversalWandRightClick.handled(player, ItemUniversalWand.setAeBinding(player, wand, null)))
                player.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_unbound"));
            return;
        }
        if (!point.isActive() || !(canInsert(player, point.getGrid())
            || canAccess(player, point.getGrid(), SecurityPermissions.EXTRACT))) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_invalid"));
            return;
        }
        NBTTagCompound binding = new NBTTagCompound();
        binding.setInteger("dimension", player.dimension);
        binding.setInteger("x", x);
        binding.setInteger("y", y);
        binding.setInteger("z", z);
        if (tile instanceof ILocatable locatable) binding.setLong("serial", locatable.getLocatableSerial());
        if (UniversalWandRightClick.handled(player, ItemUniversalWand.setAeBinding(player, wand, binding)))
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.universal_wand.ae_bound", player.dimension, x, y, z));
    }

    private static boolean sameLocation(NBTTagCompound binding, int dimension, int x, int y, int z) {
        return hasLocation(binding) && binding.getInteger("dimension") == dimension
            && binding.getInteger("x") == x
            && binding.getInteger("y") == y
            && binding.getInteger("z") == z;
    }

    /** Clears only confirmed removal or replacement; offline and unloaded targets retain their binding. */
    public static void validateHeldBinding(EntityPlayerMP player, ItemStack wand) {
        if (player.getHeldItem() != wand || !ItemUniversalWand.isHeldBy(player)) return;
        NBTTagCompound binding = ItemUniversalWand.getAeBinding(wand);
        if (binding.hasNoTags()) return;
        if (!hasLocation(binding)) {
            ItemUniversalWand.setAeBinding(player, wand, null);
            return;
        }
        World world = DimensionManager.getWorld(binding.getInteger("dimension"));
        int x = binding.getInteger("x"), y = binding.getInteger("y"), z = binding.getInteger("z");
        if (world == null || !world.blockExists(x, y, z)) return;
        if (world.isAirBlock(x, y, z) || !matchesBinding(world.getTileEntity(x, y, z), binding))
            ItemUniversalWand.setAeBinding(player, wand, null);
    }

    private static boolean hasLocation(NBTTagCompound binding) {
        return binding.hasKey("dimension", 3) && binding.hasKey("x", 3)
            && binding.hasKey("y", 3)
            && binding.hasKey("z", 3);
    }

    private static boolean matchesBinding(TileEntity tile, NBTTagCompound binding) {
        return tile instanceof IWirelessAccessPoint && !tile.isInvalid()
            && (!binding.hasKey("serial", 4) || (tile instanceof ILocatable locatable
                && binding.getLong("serial") == locatable.getLocatableSerial()));
    }

    private static IWirelessAccessPoint resolve(NBTTagCompound binding) {
        if (!hasLocation(binding)) return null;
        World world = DimensionManager.getWorld(binding.getInteger("dimension"));
        int x = binding.getInteger("x"), y = binding.getInteger("y"), z = binding.getInteger("z");
        if (world == null || !world.blockExists(x, y, z) || world.isAirBlock(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!matchesBinding(tile, binding)) return null;
        IWirelessAccessPoint point = (IWirelessAccessPoint) tile;
        return point.isActive() ? point : null;
    }

    private static boolean canInsert(EntityPlayerMP player, IGrid grid) {
        return canAccess(player, grid, SecurityPermissions.INJECT);
    }

    private static boolean canAccess(EntityPlayerMP player, IGrid grid, SecurityPermissions permission) {
        if (grid == null) return false;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return security != null && security.hasPermission(player, permission);
    }

    public static int availableBuildingMaterial(EntityPlayerMP player, NBTTagCompound binding, ItemStack material,
        int limit) {
        IAEItemStack available = extractBuildingMaterial(player, binding, material, limit, Actionable.SIMULATE);
        return available == null ? 0 : (int) available.getStackSize();
    }

    public static ItemStack takeBuildingMaterial(EntityPlayerMP player, NBTTagCompound binding, ItemStack material) {
        IAEItemStack acquired = extractBuildingMaterial(player, binding, material, 1, Actionable.MODULATE);
        if (acquired == null) return null;
        ItemStack result = material.copy();
        result.stackSize = 1;
        return result;
    }

    /** Only an authorized, online inventory can confirm depletion; an unavailable network cannot. */
    public static boolean isBuildingMaterialExhausted(EntityPlayerMP player, NBTTagCompound binding,
        ItemStack material) {
        if (material == null || extractionQuarantined(player, binding)) return false;
        try {
            IWirelessAccessPoint point = resolve(binding);
            if (point == null) return false;
            IGrid grid = point.getGrid();
            if (!canAccess(player, grid, SecurityPermissions.EXTRACT)) return false;
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null || storage.getItemInventory() == null) return false;
            IAEItemStack request = AEApi.instance()
                .storage()
                .createItemStack(material.copy());
            if (request == null) return false;
            request.setStackSize(1);
            IAEItemStack available = storage.getItemInventory()
                .extractItems(request, Actionable.SIMULATE, new PlayerSource(player, point));
            return available == null || available.getStackSize() == 0;
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.warn("Universal Wand AE depletion check unavailable", failure);
            return false;
        }
    }

    private static IAEItemStack extractBuildingMaterial(EntityPlayerMP player, NBTTagCompound binding,
        ItemStack material, int count, Actionable action) {
        if (count <= 0 || material == null || extractionQuarantined(player, binding)) return null;
        IMEMonitor<IAEItemStack> inventory;
        IAEItemStack request;
        PlayerSource source;
        try {
            IWirelessAccessPoint point = resolve(binding);
            if (point == null) return null;
            IGrid grid = point.getGrid();
            if (!canAccess(player, grid, SecurityPermissions.EXTRACT)) return null;
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null || (inventory = storage.getItemInventory()) == null) return null;
            request = AEApi.instance()
                .storage()
                .createItemStack(material.copy());
            if (request == null) return null;
            request.setStackSize(count);
            source = new PlayerSource(player, point);
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.warn("Universal Wand AE unavailable before extraction; using inventory", failure);
            return null;
        }
        try {
            IAEItemStack extracted = inventory.extractItems(request, action, source);
            if (extracted == null) return null;
            long acquired = extracted.getStackSize();
            if (acquired < 0 || acquired > count
                || !ItemInputKey.of(material)
                    .equals(ItemInputKey.of(extracted.getItemStack())))
                throw new IllegalStateException("Invalid AE extraction result");
            if (acquired == 0) return null;
            return extracted;
        } catch (RuntimeException failure) {
            if (action == Actionable.SIMULATE) {
                TenkeiTech.LOG.warn("Universal Wand AE material simulation failed; using inventory", failure);
                return null;
            }
            NBTTagCompound persisted = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            NBTTagList entries = persisted.getTagList(EXTRACTION_RECOVERY, 10);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("binding", binding.copy());
            entry.setTag("requestedItem", material.writeToNBT(new NBTTagCompound()));
            entry.setInteger("requestedCount", count);
            entry.setBoolean("quarantined", true);
            entries.appendTag(entry);
            persisted.setTag(EXTRACTION_RECOVERY, entries);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_extract_unknown"));
            throw new UnknownExtractionOutcome(failure);
        }
    }

    private static boolean extractionQuarantined(EntityPlayerMP player, NBTTagCompound binding) {
        NBTTagList entries = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getTagList(EXTRACTION_RECOVERY, 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            if (entries.getCompoundTagAt(i)
                .getCompoundTag("binding")
                .equals(binding)) return true;
        }
        return false;
    }

    /** Returns the actual remainder. Commit exceptions must be quarantined by the caller. */
    static long insert(EntityPlayerMP player, Destination destination, ItemStack template, long count) {
        if (!destination.aePriority || count <= 0) return count;
        IMEMonitor<IAEItemStack> inventory;
        IAEItemStack request;
        PlayerSource source;
        try {
            IWirelessAccessPoint point = resolve(destination.binding);
            if (point == null) return count;
            IGrid grid = point.getGrid();
            if (!canInsert(player, grid)) return count;
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return count;
            inventory = storage.getItemInventory();
            if (inventory == null) return count;
            request = AEApi.instance()
                .storage()
                .createItemStack(template.copy());
            if (request == null) return count;
            request.setStackSize(count);
            source = new PlayerSource(player, point);
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.warn("Universal Wand AE unavailable before insertion; using fallback", failure);
            return count;
        }
        try {
            IAEItemStack remainder = inventory.injectItems(request, Actionable.MODULATE, source);
            if (remainder == null) return 0;
            long remaining = remainder.getStackSize();
            if (remaining < 0 || remaining > count
                || !ItemInputKey.of(template)
                    .equals(ItemInputKey.of(remainder.getItemStack())))
                throw new IllegalStateException("Invalid AE insertion remainder");
            return remaining;
        } catch (RuntimeException failure) {
            throw new UnknownInsertionOutcome(failure);
        }
    }
}
