/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.integration.enderio.UniversalWandTravelIntegration;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandNetwork;

import appeng.api.implementations.tiles.IWirelessAccessPoint;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Only clicks that can perform another wand action need a server travel decision. */
public final class UniversalWandTravelFallback {

    private static Request pending;
    private static int sequence;
    private static volatile int approved;

    public static void allow(int id) {
        approved = id;
    }

    /** Capture entity use and offhand fallback before their packets, including Backhand's reversed hand order. */
    public static void beginClick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        net.minecraft.util.MovingObjectPosition hit = minecraft.objectMouseOver;
        if (player == null || hit == null || !Loader.isModLoaded("EnderIO")) return;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)
            || !ItemUniversalWand.isTeleportEnabled(stack)) return;
        boolean offhand = Loader.isModLoaded("backhand")
            && UniversalWandBackhandIntegration.getOffhandItem(player) != null;
        if (offhand || hit.entityHit != null && ItemUniversalWand.isShearsEnabled(stack)) request(player, stack);
    }

    public static void finishClick() {
        if (pending == null || pending.finished
            || pending.player != Minecraft.getMinecraft().thePlayer
            || pending.tick != pending.player.ticksExisted) return;
        pending.finished = true;
        UniversalWandNetwork.finishTravelFallback(pending.id);
    }

    public static void useIfImmediate(ItemStack stack, EntityPlayer player) {
        if (pending != null && pending.player == player && pending.tick == player.ticksExisted) return;
        pending = null;
        approved = 0;
        if (Loader.isModLoaded("EnderIO") && ItemUniversalWand.isTeleportEnabled(stack))
            UniversalWandTravelIntegration.use(stack, player.worldObj, player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.world.isRemote || event.useItem == cpw.mods.fml.common.eventhandler.Event.Result.DENY
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || !Loader.isModLoaded("EnderIO")) return;
        EntityPlayer player = event.entityPlayer;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)
            || !ItemUniversalWand.isTeleportEnabled(stack)) return;
        // A block click can also enter the air-use fallback in the same client tick.
        if (pending != null && pending.player == player && pending.tick == player.ticksExisted) return;
        if (!needsDecision(event, stack)) return;
        request(player, stack);
    }

    private static void request(EntityPlayer player, ItemStack stack) {
        if (pending != null && pending.player == player && pending.tick == player.ticksExisted) return;
        int id = ++sequence;
        if (id == 0) id = ++sequence;
        approved = 0;
        pending = new Request(player, stack, id);
        UniversalWandNetwork.requestTravelFallback(id);
    }

    private static boolean needsDecision(PlayerInteractEvent event, ItemStack stack) {
        EntityPlayer player = event.entityPlayer;
        boolean building = ItemUniversalWand.isBuildingEnabled(stack);
        boolean undo = building && com.tenkeitech.UniversalWandKeyHandler.isUndoKeyHeld();
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return undo || building && !player.isSneaking()
                || ItemUniversalWand.getGtToolMode(stack) != ItemUniversalWand.GtToolMode.OFF
                || ItemUniversalWand.isHoeEnabled(stack)
                || player.isSneaking() && (ItemUniversalWand.isTimeAccelerationEnabled(stack)
                    || event.world.getTileEntity(event.x, event.y, event.z) instanceof IWirelessAccessPoint);
        }
        if (building && !undo
            && !player.isSneaking()
            && ItemUniversalWand.getBuildingCore(stack) == ItemUniversalWand.BuildingCore.ANGEL) return true;
        net.minecraft.util.MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        return ItemUniversalWand.isShearsEnabled(stack) && hit != null && hit.entityHit != null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Request request = pending;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != request.player || player.worldObj != request.world
            || player.isDead
            || Minecraft.getMinecraft().currentScreen != null
            || System.nanoTime() > request.deadline
            || player.inventory.currentItem != request.slot
            || !ItemStack.areItemStacksEqual(player.getHeldItem(), request.stack)) {
            pending = null;
            approved = 0;
            return;
        }
        if (approved != request.id) return;
        pending = null;
        approved = 0;
        if (Loader.isModLoaded("EnderIO") && ItemUniversalWand.isTeleportEnabled(player.getHeldItem()))
            UniversalWandTravelIntegration.use(player.getHeldItem(), player.worldObj, player);
    }

    private static final class Request {

        final EntityPlayer player;
        final World world;
        final ItemStack stack;
        final int slot, id, tick;
        boolean finished;
        final long deadline = System.nanoTime() + 2_000_000_000L;

        Request(EntityPlayer player, ItemStack stack, int id) {
            this.player = player;
            this.world = player.worldObj;
            this.stack = stack.copy();
            this.slot = player.inventory.currentItem;
            this.id = id;
            this.tick = player.ticksExisted;
        }
    }
}
