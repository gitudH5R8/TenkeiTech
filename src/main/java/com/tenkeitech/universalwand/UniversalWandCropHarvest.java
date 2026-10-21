/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.IGrowable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tenkeitech.integration.cropsnh.CropsNhHarvestCompat;
import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;

/** Native crop activation with a seed-conserving fallback for vanilla crops. */
public final class UniversalWandCropHarvest {

    private UniversalWandCropHarvest() {}

    public static boolean isMature(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return false;
        Block crop = world.getBlock(x, y, z);
        int age = world.getBlockMetadata(x, y, z);
        if (crop instanceof BlockCrops) return age == 7;
        if (crop == Blocks.nether_wart) return age == 3;
        // Other bush crops may expose their growth limit through IGrowable rather than BlockCrops.
        // Only their native activation is used below; this does not authorize a guessed reset or seed.
        return crop instanceof BlockBush && crop instanceof IGrowable growable
            && !growable.func_149851_a(world, x, y, z, false);
    }

    static boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        World world = player.worldObj;
        if (!isHarvestable(world, x, y, z, player.getHeldItem())) return false;
        boolean cropSticks = !isMature(world, x, y, z);
        TileEntity originalTile = cropSticks ? world.getTileEntity(x, y, z) : null;
        Block crop = world.getBlock(x, y, z);
        int age = world.getBlockMetadata(x, y, z);
        PlayerInteractEvent event = ForgeEventFactory
            .onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y, z, 1, world);
        if (event.isCanceled() || event.useBlock == Event.Result.DENY) return false;
        // An event handler may already have performed the native right-click harvest.
        if (world.getBlock(x, y, z) != crop || world.getBlockMetadata(x, y, z) != age) return true;
        if (cropSticks) {
            if (world.getTileEntity(x, y, z) != originalTile) return true;
            return ItemUniversalWand.isCropHarvestEnabled(player.getHeldItem())
                && ItemUniversalWand.isCropSticksEnabled(player.getHeldItem())
                && CropsNhHarvestCompat.harvest(player, x, y, z);
        }
        if (crop.onBlockActivated(world, x, y, z, player, 1, 0.5F, 0.5F, 0.5F)) return true;
        if (world.getBlock(x, y, z) != crop || world.getBlockMetadata(x, y, z) != age) return true;
        // Unknown mod plants must implement their own harvest; never guess their seed or reset state.
        Item seed = crop == Blocks.wheat ? Items.wheat_seeds
            : crop == Blocks.carrots ? Items.carrot
                : crop == Blocks.potatoes ? Items.potato : crop == Blocks.nether_wart ? Items.nether_wart : null;
        if (seed == null) return false;
        ArrayList<ItemStack> drops = new ArrayList<>(crop.getDrops(world, x, y, z, age, 0));
        boolean reserved = false;
        for (ItemStack drop : drops) {
            if (drop != null && drop.getItem() == seed && drop.stackSize > 0) {
                drop.stackSize--;
                reserved = true;
                break;
            }
        }
        if (!reserved || !world.setBlockMetadataWithNotify(x, y, z, 0, 3)) return false;
        if (!world.getGameRules()
            .getGameRuleBooleanValue("doTileDrops")) return true;
        float chance = ForgeEventFactory.fireBlockHarvesting(drops, world, crop, x, y, z, age, 0, 1F, false, player);
        for (ItemStack drop : drops) {
            if (drop != null && drop.stackSize > 0 && world.rand.nextFloat() <= chance) {
                net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(
                    world,
                    x + 0.5D,
                    y + 0.5D,
                    z + 0.5D,
                    drop);
                item.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(item);
            }
        }
        return true;
    }

    static boolean isHarvestable(World world, int x, int y, int z, ItemStack wand) {
        if (!world.blockExists(x, y, z)) return false;
        if (isMature(world, x, y, z)) return true;
        return ItemUniversalWand.isCropHarvestEnabled(wand) && ItemUniversalWand.isCropSticksEnabled(wand)
            && Loader.isModLoaded("cropsnh")
            && CropsNhHarvestCompat.canHarvest(world, x, y, z);
    }
}
