/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.universalwand.UniversalWandStorage;

/** Adapted WandJob: plan once, then execute all accepted snapshots in the same server action. */
public final class BuildingJob {

    private final BuildingContext context;
    private final BuildingSupplier supplier;
    private final BuildingAction action;
    private final UniversalWandStorage.Destination refundDestination;
    private List<BuildingSnapshot> snapshots = Collections.emptyList();

    public BuildingJob(BuildingContext context) {
        this.context = context;
        refundDestination = UniversalWandStorage.Destination.forBuilding(context.wand);
        supplier = context.core == BuildingCore.DESTRUCTION ? null : new InventorySupplier(context);
        action = context.core == BuildingCore.ANGEL ? new ActionAngel()
            : context.core == BuildingCore.DESTRUCTION ? new ActionDestruction() : new ActionConstruction();
    }

    public boolean isSupported() {
        return supplier == null || supplier.isSupported();
    }

    public void getSnapshots(int limit) {
        snapshots = action.getSnapshots(context, supplier, limit);
    }

    public List<BuildingSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    public void execute() {
        if (!(context.player instanceof EntityPlayerMP player)) return;
        Block soundBlock = null;
        List<BuildingUndoRecord> executed = new ArrayList<>();
        try {
            for (BuildingSnapshot snapshot : snapshots) {
                if (!context.isActive() || player.getHeldItem() != context.wand) break;
                BuildingSnapshot.Result result;
                try {
                    result = snapshot.execute(context, supplier);
                } finally {
                    BuildingUndoRecord record = snapshot.getUndoRecord();
                    if (record != null) executed.add(record);
                }
                if (result == BuildingSnapshot.Result.STOP) break;
                if (result == BuildingSnapshot.Result.CHANGED) {
                    com.tenkeitech.universalwand.UniversalWandRightClick.handled(player, true);
                    if (soundBlock == null) soundBlock = snapshot.getMaterialBlock();
                }
            }
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.error("Universal Wand building stopped for {}", player.getCommandSenderName(), failure);
        } finally {
            BuildingUndoHistory.add(player, context, executed, refundDestination);
            if (soundBlock != null) context.world.playSoundEffect(
                player.posX,
                player.posY,
                player.posZ,
                soundBlock.stepSound.func_150496_b(),
                (soundBlock.stepSound.getVolume() + 1F) / 2F,
                soundBlock.stepSound.getPitch() * 0.8F);
            player.sendContainerToPlayer(player.inventoryContainer);
            if (supplier != null) supplier.onFinished();
        }
    }
}
