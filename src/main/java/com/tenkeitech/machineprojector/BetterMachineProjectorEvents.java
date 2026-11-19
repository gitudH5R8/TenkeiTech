/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Known remainders survive logout and death; uncertain external insertions are never replayed. */
public final class BetterMachineProjectorEvents {

    private static final Map<EntityPlayerMP, PendingSettings> PENDING_SETTINGS = new WeakHashMap<>();

    public static void requestSettings(EntityPlayerMP player, ItemStack stack) {
        if (!BetterMachineProjectorSettings.owns(player, stack) || player.isDead) return;
        // The native air-use packet replaces the held ItemStack after the item callback returns. Opening here
        // would capture the discarded instance and immediately fail the GUI's exact held-stack check.
        PENDING_SETTINGS.put(player, new PendingSettings(player.inventory.currentItem, stack.copy()));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
        PendingSettings pending = PENDING_SETTINGS.remove(player);
        if (pending != null && !player.isDead
            && player.openContainer == player.inventoryContainer
            && player.inventory.currentItem == pending.slot
            && ItemStack.areItemStacksEqual(player.getHeldItem(), pending.expected)
            && BetterMachineProjectorSettings.owns(player, player.getHeldItem())) {
            try {
                player.sendContainerToPlayer(player.inventoryContainer);
                GuiFactories.playerInventory()
                    .openFromMainHand(player);
            } catch (RuntimeException error) {
                TenkeiTech.LOG.error(
                    "Could not open better machine projector settings for player {}",
                    player.getUniqueID(),
                    error);
                player.addChatMessage(
                    new ChatComponentTranslation("tenkeitech.better_machine_projector.message.settings_failed"));
            }
        }
        if (!player.isDead && player.ticksExisted % 20 == 0) BetterMachineProjectorStorage.recover(player);
    }

    private static final class PendingSettings {

        private final int slot;
        private final ItemStack expected;

        private PendingSettings(int slot, ItemStack expected) {
            this.slot = slot;
            this.expected = expected;
        }
    }
}
