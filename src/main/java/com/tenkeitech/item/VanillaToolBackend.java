/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IShearable;

import com.tenkeitech.universalwand.UniversalWandMiningHandler;

/** Native item callbacks see a real tool; the original wand is restored before returning. */
public final class VanillaToolBackend {

    private VanillaToolBackend() {}

    public static final class HeldTool implements AutoCloseable {

        private final EntityPlayer player;
        private final int slot;
        private final ItemStack wand;
        private final ItemStack tool;

        private HeldTool(EntityPlayer player, ItemStack wand, Item item) {
            this.player = player;
            this.slot = player.inventory.currentItem;
            this.wand = wand;
            this.tool = new ItemStack(item);
            int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, wand);
            if (fortune > 0) tool.addEnchantment(Enchantment.fortune, fortune);
            player.inventory.mainInventory[slot] = tool;
        }

        public boolean isHeld() {
            return player.inventory.currentItem == slot && player.getHeldItem() == tool;
        }

        public boolean shearedBlock() {
            return tool.getItemDamage() > 0;
        }

        @Override
        public void close() {
            player.inventory.mainInventory[slot] = wand;
        }
    }

    /** Called only after Forge's BreakEvent has accepted the real wand's harvest. */
    public static HeldTool beginBlockShearing(EntityPlayerMP player, int x, int y, int z) {
        ItemStack wand = player.getHeldItem();
        World world = player.worldObj;
        if (!canUse(player, wand) || ItemUniversalWand.isProtectionMode(wand)
            || !ItemUniversalWand.isShearsEnabled(wand)
            || player.capabilities.isCreativeMode
            || !world.blockExists(x, y, z)
            || !player.canPlayerEdit(x, y, z, 1, wand)
            || world.getBlock(x, y, z)
                .getBlockHardness(world, x, y, z) < 0)
            return null;
        net.minecraft.block.Block block = world.getBlock(x, y, z);
        if (!(block instanceof IShearable) && block != Blocks.tripwire) return null;
        HeldTool tool = new HeldTool(player, wand, Items.shears);
        try {
            if (!(block instanceof IShearable target) || target.isShearable(tool.tool, world, x, y, z)) return tool;
        } catch (RuntimeException failure) {
            tool.close();
            throw failure;
        }
        tool.close();
        return null;
    }

    public static boolean shearEntity(ItemStack wand, EntityPlayer player, EntityLivingBase target) {
        if (!canUse(player, wand) || !ItemUniversalWand.isShearsEnabled(wand)
            || !(target instanceof IShearable)
            || target.isDead
            || target.worldObj != player.worldObj
            || !(player instanceof EntityPlayerMP mp)) return false;
        try (UniversalWandMiningHandler.HarvestScope drops = UniversalWandMiningHandler.beginInteraction(
            mp,
            (int) Math.floor(target.posX),
            (int) Math.floor(target.posY),
            (int) Math.floor(target.posZ)); HeldTool tool = new HeldTool(player, wand, Items.shears)) {
            return Items.shears.itemInteractionForEntity(tool.tool, player, target);
        }
    }

    public static boolean useHoe(ItemStack wand, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!canUse(player, wand) || !ItemUniversalWand.isHoeEnabled(wand)
            || world != player.worldObj
            || !world.blockExists(x, y, z)
            || !player.canPlayerEdit(x, y, z, side, wand)) return false;
        if (!world.isRemote && world instanceof WorldServer serverWorld
            && net.minecraft.server.MinecraftServer.getServer()
                .isBlockProtected(serverWorld, x, y, z, player))
            return false;
        try (HeldTool tool = new HeldTool(player, wand, Items.diamond_hoe)) {
            return Items.diamond_hoe.onItemUse(tool.tool, player, world, x, y, z, side, hitX, hitY, hitZ);
        }
    }

    private static boolean canUse(EntityPlayer player, ItemStack wand) {
        return player != null && !player.isDead
            && wand != null
            && player.getHeldItem() == wand
            && wand.getItem() instanceof ItemUniversalWand;
    }
}
