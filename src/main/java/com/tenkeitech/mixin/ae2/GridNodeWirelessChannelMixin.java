/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tenkeitech.wireless.WirelessAeChannelExemptNode;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridBlock;
import appeng.me.GridNode;

/** Makes only nodes explicitly owned by a live TenkeiTech wireless connection channel-free. */
@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeWirelessChannelMixin implements WirelessAeChannelExemptNode {

    @Unique
    private int tenkeitech$wirelessChannelExemptionCount;

    @Redirect(
        method = "hasFlag",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/IGridBlock;hasFlag(Lappeng/api/networking/GridFlags;)Z",
            remap = false),
        require = 1)
    private boolean tenkeitech$applyWirelessChannelExemption(IGridBlock gridBlock, GridFlags flag) {
        return !(tenkeitech$wirelessChannelExemptionCount > 0 && flag == GridFlags.REQUIRE_CHANNEL)
            && gridBlock.hasFlag(flag);
    }

    @Override
    public boolean tenkeitech$isWirelessChannelExempt() {
        return tenkeitech$wirelessChannelExemptionCount > 0;
    }

    @Override
    public boolean tenkeitech$acquireWirelessChannelExemption() {
        if (tenkeitech$wirelessChannelExemptionCount == Integer.MAX_VALUE) return false;
        tenkeitech$wirelessChannelExemptionCount++;
        return tenkeitech$wirelessChannelExemptionCount == 1;
    }

    @Override
    public boolean tenkeitech$releaseWirelessChannelExemption() {
        if (tenkeitech$wirelessChannelExemptionCount <= 0) return false;
        tenkeitech$wirelessChannelExemptionCount--;
        return tenkeitech$wirelessChannelExemptionCount == 0;
    }
}
