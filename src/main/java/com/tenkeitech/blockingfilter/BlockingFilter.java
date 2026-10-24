/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.blockingfilter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.glodblock.github.common.item.ItemFluidPacket;
import com.tenkeitech.item.ItemBlockingFilterCard;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorConduitBandle;
import appeng.util.inv.ItemSlot;

/** Immutable, per-interface matching snapshot. No quantities or physical materials are retained. */
public final class BlockingFilter {

    public static final BlockingFilter EMPTY = new BlockingFilter();
    private final boolean precise;
    private final Set<Key> identities = new HashSet<>();

    private BlockingFilter() {
        precise = true;
    }

    public BlockingFilter(ItemStack card) {
        precise = ((ItemBlockingFilterCard) card.getItem()).precise();
        BlockingFilterConfig config = new BlockingFilterConfig(card);
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            var marker = config.getAEStackInSlot(slot);
            if (marker instanceof IAEFluidStack fluid) add(fluid.getFluidStack());
            else if (marker instanceof IAEItemStack item) {
                ItemStack stack = item.getItemStack();
                if (stack.getItem() instanceof ItemFluidPacket) add(ItemFluidPacket.getFluidStack(stack));
                else identities.add(key(stack.getItem(), stack.getItemDamage(), stack.getTagCompound(), true));
            }
        }
    }

    private void add(FluidStack fluid) {
        if (fluid != null && fluid.getFluid() != null) identities.add(key(fluid.getFluid(), 0, fluid.tag, true));
    }

    private Key key(Object type, int metadata, NBTTagCompound tag, boolean snapshot) {
        return new Key(type, metadata, !precise || tag == null ? null : snapshot ? (NBTTagCompound) tag.copy() : tag);
    }

    public boolean empty() {
        return identities.isEmpty();
    }

    public boolean ignores(ItemStack stack) {
        if (stack == null || empty()) return false;
        // The fluid's identity lives inside packet NBT: decode before applying the NBT policy.
        if (stack.getItem() instanceof ItemFluidPacket) return ignores(ItemFluidPacket.getFluidStack(stack));
        return identities.contains(key(stack.getItem(), stack.getItemDamage(), stack.getTagCompound(), false));
    }

    public boolean ignores(FluidStack fluid) {
        return fluid != null && !empty() && identities.contains(key(fluid.getFluid(), 0, fluid.tag, false));
    }

    public boolean hasBlockingFluid(IFluidHandler handler, ForgeDirection side) {
        var tanks = handler.getTankInfo(side);
        if (tanks == null) return true;
        for (var tank : tanks)
            if (tank != null && tank.fluid != null && tank.fluid.amount > 0 && !ignores(tank.fluid)) return true;
        return false;
    }

    /** Keep opaque in-transit conduit contents blocking, as AE does. */
    public boolean hasBlockingContents(InventoryAdaptor adaptor) {
        if (!adaptor.containsItems()) return false;
        if (adaptor instanceof AdaptorConduitBandle) return true;
        boolean observed = false;
        for (ItemSlot slot : adaptor) {
            ItemStack stack = slot.getItemStack();
            if (stack != null) {
                observed = true;
                if (!ignores(stack)) return true;
            }
        }
        // A nonempty adapter with no exposed contents cannot prove that everything was marked.
        return !observed;
    }

    /** Used only for the fluid branch of AE's GT inventory check. */
    public boolean hasBlockingPacket(InventoryAdaptor adaptor) {
        for (ItemSlot slot : adaptor) {
            ItemStack stack = slot.getItemStack();
            if (stack != null && stack.getItem() instanceof ItemFluidPacket && !ignores(stack)) return true;
        }
        return false;
    }

    private static final class Key {

        private final Object type;
        private final int metadata;
        private final NBTTagCompound tag;

        private Key(Object type, int metadata, NBTTagCompound tag) {
            this.type = type;
            this.metadata = metadata;
            this.tag = tag;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Key other && type == other.type
                && metadata == other.metadata
                && Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(type) + metadata) + Objects.hashCode(tag);
        }
    }
}
