/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class BlockBetterLevelMaintainer extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon front;
    @SideOnly(Side.CLIENT)
    private IIcon activeFront;

    public BlockBetterLevelMaintainer() {
        super(Material.iron);
        setBlockName("tenkeitech.better_level_maintainer");
        setHardness(2);
        setResistance(10);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileBetterLevelMaintainer();
    }

    @Override
    public int getRenderType() {
        return 0;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon("ae2fc:level_maintainer");
        front = register.registerIcon("ae2fc:level_maintainerFront");
        activeFront = register.registerIcon("ae2fc:level_maintainer_active");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        return side == 3 ? front : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (side != world.getBlockMetadata(x, y, z)) return blockIcon;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileBetterLevelMaintainer maintainer && maintainer.active() ? activeFront : front;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        int[] faces = { 2, 5, 3, 4 };
        int direction = MathHelper.floor_double(entity.rotationYaw * 4 / 360 + .5) & 3;
        world.setBlockMetadataWithNotify(x, y, z, faces[direction], 2);
        if (!world.isRemote && entity instanceof EntityPlayer player
            && world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile) {
            tile.placed(player);
            if (stack.hasTagCompound() && stack.getTagCompound()
                .hasKey(TileBetterLevelMaintainer.ITEM_SETTINGS, 10))
                tile.restoreItemSettings(
                    stack.getTagCompound()
                        .getCompoundTag(TileBetterLevelMaintainer.ITEM_SETTINGS));
        }
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        // Keep the tile available until Forge has collected and processed its configured drop.
        return willHarvest || super.removedByPlayer(world, player, x, y, z, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        TileEntity tile = world.getTileEntity(x, y, z);
        try {
            super.harvestBlock(world, player, x, y, z, metadata);
        } finally {
            if (world.getBlock(x, y, z) == this && world.getTileEntity(x, y, z) == tile) world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        return false;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        ItemStack drop = new ItemStack(this);
        if (world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile) {
            NBTTagCompound settings = tile.itemSettings();
            if (settings.getInteger("tenkeitechMaintainerSchema") != 1 || settings.getTagList("entries", 10)
                .tagCount() != 0) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setTag(TileBetterLevelMaintainer.ITEM_SETTINGS, settings);
                drop.setTagCompound(tag);
            }
        }
        drops.add(drop);
        return drops;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        if (player.isSneaking()) return false;
        if (world.isRemote) return true;
        if (world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile) {
            if (tile.canUse(player)) player.openGui(TenkeiTech.instance, MaintainerRegistration.GUI_ID, world, x, y, z);
            else player.addChatMessage(new ChatComponentTranslation("tenkeitech.maintainer.permission"));
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block oldBlock, int metadata) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile) tile.destroyed();
        super.breakBlock(world, x, y, z, oldBlock, metadata);
    }

    public static final class MaintainerItem extends ItemBlock {

        public MaintainerItem(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
            lines.add(StatCollector.translateToLocal("tenkeitech.maintainer.tooltip.1"));
            lines.add(StatCollector.translateToLocal("tenkeitech.maintainer.tooltip.settings"));
            if (stack.hasTagCompound() && stack.getTagCompound()
                .hasKey(TileBetterLevelMaintainer.ITEM_SETTINGS, 10))
                lines.add(
                    StatCollector.translateToLocalFormatted(
                        "tenkeitech.maintainer.tooltip.carried",
                        Math.min(
                            TileBetterLevelMaintainer.CAPACITY,
                            stack.getTagCompound()
                                .getCompoundTag(TileBetterLevelMaintainer.ITEM_SETTINGS)
                                .getTagList("entries", 10)
                                .tagCount())));
        }
    }
}
