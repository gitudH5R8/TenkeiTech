/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tenkeitech.autoextract.AutoExtractionCardData;
import com.tenkeitech.client.InterfaceUpgradeTooltip;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shares the native lock-card upgrade limit intentionally; extraction is owned by the host. */
public final class ItemAutoExtractionCard extends Item implements IUpgradeModule {

    public ItemAutoExtractionCard() {
        setUnlocalizedName("tenkeitech.auto_extraction_card");
        setMaxStackSize(1);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabTools);
    }

    @Override
    public Upgrades getType(ItemStack stack) {
        return Upgrades.LOCK_CRAFTING;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // A true client result suppresses the native use packet in 1.7.10. Only the server changes the card.
        if (world.isRemote) return false;
        if (!AutoExtractionCardData.toggle(stack, side)) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.autoextract.invalid_direction"));
            return true;
        }
        player.inventory.markDirty();
        player.addChatMessage(
            new ChatComponentTranslation(
                "tenkeitech.autoextract.input_side",
                new ChatComponentTranslation(directionKey(AutoExtractionCardData.direction(stack)))));
        return true;
    }

    private static String directionKey(ForgeDirection direction) {
        return direction == ForgeDirection.UNKNOWN ? "tenkeitech.autoextract.any_side"
            : "tenkeitech.resonating.face." + direction.ordinal();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon("tenkeitech:auto_extraction_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("tenkeitech.autoextract.extract"));
        ForgeDirection direction = AutoExtractionCardData.direction(stack);
        lines.add(
            direction == null ? StatCollector.translateToLocal("tenkeitech.autoextract.invalid_direction")
                : StatCollector.translateToLocalFormatted(
                    "tenkeitech.autoextract.input_side",
                    StatCollector.translateToLocal(directionKey(direction))));
        lines.add(StatCollector.translateToLocal("tenkeitech.autoextract.bind_side"));
        InterfaceUpgradeTooltip.addDevices(lines);
    }
}
