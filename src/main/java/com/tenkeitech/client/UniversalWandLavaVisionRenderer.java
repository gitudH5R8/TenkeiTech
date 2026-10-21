/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.block.material.Material;
import net.minecraftforge.client.event.EntityViewRenderEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.universalwand.UniversalWandProtectionHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class UniversalWandLavaVisionRenderer {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFog(EntityViewRenderEvent.FogDensity event) {
        if (event.block.getMaterial() != Material.lava || !UniversalWandProtectionHandler.isProtected(event.entity))
            return;
        // Canceling bypasses vanilla fog-mode setup, so select the mode explicitly.
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
        event.density = 0;
        event.setCanceled(true);
    }
}
