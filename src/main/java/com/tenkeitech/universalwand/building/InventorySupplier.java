/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.universalwand.UniversalWandStorage;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

import cpw.mods.fml.common.Loader;

/** Planning reserves counts only. Each execution must acquire one real item again. */
public final class InventorySupplier implements BuildingSupplier {

    private final EntityPlayer player;
    private final int wandSlot;
    private final ItemStack material;
    private final boolean aeEnabled;
    private final NBTTagCompound binding;
    private int remaining;
    private int aeAvailable;
    private boolean aeMaterialTaken;

    public InventorySupplier(BuildingContext context) {
        player = context.player;
        wandSlot = player.inventory.currentItem;
        aeEnabled = context.core == BuildingCore.NORMAL && ItemUniversalWand.isBuildingAeEnabled(context.wand);
        binding = ItemUniversalWand.getAeBinding(context.wand);
        ItemStack selected = Loader.isModLoaded("backhand") ? UniversalWandBackhandIntegration.getOffhandItem(player)
            : null;
        BuildingMaterial placement = BuildingMaterial.from(selected);
        if (placement == null && context.core != BuildingCore.ANGEL) {
            // Forge's default getPickBlock calls Block.getItem, which is client-only in 1.7.10.
            // Use the same registered item and public metadata lookup on both preview and server paths.
            Item item = Item.getItemFromBlock(context.target);
            selected = item == null ? null
                : new ItemStack(
                    item,
                    1,
                    context.target.getDamageValue(context.world, context.origin.x, context.origin.y, context.origin.z));
            placement = BuildingMaterial.from(selected);
        }
        if (placement == null) {
            material = null;
            return;
        }
        material = placement.stack;
        if (player.capabilities.isCreativeMode) remaining = BuildingContext.MAX_BLOCKS;
        else {
            long count = 0;
            for (ItemStack stack : player.inventory.mainInventory) {
                if (sameMaterial(stack, material) && stack.stackSize > 0) count += stack.stackSize;
            }
            if (aeEnabled) {
                aeAvailable = player instanceof EntityPlayerMP mp
                    ? UniversalWandStorage.availableBuildingMaterial(mp, binding, material, BuildingContext.MAX_BLOCKS)
                    : TenkeiTech.proxy.getWandBuildingSupply(context, material);
                count += aeAvailable;
            }
            remaining = (int) Math.min(BuildingContext.MAX_BLOCKS, count);
        }
    }

    public boolean isSupported() {
        return material != null;
    }

    public boolean hasRemaining() {
        return remaining > 0;
    }

    public ItemStack getMaterial() {
        return material == null ? null : material.copy();
    }

    public int getAeAvailable() {
        return aeAvailable;
    }

    public PlacementSnapshot getPlaceSnapshot(BuildingContext context, Position source, Position destination) {
        if (!hasRemaining()) return null;
        PlacementSnapshot snapshot = PlacementSnapshot.create(context, source, destination, material);
        if (snapshot != null) remaining--;
        return snapshot;
    }

    private int findMaterialSlot() {
        // Loose main inventory first, preserving hotbar and offhand samples as long as possible.
        for (int slot = 9; slot < Math.min(36, player.inventory.mainInventory.length); slot++) {
            if (hasMaterial(slot)) return slot;
        }
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (hasMaterial(slot)) return slot;
        }
        return -1;
    }

    @Override
    public MaterialLease takeOne(ItemStack required) {
        if (!sameMaterial(material, required) || player.worldObj.isRemote) return null;
        if (player.capabilities.isCreativeMode) return new MaterialLease(player, -1, material);
        if (aeEnabled && player instanceof EntityPlayerMP mp) {
            ItemStack acquired = UniversalWandStorage.takeBuildingMaterial(mp, binding, material);
            if (acquired != null) {
                aeMaterialTaken = true;
                return new MaterialLease(mp, acquired, binding);
            }
        }
        int slot = findMaterialSlot();
        if (slot < 0) return null;
        ItemStack stack = player.inventory.mainInventory[slot];
        MaterialLease lease = new MaterialLease(player, slot, stack);
        if (--stack.stackSize == 0) player.inventory.mainInventory[slot] = null;
        player.inventory.markDirty();
        return lease;
    }

    private boolean hasMaterial(int slot) {
        if (slot == wandSlot) return false;
        ItemStack stack = player.inventory.mainInventory[slot];
        return stack != null && stack.stackSize > 0 && sameMaterial(stack, material);
    }

    @Override
    public void onFinished() {
        if (!aeMaterialTaken || !(player instanceof EntityPlayerMP mp)) return;
        aeMaterialTaken = false;
        if (UniversalWandStorage.isBuildingMaterialExhausted(mp, binding, material))
            mp.addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.ae_material_exhausted"));
    }

    public static boolean sameMaterial(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.isItemEqual(second)
            && ItemStack.areItemStackTagsEqual(first, second);
    }
}
