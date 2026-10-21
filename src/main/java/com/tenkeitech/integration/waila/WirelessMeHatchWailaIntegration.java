/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.waila;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;

/** Registers the wireless hatch overlay through Waila's public IMC API. */
public final class WirelessMeHatchWailaIntegration {

    private static final String WAILA_MOD_ID = "Waila";
    private static final String CALLBACK = "com.tenkeitech.integration.waila.WirelessMeHatchWailaProvider.callbackRegister";

    private WirelessMeHatchWailaIntegration() {}

    public static void init() {
        if (Loader.isModLoaded(WAILA_MOD_ID)) {
            FMLInterModComms.sendMessage(WAILA_MOD_ID, "register", CALLBACK);
        }
    }
}
