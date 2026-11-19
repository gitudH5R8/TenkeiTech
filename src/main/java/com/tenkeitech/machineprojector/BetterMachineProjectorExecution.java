/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.machineprojector.BetterMachineProjectorProjection.Entry;
import com.tenkeitech.machineprojector.BetterMachineProjectorProjection.Position;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** One click is one bounded round; switching modes or projectors never bypasses the player cooldown. */
public final class BetterMachineProjectorExecution {

    private static final Map<EntityPlayerMP, Long> LAST_USE = new WeakHashMap<>();

    private BetterMachineProjectorExecution() {}

    public static boolean execute(ItemStack projector, EntityPlayerMP player, World world, int x, int y, int z,
        int side) {
        if (!BetterMachineProjectorSettings.owns(player, projector) || player.isDead || !world.blockExists(x, y, z))
            return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        IConstructable constructable = resolve(tile, side);
        if (constructable == null) return false;
        if (!allowed(player, projector, world, new Position(x, y, z), side)) return true;
        long now = System.nanoTime();
        Long previous = LAST_USE.get(player);
        long remaining = previous == null ? 0
            : BetterMachineProjectorSettings.cooldown(projector) - (now - previous) / 1000000L;
        if (remaining > 0) {
            message(player, "cooldown", remaining);
            return true;
        }
        LAST_USE.put(player, now);
        ItemStack trigger = BetterMachineProjectorSettings.trigger(projector);
        BetterMachineProjectorSettings.Mode mode = BetterMachineProjectorSettings.mode(projector);
        int budget = BetterMachineProjectorSettings.budget(projector);
        BetterMachineProjectorStorage storage = new BetterMachineProjectorStorage(player);
        try {
            if (mode == BetterMachineProjectorSettings.Mode.BUILD) {
                if (player.capabilities.isCreativeMode) constructable.construct(trigger, false);
                else if (constructable instanceof ISurvivalConstructable survival) {
                    int built = BetterMachineProjectorBuild.construct(survival, trigger, budget, storage, player);
                    if (built == -1) message(player, "complete");
                    else if (built < -1) message(player, "unsupported");
                    else if (built > 0) message(player, "changed", built);
                } else message(player, "unsupported");
                return true;
            }
            if (!(tile instanceof BaseMetaTileEntity base)) {
                message(player, "unsupported");
                return true;
            }
            List<Entry> entries = BetterMachineProjectorProjection.collect(base, trigger);
            int changed = 0;
            int skipped = 0;
            Set<String> missing = new HashSet<>();
            AutoPlaceEnvironment environment = AutoPlaceEnvironment.fromLegacy(storage, player, player::addChatMessage);
            for (Entry entry : entries) {
                if (changed >= budget || !BetterMachineProjectorSettings.owns(player, projector)) break;
                Position p = entry.position;
                if (p.x == x && p.y == y && p.z == z) continue;
                if (!world.blockExists(p.x, p.y, p.z) || world.isAirBlock(p.x, p.y, p.z)) continue;
                // Every hatch is a tile entity. Unknown tile entities are also preserved rather than losing their
                // state.
                if (world.getTileEntity(p.x, p.y, p.z) != null) continue;
                if (!allowed(player, projector, world, p, side)) {
                    skipped++;
                    continue;
                }
                if (mode == BetterMachineProjectorSettings.Mode.DISMANTLE) {
                    if (dismantle(entry, projector, world, player, storage, environment)) changed++;
                } else {
                    IStructureElement<Object> selected = selected(entry, entry.element, world, environment);
                    if (selected != null
                        && replace(entry, selected, projector, world, player, storage, environment, missing)) changed++;
                }
            }
            message(player, "changed", changed);
            if (skipped > 0) message(player, "protected", skipped);
        } catch (UnsupportedOperationException unsupported) {
            message(player, "unsupported");
        } catch (RuntimeException error) {
            TenkeiTech.LOG.error(
                "Better machine projector operation failed at {}, {}, {} in dimension {}",
                x,
                y,
                z,
                player.dimension,
                error);
            message(player, "failed");
        } finally {
            player.inventoryContainer.detectAndSendChanges();
        }
        return true;
    }

    private static IConstructable resolve(TileEntity tile, int side) {
        if (tile instanceof IConstructableProvider provider) return provider.getConstructable();
        if (tile instanceof IConstructable constructable) return constructable;
        if (tile != null && IMultiblockInfoContainer.contains(tile.getClass())) {
            ExtendedFacing facing = tile instanceof IAlignment alignment ? alignment.getExtendedFacing()
                : ExtendedFacing.of(ForgeDirection.getOrientation(side));
            return IMultiblockInfoContainer.<TileEntity>get(tile.getClass())
                .toConstructable(tile, facing);
        }
        return null;
    }

    private static boolean allowed(EntityPlayerMP player, ItemStack projector, World world, Position p, int side) {
        return player.canPlayerEdit(p.x, p.y, p.z, side, projector) && !MinecraftServer.getServer()
            .isBlockProtected((WorldServer) world, p.x, p.y, p.z, player);
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<Object> selected(Entry entry, IStructureElement<Object> element, World world,
        AutoPlaceEnvironment environment) {
        if (element instanceof IStructureElementChain<?>chain) {
            for (IStructureElement<?> raw : chain.fallbacks()) {
                IStructureElement<Object> fallback = (IStructureElement<Object>) raw;
                IStructureElement<Object> selected = selected(entry, fallback, world, environment);
                if (selected != null) return selected;
            }
            // A chain describes alternatives, not interchangeable positions. Never select its aggregated channels.
            return null;
        }
        ItemStack target = target(entry, element, world, entry.trigger, environment);
        if (target == null) return null;
        for (String channel : element.getChannels()) {
            if (channel.equals("gt_hatch") || channel.equals("height") || channel.equals("length")) continue;
            if (ChannelDataAccessor.hasSubChannel(entry.trigger, channel))
                return matching(entry, element, target, world);
        }
        // Public wrappers such as GT's glass trigger transform omit getChannels(). Detect only an explicit channel
        // that actually changes this element's concrete placement identity; never inspect private wrapper fields.
        for (Map.Entry<String, Integer> channel : (Iterable<Map.Entry<String, Integer>>) ChannelDataAccessor
            .iterateChannelData(entry.trigger)::iterator) {
            if (channel.getKey()
                .equals("gt_hatch")
                || channel.getKey()
                    .equals("height")
                || channel.getKey()
                    .equals("length"))
                continue;
            for (int tier = 1; tier <= 2; tier++) {
                ItemStack probe = entry.trigger.copy();
                ChannelDataAccessor.setChannelData(probe, channel.getKey(), tier);
                ItemStack other = target(entry, element, world, probe, environment);
                if (other != null && !same(target, other)) return matching(entry, element, target, world);
            }
        }
        return null;
    }

    private static IStructureElement<Object> matching(Entry entry, IStructureElement<Object> element, ItemStack target,
        World world) {
        Position p = entry.position;
        ItemBlock item = (ItemBlock) target.getItem();
        // Already at the target tier: preserve branch priority without allocating a controller for a no-op.
        if (world.getBlock(p.x, p.y, p.z) == Block.getBlockFromItem(item)
            && world.getBlockMetadata(p.x, p.y, p.z) == item.getMetadata(target.getItemDamage())) return element;
        return entry.matches(element, world) ? element : null;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static ItemStack target(Entry entry, IStructureElement<Object> element, World world, ItemStack trigger,
        AutoPlaceEnvironment environment) {
        Position p = entry.position;
        IStructureElement.BlocksToPlace choices = element
            .getBlocksToPlace(entry.controller, world, p.x, p.y, p.z, trigger, environment);
        if (choices == null || choices.getStacks() == null) return null;
        ItemStack target = null;
        for (ItemStack candidate : choices.getStacks()) {
            if (candidate == null || !(candidate.getItem() instanceof ItemBlock item)) continue;
            if (Block.getBlockFromItem(item)
                .hasTileEntity(item.getMetadata(candidate.getItemDamage()))) continue;
            if (target != null && !same(target, candidate)) return null;
            target = candidate.copy();
            target.stackSize = 1;
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private static boolean dismantleMatches(Entry entry, IStructureElement<Object> element, World world,
        AutoPlaceEnvironment environment) {
        if (element == StructureUtility.isAir() || element == StructureUtility.notAir()) return false;
        if (element instanceof IStructureElementChain<?>chain) {
            // A concrete fallback must match on its own; an ANY sibling must not authorise unrelated blocks.
            for (IStructureElement<?> fallback : chain.fallbacks()) {
                if (dismantleMatches(entry, (IStructureElement<Object>) fallback, world, environment)) return true;
            }
            return false;
        }
        Position p = entry.position;
        IStructureElement.BlocksToPlace candidates = element
            .getBlocksToPlace(entry.controller, world, p.x, p.y, p.z, entry.trigger, environment);
        if (candidates == null || candidates.getStacks() == null) return false;
        for (ItemStack candidate : candidates.getStacks()) {
            if (candidate == null || !(candidate.getItem() instanceof ItemBlock item)) continue;
            if (Block.getBlockFromItem(item)
                .hasTileEntity(item.getMetadata(candidate.getItemDamage()))) continue;
            // Native check recognises every valid tier, independently of the projector's selected channel value.
            return entry.matches(element, world);
        }
        return false;
    }

    private static boolean dismantle(Entry entry, ItemStack projector, World world, EntityPlayerMP player,
        BetterMachineProjectorStorage storage, AutoPlaceEnvironment environment) {
        if (!dismantleMatches(entry, entry.element, world, environment)) return false;
        Position p = entry.position;
        if (world.captureBlockSnapshots || !world.capturedBlockSnapshots.isEmpty()) return false;
        Block block = world.getBlock(p.x, p.y, p.z);
        int meta = world.getBlockMetadata(p.x, p.y, p.z);
        if (block.getBlockHardness(world, p.x, p.y, p.z) < 0 || block.getMaterial()
            .isLiquid()) return false;
        BlockEvent.BreakEvent event = ForgeHooks
            .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, p.x, p.y, p.z);
        if (event.isCanceled() || !BetterMachineProjectorSettings.owns(player, projector)
            || world.getTileEntity(p.x, p.y, p.z) != null
            || world.getBlock(p.x, p.y, p.z) != block
            || world.getBlockMetadata(p.x, p.y, p.z) != meta) {
            resync(player, world, p);
            return false;
        }
        List<ItemStack> drops = drops(block, meta, world, player, p);
        if (!BetterMachineProjectorSettings.owns(player, projector) || world.getTileEntity(p.x, p.y, p.z) != null
            || world.getBlock(p.x, p.y, p.z) != block
            || world.getBlockMetadata(p.x, p.y, p.z) != meta) {
            resync(player, world, p);
            return false;
        }
        BlockSnapshot original = BlockSnapshot.getBlockSnapshot(world, p.x, p.y, p.z);
        boolean committed = false;
        try {
            world.captureBlockSnapshots = true;
            try {
                if (!world.setBlockToAir(p.x, p.y, p.z)) return false;
            } finally {
                world.captureBlockSnapshots = false;
            }
            if (!BetterMachineProjectorSettings.owns(player, projector) || !world.isAirBlock(p.x, p.y, p.z))
                return false;
            for (BlockSnapshot change : world.capturedBlockSnapshots) {
                if (change.x != p.x || change.y != p.y || change.z != p.z) return false;
            }
            world.capturedBlockSnapshots.clear();
            committed = true;
            world.markAndNotifyBlock(p.x, p.y, p.z, null, block, world.getBlock(p.x, p.y, p.z), 3);
        } finally {
            world.captureBlockSnapshots = false;
            if (!committed) {
                rollback(world, original, block, meta, p);
                resync(player, world, p);
            } else storage.giveAll(drops);
        }
        return true;
    }

    private static boolean replace(Entry entry, IStructureElement<Object> element, ItemStack projector, World world,
        EntityPlayerMP player, BetterMachineProjectorStorage storage, AutoPlaceEnvironment environment,
        Set<String> missing) {
        Position p = entry.position;
        ItemStack target = target(entry, element, world, entry.trigger, environment);
        if (target == null) return false;
        Block old = world.getBlock(p.x, p.y, p.z);
        int meta = world.getBlockMetadata(p.x, p.y, p.z);
        if (old.getBlockHardness(world, p.x, p.y, p.z) < 0 || old.getMaterial()
            .isLiquid()) return false;
        ItemBlock targetItem = (ItemBlock) target.getItem();
        Block targetBlock = Block.getBlockFromItem(targetItem);
        if (targetBlock == old && targetItem.getMetadata(target.getItemDamage()) == meta) return false;
        if (targetBlock.hasTileEntity(targetItem.getMetadata(target.getItemDamage()))) return false;
        if (world.captureBlockSnapshots || !world.capturedBlockSnapshots.isEmpty()) return false;
        if (!player.capabilities.isCreativeMode && !storage.takeOne(target, true)) {
            if (missing.add(target.getDisplayName())) message(player, "missing", target.getDisplayName());
            return false;
        }
        BlockEvent.BreakEvent event = ForgeHooks
            .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, p.x, p.y, p.z);
        if (event.isCanceled() || !BetterMachineProjectorSettings.owns(player, projector)
            || world.getTileEntity(p.x, p.y, p.z) != null
            || world.getBlock(p.x, p.y, p.z) != old
            || world.getBlockMetadata(p.x, p.y, p.z) != meta) {
            resync(player, world, p);
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, p.x, p.y, p.z);
        List<ItemStack> drops = drops(old, meta, world, player, p);
        if (!BetterMachineProjectorSettings.owns(player, projector) || world.getTileEntity(p.x, p.y, p.z) != null
            || world.getBlock(p.x, p.y, p.z) != old
            || world.getBlockMetadata(p.x, p.y, p.z) != meta) {
            resync(player, world, p);
            return false;
        }
        boolean paid = !player.capabilities.isCreativeMode;
        if (paid && !storage.takeOne(target, false)) {
            resync(player, world, p);
            return false;
        }
        if (!BetterMachineProjectorSettings.owns(player, projector) || world.captureBlockSnapshots
            || !world.capturedBlockSnapshots.isEmpty()
            || world.getTileEntity(p.x, p.y, p.z) != null
            || world.getBlock(p.x, p.y, p.z) != old
            || world.getBlockMetadata(p.x, p.y, p.z) != meta) {
            if (paid) storage.give(target);
            resync(player, world, p);
            return false;
        }
        boolean committed = false;
        try {
            world.captureBlockSnapshots = true;
            try {
                ItemStack placing = target.copy();
                if (!targetItem.placeBlockAt(
                    placing,
                    player,
                    world,
                    p.x,
                    p.y,
                    p.z,
                    ForgeDirection.UP.ordinal(),
                    0.5F,
                    0.5F,
                    0.5F,
                    targetItem.getMetadata(target.getItemDamage()))) return false;
            } finally {
                world.captureBlockSnapshots = false;
            }
            List<BlockSnapshot> changes = new ArrayList<>(world.capturedBlockSnapshots);
            BlockEvent.PlaceEvent placed = changes.size() > 1
                ? ForgeEventFactory.onPlayerMultiBlockPlace(player, changes, ForgeDirection.UP)
                : changes.isEmpty() ? null
                    : ForgeEventFactory.onPlayerBlockPlace(player, changes.get(0), ForgeDirection.UP);
            if (placed != null && placed.isCanceled() || !BetterMachineProjectorSettings.owns(player, projector)
                || world.getBlock(p.x, p.y, p.z) != targetBlock
                || world.getBlockMetadata(p.x, p.y, p.z) != targetItem.getMetadata(target.getItemDamage()))
                return false;
            for (BlockSnapshot change : changes) {
                if (change.x != p.x || change.y != p.y
                    || change.z != p.z
                    || world.getTileEntity(change.x, change.y, change.z) != null) return false;
            }
            world.capturedBlockSnapshots.clear();
            committed = true;
            targetBlock.onBlockAdded(world, p.x, p.y, p.z);
            world.markAndNotifyBlock(p.x, p.y, p.z, null, old, targetBlock, 3);
        } finally {
            world.captureBlockSnapshots = false;
            if (!committed) {
                rollback(world, snapshot, old, meta, p);
                if (paid && world.getBlock(p.x, p.y, p.z) == old && world.getBlockMetadata(p.x, p.y, p.z) == meta)
                    storage.give(target);
                resync(player, world, p);
            } else {
                storage.giveAll(drops);
            }
        }
        return true;
    }

    private static void rollback(World world, BlockSnapshot original, Block old, int meta, Position p) {
        world.restoringBlockSnapshots = true;
        try {
            for (int index = world.capturedBlockSnapshots.size() - 1; index >= 0; index--)
                world.capturedBlockSnapshots.get(index)
                    .restore(true, false);
            if (world.getBlock(p.x, p.y, p.z) != old || world.getBlockMetadata(p.x, p.y, p.z) != meta
                || world.getTileEntity(p.x, p.y, p.z) != null) original.restore(true, false);
            if (world.getBlock(p.x, p.y, p.z) != old || world.getBlockMetadata(p.x, p.y, p.z) != meta
                || world.getTileEntity(p.x, p.y, p.z) != null)
                throw new IllegalStateException("Machine projector could not restore original block");
        } finally {
            world.restoringBlockSnapshots = false;
            world.capturedBlockSnapshots.clear();
        }
    }

    private static List<ItemStack> drops(Block block, int meta, World world, EntityPlayerMP player, Position p) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        if (!world.getGameRules()
            .getGameRuleBooleanValue("doTileDrops")) return drops;
        drops.addAll(block.getDrops(world, p.x, p.y, p.z, meta, 0));
        float chance = ForgeEventFactory
            .fireBlockHarvesting(drops, world, block, p.x, p.y, p.z, meta, 0, 1F, false, player);
        drops.removeIf(drop -> drop == null || drop.stackSize <= 0 || world.rand.nextFloat() > chance);
        return drops;
    }

    private static void message(EntityPlayerMP player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation("tenkeitech.better_machine_projector.message." + key, args));
    }

    private static void resync(EntityPlayerMP player, World world, Position position) {
        player.playerNetServerHandler.sendPacket(new S23PacketBlockChange(position.x, position.y, position.z, world));
    }
}
