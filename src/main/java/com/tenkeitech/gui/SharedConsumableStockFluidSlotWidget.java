/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

/** Read-only shared fluid stock slot whose displayed amount is backed by a long counter. */
public final class SharedConsumableStockFluidSlotWidget extends FluidSlot {

    private final LongSyncValue amount;

    public SharedConsumableStockFluidSlotWidget(LongSyncValue amount) {
        this.amount = amount;
    }

    @Override
    protected boolean displayAmountText() {
        return false;
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawOverlay(context, widgetTheme);
        if (getFluidStack() != null) GuiDraw.drawStandardSlotAmountText(amount.getLongValue(), "", getArea());
    }
}
