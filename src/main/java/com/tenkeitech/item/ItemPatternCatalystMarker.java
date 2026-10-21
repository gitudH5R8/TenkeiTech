/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTUtility;

/** Hidden pattern input that records one non-consumable item catalyst without reusing the tool item ID. */
public final class ItemPatternCatalystMarker extends Item {

    private static final String NBT_MARKER = "tenkeitechCatalystMarker";
    private static final String NBT_SCHEMA = "schemaVersion";
    private static final String NBT_CATALYST = "catalyst";
    private static final int MARKER_SCHEMA = 1;

    public ItemPatternCatalystMarker() {
        setUnlocalizedName(TenkeiTech.MOD_ID + ".pattern_catalyst_marker");
        setTextureName("minecraft:paper");
        setMaxStackSize(1);
    }

    public ItemStack createMarker(ItemStack catalyst) {
        if (!isValidCatalyst(catalyst)) {
            throw new IllegalArgumentException("Cannot encode an invalid automatic catalyst");
        }

        ItemStack normalized = catalyst.copy();
        normalized.stackSize = 1;
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(NBT_SCHEMA, MARKER_SCHEMA);
        payload.setTag(NBT_CATALYST, GTUtility.saveItem(normalized));

        ItemStack marker = new ItemStack(this, 1);
        NBTTagCompound markerNbt = new NBTTagCompound();
        markerNbt.setTag(NBT_MARKER, payload);
        marker.setTagCompound(markerNbt);
        return marker;
    }

    public static boolean isMarker(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemPatternCatalystMarker
            && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey(NBT_MARKER, Constants.NBT.TAG_COMPOUND);
    }

    @Nullable
    public static ItemStack getCatalyst(ItemStack marker) {
        if (!isMarker(marker)) return null;
        NBTTagCompound payload = marker.getTagCompound()
            .getCompoundTag(NBT_MARKER);
        if (payload.getInteger(NBT_SCHEMA) != MARKER_SCHEMA
            || !payload.hasKey(NBT_CATALYST, Constants.NBT.TAG_COMPOUND)) return null;

        ItemStack catalyst;
        try {
            catalyst = GTUtility.loadItem(payload.getCompoundTag(NBT_CATALYST));
        } catch (RuntimeException exception) {
            return null;
        }
        if (!isValidCatalyst(catalyst)) return null;
        catalyst.stackSize = 1;
        return catalyst;
    }

    public static boolean isSameCatalyst(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.getItem() == second.getItem()
            && first.getItemDamage() == second.getItemDamage()
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ItemStack catalyst = getCatalyst(stack);
        if (catalyst == null) return;
        tooltip.add(
            EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("tenkeitech.item.pattern_catalyst_marker.tooltip"));
        tooltip.add(EnumChatFormatting.WHITE + catalyst.getDisplayName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        ItemStack catalyst = getCatalyst(stack);
        if (catalyst == null) return super.getIconIndex(stack);
        IIcon catalystIcon = catalyst.getIconIndex();
        return catalystIcon == null ? super.getIconIndex(stack) : catalystIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        ItemStack catalyst = getCatalyst(stack);
        if (catalyst == null) return super.getIcon(stack, pass);
        IIcon catalystIcon = catalyst.getItem()
            .getIcon(catalyst, pass);
        return catalystIcon == null ? super.getIcon(stack, pass) : catalystIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        ItemStack catalyst = getCatalyst(stack);
        return catalyst == null ? super.getColorFromItemStack(stack, pass)
            : catalyst.getItem()
                .getColorFromItemStack(catalyst, pass);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ItemStack catalyst = getCatalyst(stack);
        if (catalyst == null) return super.getItemStackDisplayName(stack);
        return StatCollector
            .translateToLocalFormatted("tenkeitech.item.pattern_catalyst_marker.name", catalyst.getDisplayName());
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return getCatalyst(stack) != null;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return getCatalyst(stack) != null;
    }

    private static boolean isValidCatalyst(ItemStack catalyst) {
        return catalyst != null && catalyst.getItem() != null
            && catalyst.stackSize >= 0
            && !(catalyst.getItem() instanceof ItemPatternCatalystMarker)
            && !(catalyst.getItem() instanceof ItemUniversalWand)
            && !GTUtility.isAnyIntegratedCircuit(catalyst);
    }
}
