/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Moves decoded S2C messages onto the Minecraft client thread. */
public final class PatternUploadClient {

    private static final int MAX_QUEUED_TASKS = 128;
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger TASK_COUNT = new AtomicInteger();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private PatternUploadClient() {}

    public static void init() {
        if (!Config.enablePatternUpload) return;
        if (!INITIALIZED.compareAndSet(false, true)) return;
        PatternUploadClient listener = new PatternUploadClient();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        MinecraftForge.EVENT_BUS.register(listener);
    }

    public static void enqueuePage(PatternUploadNetwork.TargetsPageMessage message) {
        if (!Config.enablePatternUpload) return;
        enqueue(() -> deliverPage(message));
    }

    public static void enqueueResult(PatternUploadNetwork.UploadResultMessage message) {
        if (!Config.enablePatternUpload) return;
        enqueue(() -> deliverResult(message));
    }

    private static void enqueue(Runnable task) {
        int queued = TASK_COUNT.incrementAndGet();
        if (queued > MAX_QUEUED_TASKS) {
            TASK_COUNT.decrementAndGet();
            TenkeiTech.LOG.warn("Dropping pattern upload client update because the work queue is full");
            return;
        }
        TASKS.offer(task);
    }

    private static void deliverPage(PatternUploadNetwork.TargetsPageMessage message) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof PatternUploadScreenBridge bridge) {
            bridge.tenkeitech$acceptPatternUploadPage(message);
        }
    }

    private static void deliverResult(PatternUploadNetwork.UploadResultMessage message) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof PatternUploadScreenBridge bridge) {
            bridge.tenkeitech$acceptPatternUploadResult(message);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (int handled = 0; handled < MAX_QUEUED_TASKS; handled++) {
            Runnable task = TASKS.poll();
            if (task == null) break;
            TASK_COUNT.decrementAndGet();
            try {
                task.run();
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.error("Pattern upload client task failed", exception);
            }
        }
        if (Minecraft.getMinecraft().currentScreen instanceof PatternUploadScreenBridge bridge)
            bridge.tenkeitech$tickPatternUpload();
    }

    @SubscribeEvent
    public void hideHudDuringUpload(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL
            && Minecraft.getMinecraft().currentScreen instanceof PatternUploadScreenBridge bridge
            && bridge.tenkeitech$isPatternUploadOverlayOpen()) {
            // Forge fires Pre(ALL) before setting up the GUI projection. Keep that setup when hiding the HUD,
            // otherwise the exclusive upload controls are drawn with the world's perspective matrices.
            Minecraft.getMinecraft().entityRenderer.setupOverlayRendering();
            event.setCanceled(true);
        }
    }
}
