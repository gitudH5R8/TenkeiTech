/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.machine.MTESuperCraftingInputME;

import appeng.api.AEApi;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Copies or moves the internal configuration of an ME Super Pattern Buffer through a versioned NBT envelope. */
public final class ItemPatternBufferTransferTool extends Item {

    public enum Mode {

        COPY("copy"),
        CUT("cut");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum TransferResult {

        COPY_STORED("copy_stored"),
        COPY_PASTED("copy_pasted"),
        CUT_STORED("cut_stored"),
        CUT_PASTED("cut_pasted"),
        SOURCE_EMPTY("source_empty"),
        NO_STORED_DATA("no_stored_data"),
        CUT_TOOL_NOT_EMPTY("cut_tool_not_empty"),
        DATA_TOO_LARGE("data_too_large"),
        INVALID_DATA("invalid_data"),
        TARGET_CAPACITY("target_capacity"),
        TARGET_NOT_EMPTY("target_not_empty"),
        MISSING_BLANK_PATTERNS("missing_blank_patterns"),
        REFUND_FAILED("refund_failed");

        private final String translationSuffix;

        TransferResult(String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        public String translationKey() {
            return "tenkeitech.item.pattern_buffer_transfer.result." + translationSuffix;
        }
    }

    public static final int TRANSFER_SCHEMA_VERSION = 1;
    public static final int MAX_COMPRESSED_DATA_BYTES = 1024 * 1024;

    private static final String NBT_TRANSFER = "tenkeitechPatternBufferTransfer";
    private static final String NBT_SCHEMA_VERSION = "schemaVersion";
    private static final String NBT_MODE = "mode";
    private static final String NBT_SOURCE_CAPACITY = "sourceCapacity";
    private static final String NBT_PATTERN_COUNT = "patternCount";
    private static final String NBT_PAYLOAD = "payload";

    private final Mode mode;

    public ItemPatternBufferTransferTool(Mode mode) {
        this.mode = mode;
        setUnlocalizedName(TenkeiTech.MOD_ID + ".pattern_buffer_" + mode.serializedName() + "_tool");
        setTextureName("minecraft:paper");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity gregTechTile)) return false;
        IMetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
        if (!(metaTileEntity instanceof MTESuperCraftingInputME machine)) return false;

        // In 1.7.10, a true client result stops PlayerControllerMP before it sends the use packet.
        if (world.isRemote) return false;

        TransferResult result;
        if (player.isSneaking()) {
            result = mode == Mode.COPY ? machine.capturePatternCopy(stack) : machine.capturePatternCut(stack);
        } else {
            result = mode == Mode.COPY ? machine.pastePatternCopy(stack, player) : machine.pastePatternCut(stack);
            if (mode == Mode.CUT && result == TransferResult.CUT_PASTED) clearStoredData(stack);
        }
        if (result == TransferResult.COPY_STORED || result == TransferResult.COPY_PASTED
            || result == TransferResult.CUT_STORED
            || result == TransferResult.CUT_PASTED) {
            player.inventory.markDirty();
            if (player instanceof EntityPlayerMP serverPlayer) {
                serverPlayer.sendContainerToPlayer(serverPlayer.inventoryContainer);
            }
        }
        player.addChatMessage(new ChatComponentTranslation(result.translationKey()));
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        String prefix = "tenkeitech.item.pattern_buffer_" + mode.serializedName() + "_tool.tooltip.";
        tooltip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal(prefix + "1"));
        tooltip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal(prefix + "2"));
        NBTTagCompound transfer = getValidTransferTag(stack, mode);
        if (transfer != null) {
            tooltip.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "tenkeitech.item.pattern_buffer_transfer.stored",
                    transfer.getInteger(NBT_PATTERN_COUNT)));
            int compressedBytes = getStoredDataLength(stack, mode);
            if (compressedBytes >= 0) {
                tooltip.add(
                    EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                        "tenkeitech.item.pattern_buffer_transfer.nbt_length",
                        compressedBytes,
                        MAX_COMPRESSED_DATA_BYTES));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        ItemStack memoryCard = AEApi.instance()
            .definitions()
            .items()
            .memoryCard()
            .maybeStack(1)
            .orNull();
        IIcon memoryCardIcon = memoryCard == null ? null : memoryCard.getIconIndex();
        return memoryCardIcon == null ? Items.paper.getIconFromDamage(0) : memoryCardIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        return getValidTransferTag(stack, mode) != null;
    }

    @Nullable
    public static NBTTagCompound getStoredPayload(ItemStack tool, Mode expectedMode) {
        NBTTagCompound transfer = getValidTransferTag(tool, expectedMode);
        if (transfer == null) return null;
        return (NBTTagCompound) transfer.getCompoundTag(NBT_PAYLOAD)
            .copy();
    }

    public static int getStoredSourceCapacity(ItemStack tool, Mode expectedMode) {
        NBTTagCompound transfer = getValidTransferTag(tool, expectedMode);
        return transfer == null ? -1 : transfer.getInteger(NBT_SOURCE_CAPACITY);
    }

    public static int getStoredPatternCount(ItemStack tool, Mode expectedMode) {
        NBTTagCompound transfer = getValidTransferTag(tool, expectedMode);
        return transfer == null ? -1 : transfer.getInteger(NBT_PATTERN_COUNT);
    }

    public static boolean hasStoredData(ItemStack tool, Mode expectedMode) {
        return getValidTransferTag(tool, expectedMode) != null;
    }

    public static boolean hasAnyStoredData(ItemStack tool) {
        return tool != null && tool.getTagCompound() != null
            && tool.getTagCompound()
                .hasKey(NBT_TRANSFER);
    }

    public static boolean isStoredDataWithinLimit(ItemStack tool, Mode expectedMode) {
        int compressedBytes = getStoredDataLength(tool, expectedMode);
        return compressedBytes >= 0 && compressedBytes <= MAX_COMPRESSED_DATA_BYTES;
    }

    public static int getStoredDataLength(ItemStack tool, Mode expectedMode) {
        NBTTagCompound transfer = getValidTransferTag(tool, expectedMode);
        if (transfer == null) return -1;
        try {
            return CompressedStreamTools.compress(transfer).length;
        } catch (IOException exception) {
            return -1;
        }
    }

    public static boolean canStorePayload(Mode mode, int sourceCapacity, int patternCount, NBTTagCompound payload) {
        if (payload == null || sourceCapacity < 0 || patternCount < 0) return false;
        try {
            return CompressedStreamTools.compress(createTransferTag(mode, sourceCapacity, patternCount, payload)).length
                <= MAX_COMPRESSED_DATA_BYTES;
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean storePayload(ItemStack tool, Mode mode, int sourceCapacity, int patternCount,
        NBTTagCompound payload) {
        if (tool == null || !canStorePayload(mode, sourceCapacity, patternCount, payload)) return false;
        NBTTagCompound root = tool.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        root.setTag(NBT_TRANSFER, createTransferTag(mode, sourceCapacity, patternCount, payload));
        tool.setTagCompound(root);
        return true;
    }

    public static void clearStoredData(ItemStack tool) {
        if (tool == null || tool.getTagCompound() == null) return;
        tool.getTagCompound()
            .removeTag(NBT_TRANSFER);
    }

    private static NBTTagCompound createTransferTag(Mode mode, int sourceCapacity, int patternCount,
        NBTTagCompound payload) {
        NBTTagCompound transfer = new NBTTagCompound();
        transfer.setInteger(NBT_SCHEMA_VERSION, TRANSFER_SCHEMA_VERSION);
        transfer.setString(NBT_MODE, mode.serializedName());
        transfer.setInteger(NBT_SOURCE_CAPACITY, sourceCapacity);
        transfer.setInteger(NBT_PATTERN_COUNT, patternCount);
        transfer.setTag(NBT_PAYLOAD, payload.copy());
        return transfer;
    }

    @Nullable
    private static NBTTagCompound getValidTransferTag(ItemStack tool, Mode expectedMode) {
        if (tool == null || tool.getTagCompound() == null
            || !tool.getTagCompound()
                .hasKey(NBT_TRANSFER, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound transfer = tool.getTagCompound()
            .getCompoundTag(NBT_TRANSFER);
        if (!transfer.hasKey(NBT_SCHEMA_VERSION, Constants.NBT.TAG_INT)
            || !transfer.hasKey(NBT_MODE, Constants.NBT.TAG_STRING)
            || !transfer.hasKey(NBT_SOURCE_CAPACITY, Constants.NBT.TAG_INT)
            || !transfer.hasKey(NBT_PATTERN_COUNT, Constants.NBT.TAG_INT)
            || transfer.getInteger(NBT_SCHEMA_VERSION) != TRANSFER_SCHEMA_VERSION
            || !expectedMode.serializedName()
                .equals(transfer.getString(NBT_MODE))
            || transfer.getInteger(NBT_SOURCE_CAPACITY) < 0
            || transfer.getInteger(NBT_PATTERN_COUNT) < 0
            || !transfer.hasKey(NBT_PAYLOAD, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return transfer;
    }
}
