/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.autotemplate.AutoTemplateCatalogSync;
import com.tenkeitech.autotemplate.AutoTemplateCatalogSync.Page;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.integration.nei.AutoTemplateNEIIntegration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Bounded, connection-scoped assembly; only the client main thread publishes a complete catalog. */
public final class AutoTemplateCatalogClient {

    private static final ArrayBlockingQueue<Received> PAGES = new ArrayBlockingQueue<>(
        AutoTemplateCatalogSync.MAX_PAGES);
    private static volatile boolean reset;
    private INetHandler connection;
    private long revision;
    private int total;
    private int next;
    private ByteArrayOutputStream pending;

    public static void receive(Page page, INetHandler handler) {
        if (!PAGES.offer(new Received(page, handler))) {
            PAGES.clear();
            reset = true;
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        PAGES.clear();
        reset = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        INetHandler current = minecraft.getNetHandler();
        if (reset || connection != current) {
            reset = false;
            pending = null;
            connection = current;
            next = 0;
        }
        Received received;
        for (int count = 0; count < 8 && (received = PAGES.poll()) != null; count++) {
            if (received.connection != current || current == null) continue;
            Page page = received.page;
            if (page.index == 0) {
                pending = new ByteArrayOutputStream();
                revision = page.revision;
                total = page.total;
                next = 0;
            }
            if (pending == null) continue;
            if (page.revision != revision || page.total != total
                || page.index != next
                || pending.size() + page.data.length > AutoTemplateCatalogSync.MAX_BYTES) {
                pending = null;
                TenkeiTech.LOG.warn("Discarding incomplete auto-template catalog sync");
                continue;
            }
            pending.write(page.data, 0, page.data.length);
            next++;
            if (next != total) continue;
            byte[] bytes = pending.toByteArray();
            pending = null;
            try {
                // The integrated server already owns the shared in-process registry.
                if (!minecraft.isIntegratedServerRunning()) {
                    AutoTemplateRecipeRegistry.installServerCatalog(AutoTemplateCatalogSync.decode(bytes));
                }
                if (Loader.isModLoaded("NotEnoughItems")) AutoTemplateNEIIntegration.refreshCatalog();
                TenkeiTech.LOG.info("Received auto-template catalog from server ({} bytes)", bytes.length);
            } catch (IOException | RuntimeException failure) {
                TenkeiTech.LOG.error("Failed to apply server auto-template catalog", failure);
            }
        }
    }

    private static final class Received {

        final Page page;
        final INetHandler connection;

        Received(Page page, INetHandler connection) {
            this.page = page;
            this.connection = connection;
        }
    }
}
