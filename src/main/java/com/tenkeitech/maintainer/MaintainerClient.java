/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;

import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class MaintainerClient {

    private static final Queue<MaintainerNetwork.Data> PAGES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static File preferences;
    static boolean tall;

    public static void init(File configDirectory) {
        preferences = new File(configDirectory, "tenkeitech-maintainer-client.properties");
        if (preferences.isFile()) try (FileInputStream stream = new FileInputStream(preferences)) {
            Properties properties = new Properties();
            properties.load(stream);
            tall = Boolean.parseBoolean(properties.getProperty("tall", "false"));
        } catch (IOException exception) {
            TenkeiTech.LOG.warn("Could not read maintainer layout", exception);
        }
        MaintainerClient handler = new MaintainerClient();
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    static void layout(boolean value) {
        tall = value;
        Properties properties = new Properties();
        properties.setProperty("tall", Boolean.toString(value));
        try (FileOutputStream stream = new FileOutputStream(preferences)) {
            properties.store(stream, "TenkeiTech maintainer client layout");
        } catch (IOException exception) {
            TenkeiTech.LOG.warn("Could not save maintainer layout", exception);
        }
    }

    public static void receive(MaintainerNetwork.Data data) {
        if (COUNT.incrementAndGet() > 256) {
            COUNT.decrementAndGet();
            return;
        }
        PAGES.offer(data);
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        PAGES.clear();
        COUNT.set(0);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 64; i++) {
            MaintainerNetwork.Data data = PAGES.poll();
            if (data == null) break;
            COUNT.decrementAndGet();
            if (Minecraft.getMinecraft().currentScreen instanceof GuiBetterLevelMaintainer gui) gui.receive(data);
        }
    }
}
