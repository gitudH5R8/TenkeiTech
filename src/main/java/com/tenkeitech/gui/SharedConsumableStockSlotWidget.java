/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/** Read-only shared item stock slot whose displayed amount is backed by a long counter. */
public final class SharedConsumableStockSlotWidget extends ItemSlot {

    private final LongSyncValue amount;

    public SharedConsumableStockSlotWidget(LongSyncValue amount) {
        this.amount = amount;
    }

    @Override
    protected void drawSlotAmountText(int ignored, String color) {
        GuiDraw.drawStandardSlotAmountText(amount.getLongValue(), color, getArea());
    }
}
