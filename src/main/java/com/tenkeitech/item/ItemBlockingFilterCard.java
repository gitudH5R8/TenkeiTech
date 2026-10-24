/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.glodblock.github.common.item.ItemFluidPacket;
import com.tenkeitech.blockingfilter.BlockingFilterConfig;
import com.tenkeitech.client.InterfaceUpgradeTooltip;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A standalone interface upgrade, deliberately absent from AE's fixed upgrade enum. */
public final class ItemBlockingFilterCard extends Item implements ICellWorkbenchItem {

    private final boolean precise;

    public ItemBlockingFilterCard(boolean precise) {
        this.precise = precise;
        setUnlocalizedName("tenkeitech.blocking_filter_card_" + (precise ? "precise" : "fuzzy"));
        setMaxStackSize(1);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabTools);
    }

    public boolean precise() {
        return precise;
    }

    @Override
    public boolean isEditable(ItemStack stack) {
        return BlockingFilterConfig.supported(stack);
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack stack) {
        return new AppEngInternalInventory(null, 0);
    }

    @Override
    public IAEStackInventory getConfigAEInventory(ItemStack stack) {
        return new BlockingFilterConfig(stack);
    }

    @Override
    public IAEStackType<?> getStackType() {
        return null; // Native workbench accepts both item and fluid ghost stacks.
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        // Matching is determined by the physical card variant, not AE's damage-fuzzy setting.
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(
            precise ? "tenkeitech:blocking_filter_card_precise" : "tenkeitech:blocking_filter_card_fuzzy");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter." + (precise ? "precise" : "fuzzy")));
        lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter.configure"));
        InterfaceUpgradeTooltip.addDevices(lines);
        if (!BlockingFilterConfig.supported(stack)) {
            lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter.invalid"));
            return;
        }
        if (!GuiScreen.isShiftKeyDown()) {
            lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter.show_markers"));
            return;
        }
        lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter.markers"));
        BlockingFilterConfig config = new BlockingFilterConfig(stack);
        boolean found = false;
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            var marker = config.getAEStackInSlot(slot);
            if (marker instanceof IAEItemStack item && item.getItemStack()
                .getItem() instanceof ItemFluidPacket) marker = ItemFluidPacket.getFluidAEStack(item);
            if (marker == null || !marker.isItem() && !marker.isFluid()) continue;
            found = true;
            lines.add(
                StatCollector.translateToLocalFormatted(
                    marker.isFluid() ? "tenkeitech.blockingfilter.marker_fluid"
                        : "tenkeitech.blockingfilter.marker_item",
                    marker.getDisplayName()));
        }
        if (!found) lines.add(StatCollector.translateToLocal("tenkeitech.blockingfilter.no_markers"));
    }
}
