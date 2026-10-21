/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.tenkeitech.resonating.ResonatingBindingCardData;
import com.tenkeitech.resonating.ResonatingPatternData.Target;
import com.tenkeitech.resonating.ResonatingTargetAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemResonatingBindingCard extends Item {

    public ItemResonatingBindingCard() {
        setUnlocalizedName("tenkeitech.resonating_binding_card");
        setTextureName("tenkeitech:resonating_binding_card");
        setMaxStackSize(1);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking() && ResonatingBindingCardData.valid(stack)) {
            ResonatingBindingCardData.toggle(stack);
            player.inventory.markDirty();
        }
        return stack;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote || !ResonatingBindingCardData.valid(stack)) return false;
        if (player.isSneaking()) {
            ResonatingBindingCardData.toggle(stack);
        } else {
            Target target = Target.capture(player, world, x, y, z, side);
            if (target == null || !ResonatingTargetAccess.allowed(world, target, player, stack)) {
                player.addChatMessage(new ChatComponentTranslation("tenkeitech.resonating.bind_denied"));
                return true;
            }
            Target current = ResonatingBindingCardData
                .target(stack, ResonatingBindingCardData.fluid(stack), ResonatingBindingCardData.selected(stack));
            ResonatingBindingCardData.setTarget(stack, target.samePosition(current) ? null : target);
        }
        player.inventory.markDirty();
        return true;
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    @Override
    public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        if (!ResonatingBindingCardData.valid(stack)) {
            lines.add(StatCollector.translateToLocal("tenkeitech.resonating.invalid"));
            return;
        }
        boolean fluid = ResonatingBindingCardData.fluid(stack);
        String color = fluid ? "\u00a7b" : "\u00a7a";
        lines.add(
            color + StatCollector.translateToLocalFormatted(
                "tenkeitech.resonating.card.mode",
                StatCollector.translateToLocal("tenkeitech.resonating.card." + (fluid ? "fluid" : "item"))));
        lines.add(
            color + StatCollector.translateToLocalFormatted(
                "tenkeitech.resonating.card.slot",
                ResonatingBindingCardData.selected(stack) + 1,
                ResonatingBindingCardData.SLOTS));
        lines.add(StatCollector.translateToLocal("tenkeitech.resonating.card.usage"));
        lines.add(StatCollector.translateToLocal("tenkeitech.resonating.card.copy"));
        for (int slot = 0; slot < ResonatingBindingCardData.SLOTS; slot++) {
            Target target = ResonatingBindingCardData.target(stack, fluid, slot);
            if (target == null) continue;
            lines.add(
                color + (slot + 1)
                    + ": "
                    + StatCollector.translateToLocalFormatted(
                        "tenkeitech.resonating.target",
                        target.dimension,
                        target.x,
                        target.y,
                        target.z,
                        StatCollector.translateToLocal("tenkeitech.resonating.face." + target.side)));
        }
    }
}
