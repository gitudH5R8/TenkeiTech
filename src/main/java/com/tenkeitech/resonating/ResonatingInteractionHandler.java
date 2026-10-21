/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tenkeitech.ModItems;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Clearing is restricted to the actual air interaction, never the fallback item callback after a block click. */
public final class ResonatingInteractionHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent event) {
        if (event.isCanceled() || event.world == null
            || event.world.isRemote
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.useItem == Event.Result.DENY
            || event.entityPlayer == null
            || !event.entityPlayer.isSneaking()) return;
        var held = event.entityPlayer.getHeldItem();
        if (held == null || held.getItem() != ModItems.RESONATING_PATTERN
            || !(ModItems.RESONATING_PATTERN
                .getPatternForItem(held, event.world) instanceof ResonatingPatternData details))
            return;
        if (!(event.entityPlayer instanceof net.minecraft.entity.player.EntityPlayerMP player)) return;
        double reach = player.theItemInWorldManager.getBlockReachDistance();
        if (reach <= 0 || reach > 32) return;
        var eye = net.minecraft.util.Vec3
            .createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        var look = player.getLookVec();
        for (double distance = 0; distance <= reach; distance += 0.5) {
            int x = (int) Math.floor(eye.xCoord + look.xCoord * distance);
            int z = (int) Math.floor(eye.zCoord + look.zCoord * distance);
            if (!event.world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) return;
        }
        var end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        if (!event.world.getChunkProvider()
            .chunkExists((int) Math.floor(end.xCoord) >> 4, (int) Math.floor(end.zCoord) >> 4)
            || event.world.rayTraceBlocks(eye, end) != null) return;
        int selected = ResonatingPatternData.selectedInput(held, details);
        var route = held.getTagCompound()
            .getCompoundTag(ResonatingPatternData.ROUTING);
        route.setInteger("selected", selected);
        ResonatingPatternData.setTarget(held, details, selected, null);
        event.entityPlayer.inventory.markDirty();
        event.entityPlayer.inventoryContainer.detectAndSendChanges();
        event.useItem = Event.Result.DENY;
        event.setCanceled(true);
    }
}
