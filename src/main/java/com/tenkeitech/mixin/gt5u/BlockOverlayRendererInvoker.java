/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.client.BlockOverlayRenderer;

/** Reuses the fixed GT renderer for the wand's per-stack tool mode without reflection. */
@Mixin(value = BlockOverlayRenderer.class, remap = false)
public interface BlockOverlayRendererInvoker {

    @Invoker("drawGrid")
    static void tenkeitech$drawGrid(DrawBlockHighlightEvent event, boolean showCoverConnections, boolean wrench,
        boolean sneaking) {
        throw new AssertionError();
    }
}
