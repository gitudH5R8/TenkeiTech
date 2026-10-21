/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import static gregtech.api.modularui2.GTGuis.createPopUpPanel;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/** Phantom stock identity slot with a middle-click target-amount editor. */
public final class SharedConsumableConfigSlotWidget extends PhantomItemSlot {

    private static final String AMOUNT_PANEL = "tenkeitech_shared_consumable_amount_panel_";

    private final IPanelHandler amountPanel;
    private final LongSyncValue target;

    public SharedConsumableConfigSlotWidget(int slot, PanelSyncManager syncManager, LongSyncValue target) {
        this.target = target;
        amountPanel = syncManager
            .syncedPanel(AMOUNT_PANEL + slot, true, (manager, handler) -> createAmountPanel(slot, target));
        addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.configure_item_amount"));
        addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.amount_performance_warning"));
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == 2) {
            amountPanel.openPanel();
            return Interactable.Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    @Override
    protected void drawSlotAmountText(int amount, String color) {
        GuiDraw.drawStandardSlotAmountText(Math.max(1, target.getLongValue()), color, getArea());
    }

    private static ModularPanel createAmountPanel(int slot, LongSyncValue target) {
        return createPopUpPanel(AMOUNT_PANEL + slot).size(210, 54)
            .child(
                new TextWidget<>(IKey.lang("tenkeitech.gui.shared_consumable.item_amount_title", slot + 1)).pos(7, 7)
                    .maxWidth(196))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.shared_consumable.item_amount")).pos(7, 29))
            .child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersLong(1, Long.MAX_VALUE)
                    .setMaxLength(19)
                    .value(target)
                    .pos(92, 25)
                    .size(111, 18)
                    .addTooltipLine(
                        StatCollector.translateToLocalFormatted(
                            "tenkeitech.gui.shared_consumable.amount_tooltip",
                            Long.MAX_VALUE))
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.amount_performance_warning")));
    }
}
