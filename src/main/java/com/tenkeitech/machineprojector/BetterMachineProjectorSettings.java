/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.tenkeitech.item.ItemBetterMachineProjector;

/** Persistent preferences belong to the projector, never to StructureLib's global configuration. */
public final class BetterMachineProjectorSettings {

    public enum Mode {
        BUILD,
        REPLACE,
        DISMANTLE
    }

    public static final int MAX_REPEAT = 1024;
    public static final int MAX_BUDGET = 256;
    public static final int MAX_COOLDOWN = 20000;
    private static final String KEY = "tenkeitechBetterMachineProjector";

    private BetterMachineProjectorSettings() {}

    private static NBTTagCompound read(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(KEY) : new NBTTagCompound();
    }

    public static Mode mode(ItemStack stack) {
        int mode = read(stack).getInteger("mode");
        return mode >= 0 && mode < Mode.values().length ? Mode.values()[mode] : Mode.BUILD;
    }

    public static int repeat(ItemStack stack) {
        return number(stack, "repeat", 1, 1, MAX_REPEAT);
    }

    public static int budget(ItemStack stack) {
        return number(stack, "budget", 25, 1, MAX_BUDGET);
    }

    public static int cooldown(ItemStack stack) {
        return number(stack, "cooldown", 300, 0, MAX_COOLDOWN);
    }

    public static boolean skipMissing(ItemStack stack) {
        return read(stack).getBoolean("skipMissing");
    }

    public static void setSkipMissing(EntityPlayer player, ItemStack stack, boolean enabled) {
        edit(player, stack, tag -> {
            NBTTagCompound settings = tag.getCompoundTag(KEY);
            settings.setInteger("schemaVersion", 1);
            settings.setBoolean("skipMissing", enabled);
            tag.setTag(KEY, settings);
        });
    }

    private static int number(ItemStack stack, String key, int fallback, int min, int max) {
        NBTTagCompound tag = read(stack);
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) return fallback;
        return Math.max(min, Math.min(max, tag.getInteger(key)));
    }

    public static boolean owns(EntityPlayer player, ItemStack stack) {
        return player != null && stack != null
            && player.getHeldItem() == stack
            && stack.getItem() instanceof ItemBetterMachineProjector;
    }

    public static void set(EntityPlayer player, ItemStack stack, String key, int value) {
        if (!owns(player, stack) || player.worldObj.isRemote) return;
        switch (key) {
            case "mode":
                value = Math.max(0, Math.min(Mode.values().length - 1, value));
                break;
            case "repeat":
                value = Math.max(1, Math.min(MAX_REPEAT, value));
                break;
            case "budget":
                value = Math.max(1, Math.min(MAX_BUDGET, value));
                break;
            case "cooldown":
                value = Math.max(0, Math.min(MAX_COOLDOWN, value));
                break;
            default:
                return;
        }
        final int accepted = value;
        edit(player, stack, tag -> {
            NBTTagCompound settings = tag.getCompoundTag(KEY);
            settings.setInteger("schemaVersion", 1);
            settings.setInteger(key, accepted);
            tag.setTag(KEY, settings);
        });
    }

    public static void edit(EntityPlayer player, ItemStack stack, Consumer<NBTTagCompound> edit) {
        if (!owns(player, stack) || player.worldObj.isRemote) return;
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        stack.setTagCompound(tag);
        edit.accept(tag);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    /** A transient trigger; its virtual count must never be inserted into an inventory. */
    public static ItemStack trigger(ItemStack projector) {
        ItemStack trigger = projector.copy();
        trigger.stackSize = repeat(projector);
        return trigger;
    }
}
