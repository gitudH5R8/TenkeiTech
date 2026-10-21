/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.betterquesting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.util.EnumChatFormatting;

import com.tenkeitech.questskip.QuestSkipNetwork;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.context.IQuestContextMenuEntry;
import betterquesting.api2.client.gui.context.QuestContextMenuRegistry;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.popups.PopChoice;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** All UI changes execute on the client thread and are bound to the originating screen/connection. */
public final class QuestSkipClient {

    private static volatile boolean enabled;
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static int sequence;
    private static UUID pendingQuest;
    private static GuiScreen pendingScreen;
    private static long deadline;
    private static UUID refreshQuest;
    private static final IQuestContextMenuEntry ENTRY = new IQuestContextMenuEntry() {

        public String getLabel(UUID id, IQuest quest) {
            return I18n.format("tenkeitech.questskip.skip");
        }

        public Runnable getAction(UUID id, IQuest quest) {
            return () -> {
                if (!enabled) return;
                pendingQuest = id;
                pendingScreen = Minecraft.getMinecraft().currentScreen;
                deadline = System.currentTimeMillis() + 15000L;
                sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
                QuestSkipNetwork.request(id, false, sequence);
            };
        }
    };

    private QuestSkipClient() {}

    public static void init() {
        QuestContextMenuRegistry.register(ENTRY);
        FMLCommonHandler.instance()
            .bus()
            .register(new QuestSkipClient());
    }

    public static void filter(List<IQuestContextMenuEntry> entries, IQuest quest) {
        var player = Minecraft.getMinecraft().thePlayer;
        if (!enabled || quest == null || player == null || quest.isComplete(QuestingAPI.getQuestingUUID(player)))
            entries.remove(ENTRY);
    }

    public static void color(PanelButtonQuest button, Map.Entry<UUID, IQuest> entry) {
        if (entry == null || entry.getValue() == null || Minecraft.getMinecraft().thePlayer == null) return;
        var info = entry.getValue()
            .getCompletionInfo(QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer));
        if (info == null || !info.getBoolean(QuestSkipService.MARKER)) return;
        var texture = new GuiTextureColored(button.txFrame, new GuiColorStatic(0xFF006400));
        button.setTextures(texture, texture, texture);
    }

    public static void tooltip(IQuest quest, EntityPlayer player, List<String> lines) {
        if (quest == null || player == null || lines == null || lines.size() < 2) return;
        UUID playerId = QuestingAPI.getQuestingUUID(player);
        if (!quest.isComplete(playerId)) return;
        var info = quest.getCompletionInfo(playerId);
        if (info == null || !info.getBoolean(QuestSkipService.MARKER)) return;
        // BQ 3.8.84 places the completion status immediately after the quest title.
        lines.set(1, EnumChatFormatting.GREEN + I18n.format("tenkeitech.questskip.skipped"));
    }

    public static void receive(QuestSkipNetwork.Reply reply, INetHandler connection) {
        if (QUEUE.size() >= 128) return;
        QUEUE.offer(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() != connection) return;
            if (reply.sequence == 0) {
                enabled = reply.result == 1;
                return;
            }
            if (reply.sequence != sequence || pendingQuest == null
                || mc.currentScreen != pendingScreen
                || System.currentTimeMillis() > deadline
                || SceneController.getActiveScene() != pendingScreen) return;
            if (reply.result == 3) {
                deadline = System.currentTimeMillis() + 60000L;
                UUID id = pendingQuest;
                SceneController.getActiveScene()
                    .openPopup(new PopChoice(I18n.format("tenkeitech.questskip.confirm"), option -> {
                        if (option == 0 && enabled && mc.currentScreen == pendingScreen) {
                            deadline = System.currentTimeMillis() + 15000L;
                            QuestSkipNetwork.request(id, true, sequence);
                        } else pendingQuest = null;
                    }, I18n.format("gui.yes"), I18n.format("gui.no")));
            } else if (reply.result == 2 || reply.result == 5) {
                pendingQuest = null;
                SceneController.getActiveScene()
                    .openPopup(
                        new PopChoice(
                            I18n.format(
                                reply.result == 5 ? "tenkeitech.questskip.locked" : "tenkeitech.questskip.unsupported"),
                            option -> {},
                            I18n.format("gui.done")));
            } else if (reply.result == 4) {
                refreshQuest = pendingQuest;
                deadline = System.currentTimeMillis() + 15000L;
                pendingQuest = null;
            }
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (int n = 0; n < 128; n++) {
            Runnable action = QUEUE.poll();
            if (action == null) break;
            try {
                action.run();
            } catch (RuntimeException failure) {
                com.tenkeitech.TenkeiTech.LOG.error("Quest skip UI update failed", failure);
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (refreshQuest != null) {
            IQuest quest = QuestDatabase.INSTANCE.get(refreshQuest);
            var info = quest == null || mc.thePlayer == null ? null
                : quest.getCompletionInfo(QuestingAPI.getQuestingUUID(mc.thePlayer));
            if (mc.currentScreen != pendingScreen || System.currentTimeMillis() > deadline) refreshQuest = null;
            else if (info != null && info.getBoolean(QuestSkipService.MARKER)) {
                refreshQuest = null;
                mc.currentScreen.initGui();
            }
        }
    }

    @SubscribeEvent
    public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        reset();
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        reset();
    }

    private static void reset() {
        enabled = false;
        refreshQuest = null;
        pendingQuest = null;
        pendingScreen = null;
        QUEUE.clear();
    }
}
