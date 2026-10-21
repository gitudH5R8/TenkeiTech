/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.UniversalWandStorage;

/** Owns one actually acquired inventory item until the placement commits or returns it. */
public final class MaterialLease implements AutoCloseable {

    private final EntityPlayer player;
    private final int slot;
    private final ItemStack material;
    private final NBTTagCompound aeBinding;
    private boolean committed;
    private boolean closed;

    MaterialLease(EntityPlayer player, int slot, ItemStack material) {
        this.player = player;
        this.slot = slot;
        this.material = material.copy();
        this.material.stackSize = 1;
        this.aeBinding = null;
    }

    MaterialLease(EntityPlayerMP player, ItemStack material, NBTTagCompound binding) {
        this.player = player;
        this.slot = -1;
        this.material = material.copy();
        this.material.stackSize = 1;
        this.aeBinding = (NBTTagCompound) binding.copy();
    }

    public void commit() {
        committed = true;
    }

    public ItemStack getPaidMaterial() {
        return slot < 0 && aeBinding == null ? null : material.copy();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (committed) return;
        if (aeBinding != null) {
            UniversalWandMiningHandler.refundBuildingMaterials(
                (EntityPlayerMP) player,
                java.util.Collections.singletonList(material),
                UniversalWandStorage.Destination.forAeReturn(aeBinding));
            return;
        }
        if (slot < 0) return;
        ItemStack current = player.inventory.mainInventory[slot];
        if (current == null) player.inventory.mainInventory[slot] = material;
        else if (InventorySupplier.sameMaterial(current, material) && current.stackSize < current.getMaxStackSize())
            current.stackSize++;
        else if (!player.inventory.addItemStackToInventory(material))
            player.dropPlayerItemWithRandomChoice(material, false);
        player.inventory.markDirty();
    }
}
