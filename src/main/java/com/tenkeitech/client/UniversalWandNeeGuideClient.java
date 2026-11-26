/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.tenkeitech.ModItems;
import com.tenkeitech.UniversalWandKeyHandler;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.patternupload.PatternUploadScreenBridge;
import com.tenkeitech.universalwand.UniversalWandNetwork;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client-only text and terminal shortcut; no optional NEE types enter the common loading path. */
public final class UniversalWandNeeGuideClient {

    private static volatile INetHandler pending;
    private static final AtomicReference<SettingsReply> PENDING_SETTINGS = new AtomicReference<>();

    private static final class SettingsReply {

        final INetHandler connection;
        final UniversalWandNetwork.NeeSettingsReply message;

        SettingsReply(INetHandler connection, UniversalWandNetwork.NeeSettingsReply message) {
            this.connection = connection;
            this.message = message;
        }
    }

    public static void enqueue(INetHandler connection) {
        if (Loader.isModLoaded("neenergistics")) pending = connection;
    }

    public static void enqueueSettings(UniversalWandNetwork.NeeSettingsReply message, INetHandler connection) {
        PENDING_SETTINGS.set(new SettingsReply(connection, message));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (pending != null && mc.getNetHandler() != pending) {
            pending = null;
        } else if (pending != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                new ChatComponentTranslation(
                    "tenkeitech.gui.universal_wand.nee_intro",
                    UniversalWandKeyHandler.getSettingsKeyName()));
            pending = null;
        }
        SettingsReply reply = PENDING_SETTINGS.getAndSet(null);
        if (mc.currentScreen instanceof PatternUploadScreenBridge bridge) {
            if (reply != null && mc.getNetHandler() == reply.connection)
                bridge.tenkeitech$acceptNeeSettingsReply(reply.message);
            bridge.tenkeitech$tickNeeSettings();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.gui != mc.currentScreen && mc.currentScreen instanceof PatternUploadScreenBridge bridge)
            bridge.tenkeitech$closeNeeSettings();
    }

    @SubscribeEvent
    public void hideHudDuringTerminalOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL
            && Minecraft.getMinecraft().currentScreen instanceof PatternUploadScreenBridge bridge
            && bridge.tenkeitech$isTerminalOverlayOpen()) {
            // Pre(ALL) precedes the GUI projection setup; keep that setup when hiding the HUD.
            Minecraft.getMinecraft().entityRenderer.setupOverlayRendering();
            event.setCanceled(true);
        }
    }

    public static int findWandSlot() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return -1;
        int held = mc.thePlayer.inventory.currentItem;
        if (isWand(mc.thePlayer.inventory.mainInventory[held])) return held;
        for (int slot = 0; slot < mc.thePlayer.inventory.mainInventory.length; slot++) {
            if (isWand(mc.thePlayer.inventory.mainInventory[slot])) return slot;
        }
        return -1;
    }

    public static int activeCount() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return 0;
        return (ModItems.isTenkeiMagicToolCircuitPreserverEnabled(mc.thePlayer.inventory) ? 1 : 0)
            + (ModItems.isTenkeiMagicToolCatalystRecorderEnabled(mc.thePlayer.inventory) ? 1 : 0)
            + (ModItems.isTenkeiMagicToolCondensateReplacementEnabled(mc.thePlayer.inventory) ? 1 : 0)
            + (ModItems.isCutterFluidFilterEnabled(mc.thePlayer.inventory) ? 1 : 0);
    }

    public static List<String> tooltip() {
        List<String> lines = new ArrayList<>();
        lines.add(
            "\u00a7b"
                + StatCollector.translateToLocalFormatted("tenkeitech.gui.universal_wand.nee_status", activeCount()));
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return lines;
        addSetting(lines, "tenkei_circuits", ModItems.isTenkeiMagicToolCircuitPreserverEnabled(mc.thePlayer.inventory));
        addSetting(
            lines,
            "tenkei_catalysts",
            ModItems.isTenkeiMagicToolCatalystRecorderEnabled(mc.thePlayer.inventory));
        addSetting(
            lines,
            "tenkei_condensate",
            ModItems.isTenkeiMagicToolCondensateReplacementEnabled(mc.thePlayer.inventory));
        addSetting(lines, "tenkei_cutter_fluids", ModItems.isCutterFluidFilterEnabled(mc.thePlayer.inventory));
        lines.add("\u00a77" + StatCollector.translateToLocal("tenkeitech.gui.universal_wand.nee_button_config"));
        return lines;
    }

    private static void addSetting(List<String> lines, String key, boolean enabled) {
        lines.add(
            (enabled ? "\u00a7a" : "\u00a77") + StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key)
                + ": "
                + StatCollector
                    .translateToLocal("tenkeitech.item.universal_wand." + (enabled ? "enabled" : "disabled")));
    }

    private static boolean isWand(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemUniversalWand;
    }
}
