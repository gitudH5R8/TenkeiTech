/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSkull;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Adapts supported real items to placement callbacks; skulls are not ItemBlock in 1.7.10. */
public final class BuildingMaterial {

    public final Block block;
    public final ItemStack stack;
    private final boolean skull;
    private final boolean machine;

    private BuildingMaterial(ItemStack stack, Block block, boolean skull) {
        this.stack = stack.copy();
        this.stack.stackSize = 1;
        this.block = block;
        this.skull = skull;
        this.machine = block == GregTechAPI.sBlockMachines;
    }

    public static BuildingMaterial from(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return null;
        if (stack.getItem() == Items.skull && stack.getItemDamage() >= 0 && stack.getItemDamage() <= 4)
            return new BuildingMaterial(stack, Blocks.skull, true);
        if (!(stack.getItem() instanceof ItemBlock)) return null;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == GregTechAPI.sBlockMachines) {
            int id = stack.getItemDamage();
            return stack.getItem() instanceof ItemMachines && id > 0
                && id < GregTechAPI.METATILEENTITIES.length
                && GregTechAPI.METATILEENTITIES[id] != null ? new BuildingMaterial(stack, block, false) : null;
        }
        if (block == null || block == Blocks.air
            || block.hasTileEntity(
                stack.getItem()
                    .getMetadata(stack.getItemDamage())))
            return null;
        return new BuildingMaterial(stack, block, false);
    }

    public int getMetadata(BuildingContext context, Position position) {
        if (skull) return context.face.ordinal();
        if (machine) return GregTechAPI.METATILEENTITIES[stack.getItemDamage()].getTileEntityBaseType();
        return block.onBlockPlaced(
            context.world,
            position.x,
            position.y,
            position.z,
            context.face.ordinal(),
            context.hitX,
            context.hitY,
            context.hitZ,
            stack.getItem()
                .getMetadata(stack.getItemDamage()));
    }

    public boolean canPlace(BuildingContext context, Position source, int metadata) {
        if (machine) return true;
        if (!skull) return !block.hasTileEntity(metadata);
        return context.face != ForgeDirection.DOWN
            && BuildingContext.loaded(context.world, source.x, source.y, source.z)
            && (context.world.getBlock(source.x, source.y, source.z)
                .getMaterial()
                .isSolid() || context.world.isSideSolid(source.x, source.y, source.z, context.face));
    }

    public boolean place(BuildingContext context, Position position, ItemStack heldStack, int metadata) {
        if (!skull) return ((ItemBlock) heldStack.getItem()).placeBlockAt(
            heldStack,
            context.player,
            context.world,
            position.x,
            position.y,
            position.z,
            context.face.ordinal(),
            context.hitX,
            context.hitY,
            context.hitZ,
            metadata);
        if (!context.world.setBlock(position.x, position.y, position.z, block, metadata, 2)) return false;
        if (!(context.world.getTileEntity(position.x, position.y, position.z) instanceof TileEntitySkull tile))
            return false;
        if (heldStack.getItemDamage() == 3) tile.func_152106_a(getOwner(heldStack));
        else tile.func_152107_a(heldStack.getItemDamage());
        int rotation = context.face == ForgeDirection.UP
            ? MathHelper.floor_double(context.player.rotationYaw * 16D / 360D + 0.5D) & 15
            : 0;
        tile.func_145903_a(rotation);
        tile.markDirty();
        return true;
    }

    public boolean allowsTileEntity(BuildingContext context, Position position) {
        if (machine) return machineId(context.world, position) == stack.getItemDamage();
        return skull && context.world.getBlock(position.x, position.y, position.z) == Blocks.skull
            && context.world.getTileEntity(position.x, position.y, position.z) instanceof TileEntitySkull;
    }

    /** Machine IDs live on the tile, not in the shared block's four-bit metadata. */
    public static int machineId(World world, Position position) {
        if (world.getBlock(position.x, position.y, position.z) != GregTechAPI.sBlockMachines
            || !(world.getTileEntity(position.x, position.y, position.z) instanceof IGregTechTileEntity tile)
            || tile.getMetaTileEntity() == null) return -1;
        return tile.getMetaTileID();
    }

    public void afterCommit(BuildingContext context, Position position) {
        // Summoning can consume the structure and spawn an entity, so it must follow protection and material commit.
        if (!skull || context.world.getBlock(position.x, position.y, position.z) != Blocks.skull
            || !(context.world.getTileEntity(position.x, position.y, position.z) instanceof TileEntitySkull tile)
            || tile.func_145904_a() != 1) return;
        for (int x = (position.x - 2) >> 4; x <= (position.x + 2) >> 4; x++) {
            for (int z = (position.z - 2) >> 4; z <= (position.z + 2) >> 4; z++) {
                if (!context.world.getChunkProvider()
                    .chunkExists(x, z)) return;
            }
        }
        ((BlockSkull) Blocks.skull).func_149965_a(context.world, position.x, position.y, position.z, tile);
    }

    private static GameProfile getOwner(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("SkullOwner", 10)) return NBTUtil.func_152459_a(tag.getCompoundTag("SkullOwner"));
        String name = tag.getString("SkullOwner");
        return name.isEmpty() ? null : new GameProfile((UUID) null, name);
    }
}
