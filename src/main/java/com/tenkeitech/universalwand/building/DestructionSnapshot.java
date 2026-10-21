/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Removes a validated ordinary block without harvesting, drops or experience. */
public final class DestructionSnapshot implements BuildingSnapshot {

    private final Position position;
    private final Block block;
    private BuildingUndoRecord undoRecord;

    private DestructionSnapshot(BuildingContext context, Position position) {
        this.position = position;
        this.block = context.target;
    }

    public static DestructionSnapshot create(BuildingContext context, Position position) {
        DestructionSnapshot snapshot = new DestructionSnapshot(context, position);
        return snapshot.canRemove(context) ? snapshot : null;
    }

    private boolean canRemove(BuildingContext context) {
        return context.isActive() && !ItemUniversalWand.isProtectionMode(context.wand)
            && context.matches(position)
            && context.world.getTileEntity(position.x, position.y, position.z) == null
            && context.permeable(position.offset(context.face))
            && context.player.canPlayerEdit(position.x, position.y, position.z, context.face.ordinal(), context.wand)
            && (context.world.isRemote || !MinecraftServer.getServer()
                .isBlockProtected((WorldServer) context.world, position.x, position.y, position.z, context.player));
    }

    @Override
    public Result execute(BuildingContext context, BuildingSupplier supplier) {
        if (!(context.player instanceof EntityPlayerMP player) || !canRemove(context)) return Result.SKIPPED;
        if (context.world.captureBlockSnapshots || !context.world.capturedBlockSnapshots.isEmpty()) return Result.STOP;
        try {
            BlockEvent.BreakEvent event;
            // A building break must not become a new chain-mining origin.
            try (UniversalWandMiningHandler.ForcedBreakScope ignored = UniversalWandMiningHandler.beginForcedBreak()) {
                event = ForgeHooks.onBlockBreakEvent(
                    context.world,
                    player.theItemInWorldManager.getGameType(),
                    player,
                    position.x,
                    position.y,
                    position.z);
            }
            if (event.isCanceled() || player.getHeldItem() != context.wand || !canRemove(context))
                return Result.SKIPPED;
            BlockSnapshot original = BlockSnapshot.getBlockSnapshot(context.world, position.x, position.y, position.z);
            if (!context.world.setBlockToAir(position.x, position.y, position.z)) return Result.SKIPPED;
            if (!context.world.isAirBlock(position.x, position.y, position.z)) return Result.SKIPPED;
            undoRecord = BuildingUndoRecord
                .capture((WorldServer) context.world, Collections.singletonList(original), context.face, null);
            return Result.CHANGED;
        } finally {
            // Forge sends an optimistic air packet before the event, including when our revalidation later rejects it.
            player.playerNetServerHandler
                .sendPacket(new S23PacketBlockChange(position.x, position.y, position.z, context.world));
        }
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public Block getMaterialBlock() {
        return block;
    }

    public BuildingUndoRecord getUndoRecord() {
        return undoRecord;
    }
}
