/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

import cpw.mods.fml.common.eventhandler.Event;
import gregtech.api.GregTechAPI;

/** A planned placement, revalidated on execution and retaining only committed undo state. */
public final class PlacementSnapshot implements BuildingSnapshot {

    public final Position source;
    public final Position position;
    private final ItemStack material;
    private final BuildingMaterial placement;
    private List<BlockSnapshot> committedChanges;
    private BuildingUndoRecord undoRecord;

    private PlacementSnapshot(Position source, Position position, ItemStack material) {
        this.source = source;
        this.position = position;
        this.material = material.copy();
        this.material.stackSize = 1;
        this.placement = BuildingMaterial.from(material);
    }

    public static PlacementSnapshot create(BuildingContext context, Position source, Position position,
        ItemStack material) {
        PlacementSnapshot snapshot = new PlacementSnapshot(source, position, material);
        return snapshot.canPlace(context) ? snapshot : null;
    }

    private boolean canPlace(BuildingContext context) {
        World world = context.world;
        if (!context.isActive() || placement == null
            || !context.supportsPlacement(source)
            || !BuildingContext.loaded(world, position.x, position.y, position.z)) return false;
        Block previous = world.getBlock(position.x, position.y, position.z);
        if (previous.hasTileEntity(world.getBlockMetadata(position.x, position.y, position.z))
            || !previous.isReplaceable(world, position.x, position.y, position.z)) return false;
        Block block = placement.block;
        int metadata = placement.getMetadata(context, position);
        AxisAlignedBB cell = AxisAlignedBB
            .getBoundingBox(position.x, position.y, position.z, position.x + 1D, position.y + 1D, position.z + 1D);
        // Native canPlaceEntityOnSide excludes its entity argument; never exclude the building player.
        if (cell.intersectsWith(context.player.boundingBox) || collidesWithLivingEntity(world, cell)
            || !placement.canPlace(context, position.offset(context.face.getOpposite()), metadata)
            || !context.player.canPlayerEdit(position.x, position.y, position.z, context.face.ordinal(), material)
            || !context.player.canPlayerEdit(source.x, source.y, source.z, context.face.ordinal(), material))
            return false;
        if (!world.isRemote && (MinecraftServer.getServer()
            .isBlockProtected((WorldServer) world, position.x, position.y, position.z, context.player)
            || MinecraftServer.getServer()
                .isBlockProtected((WorldServer) world, source.x, source.y, source.z, context.player)))
            return false;
        return world.canPlaceEntityOnSide(
            block,
            position.x,
            position.y,
            position.z,
            false,
            context.face.ordinal(),
            null,
            material);
    }

    public Result execute(BuildingContext context, BuildingSupplier supplier) {
        if (!(context.player instanceof EntityPlayerMP player) || !canPlace(context)) return Result.SKIPPED;
        WorldServer world = (WorldServer) context.world;
        if (world.captureBlockSnapshots || !world.capturedBlockSnapshots.isEmpty()) return Result.STOP;
        int heldSlot = player.inventory.currentItem;
        ItemStack placedStack = material.copy();
        Block previous = world.getBlock(position.x, position.y, position.z);
        int previousMeta = world.getBlockMetadata(position.x, position.y, position.z);
        MaterialLease lease = null;
        boolean changed = false;
        player.inventory.mainInventory[heldSlot] = placedStack;
        try {
            PlayerInteractEvent interaction = ForgeEventFactory.onPlayerInteract(
                player,
                context.air ? PlayerInteractEvent.Action.RIGHT_CLICK_AIR : PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
                source.x,
                source.y,
                source.z,
                context.face.ordinal(),
                world);
            if (interaction.isCanceled() || interaction.useItem == Event.Result.DENY || !canPlace(context))
                return Result.SKIPPED;
            if (player.getHeldItem() != placedStack || placedStack.stackSize != 1
                || !InventorySupplier.sameMaterial(placedStack, material)) return Result.STOP;
            lease = supplier.takeOne(material);
            if (lease == null) return Result.STOP;
            boolean accepted = placeBlock(context, placedStack, world, player);
            changed = changed(world, previous, previousMeta);
            if (changed && !accepted) {
                TenkeiTech.LOG.warn(
                    "Universal Wand placement returned inconsistent material/world state at {}, {}, {}",
                    position.x,
                    position.y,
                    position.z);
                return Result.STOP;
            }
            if (accepted && changed) {
                lease.commit();
                // Capture before skull summoning: consumed structures no longer match and cannot refund materials.
                if (world.getBlock(position.x, position.y, position.z) == placement.block)
                    undoRecord = BuildingUndoRecord
                        .capture(world, committedChanges, context.face, lease.getPaidMaterial());
                placement.afterCommit(context, position);
                return Result.CHANGED;
            }
            return Result.SKIPPED;
        } catch (RuntimeException failure) {
            world.captureBlockSnapshots = false;
            restore(world, new ArrayList<>(world.capturedBlockSnapshots));
            throw failure;
        } finally {
            // Never refund an item while its placed block still exists, including an exception after a Forge commit.
            changed = changed(world, previous, previousMeta);
            world.captureBlockSnapshots = false;
            world.capturedBlockSnapshots.clear();
            player.inventory.currentItem = heldSlot;
            player.inventory.mainInventory[heldSlot] = context.wand;
            if (lease != null) {
                if (changed) lease.commit();
                lease.close();
            }
            player.inventory.markDirty();
        }
    }

    private boolean placeBlock(BuildingContext context, ItemStack stack, WorldServer world, EntityPlayerMP player) {
        int metadata = placement.getMetadata(context, position);
        if (!placement.canPlace(context, position.offset(context.face.getOpposite()), metadata)) return false;
        world.captureBlockSnapshots = true;
        boolean accepted;
        try {
            accepted = placement.place(context, position, stack, metadata);
        } finally {
            world.captureBlockSnapshots = false;
        }
        ArrayList<BlockSnapshot> snapshots = new ArrayList<>(world.capturedBlockSnapshots);
        BlockEvent.PlaceEvent event = snapshots.size() > 1
            ? ForgeEventFactory.onPlayerMultiBlockPlace(player, snapshots, context.face)
            : snapshots.isEmpty() ? null : ForgeEventFactory.onPlayerBlockPlace(player, snapshots.get(0), context.face);
        if (!accepted || event != null && event.isCanceled() || !context.isActive() || player.getHeldItem() != stack) {
            restore(world, snapshots);
            return false;
        }
        for (BlockSnapshot snapshot : snapshots) {
            Block newBlock = world.getBlock(snapshot.x, snapshot.y, snapshot.z);
            int newMetadata = world.getBlockMetadata(snapshot.x, snapshot.y, snapshot.z);
            Position changedPosition = new Position(snapshot.x, snapshot.y, snapshot.z);
            AxisAlignedBB collision = newBlock
                .getCollisionBoundingBoxFromPool(world, snapshot.x, snapshot.y, snapshot.z);
            if (newBlock.hasTileEntity(newMetadata) && !placement.allowsTileEntity(context, changedPosition)
                || collision != null && collidesWithLivingEntity(world, collision)) {
                restore(world, snapshots);
                return false;
            }
        }
        world.capturedBlockSnapshots.clear();
        committedChanges = snapshots;
        // Keep Forge's deferred block lifecycle and notifications after protection accepts the complete snapshot.
        for (BlockSnapshot snapshot : snapshots) {
            Block newBlock = world.getBlock(snapshot.x, snapshot.y, snapshot.z);
            newBlock.onBlockAdded(world, snapshot.x, snapshot.y, snapshot.z);
            world.markAndNotifyBlock(
                snapshot.x,
                snapshot.y,
                snapshot.z,
                null,
                snapshot.replacedBlock,
                newBlock,
                snapshot.flag);
        }
        return true;
    }

    private static void restore(WorldServer world, ArrayList<BlockSnapshot> snapshots) {
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            world.restoringBlockSnapshots = true;
            try {
                BlockSnapshot snapshot = snapshots.get(index);
                // GT breakBlock drops stored item contents. Remove only the newly placed tile before rollback,
                // since the lease returns the complete original machine item, including its stored contents.
                if (snapshot.getTileEntity() == null
                    && world.getBlock(snapshot.x, snapshot.y, snapshot.z) == GregTechAPI.sBlockMachines)
                    world.removeTileEntity(snapshot.x, snapshot.y, snapshot.z);
                snapshots.get(index)
                    .restore(true, false);
            } finally {
                world.restoringBlockSnapshots = false;
            }
        }
    }

    private boolean changed(World world, Block previous, int previousMeta) {
        return world.getBlock(position.x, position.y, position.z) != previous
            || world.getBlockMetadata(position.x, position.y, position.z) != previousMeta;
    }

    public Block getMaterialBlock() {
        return placement.block;
    }

    public Position getPosition() {
        return position;
    }

    public BuildingUndoRecord getUndoRecord() {
        return undoRecord;
    }

    private static boolean collidesWithLivingEntity(World world, AxisAlignedBB box) {
        for (Object entity : world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (!((EntityLivingBase) entity).isDead) return true;
        }
        return false;
    }

}
