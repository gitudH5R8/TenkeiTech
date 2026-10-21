/*
 * SPDX-FileCopyrightText: GT5-Unofficial contributors
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 * Adapted from GT5-Unofficial's MTEHatchCraftingInputMEGui 5.09.54.105.
 */
package com.tenkeitech.gui;

import static com.tenkeitech.machine.MTESuperCraftingInputME.MAX_PATTERN_COUNT;
import static com.tenkeitech.machine.MTESuperCraftingInputME.MAX_PROGRAMMED_CIRCUIT;
import static com.tenkeitech.machine.MTESuperCraftingInputME.MIN_PROGRAMMED_CIRCUIT;
import static com.tenkeitech.machine.MTESuperCraftingInputME.PAGE_COUNT;
import static com.tenkeitech.machine.MTESuperCraftingInputME.PAGE_ROWS;
import static com.tenkeitech.machine.MTESuperCraftingInputME.PATTERNS_PER_PAGE;
import static com.tenkeitech.machine.MTESuperCraftingInputME.PATTERN_COLUMNS;
import static com.tenkeitech.machine.MTESuperCraftingInputME.SLOT_MANUAL_START;
import static gregtech.api.modularui2.GTGuis.createPopUpPanel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.MCHelper;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.PatternCatalystFluidTank;
import com.tenkeitech.machine.PatternCatalystItemHandler;
import com.tenkeitech.machine.SharedCatalystFluidTank;
import com.tenkeitech.machine.SharedConsumableConfigFluidTank;
import com.tenkeitech.machine.SharedConsumableConfigItemHandler;
import com.tenkeitech.machine.SharedConsumableFluidStock;
import com.tenkeitech.machine.SharedConsumableItemStock;
import com.tenkeitech.machine.SharedConsumableStockFluidTank;
import com.tenkeitech.machine.SharedConsumableStockItemHandler;
import com.tenkeitech.machine.VirtualCircuitItemHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.gui.modularui.util.PatternSlot;
import gregtech.common.modularui2.widget.builder.ItemSlotGridBuilder;

public class MTESuperCraftingInputMEGui extends MTEHatchBaseGui<MTESuperCraftingInputME> {

    private static final String PATTERN_INV_NAME = "tenkeitech_super_pattern_inv";
    private static final String MANUAL_ITEM_INV_NAME = "tenkeitech_super_manual_item_inv";
    private static final String PATTERN_CATALYST_PANEL = "tenkeitech_pattern_catalyst_panel_";
    private static final String PATTERN_CATALYST_ITEMS = "tenkeitech_pattern_catalyst_items_";
    private static final String PATTERN_CIRCUIT_ITEM = "tenkeitech_pattern_circuit_item_";
    private static final String SIDE_DOCK_PANEL = "tenkeitech_super_side_dock_panel";
    private static final String MANUAL_ITEMS_PANEL = "tenkeitech_super_manual_slots_panel";
    private static final String SHARED_FLUID_PANEL = "tenkeitech_shared_fluid_catalyst_panel";
    private static final String SHARED_CONSUMABLE_PANEL = "tenkeitech_shared_consumable_panel";
    private static final String SHARED_CONSUMABLE_CONFIG_INV = "tenkeitech_shared_consumable_config_inv";
    private static final String SHARED_CONSUMABLE_STOCK_INV = "tenkeitech_shared_consumable_stock_inv";
    private static final String SHARED_CONSUMABLE_ITEM_AMOUNT = "tenkeitech_shared_consumable_item_amount";
    private static final String SHARED_CONSUMABLE_FLUID_AMOUNT = "tenkeitech_shared_consumable_fluid_amount";
    private static final String SHARED_CONSUMABLE_ITEM_STORED = "tenkeitech_shared_consumable_item_stored";
    private static final String SHARED_CONSUMABLE_FLUID_STORED = "tenkeitech_shared_consumable_fluid_stored";
    private static final String SHARED_CONSUMABLE_REFILL_REMAINING = "tenkeitech_shared_consumable_refill_remaining";
    private static final String CIRCUIT_WRITER_PANEL = "tenkeitech_pattern_circuit_writer_panel";
    private static final int MANUAL_SLOT_ROW = 3;
    private static final int MANUAL_SLOT_PER_ROW = 3;
    private static final int SIDE_DOCK_WIDTH = 22;
    private static final int SIDE_DOCK_HEIGHT = 162;
    private static final int SIDE_DOCK_TOP = 16;
    private static final int SIDE_DOCK_GAP = 3;
    private static final int SIDE_DOCK_LEFT_OFFSET = -(SIDE_DOCK_WIDTH + SIDE_DOCK_GAP);
    private static final int MANUAL_PANEL_WIDTH = 68;
    private static final int SHARED_FLUID_PANEL_WIDTH = 176;
    private static final int SHARED_CONSUMABLE_PANEL_WIDTH = 158;
    private static final int CIRCUIT_WRITER_PANEL_WIDTH = 190;
    private final IPanelHandler[] patternCatalystPanels = new IPanelHandler[MAX_PATTERN_COUNT];

    public MTESuperCraftingInputMEGui(MTESuperCraftingInputME hatch) {
        super(hatch);
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + SLOT_SIZE * 4;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager).child(createPatternPages(syncManager));
    }

    private ParentWidget<?> createPatternPages(PanelSyncManager syncManager) {
        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>().controller(controller)
            .width(PATTERN_COLUMNS * SLOT_SIZE)
            .height(PAGE_ROWS * SLOT_SIZE);
        for (int page = 0; page < PAGE_COUNT; page++) {
            pages.addPage(createSlots(syncManager, page));
        }

        Flow pageButtons = Flow.row()
            .coverChildren()
            .childPadding(3);
        for (int page = 0; page < PAGE_COUNT; page++) {
            pageButtons.child(
                new PageButton(page, controller).size(50, 14)
                    .overlay(IKey.str(StatCollector.translateToLocalFormatted("tenkeitech.gui.page", page + 1))));
        }

        return Flow.column()
            .coverChildren()
            .child(pageButtons)
            .child(pages);
    }

    private Grid createSlots(PanelSyncManager syncManager, int page) {
        String groupName = PATTERN_INV_NAME + "_" + page;
        syncManager.registerSlotGroup(groupName, PAGE_ROWS);
        int firstSlot = page * PATTERNS_PER_PAGE;

        return new Grid().coverChildren()
            .gridOfWidthHeight(PATTERN_COLUMNS, PAGE_ROWS, ($x, $y, index) -> {
                int slotIndex = firstSlot + index;
                String panelId = PATTERN_CATALYST_PANEL + slotIndex;
                IPanelHandler catalystPanel = syncManager.syncedPanel(
                    panelId,
                    true,
                    (manager, handler) -> createPatternCatalystUI(manager, slotIndex, panelId));
                patternCatalystPanels[slotIndex] = catalystPanel;
                PatternSlot patternSlot = new PatternSlot() {

                    @Override
                    public Interactable.Result onMousePressed(int mouseButton) {
                        if (mouseButton == 2) {
                            closeOtherCatalystPanels(slotIndex);
                            catalystPanel.openPanel();
                            return Interactable.Result.SUCCESS;
                        }
                        return super.onMousePressed(mouseButton);
                    }
                };
                patternSlot.slot(new ModularSlot(machine.inventoryHandler, slotIndex) {

                    @Override
                    public boolean canTakeStack(EntityPlayer player) {
                        return super.canTakeStack(player) && machine.preparePatternSlotForEdit(slotIndex);
                    }
                }.changeListener(
                    (itemStack, onlyChangedAmount, client, init) -> {
                        if (!client) machine.onPatternChange(slotIndex, itemStack);
                    })
                    .slotGroup(groupName));
                patternSlot.addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.catalyst.open"));
                return patternSlot;
            });
    }

    private ModularPanel createPatternCatalystUI(PanelSyncManager syncManager, int patternSlot, String panelId) {
        PatternCatalystItemHandler itemHandler = new PatternCatalystItemHandler(machine, patternSlot);
        VirtualCircuitItemHandler circuitHandler = new VirtualCircuitItemHandler(machine, patternSlot);
        StringSyncValue outputName = new StringSyncValue(() -> machine.getPatternOutputName(patternSlot));
        syncManager.syncValue("pattern_output", 0, outputName);
        Grid fluidSlots = new Grid().coverChildren()
            .gridOfWidthHeight(
                9,
                1,
                ($x, $y, index) -> new FluidSlot()
                    .syncHandler(new PatternCatalystFluidTank(machine, patternSlot, index))
                    .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.catalyst.fluids.note")));

        return createPopUpPanel(panelId).size(176, 112)
            .child(
                new TextWidget<>(IKey.lang("tenkeitech.gui.catalyst.title", patternSlot + 1)).pos(7, 5)
                    .maxWidth(162))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.catalyst.items")).pos(7, 18))
            .child(
                new ItemSlotGridBuilder(itemHandler, syncManager).size(9, 1)
                    .slotGroupKey(PATTERN_CATALYST_ITEMS + patternSlot)
                    .modularSlotSupplier((handler, index) -> new ModularSlot(handler, index) {

                        @Override
                        public boolean isItemValid(net.minecraft.item.ItemStack stack) {
                            return super.isItemValid(stack) && itemHandler.prepareInsertion(index, stack);
                        }

                        @Override
                        public boolean canTakeStack(EntityPlayer player) {
                            return super.canTakeStack(player) && itemHandler.prepareExtraction(index);
                        }
                    }.changeListener(
                        (stack, onlyChangedAmount, client, init) -> {
                            if (!client && !init) itemHandler.commitSlot(index);
                        }))
                    .build()
                    .pos(7, 28))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.catalyst.fluids")).pos(7, 48))
            .child(fluidSlots.pos(7, 58))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.catalyst.output", () -> new Object[] { outputName.getStringValue() }))
                        .pos(7, 80)
                        .maxWidth(162))
            .child(
                new TextWidget<>(IKey.lang("tenkeitech.gui.catalyst.circuit")).pos(7, 92)
                    .maxWidth(90))
            .child(
                new ItemSlotGridBuilder(circuitHandler, syncManager).size(1, 1)
                    .slotGroupKey(PATTERN_CIRCUIT_ITEM + patternSlot)
                    .accessibility(false, false)
                    .itemSlotSupplier(() -> new PatternCircuitSlotWidget(machine, patternSlot, syncManager))
                    .build()
                    .pos(100, 88));
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        IPanelHandler sideDockPanel = syncManager
            .syncedPanel(SIDE_DOCK_PANEL, true, (manager, handler) -> createSideDockUI(manager, panel));
        syncManager.addOpenListener(player -> { if (syncManager.isClient()) sideDockPanel.openPanel(); });

        return super.createBottomLeftCornerFlow(panel, syncManager);
    }

    private ModularPanel createSideDockUI(PanelSyncManager syncManager, ModularPanel mainPanel) {
        IPanelHandler wirelessNetworkPanel = WirelessMeHatchGuiSupport
            .registerBindingPanel(machine, syncManager, mainPanel);
        IPanelHandler manualItemsPanel = syncManager
            .syncedPanel(MANUAL_ITEMS_PANEL, true, (manager, handler) -> createManualSlotUI(manager, mainPanel));
        IPanelHandler sharedFluidPanel = syncManager
            .syncedPanel(SHARED_FLUID_PANEL, true, (manager, handler) -> createSharedFluidUI(manager, mainPanel));
        IPanelHandler sharedConsumablePanel = syncManager.syncedPanel(
            SHARED_CONSUMABLE_PANEL,
            true,
            (manager, handler) -> createSharedConsumableUI(manager, mainPanel));
        IPanelHandler circuitWriterPanel = syncManager
            .syncedPanel(CIRCUIT_WRITER_PANEL, true, (manager, handler) -> createCircuitWriterUI(manager, mainPanel));

        Flow buttons = Flow.column()
            .coverChildren()
            .childPadding(2)
            .child(
                WirelessMeHatchGuiSupport
                    .createWirelessButton(
                        wirelessNetworkPanel,
                        manualItemsPanel,
                        sharedFluidPanel,
                        sharedConsumablePanel,
                        circuitWriterPanel)
                    .size(SLOT_SIZE, SLOT_SIZE))
            .child(
                createManualItemsButton(
                    manualItemsPanel,
                    wirelessNetworkPanel,
                    sharedFluidPanel,
                    sharedConsumablePanel,
                    circuitWriterPanel).size(SLOT_SIZE, SLOT_SIZE))
            .child(
                createSharedFluidButton(
                    sharedFluidPanel,
                    wirelessNetworkPanel,
                    manualItemsPanel,
                    sharedConsumablePanel,
                    circuitWriterPanel).size(SLOT_SIZE, SLOT_SIZE))
            .child(
                createSharedConsumableButton(
                    sharedConsumablePanel,
                    wirelessNetworkPanel,
                    manualItemsPanel,
                    sharedFluidPanel,
                    circuitWriterPanel).size(SLOT_SIZE, SLOT_SIZE))
            .child(
                createCircuitWriterButton(
                    circuitWriterPanel,
                    wirelessNetworkPanel,
                    manualItemsPanel,
                    sharedFluidPanel,
                    sharedConsumablePanel).size(SLOT_SIZE, SLOT_SIZE))
            .child(createOptimizerButton().size(SLOT_SIZE, SLOT_SIZE))
            .child(createShowPatternButton().size(SLOT_SIZE, SLOT_SIZE))
            .child(createExportButton().size(SLOT_SIZE, SLOT_SIZE));

        ModularPanel sideDock = new ModularPanel(SIDE_DOCK_PANEL) {

            @Override
            public boolean isDraggable() {
                return false;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public boolean onKeyPressed(char typedChar, int keyCode) {
                if (MCHelper.hasMc() && keyCode == MCHelper.getMc().gameSettings.keyBindInventory.getKeyCode()) {
                    getScreen().getPanelManager()
                        .closePanelsAndScreen();
                    return true;
                }
                return super.onKeyPressed(typedChar, keyCode);
            }
        };

        return sideDock.invisible()
            .size(SIDE_DOCK_WIDTH, SIDE_DOCK_HEIGHT)
            .relative(mainPanel)
            .leftRelOffset(0f, SIDE_DOCK_LEFT_OFFSET)
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(buttons.pos(2, 2));
    }

    private ButtonWidget<?> createOptimizerButton() {
        return new ButtonWidget<>().playClickSound(false)
            .background(GTGuiTextures.BUTTON_STANDARD_DISABLED)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE, GTGuiTextures.OVERLAY_BUTTON_FORBIDDEN)
            .addTooltipLine(StatCollector.translateToLocal("GT5U.infodata.hatch.crafting_input_me.optimize_pattern"))
            .addTooltipLine(
                StatCollector.translateToLocal("GT5U.infodata.hatch.crafting_input_me.optimize_pattern.disabled"))
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.pattern_optimization.disabled_reason"));
    }

    private ToggleButton createShowPatternButton() {
        BooleanSyncValue showPatternSync = new BooleanSyncValue(
            () -> machine.showPattern,
            val -> machine.showPattern = val).allowC2S();

        return new ToggleButton().value(showPatternSync)
            .overlay(true, GTGuiTextures.OVERLAY_BUTTON_WHITELIST)
            .overlay(false, GTGuiTextures.OVERLAY_BUTTON_BLACKLIST)
            .addTooltip(
                true,
                StatCollector.translateToLocal("GT5U.infodata.hatch.crafting_input_me.show_pattern.enable"))
            .addTooltip(
                false,
                StatCollector.translateToLocal("GT5U.infodata.hatch.crafting_input_me.show_pattern.disabled"));
    }

    private ButtonWidget<?> createExportButton() {
        InteractionSyncHandler exportSyncHandler = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) machine.refundAll(false); });

        return new ButtonWidget<>().syncHandler(exportSyncHandler)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_EXPORT)
            .addTooltipLine(StatCollector.translateToLocal("GT5U.gui.tooltip.hatch.crafting_input_me.export"));
    }

    private ButtonWidget<?> createManualItemsButton(IPanelHandler popupPanel, IPanelHandler... otherPanels) {
        return new ButtonWidget<>().overlay(GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE)
            .addTooltipLine(
                StatCollector.translateToLocal("GT5U.gui.tooltip.hatch.crafting_input_me.place_manual_items"))
            .onMousePressed(mouseButton -> toggleSidePanel(popupPanel, otherPanels));
    }

    private ModularPanel createManualSlotUI(PanelSyncManager syncManager, ModularPanel mainPanel) {
        return createPopUpPanel(MANUAL_ITEMS_PANEL).size(MANUAL_PANEL_WIDTH, 76)
            .relative(mainPanel)
            .leftRelOffset(0f, getSidePanelLeftOffset(MANUAL_PANEL_WIDTH))
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(
                new ItemSlotGridBuilder(machine.inventoryHandler, syncManager)
                    .size(MANUAL_SLOT_PER_ROW, MANUAL_SLOT_ROW)
                    .slotGroupKey(MANUAL_ITEM_INV_NAME)
                    .indexOffset(SLOT_MANUAL_START)
                    .build()
                    .marginTop(16)
                    .horizontalCenter());
    }

    private ButtonWidget<?> createSharedFluidButton(IPanelHandler sharedCatalystPanel, IPanelHandler... otherPanels) {
        return new ButtonWidget<>().overlay(GTGuiTextures.OVERLAY_BUTTON_AUTOOUTPUT_FLUID)
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.catalyst.shared_fluids"))
            .onMousePressed(mouseButton -> toggleSidePanel(sharedCatalystPanel, otherPanels));
    }

    private ButtonWidget<?> createSharedConsumableButton(IPanelHandler sharedConsumablePanel,
        IPanelHandler... otherPanels) {
        return new ButtonWidget<>().overlay(GTGuiTextures.OVERLAY_BUTTON_AUTOPULL_ME)
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.open"))
            .onMousePressed(mouseButton -> toggleSidePanel(sharedConsumablePanel, otherPanels));
    }

    private ButtonWidget<?> createCircuitWriterButton(IPanelHandler circuitWriterPanel, IPanelHandler... otherPanels) {
        return new ButtonWidget<>().overlay(new ItemDrawable(GTUtility.getIntegratedCircuit(1)))
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.pattern_circuit_writer.tooltip"))
            .onMousePressed(mouseButton -> toggleSidePanel(circuitWriterPanel, otherPanels));
    }

    private static boolean toggleSidePanel(IPanelHandler targetPanel, IPanelHandler... otherPanels) {
        if (targetPanel.isPanelOpen()) {
            targetPanel.closePanel();
        } else {
            for (IPanelHandler otherPanel : otherPanels) {
                if (otherPanel.isPanelOpen()) otherPanel.closePanel();
            }
            targetPanel.openPanel();
        }
        return true;
    }

    private static int getSidePanelLeftOffset(int panelWidth) {
        return SIDE_DOCK_LEFT_OFFSET - SIDE_DOCK_GAP - panelWidth;
    }

    private void closeOtherCatalystPanels(int exceptPatternSlot) {
        for (int slot = 0; slot < patternCatalystPanels.length; slot++) {
            IPanelHandler panel = patternCatalystPanels[slot];
            if (slot != exceptPatternSlot && panel != null && panel.isPanelOpen()) panel.closePanel();
        }
    }

    private ModularPanel createSharedFluidUI(PanelSyncManager syncManager, ModularPanel mainPanel) {
        Grid fluidSlots = new Grid().coverChildren()
            .gridOfWidthHeight(
                9,
                1,
                ($x, $y, index) -> new FluidSlot().syncHandler(new SharedCatalystFluidTank(machine, index)));
        return createPopUpPanel(SHARED_FLUID_PANEL).size(SHARED_FLUID_PANEL_WIDTH, 52)
            .relative(mainPanel)
            .leftRelOffset(0f, getSidePanelLeftOffset(SHARED_FLUID_PANEL_WIDTH))
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.catalyst.shared_fluids")).pos(7, 7))
            .child(fluidSlots.pos(7, 24));
    }

    private ModularPanel createSharedConsumableUI(PanelSyncManager syncManager, ModularPanel mainPanel) {
        SharedConsumableConfigItemHandler configHandler = new SharedConsumableConfigItemHandler(machine);
        SharedConsumableStockItemHandler stockHandler = new SharedConsumableStockItemHandler(machine);
        syncManager.registerSlotGroup(SHARED_CONSUMABLE_CONFIG_INV, 2);
        syncManager.registerSlotGroup(SHARED_CONSUMABLE_STOCK_INV, 2);
        LongSyncValue[] itemTargets = new LongSyncValue[SharedConsumableItemStock.SLOT_COUNT];
        LongSyncValue[] fluidTargets = new LongSyncValue[SharedConsumableFluidStock.SLOT_COUNT];
        LongSyncValue[] itemStored = new LongSyncValue[SharedConsumableItemStock.SLOT_COUNT];
        LongSyncValue[] fluidStored = new LongSyncValue[SharedConsumableFluidStock.SLOT_COUNT];
        for (int slot = 0; slot < SharedConsumableItemStock.SLOT_COUNT; slot++) {
            int stockSlot = slot;
            itemTargets[slot] = new LongSyncValue(
                () -> Math.max(1, machine.getSharedConsumableTarget(stockSlot)),
                amount -> machine.setSharedConsumableTarget(stockSlot, amount)).allowC2S();
            fluidTargets[slot] = new LongSyncValue(
                () -> Math.max(1, machine.getSharedConsumableFluidTarget(stockSlot)),
                amount -> machine.setSharedConsumableFluidTarget(stockSlot, amount)).allowC2S();
            itemStored[slot] = new LongSyncValue(() -> machine.getSharedConsumableStoredAmount(stockSlot));
            fluidStored[slot] = new LongSyncValue(() -> machine.getSharedConsumableStoredFluidAmount(stockSlot));
            syncManager.syncValue(SHARED_CONSUMABLE_ITEM_AMOUNT, slot, itemTargets[slot]);
            syncManager.syncValue(SHARED_CONSUMABLE_FLUID_AMOUNT, slot, fluidTargets[slot]);
            syncManager.syncValue(SHARED_CONSUMABLE_ITEM_STORED, slot, itemStored[slot]);
            syncManager.syncValue(SHARED_CONSUMABLE_FLUID_STORED, slot, fluidStored[slot]);
        }

        Grid slots = new Grid().coverChildren()
            .gridOfWidthHeight(8, 4, ($x, $y, index) -> {
                if ($y == 0) {
                    SharedConsumableConfigSlotWidget widget = new SharedConsumableConfigSlotWidget(
                        $x,
                        syncManager,
                        itemTargets[$x]);
                    widget.slot(new ModularSlot(configHandler, $x).slotGroup(SHARED_CONSUMABLE_CONFIG_INV));
                    return widget.backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK);
                }
                if ($y == 1) {
                    return new SharedConsumableStockSlotWidget(itemStored[$x])
                        .slot(
                            new ModularSlot(stockHandler, $x).accessibility(false, false)
                                .slotGroup(SHARED_CONSUMABLE_STOCK_INV))
                        .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.stored_item"));
                }
                if ($y == 2) {
                    return new SharedConsumableConfigFluidSlotWidget(
                        new SharedConsumableConfigFluidTank(machine, $x, fluidTargets[$x]::getLongValue),
                        $x,
                        syncManager,
                        fluidTargets[$x]).backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK);
                }
                return new SharedConsumableStockFluidSlotWidget(fluidStored[$x])
                    .syncHandler(
                        new FluidSlotSyncHandler(
                            new SharedConsumableStockFluidTank(machine, $x, fluidTargets[$x]::getLongValue))
                                .canFillSlot(false)
                                .canDrainSlot(false))
                    .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.stored_fluid"));
            });
        IntSyncValue interval = new IntSyncValue(
            machine::getSharedConsumableRefillIntervalSeconds,
            machine::setSharedConsumableRefillIntervalSeconds).allowC2S();
        BooleanSyncValue afterRecipe = new BooleanSyncValue(
            machine::isSharedConsumableRefillOnRecipeStart,
            machine::setSharedConsumableRefillOnRecipeStart).allowC2S();
        IntSyncValue refillRemaining = new IntSyncValue(machine::getSharedConsumableRefillRemainingSeconds);
        syncManager.syncValue(SHARED_CONSUMABLE_REFILL_REMAINING, 0, refillRemaining);

        return createPopUpPanel(SHARED_CONSUMABLE_PANEL).size(SHARED_CONSUMABLE_PANEL_WIDTH, 151)
            .relative(mainPanel)
            .leftRelOffset(0f, getSidePanelLeftOffset(SHARED_CONSUMABLE_PANEL_WIDTH))
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.shared_consumable.title")).pos(7, 5))
            .child(slots.pos(7, 18))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.shared_consumable.interval")).pos(7, 96))
            .child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersInt(0, Integer.MAX_VALUE)
                    .setMaxLength(10)
                    .value(interval)
                    .pos(91, 92)
                    .size(60, 18)
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.interval_tooltip")))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.shared_consumable.after_recipe")).pos(7, 118))
            .child(
                new ToggleButton().value(afterRecipe)
                    .overlay(true, IKey.str("ON"))
                    .overlay(false, IKey.str("OFF"))
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.after_recipe_tooltip"))
                    .pos(113, 113)
                    .size(38, 16))
            .child(
                new TextWidget<>(IKey.dynamic(() -> formatRefillRemaining(refillRemaining.getIntValue()))).pos(7, 136)
                    .maxWidth(SHARED_CONSUMABLE_PANEL_WIDTH - 14));
    }

    private static String formatRefillRemaining(int seconds) {
        return seconds < 0 ? StatCollector.translateToLocal("tenkeitech.gui.shared_consumable.remaining_disabled")
            : StatCollector.translateToLocalFormatted("tenkeitech.gui.shared_consumable.remaining", seconds);
    }

    private ModularPanel createCircuitWriterUI(PanelSyncManager syncManager, ModularPanel mainPanel) {
        IntSyncValue circuitConfig = new IntSyncValue(
            machine::getEmbeddedCircuitConfig,
            machine::setEmbeddedCircuitConfig).allowC2S();
        BooleanSyncValue skipExisting = new BooleanSyncValue(
            machine::shouldSkipExistingCircuitPatterns,
            machine::setSkipExistingCircuitPatterns).allowC2S();
        InteractionSyncHandler applyHandler = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> {
                if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) machine.applyConfiguredCircuitToPatterns();
            });
        InteractionSyncHandler clearHandler = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> {
                if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) machine.clearCircuitsFromPatterns();
            });

        return createPopUpPanel(CIRCUIT_WRITER_PANEL).size(CIRCUIT_WRITER_PANEL_WIDTH, 104)
            .relative(mainPanel)
            .leftRelOffset(0f, getSidePanelLeftOffset(CIRCUIT_WRITER_PANEL_WIDTH))
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.pattern_circuit_writer.title")).pos(7, 5))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.pattern_circuit_writer.number")).pos(14, 29))
            .child(
                new ButtonWidget<>().overlay(IKey.str("-1"))
                    .onMousePressed(mouseButton -> adjustCircuitConfig(circuitConfig, -1, mouseButton))
                    .pos(100, 24)
                    .size(16, 18))
            .child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersInt(MIN_PROGRAMMED_CIRCUIT, MAX_PROGRAMMED_CIRCUIT)
                    .setMaxLength(2)
                    .value(circuitConfig)
                    .pos(118, 27)
                    .size(42, 14)
                    .addTooltipLine(
                        StatCollector.translateToLocalFormatted(
                            "tenkeitech.gui.pattern_circuit_writer.number.tooltip",
                            MAX_PROGRAMMED_CIRCUIT)))
            .child(
                new ButtonWidget<>().overlay(IKey.str("+1"))
                    .onMousePressed(mouseButton -> adjustCircuitConfig(circuitConfig, 1, mouseButton))
                    .pos(162, 24)
                    .size(16, 18))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.pattern_circuit_writer.skip_existing")).pos(14, 51))
            .child(
                new ToggleButton().value(skipExisting)
                    .overlay(true, IKey.str("ON"))
                    .overlay(false, IKey.str("OFF"))
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.pattern_circuit_writer.skip_existing.tooltip"))
                    .pos(140, 46)
                    .size(38, 16))
            .child(
                new ButtonWidget<>().syncHandler(applyHandler)
                    .overlay(IKey.lang("tenkeitech.gui.pattern_circuit_writer.apply"))
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.pattern_circuit_writer.apply.tooltip"))
                    .pos(14, 75)
                    .size(78, 18))
            .child(
                new ButtonWidget<>().syncHandler(clearHandler)
                    .overlay(IKey.lang("tenkeitech.gui.pattern_circuit_writer.clear"))
                    .addTooltipLine(
                        StatCollector.translateToLocal("tenkeitech.gui.pattern_circuit_writer.clear.tooltip"))
                    .pos(100, 75)
                    .size(78, 18));
    }

    private static boolean adjustCircuitConfig(IntSyncValue circuitConfig, int delta, int mouseButton) {
        if (mouseButton != 0) return false;
        int newConfig = Math
            .max(MIN_PROGRAMMED_CIRCUIT, Math.min(MAX_PROGRAMMED_CIRCUIT, circuitConfig.getIntValue() + delta));
        circuitConfig.setIntValue(newConfig, true, true);
        return true;
    }
}
