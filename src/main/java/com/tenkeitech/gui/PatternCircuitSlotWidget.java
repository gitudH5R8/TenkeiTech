/*
 * SPDX-FileCopyrightText: GT5-Unofficial contributors
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 * Adapted from GT5-Unofficial's GhostCircuitSlotWidget 5.09.54.133.
 */
package com.tenkeitech.gui;

import static gregtech.api.modularui2.GTGuis.createPopUpPanel;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.tenkeitech.machine.MTESuperCraftingInputME;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.common.modularui2.factory.SelectItemGuiBuilder;

/** GT-style circuit control which writes the selected circuit into one encoded pattern. */
public final class PatternCircuitSlotWidget extends PhantomItemSlot {

    private static final int CIRCUIT_COUNT = 24;
    private static final String SELECTOR_SYNC = "tenkeitech_pattern_circuit_selector_sync_";
    private static final String SELECTOR_PANEL = "tenkeitech_pattern_circuit_selector_";

    private final IntSyncValue selectedIndex;
    private final IPanelHandler selectorPanelHandler;

    public PatternCircuitSlotWidget(MTESuperCraftingInputME machine, int patternSlot, PanelSyncManager syncManager) {
        selectedIndex = new IntSyncValue(
            () -> toSelectedIndex(machine.getPatternVirtualCircuitConfig(patternSlot)),
            selected -> machine.setPatternVirtualCircuitConfig(patternSlot, toCircuitConfig(selected))).allowC2S();
        syncManager.syncValue("pattern_circuit_selection", patternSlot, selectedIndex);
        selectorPanelHandler = syncManager.syncedPanel(
            SELECTOR_SYNC + patternSlot,
            true,
            (manager, handler) -> buildSelectorPanel(machine, patternSlot));
        addTooltipLine(
            IKey.lang("GT5U.machines.select_circuit.tooltip")
                .get());
        addTooltipLine(
            IKey.lang("GT5U.machines.select_circuit.tooltip.1")
                .get());
        addTooltipLine(
            IKey.lang("GT5U.machines.select_circuit.tooltip.2")
                .get());
        addTooltipLine(
            IKey.lang("GT5U.machines.select_circuit.tooltip.3")
                .get());
    }

    @Override
    public IDrawable getCurrentBackground(WidgetThemeEntry<?> widgetTheme) {
        return new DrawableStack(super.getCurrentBackground(widgetTheme), GTGuiTextures.OVERLAY_SLOT_INT_CIRCUIT);
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (selectorPanelHandler.isPanelOpen()) return Interactable.Result.SUCCESS;
        if (mouseButton == 0 && Interactable.hasShiftDown()) {
            selectorPanelHandler.openPanel();
            return Interactable.Result.SUCCESS;
        }
        if (mouseButton == 0) {
            updateSelectedIndex(nextSelectedIndex(1));
        } else if (mouseButton == 1 && Interactable.hasShiftDown()) {
            updateSelectedIndex(-1);
        } else if (mouseButton == 1) {
            updateSelectedIndex(nextSelectedIndex(-1));
        }
        return Interactable.Result.SUCCESS;
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        if (!selectorPanelHandler.isPanelOpen()) updateSelectedIndex(nextSelectedIndex(direction.modifier));
        return true;
    }

    private ModularPanel buildSelectorPanel(MTESuperCraftingInputME machine, int patternSlot) {
        ModularPanel panel = createPopUpPanel(SELECTOR_PANEL + patternSlot);
        return new SelectItemGuiBuilder(panel, GTUtility.getAllIntegratedCircuits())
            .setHeaderItem(machine.getStackForm(1L))
            .setTitle(IKey.lang("GT5U.machines.select_circuit"))
            .setSelectedSyncHandler(selectedIndex)
            .setOnSelectedClientAction((selected, mouse) -> {
                updateSelectedIndex(selected);
                if (mouse.shift) panel.closeIfOpen();
            })
            .setCurrentItemSlotOverlay(GTGuiTextures.OVERLAY_SLOT_INT_CIRCUIT)
            .setAllowDeselected(true)
            .build();
    }

    private int nextSelectedIndex(int delta) {
        int current = normalizeSelectedIndex(selectedIndex.getIntValue());
        if (delta == 0) return current;
        if (current == -1) return delta > 0 ? 0 : CIRCUIT_COUNT - 1;
        int next = current + (delta > 0 ? 1 : -1);
        return next < 0 || next >= CIRCUIT_COUNT ? -1 : next;
    }

    private void updateSelectedIndex(int selected) {
        selectedIndex.setIntValue(normalizeSelectedIndex(selected), true, true);
    }

    private static int normalizeSelectedIndex(int selected) {
        return selected >= 0 && selected < CIRCUIT_COUNT ? selected : -1;
    }

    private static int toSelectedIndex(int circuitConfig) {
        return circuitConfig >= 1 && circuitConfig <= CIRCUIT_COUNT ? circuitConfig - 1 : -1;
    }

    private static int toCircuitConfig(int selected) {
        return selected >= 0 && selected < CIRCUIT_COUNT ? selected + 1 : -1;
    }
}
