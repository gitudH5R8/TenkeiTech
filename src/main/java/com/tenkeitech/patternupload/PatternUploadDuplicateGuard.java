/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.TenkeiTech;

/** Conservative raw-pattern comparison and AE refund; never edits an installed provider pattern. */
final class PatternUploadDuplicateGuard {

    private PatternUploadDuplicateGuard() {}

    static Scan scan(IInventory inventory, int slots, ItemStack pattern) {
        int free = 0;
        int duplicate = -1;
        try {
            for (int slot = 0; slot < slots; slot++) {
                ItemStack installed = inventory.getStackInSlot(slot);
                if (installed == null) {
                    if (inventory.isItemValidForSlot(slot, pattern)) free++;
                } else if (duplicate < 0 && matches(pattern, installed)) duplicate = slot;
            }
            return new Scan(free, duplicate, true);
        } catch (RuntimeException ignored) {
            return new Scan(free, duplicate, false);
        }
    }

    static final class Scan {

        final int freeSlots;
        final int duplicateSlot;
        final boolean complete;

        private Scan(int freeSlots, int duplicateSlot, boolean complete) {
            this.freeSlots = freeSlots;
            this.duplicateSlot = duplicateSlot;
            this.complete = complete;
        }
    }

    static boolean matches(ItemStack expected, ItemStack installed) {
        return expected != null && installed != null
            && installed.stackSize > 0
            && expected.getItem() == installed.getItem()
            && expected.getItemDamage() == installed.getItemDamage()
            && samePatternData(expected.getTagCompound(), installed.getTagCompound());
    }

    static boolean samePatternData(NBTTagCompound expected, NBTTagCompound installed) {
        if (expected == null || installed == null || !expected.hasKey("in", 9) || !expected.hasKey("out", 9))
            return false;
        // Only the fixed AE2 encoder's attribution is cosmetic. Preserve every other (including unknown) key.
        for (Object key : expected.func_150296_c()) {
            String name = (String) key;
            if (!"author".equals(name) && !expected.getTag(name)
                .equals(installed.getTag(name))) return false;
        }
        for (Object key : installed.func_150296_c()) {
            String name = (String) key;
            if (!"author".equals(name) && !expected.hasKey(name)) return false;
        }
        return true;
    }

    enum RefundResult {
        REFUNDED,
        REJECTED,
        FAILED,
        UNCERTAIN
    }

    interface RefundDestination {

        /** Accept the whole single blank, or reject it. Simulation must not mutate storage. */
        boolean insert(ItemStack blank, boolean simulate);
    }

    static RefundResult refundToNetwork(IInventory inventory, int slot, ItemStack expected, ItemStack blank,
        RefundDestination destination) {
        if (expected == null || expected.stackSize != 1 || blank == null || blank.stackSize != 1)
            return RefundResult.FAILED;
        ItemStack original = expected.copy();
        try {
            if (!ItemStack.areItemStacksEqual(original, inventory.getStackInSlot(slot))) return RefundResult.FAILED;
            if (!destination.insert(blank.copy(), true)) return RefundResult.REJECTED;
            // Simulation can call other mods. Revalidate before detaching the source on the server thread.
            if (!ItemStack.areItemStacksEqual(original, inventory.getStackInSlot(slot))) return RefundResult.FAILED;
            if (!writeSource(inventory, slot, null)) {
                return writeSource(inventory, slot, original) ? RefundResult.FAILED
                    : uncertain(original, "Cannot verify source restoration before AE deposit", null);
            }
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Could not prepare a duplicate pattern refund", exception);
            return RefundResult.FAILED;
        }

        boolean accepted;
        try {
            accepted = destination.insert(blank.copy(), false);
        } catch (RuntimeException exception) {
            // AE's public storage API has no rollback token. A broken cell/notification can throw after accepting
            // the item: never restore/retry blindly and create another pattern. Record the source for recovery.
            return uncertain(original, "AE deposit threw without a receipt", exception);
        }
        if (accepted) return RefundResult.REFUNDED;
        // A normal rejection has an explicit remainder, so restoring the original is safe.
        return writeSource(inventory, slot, original) ? RefundResult.REJECTED
            : uncertain(original, "Cannot restore source after AE rejected the deposit", null);
    }

    private static RefundResult uncertain(ItemStack original, String reason, RuntimeException exception) {
        TenkeiTech.LOG.error(
            "Pattern refund outcome is uncertain ({}); check storage and terminal before recovering source {}",
            reason,
            original.writeToNBT(new NBTTagCompound()),
            exception);
        return RefundResult.UNCERTAIN;
    }

    private static boolean writeSource(IInventory inventory, int slot, ItemStack stack) {
        try {
            inventory.setInventorySlotContents(slot, stack == null ? null : stack.copy());
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Pattern refund source setter failed; checking its postcondition", exception);
        }
        try {
            if (!ItemStack.areItemStacksEqual(stack, inventory.getStackInSlot(slot))) return false;
            try {
                inventory.markDirty();
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.error("Pattern refund source changed but its dirty notification failed", exception);
            }
            return true;
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Could not verify the pattern refund source", exception);
            return false;
        }
    }
}
