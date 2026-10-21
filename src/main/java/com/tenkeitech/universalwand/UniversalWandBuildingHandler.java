/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.universalwand.building.BuildingContext;
import com.tenkeitech.universalwand.building.BuildingContext.Position;
import com.tenkeitech.universalwand.building.BuildingJob;
import com.tenkeitech.universalwand.building.BuildingUndoHistory;
import com.tenkeitech.universalwand.building.InventorySupplier;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Dispatches a click to the main thread. BuildingJob completes the entire placement in that call. */
public final class UniversalWandBuildingHandler {

    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Request> SUPPLY_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SUPPLY_TICK = new java.util.HashMap<>();

    private static final class Request {

        final EntityPlayerMP player;
        final int dimension, x, y, z, side;
        final float hitX, hitY, hitZ;
        final int core;
        final BuildingContext airContext;
        final int heldSlot;
        final ItemStack wandSnapshot;
        final boolean undo;

        Request(EntityPlayerMP player, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int core,
            BuildingContext airContext, boolean undo) {
            this.player = player;
            this.dimension = player.dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.core = core;
            this.airContext = airContext;
            this.heldSlot = airContext == null ? -1 : player.inventory.currentItem;
            this.wandSnapshot = airContext == null ? null : airContext.wand.copy();
            this.undo = undo;
        }
    }

    public static void requestStart(EntityPlayerMP player, int x, int y, int z, int side, float hitX, float hitY,
        float hitZ, int core) {
        requestStart(player, x, y, z, side, hitX, hitY, hitZ, core, false);
    }

    public static void requestStart(EntityPlayerMP player, int x, int y, int z, int side, float hitX, float hitY,
        float hitZ, int core, boolean undo) {
        if (player != null && side >= 0
            && side < 6
            && validHit(hitX)
            && validHit(hitY)
            && validHit(hitZ)
            && core >= 0
            && core < BuildingCore.values().length)
            REQUESTS.put(player.getUniqueID(), new Request(player, x, y, z, side, hitX, hitY, hitZ, core, null, undo));
    }

    /** Native air-use packets carry no placement coordinates; derive them on the server. */
    public static void requestAir(EntityPlayerMP player) {
        if (!ItemUniversalWand.isHeldBy(player)) return;
        BuildingContext context = BuildingContext.createAir(player, player.getHeldItem());
        if (context == null) return;
        Request request = new Request(
            player,
            context.origin.x,
            context.origin.y,
            context.origin.z,
            context.face.ordinal(),
            context.hitX,
            context.hitY,
            context.hitZ,
            context.core.ordinal(),
            context,
            false);
        // A block-use fallback must not replace a pending clicked-surface action with an air action.
        REQUESTS.compute(
            player.getUniqueID(),
            (id, previous) -> previous != null && previous.airContext == null ? previous : request);
    }

    private static boolean validHit(float hit) {
        return Float.isFinite(hit) && hit >= 0F && hit <= 1F;
    }

    public static boolean hasPendingAction(EntityPlayerMP player) {
        Request request = REQUESTS.get(player.getUniqueID());
        return request != null && request.player == player;
    }

    public static void requestSupply(EntityPlayerMP player, int x, int y, int z, int side, float hitX, float hitY,
        float hitZ) {
        if (player != null && side >= 0 && side < 6 && validHit(hitX) && validHit(hitY) && validHit(hitZ))
            SUPPLY_REQUESTS.put(
                player.getUniqueID(),
                new Request(player, x, y, z, side, hitX, hitY, hitZ, BuildingCore.NORMAL.ordinal(), null, false));
    }

    private static void updateSupply(Request request) {
        EntityPlayerMP player = request.player;
        if (player.isDead || player.worldObj == null
            || player.dimension != request.dimension
            || !player.worldObj.playerEntities.contains(player)
            || player.isSneaking()
            || !ItemUniversalWand.isHeldBy(player)
            || !ItemUniversalWand.isBuildingEnabled(player.getHeldItem())
            || !ItemUniversalWand.isBuildingAeEnabled(player.getHeldItem())
            || ItemUniversalWand.getBuildingCore(player.getHeldItem()) != BuildingCore.NORMAL) return;
        long now = player.worldObj.getTotalWorldTime();
        Long previous = LAST_SUPPLY_TICK.get(player.getUniqueID());
        if (previous != null && now >= previous && now - previous < 10L) return;
        LAST_SUPPLY_TICK.put(player.getUniqueID(), now);
        BuildingContext context = BuildingContext.create(player, player.getHeldItem(), validateClick(request));
        if (context == null) return;
        InventorySupplier supplier = new InventorySupplier(context);
        UniversalWandNetwork.sendBuildingSupply(player, context, supplier.getMaterial(), supplier.getAeAvailable());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOffhandInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.entityPlayer.isSneaking()
            || !Loader.isModLoaded("backhand")) return;
        ItemStack mainhand = UniversalWandBackhandIntegration.getMainhandItem(event.entityPlayer);
        if (mainhand != event.entityPlayer.getHeldItem() && mainhand != null
            && mainhand.getItem() instanceof ItemUniversalWand
            && ItemUniversalWand.isBuildingEnabled(mainhand)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BuildingUndoHistory.tick();
        for (Map.Entry<UUID, Request> entry : REQUESTS.entrySet()) {
            Request request = entry.getValue();
            if (!REQUESTS.remove(entry.getKey(), request)) continue;
            try {
                execute(request);
            } catch (RuntimeException failure) {
                TenkeiTech.LOG.error(
                    "Universal Wand building request failed for {}",
                    request.player.getCommandSenderName(),
                    failure);
            } finally {
                UniversalWandRightClick.complete(request.player);
            }
        }
        for (Map.Entry<UUID, Request> entry : SUPPLY_REQUESTS.entrySet()) {
            if (!SUPPLY_REQUESTS.remove(entry.getKey(), entry.getValue())) continue;
            try {
                updateSupply(entry.getValue());
            } catch (RuntimeException failure) {
                TenkeiTech.LOG.warn("Universal Wand building supply preview unavailable", failure);
            }
        }
    }

    private static void execute(Request request) {
        EntityPlayerMP player = request.player;
        if (player.isDead || player.dimension != request.dimension
            || player.worldObj == null
            || !player.worldObj.playerEntities.contains(player)
            || player.isSneaking() && !request.undo
            || !ItemUniversalWand.isHeldBy(player)
            || !ItemUniversalWand.isBuildingEnabled(player.getHeldItem())
            || ItemUniversalWand.getBuildingCore(player.getHeldItem())
                .ordinal() != request.core)
            return;
        if (request.undo) {
            if (!BuildingUndoHistory.isActive(player) || validateClick(request) == null) return;
            if (!player.canPlayerEdit(request.x, request.y, request.z, request.side, player.getHeldItem())
                || MinecraftServer.getServer()
                    .isBlockProtected((WorldServer) player.worldObj, request.x, request.y, request.z, player))
                return;
            PlayerInteractEvent interaction = ForgeEventFactory.onPlayerInteract(
                player,
                PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
                request.x,
                request.y,
                request.z,
                request.side,
                player.worldObj);
            if (!interaction.isCanceled() && interaction.useItem != Event.Result.DENY)
                BuildingUndoHistory.undo(player, new Position(request.x, request.y, request.z));
            return;
        }
        if (BuildingUndoHistory.isActive(player)) return;
        BuildingContext context;
        if (request.airContext != null) {
            context = request.airContext;
            if (request.heldSlot != player.inventory.currentItem
                || !ItemStack.areItemStacksEqual(request.wandSnapshot, player.getHeldItem())
                || context.world != player.worldObj
                || !validateAir(context)) return;
            context = context.withWand(player.getHeldItem());
        } else {
            MovingObjectPosition hit = validateClick(request);
            if (hit == null) return;
            context = BuildingContext.create(player, player.getHeldItem(), hit);
        }
        if (context == null) return;
        if (MinecraftServer.getServer()
            .isBlockProtected((WorldServer) player.worldObj, request.x, request.y, request.z, player)
            || !player.canPlayerEdit(request.x, request.y, request.z, request.side, context.wand)) return;
        PlayerInteractEvent interaction = ForgeEventFactory.onPlayerInteract(
            player,
            context.air ? PlayerInteractEvent.Action.RIGHT_CLICK_AIR : PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
            request.x,
            request.y,
            request.z,
            request.side,
            player.worldObj);
        if (interaction.isCanceled() || interaction.useItem == Event.Result.DENY
            || player.getHeldItem() != context.wand) return;
        BuildingJob job = new BuildingJob(context);
        if (!job.isSupported()) return;
        job.getSnapshots(BuildingContext.MAX_BLOCKS);
        job.execute();
    }

    private static boolean validateAir(BuildingContext context) {
        EntityPlayerMP player = (EntityPlayerMP) context.player;
        if (context.core != BuildingCore.ANGEL || !player.capabilities.isCreativeMode && player.fallDistance > 10F
            || !BuildingContext.loaded(context.world, context.origin.x, context.origin.y, context.origin.z))
            return false;
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 end = context.hit.hitVec;
        if (eye.squareDistanceTo(end) > 16D || !loadedRay(context.world, eye, end)) return false;
        MovingObjectPosition visible = context.world.rayTraceBlocks(eye, end);
        return visible == null || visible.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private static boolean loadedRay(net.minecraft.world.World world, Vec3 start, Vec3 end) {
        int minChunkX = (int) Math.floor(Math.min(start.xCoord, end.xCoord)) >> 4;
        int maxChunkX = (int) Math.floor(Math.max(start.xCoord, end.xCoord)) >> 4;
        int minChunkZ = (int) Math.floor(Math.min(start.zCoord, end.zCoord)) >> 4;
        int maxChunkZ = (int) Math.floor(Math.max(start.zCoord, end.zCoord)) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!world.getChunkProvider()
                    .chunkExists(x, z)) return false;
            }
        }
        return BuildingContext
            .loaded(world, (int) Math.floor(end.xCoord), (int) Math.floor(end.yCoord), (int) Math.floor(end.zCoord));
    }

    private static MovingObjectPosition validateClick(Request request) {
        EntityPlayerMP player = request.player;
        double reach = player.theItemInWorldManager.getBlockReachDistance() + 1D;
        if (!Double.isFinite(reach) || reach <= 0D
            || player.getDistanceSq(request.x + 0.5D, request.y + 0.5D, request.z + 0.5D) >= reach * reach
            || !BuildingContext.loaded(player.worldObj, request.x, request.y, request.z)) return null;
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 click = Vec3
            .createVectorHelper(request.x + request.hitX, request.y + request.hitY, request.z + request.hitZ);
        Vec3 delta = Vec3
            .createVectorHelper(click.xCoord - eye.xCoord, click.yCoord - eye.yCoord, click.zCoord - eye.zCoord);
        double length = delta.lengthVector();
        if (!Double.isFinite(length) || length <= 0D || length > reach + 1D) return null;
        // Preserve the original click face; movement may change the current crosshair before main-thread dispatch.
        Vec3 end = click
            .addVector(delta.xCoord / length * 0.001D, delta.yCoord / length * 0.001D, delta.zCoord / length * 0.001D);
        int minChunkX = (int) Math.floor(Math.min(eye.xCoord, end.xCoord)) >> 4;
        int maxChunkX = (int) Math.floor(Math.max(eye.xCoord, end.xCoord)) >> 4;
        int minChunkZ = (int) Math.floor(Math.min(eye.zCoord, end.zCoord)) >> 4;
        int maxChunkZ = (int) Math.floor(Math.max(eye.zCoord, end.zCoord)) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!player.worldObj.getChunkProvider()
                    .chunkExists(x, z)) return null;
            }
        }
        if (!BuildingContext.loaded(
            player.worldObj,
            (int) Math.floor(end.xCoord),
            (int) Math.floor(end.yCoord),
            (int) Math.floor(end.zCoord))) return null;
        MovingObjectPosition visible = player.worldObj.rayTraceBlocks(eye, end);
        if (visible == null || visible.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || visible.blockX != request.x
            || visible.blockY != request.y
            || visible.blockZ != request.z) return null;
        return new MovingObjectPosition(request.x, request.y, request.z, request.side, click);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.player.getUniqueID());
        SUPPLY_REQUESTS.remove(event.player.getUniqueID());
        LAST_SUPPLY_TICK.remove(event.player.getUniqueID());
        BuildingUndoHistory.clear(event.player.getUniqueID());
    }

    public static void clearAll() {
        REQUESTS.clear();
        SUPPLY_REQUESTS.clear();
        LAST_SUPPLY_TICK.clear();
        BuildingUndoHistory.clearAll();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        BuildingUndoHistory.unload(event.world);
        REQUESTS.entrySet()
            .removeIf(entry -> entry.getValue().player.worldObj == event.world);
        SUPPLY_REQUESTS.entrySet()
            .removeIf(entry -> entry.getValue().player.worldObj == event.world);
        for (Object player : event.world.playerEntities)
            LAST_SUPPLY_TICK.remove(((net.minecraft.entity.player.EntityPlayer) player).getUniqueID());
    }
}
