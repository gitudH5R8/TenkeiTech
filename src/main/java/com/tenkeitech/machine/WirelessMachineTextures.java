/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/** Fixed GTNH voltage-tier machine casings shared by TenkeiTech machines. */
final class WirelessMachineTextures {

    private static final ITexture EV_TOP = TextureFactory.of(Textures.BlockIcons.MACHINE_EV_TOP);
    private static final ITexture EV_SIDE = TextureFactory.of(Textures.BlockIcons.MACHINE_EV_SIDE);
    private static final ITexture EV_BOTTOM = TextureFactory.of(Textures.BlockIcons.MACHINE_EV_BOTTOM);
    private static final ITexture IV_TOP = TextureFactory.of(Textures.BlockIcons.MACHINE_IV_TOP);
    private static final ITexture IV_SIDE = TextureFactory.of(Textures.BlockIcons.MACHINE_IV_SIDE);
    private static final ITexture IV_BOTTOM = TextureFactory.of(Textures.BlockIcons.MACHINE_IV_BOTTOM);

    private WirelessMachineTextures() {}

    static ITexture evCasing(ForgeDirection side) {
        if (side == ForgeDirection.UP) return EV_TOP;
        if (side == ForgeDirection.DOWN) return EV_BOTTOM;
        return EV_SIDE;
    }

    static ITexture ivCasing(ForgeDirection side) {
        if (side == ForgeDirection.UP) return IV_TOP;
        if (side == ForgeDirection.DOWN) return IV_BOTTOM;
        return IV_SIDE;
    }
}
