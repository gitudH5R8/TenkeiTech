/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Committed before/after states and the one material actually paid for this operation. */
public final class BuildingUndoRecord {

    private final List<BlockSnapshot> before;
    private final List<BlockSnapshot> after;
    private final ForgeDirection face;
    private ItemStack refund;
    private boolean completed;
    private boolean quarantined;

    private BuildingUndoRecord(List<BlockSnapshot> before, List<BlockSnapshot> after, ForgeDirection face,
        ItemStack refund) {
        this.before = before;
        this.after = after;
        this.face = face;
        this.refund = refund == null ? null : refund.copy();
    }

    public static BuildingUndoRecord capture(WorldServer world, List<BlockSnapshot> changes, ForgeDirection face,
        ItemStack refund) {
        Map<Long, BlockSnapshot> originals = new LinkedHashMap<>();
        for (BlockSnapshot snapshot : changes) {
            originals.putIfAbsent(new Position(snapshot.x, snapshot.y, snapshot.z).key(), snapshot);
        }
        if (originals.isEmpty() || originals.size() > BuildingContext.MAX_BLOCKS) return null;
        List<BlockSnapshot> before = new ArrayList<>();
        List<BlockSnapshot> after = new ArrayList<>();
        for (BlockSnapshot original : originals.values()) {
            // Machine data is not part of building. Only our placed vanilla skull can have a tile.
            if (original.getTileEntity() != null || !BuildingContext.loaded(world, original.x, original.y, original.z))
                return null;
            TileEntity tile = world.getTileEntity(original.x, original.y, original.z);
            if (tile != null && !(tile instanceof TileEntitySkull)) return null;
            before.add(original);
            after.add(BlockSnapshot.getBlockSnapshot(world, original.x, original.y, original.z));
        }
        return new BuildingUndoRecord(before, after, face, refund);
    }

    public List<Position> getPositions() {
        List<Position> positions = new ArrayList<>();
        for (BlockSnapshot snapshot : before) positions.add(new Position(snapshot.x, snapshot.y, snapshot.z));
        return positions;
    }

    private static boolean matches(WorldServer world, BlockSnapshot snapshot) {
        if (!BuildingContext.loaded(world, snapshot.x, snapshot.y, snapshot.z)
            || world.getBlock(snapshot.x, snapshot.y, snapshot.z) != snapshot.replacedBlock
            || world.getBlockMetadata(snapshot.x, snapshot.y, snapshot.z) != snapshot.meta) return false;
        TileEntity tile = world.getTileEntity(snapshot.x, snapshot.y, snapshot.z);
        NBTTagCompound saved = new NBTTagCompound();
        snapshot.writeToNBT(saved);
        if (!saved.getBoolean("hasTE")) return tile == null;
        if (!(tile instanceof TileEntitySkull)) return false;
        NBTTagCompound current = new NBTTagCompound();
        tile.writeToNBT(current);
        return saved.getCompoundTag("tileEntity")
            .equals(current);
    }

    public boolean canRestore(EntityPlayerMP player, ItemStack wand) {
        if (completed || quarantined || !active(player, wand)) return false;
        WorldServer world = (WorldServer) player.worldObj;
        if (world != before.get(0).world) return false;
        for (int index = 0; index < after.size(); index++) {
            BlockSnapshot current = after.get(index);
            BlockSnapshot original = before.get(index);
            if (!matches(world, current) || !player.canPlayerEdit(current.x, current.y, current.z, face.ordinal(), wand)
                || MinecraftServer.getServer()
                    .isBlockProtected(world, current.x, current.y, current.z, player))
                return false;
            if (!current.replacedBlock.isAir(world, current.x, current.y, current.z)
                && (ItemUniversalWand.isProtectionMode(wand)
                    || current.replacedBlock.getBlockHardness(world, current.x, current.y, current.z) < 0F))
                return false;
            if (!original.replacedBlock.isAir(world, original.x, original.y, original.z)) {
                AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(
                    original.x,
                    original.y,
                    original.z,
                    original.x + 1D,
                    original.y + 1D,
                    original.z + 1D);
                for (Object entity : world.getEntitiesWithinAABB(EntityLivingBase.class, cell)) {
                    if (!((EntityLivingBase) entity).isDead) return false;
                }
            }
        }
        return true;
    }

    public static boolean active(EntityPlayerMP player, ItemStack wand) {
        return !player.isDead && player.worldObj instanceof WorldServer
            && player.getHeldItem() == wand
            && ItemUniversalWand.isHeldBy(player)
            && ItemUniversalWand.isBuildingEnabled(wand);
    }

    public boolean restore(EntityPlayerMP player, ItemStack wand) {
        WorldServer world = (WorldServer) player.worldObj;
        if (world.captureBlockSnapshots || world.restoringBlockSnapshots
            || !world.capturedBlockSnapshots.isEmpty()
            || !canRestore(player, wand)) return false;
        List<BlockSnapshot> captured = new ArrayList<>();
        try {
            for (BlockSnapshot current : after) {
                if (current.replacedBlock.isAir(world, current.x, current.y, current.z)) continue;
                BlockEvent.BreakEvent event;
                try (UniversalWandMiningHandler.ForcedBreakScope ignored = UniversalWandMiningHandler
                    .beginForcedBreak()) {
                    event = ForgeHooks.onBlockBreakEvent(
                        world,
                        player.theItemInWorldManager.getGameType(),
                        player,
                        current.x,
                        current.y,
                        current.z);
                }
                if (event.isCanceled() || !canRestore(player, wand)) return false;
            }
            if (!canRestore(player, wand)) return false;
            world.captureBlockSnapshots = true;
            world.restoringBlockSnapshots = true;
            try {
                for (int index = before.size() - 1; index >= 0; index--) {
                    BlockSnapshot original = before.get(index);
                    world.setBlock(original.x, original.y, original.z, original.replacedBlock, original.meta, 3);
                }
            } finally {
                world.captureBlockSnapshots = false;
                world.restoringBlockSnapshots = false;
                captured.addAll(world.capturedBlockSnapshots);
            }
            List<BlockSnapshot> placements = new ArrayList<>();
            for (BlockSnapshot change : captured) {
                if (!world.isAirBlock(change.x, change.y, change.z)) placements.add(change);
            }
            BlockEvent.PlaceEvent event = placements.size() > 1
                ? ForgeEventFactory.onPlayerMultiBlockPlace(player, placements, face)
                : placements.isEmpty() ? null : ForgeEventFactory.onPlayerBlockPlace(player, placements.get(0), face);
            if (event != null && event.isCanceled() || !active(player, wand)
                || player.worldObj != world
                || !restored(world)
                || !acceptsChanges(player, wand, captured)) {
                rollback(world, captured);
                return false;
            }
            // Mark the inverse committed before callbacks; a callback failure must never refund twice.
            completed = true;
            world.capturedBlockSnapshots.clear();
            world.restoringBlockSnapshots = true;
            for (BlockSnapshot change : captured) {
                Block block = world.getBlock(change.x, change.y, change.z);
                block.onBlockAdded(world, change.x, change.y, change.z);
                world.markAndNotifyBlock(change.x, change.y, change.z, null, change.replacedBlock, block, change.flag);
            }
            return true;
        } catch (RuntimeException failure) {
            if (!completed) {
                try {
                    if (captured.isEmpty()) captured.addAll(world.capturedBlockSnapshots);
                    rollback(world, captured);
                } catch (RuntimeException rollbackFailure) {
                    quarantined = true;
                    failure.addSuppressed(rollbackFailure);
                }
            }
            TenkeiTech.LOG.error("Universal Wand undo stopped at a block transaction", failure);
            return completed;
        } finally {
            world.captureBlockSnapshots = false;
            world.restoringBlockSnapshots = false;
            world.capturedBlockSnapshots.clear();
            for (BlockSnapshot snapshot : before) {
                player.playerNetServerHandler
                    .sendPacket(new S23PacketBlockChange(snapshot.x, snapshot.y, snapshot.z, world));
            }
        }
    }

    private boolean restored(WorldServer world) {
        for (BlockSnapshot original : before) if (!matches(world, original)) return false;
        return true;
    }

    /**
     * Loaded positions changed by another operation cannot be undone or refunded. Unloaded positions remain pending.
     */
    public boolean isObsolete(World world) {
        if (!(world instanceof WorldServer server) || world != before.get(0).world) return false;
        for (BlockSnapshot snapshot : after) {
            if (BuildingContext.loaded(world, snapshot.x, snapshot.y, snapshot.z) && !matches(server, snapshot))
                return true;
        }
        return false;
    }

    private boolean acceptsChanges(EntityPlayerMP player, ItemStack wand, List<BlockSnapshot> captured) {
        WorldServer world = (WorldServer) player.worldObj;
        for (BlockSnapshot change : captured) {
            if (!BuildingContext.loaded(world, change.x, change.y, change.z)
                || change.getTileEntity() != null && !(change.getTileEntity() instanceof TileEntitySkull)
                || world.getTileEntity(change.x, change.y, change.z) != null
                || !player.canPlayerEdit(change.x, change.y, change.z, face.ordinal(), wand)
                || MinecraftServer.getServer()
                    .isBlockProtected(world, change.x, change.y, change.z, player))
                return false;
            Block block = world.getBlock(change.x, change.y, change.z);
            AxisAlignedBB collision = block.getCollisionBoundingBoxFromPool(world, change.x, change.y, change.z);
            if (collision != null) {
                for (Object entity : world.getEntitiesWithinAABB(EntityLivingBase.class, collision)) {
                    if (!((EntityLivingBase) entity).isDead) return false;
                }
            }
        }
        return true;
    }

    private static void rollback(WorldServer world, List<BlockSnapshot> snapshots) {
        world.captureBlockSnapshots = false;
        world.restoringBlockSnapshots = true;
        try {
            for (int index = snapshots.size() - 1; index >= 0; index--) snapshots.get(index)
                .restore(true, false);
        } finally {
            world.restoringBlockSnapshots = false;
            world.capturedBlockSnapshots.clear();
        }
    }

    public ItemStack takeRefund() {
        if (!completed) return null;
        ItemStack result = refund;
        refund = null;
        return result;
    }
}
