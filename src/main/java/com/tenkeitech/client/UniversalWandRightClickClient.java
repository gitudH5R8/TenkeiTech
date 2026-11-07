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

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.WandRightClickRouter;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.universalwand.interaction.WandActionResult;
import com.tenkeitech.universalwand.interaction.WandRightClickContext;
import com.tenkeitech.universalwand.interaction.WandRightClickContext.Phase;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Input boundaries and validated continuations only; feature priority lives in the common router. */
public final class UniversalWandRightClickClient {

    private static Request pending;
    private static int sequence;
    private static volatile Approval approved;

    public static void allow(int id, int action) {
        approved = new Approval(id, action);
    }

    public static void beginClick() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        pending = null;
        approved = null;
        if (player == null || Minecraft.getMinecraft().objectMouseOver == null || !ItemUniversalWand.isHeldBy(player))
            return;
        int id = ++sequence;
        if (id == 0) id = ++sequence;
        pending = new Request(player, player.getHeldItem(), id);
        UniversalWandNetwork.beginRightClick(id);
        if (ItemUniversalWand.isBuildingEnabled(player.getHeldItem())
            || ItemUniversalWand.isTimeAccelerationEnabled(player.getHeldItem()))
            UniversalWandNetwork.requestUndoKey(com.tenkeitech.UniversalWandKeyHandler.isUndoKeyHeld());
    }

    public static void finishClick() {
        if (pending == null || pending.finished || pending.player != Minecraft.getMinecraft().thePlayer) return;
        pending.finished = true;
        UniversalWandNetwork.finishRightClick(pending.id, pending.blocked);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.world.isRemote && pending != null
            && pending.player == event.entityPlayer
            && WandActionResult.fromInteraction(event) == WandActionResult.BLOCKED) pending.blocked = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Request request = pending;
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (player != request.player || player.worldObj != request.world
            || player.isDead
            || minecraft.currentScreen != null
            || System.nanoTime() > request.deadline
            || player.inventory.currentItem != request.slot
            || !ItemStack.areItemStacksEqual(player.getHeldItem(), request.stack)) {
            pending = null;
            approved = null;
            return;
        }
        Approval approval = approved;
        if (!request.finished || approval == null || approval.id != request.id) return;
        pending = null;
        approved = null;
        WandRightClickRouter.resume(
            approval.action,
            WandRightClickContext.air(Phase.COMPLETE, player, player.getHeldItem(), request.id));
    }

    private static final class Approval {

        final int id, action;

        Approval(int id, int action) {
            this.id = id;
            this.action = action;
        }
    }

    private static final class Request {

        final EntityPlayer player;
        final World world;
        final ItemStack stack;
        final int slot, id;
        boolean finished, blocked;
        final long deadline = System.nanoTime() + 2_000_000_000L;

        Request(EntityPlayer player, ItemStack stack, int id) {
            this.player = player;
            world = player.worldObj;
            this.stack = stack.copy();
            slot = player.inventory.currentItem;
            this.id = id;
        }
    }
}
