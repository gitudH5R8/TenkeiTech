/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;

/** 1.7.10 equivalent of the wand's placement context. It never loads a chunk. */
public final class BuildingContext {

    public static final int MAX_BLOCKS = 1024;
    public static final int ANGEL_DISTANCE = 16;
    public static final int DESTRUCTION_BLOCKS = 81;

    public static final class Position {

        public final int x, y, z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Position offset(ForgeDirection direction) {
            return new Position(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
        }

        public long key() {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
        }
    }

    public final EntityPlayer player;
    public final World world;
    public final ItemStack wand;
    public final MovingObjectPosition hit;
    public final Position origin;
    public final Block target;
    public final int metadata;
    public final ForgeDirection face;
    public final float hitX, hitY, hitZ;
    public final BuildingCore core;
    public final boolean air;
    private final int machineId;

    private BuildingContext(EntityPlayer player, ItemStack wand, MovingObjectPosition hit, boolean air) {
        this.player = player;
        this.world = player.worldObj;
        this.wand = wand;
        this.hit = hit;
        this.origin = new Position(hit.blockX, hit.blockY, hit.blockZ);
        this.target = world.getBlock(origin.x, origin.y, origin.z);
        this.metadata = world.getBlockMetadata(origin.x, origin.y, origin.z);
        this.face = ForgeDirection.getOrientation(hit.sideHit);
        this.hitX = (float) (hit.hitVec.xCoord - origin.x);
        this.hitY = (float) (hit.hitVec.yCoord - origin.y);
        this.hitZ = (float) (hit.hitVec.zCoord - origin.z);
        this.core = ItemUniversalWand.getBuildingCore(wand);
        this.air = air;
        this.machineId = BuildingMaterial.machineId(world, origin);
    }

    public static BuildingContext create(EntityPlayer player, ItemStack wand, MovingObjectPosition hit) {
        if (player == null || hit == null
            || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || hit.sideHit < 0
            || hit.sideHit > 5
            || hit.hitVec == null
            || !loaded(player.worldObj, hit.blockX, hit.blockY, hit.blockZ)) return null;
        BuildingContext context = new BuildingContext(player, wand, hit, false);
        return context.core == BuildingCore.ANGEL
            ? (!context.target.isAir(context.world, hit.blockX, hit.blockY, hit.blockZ) ? context : null)
            : context.matches(context.origin) ? context : null;
    }

    public static BuildingContext createAir(EntityPlayer player, ItemStack wand) {
        if (player == null || player.worldObj == null
            || ItemUniversalWand.getBuildingCore(wand) != BuildingCore.ANGEL
            || !player.capabilities.isCreativeMode && player.fallDistance > 10F) return null;
        Vec3 look = player.getLookVec();
        if (look == null) return null;
        int x = (int) Math.round(player.posX + look.xCoord * 2D);
        int y = (int) Math.round((player.boundingBox.minY + player.boundingBox.maxY) / 2D + look.yCoord * 2D);
        int z = (int) Math.round(player.posZ + look.zCoord * 2D);
        if (!loaded(player.worldObj, x, y, z)) return null;
        ForgeDirection direction = Math.abs(look.xCoord) >= Math.abs(look.yCoord)
            && Math.abs(look.xCoord) >= Math.abs(look.zCoord)
                ? (look.xCoord > 0D ? ForgeDirection.EAST : ForgeDirection.WEST)
                : Math.abs(look.yCoord) >= Math.abs(look.zCoord)
                    ? (look.yCoord > 0D ? ForgeDirection.UP : ForgeDirection.DOWN)
                    : (look.zCoord > 0D ? ForgeDirection.SOUTH : ForgeDirection.NORTH);
        return new BuildingContext(
            player,
            wand,
            new MovingObjectPosition(
                x,
                y,
                z,
                direction.ordinal(),
                Vec3.createVectorHelper(x + 0.5D, y + 0.5D, z + 0.5D)),
            true);
    }

    public boolean supportsPlacement(Position source) {
        if (core != BuildingCore.ANGEL) return matches(source);
        return loaded(world, source.x, source.y, source.z) && world.getBlock(source.x, source.y, source.z) == target
            && world.getBlockMetadata(source.x, source.y, source.z) == metadata;
    }

    /** Native 1.7.10 use packets copy the held stack after invoking the item. */
    public BuildingContext withWand(ItemStack currentWand) {
        return new BuildingContext(player, currentWand, hit, air);
    }

    public boolean isActive() {
        return !player.isDead && player.worldObj == world
            && !player.isSneaking()
            && ItemUniversalWand.isBuildingEnabled(wand)
            && ItemUniversalWand.getBuildingCore(wand) == core;
    }

    public boolean permeable(Position position) {
        return loaded(world, position.x, position.y, position.z) && world.getBlock(position.x, position.y, position.z)
            .getCollisionBoundingBoxFromPool(world, position.x, position.y, position.z) == null;
    }

    public boolean matches(Position source) {
        return loaded(world, source.x, source.y, source.z) && world.getBlock(source.x, source.y, source.z) == target
            && world.getBlockMetadata(source.x, source.y, source.z) == metadata
            && !target.isAir(world, source.x, source.y, source.z)
            && (!target.hasTileEntity(metadata) || core == BuildingCore.NORMAL && machineId > 0
                && BuildingMaterial.machineId(world, source) == machineId)
            && !target.isReplaceable(world, source.x, source.y, source.z)
            && target.getBlockHardness(world, source.x, source.y, source.z) >= 0;
    }

    public static boolean loaded(World world, int x, int y, int z) {
        return y >= 0 && y < world.getHeight()
            && world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4);
    }
}
