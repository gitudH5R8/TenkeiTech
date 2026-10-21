/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import com.glodblock.github.common.parts.PartFluidInterface;
import com.glodblock.github.common.tile.TileFluidInterface;

import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;

/** Only audited hosts whose crafting, persistence and drops delegate to DualityInterface. */
public final class ResonatingInterfaceHosts {

    private ResonatingInterfaceHosts() {}

    public static boolean supported(IInterfaceHost host) {
        Class<?> type = host.getClass();
        return type == TileInterface.class || type == PartInterface.class
            || type == TileFluidInterface.class
            || type == PartFluidInterface.class;
    }
}
