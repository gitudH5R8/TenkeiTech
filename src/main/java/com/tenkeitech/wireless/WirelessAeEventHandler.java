/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tenkeitech.machine.MTEWirelessNetworkCore.BindResult;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server-authoritative event entries for wireless binding and lifecycle cleanup. */
public final class WirelessAeEventHandler {

    private static final WirelessAeEventHandler INSTANCE = new WirelessAeEventHandler();
    private static boolean registered;

    private WirelessAeEventHandler() {}

    public static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || event.world == null || event.world.isRemote) return;
        WirelessAeSneakPlaceBinder.invalidate(event.world, event.x, event.y, event.z);
        clearDestroyedTarget(event.world, event.x, event.y, event.z);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (event.isCanceled() || event.world == null || event.world.isRemote) return;
        if (event instanceof BlockEvent.MultiPlaceEvent multiPlaceEvent) {
            for (BlockSnapshot snapshot : multiPlaceEvent.getReplacedBlockSnapshots()) {
                WirelessAeSneakPlaceBinder.invalidate(snapshot.world, snapshot.x, snapshot.y, snapshot.z);
                clearDestroyedTarget(snapshot.world, snapshot.x, snapshot.y, snapshot.z);
            }
            return;
        }
        WirelessAeSneakPlaceBinder.invalidate(event.world, event.x, event.y, event.z);
        clearDestroyedTarget(event.world, event.x, event.y, event.z);
        WirelessAeSneakPlaceBinder.enqueue(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.world == null || event.world.isRemote) return;
        for (ChunkPosition position : event.getAffectedBlocks()) {
            WirelessAeSneakPlaceBinder
                .invalidate(event.world, position.chunkPosX, position.chunkPosY, position.chunkPosZ);
            clearDestroyedTarget(event.world, position.chunkPosX, position.chunkPosY, position.chunkPosZ);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) return;
        WirelessAeRuntimeRegistry.disconnectTargetWorld(event.world);
        WirelessAeSneakPlaceBinder.clear(event.world);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) return;
        WirelessAeSneakPlaceBinder.tick(event.world);
    }

    private static void clearDestroyedTarget(net.minecraft.world.World world, int x, int y, int z) {
        WirelessAeWorldData.removeDestroyedBlock(world, x, y, z);
        WirelessAeRuntimeRegistry.clearDestroyedTarget(world, x, y, z);
    }

    public static String getTranslationKey(BindResult result) {
        return switch (result) {
            case BOUND -> "tenkeitech.wireless.bind_success";
            case ALREADY_SAME_GRID -> "tenkeitech.wireless.bind_same_grid";
            case MEMBER_ALREADY_BOUND -> "tenkeitech.wireless.member_already_bound";
            case MEMBER_LIMIT -> "tenkeitech.wireless.member_limit";
            case ACCESS_DENIED -> "tenkeitech.wireless.access_denied";
            case CORE_OFFLINE -> "tenkeitech.wireless.core_offline";
            case CROSS_DIMENSION_DISABLED -> "tenkeitech.wireless.cross_dimension_disabled";
            case INVALID_TARGET -> "tenkeitech.wireless.invalid_target";
            case TARGET_ALREADY_BOUND -> "tenkeitech.wireless.target_already_bound";
            case CONTROLLER_CONFLICT -> "tenkeitech.wireless.controller_conflict";
            case SECURITY_REJECTED -> "tenkeitech.wireless.security_rejected";
            case FAILED -> "tenkeitech.wireless.bind_failed";
        };
    }

    public static void send(EntityPlayer player, String translationKey) {
        if (player != null) player.addChatMessage(new ChatComponentTranslation(translationKey));
    }
}
