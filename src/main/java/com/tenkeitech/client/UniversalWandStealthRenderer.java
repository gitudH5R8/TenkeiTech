/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraftforge.client.event.RenderPlayerEvent;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class UniversalWandStealthRenderer {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayer(RenderPlayerEvent.Pre event) {
        if (UniversalWandProtectionHandler.isHidden(event.entityPlayer)) event.setCanceled(true);
    }

}
