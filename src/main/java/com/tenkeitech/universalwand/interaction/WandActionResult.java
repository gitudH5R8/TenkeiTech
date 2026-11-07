/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand.interaction;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.Event;

/** Domain results, independent of the boolean used by a particular vanilla callback. */
public enum WandActionResult {

    PASS,
    PENDING,
    NO_EFFECT,
    HANDLED,
    BLOCKED;

    public boolean consumesClick() {
        return this != PASS;
    }

    public static WandActionResult nativeResult(boolean handled) {
        return handled ? HANDLED : PASS;
    }

    public static WandActionResult fromInteraction(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return PASS;
        // Forge sets useBlock to DENY for ordinary air clicks (face == -1).
        boolean blockDenied = event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.useBlock == Event.Result.DENY;
        return event.isCanceled() || event.useItem == Event.Result.DENY || blockDenied ? BLOCKED : PASS;
    }

    public WandActionResult merge(WandActionResult other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
