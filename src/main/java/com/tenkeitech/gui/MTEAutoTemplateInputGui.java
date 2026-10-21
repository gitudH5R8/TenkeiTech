/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.autotemplate.PatternKey;
import com.tenkeitech.autotemplate.TemplateRecipeCatalog;
import com.tenkeitech.autotemplate.TemplateType;
import com.tenkeitech.autotemplate.VirtualPattern;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;
import com.tenkeitech.patternmode.AutoTemplateModeBuffer.DisplayStatus;

import gregtech.api.enums.GTValues;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/** Server-authoritative fixed-template recipe browser grouped by voltage tier. */
public final class MTEAutoTemplateInputGui extends MTEHatchBaseGui<MTEHatchAutoTemplateInputME> {

    private static final int PANEL_WIDTH = 325;
    private static final int PANEL_HEIGHT = 309;
    private static final int CIRCUIT_SELECTOR_HEIGHT = 24;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - 14;
    private static final int VISIBLE_ROWS = 7;
    private static final int PATTERNS_PER_ROW = 13;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_Y = 38;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int TIER_WIDTH = 40;
    private static final int CELL_START_X = 42;
    private static final int CELL_STEP = 20;
    private static final int CELL_SIZE = 18;
    private static final int SCROLL_X = 302;
    private static final int REFUND_X = 122;
    private static final int ENABLE_ALL_X = 144;
    private static final int DISABLE_ALL_X = 216;
    private static final int GT_LOGO_X = 288;
    private static final int SEARCH_X = 204;

    private final EntityPlayer openingPlayer;

    public MTEAutoTemplateInputGui(MTEHatchAutoTemplateInputME machine, EntityPlayer openingPlayer) {
        super(machine);
        this.openingPlayer = openingPlayer;
    }

    @Override
    protected int getBasePanelWidth() {
        return Math.max(super.getBasePanelWidth(), PANEL_WIDTH);
    }

    @Override
    protected int getBasePanelHeight() {
        return Math.max(
            super.getBasePanelHeight(),
            PANEL_HEIGHT + (machine.hasCircuitSelector() ? CIRCUIT_SELECTOR_HEIGHT : 0));
    }

    @Override
    protected boolean doesAddGregTechLogo() {
        return false;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        BrowserSession session = new BrowserSession(machine, syncManager.isClient(), openingPlayer);
        AutoTemplateSearchSyncHandler search = new AutoTemplateSearchSyncHandler(
            session::searchOutputs,
            session::setSearchFilter,
            session::togglePattern,
            session::toggleTier);
        syncManager.onServerTick(session::tick);

        GenericSyncValue<ItemStack, ?> template = GenericSyncValue.builder(ItemStack.class)
            .getter(session::templateStack)
            .adapter(ByteBufAdapters.ITEM_STACK)
            .nullable()
            .build();
        IntSyncValue enabledCount = new IntSyncValue(session::effectivePatternCount);
        IntSyncValue totalCount = new IntSyncValue(session::totalPatternCount);
        IntSyncValue machineTier = new IntSyncValue(machine::getCurrentMachineTier);
        IntSyncValue modeStatus = new IntSyncValue(
            () -> machine.getPatternMode()
                .displayStatus()
                .ordinal());
        StringSyncValue modeTarget = new StringSyncValue(machine::getPatternModeRecipeMapId);
        BooleanSyncValue modeAllowsPatterns = new BooleanSyncValue(machine::isPatternModeAvailable);
        IntSyncValue highlightedTier = new IntSyncValue(session::highlightedTier);
        IntSyncValue rowCount = new IntSyncValue(session::rowCount);
        IntSyncValue selectedCircuitIndex = new IntSyncValue(session::selectedCircuitIndex);
        syncManager.syncValue("auto_template_template", 0, template);
        syncManager.syncValue("auto_template_enabled_count", 0, enabledCount);
        syncManager.syncValue("auto_template_total_count", 0, totalCount);
        syncManager.syncValue("auto_template_machine_tier", 0, machineTier);
        syncManager.syncValue("auto_template_mode_status", 0, modeStatus);
        syncManager.syncValue("auto_template_mode_target", 0, modeTarget);
        syncManager.syncValue("auto_template_mode_allows_patterns", 0, modeAllowsPatterns);
        syncManager.syncValue("auto_template_highlighted_tier", 0, highlightedTier);
        syncManager.syncValue("auto_template_row_count", 0, rowCount);
        syncManager.syncValue("auto_template_selected_circuit", 0, selectedCircuitIndex);

        DoubleSyncValue scrollPosition = new DoubleSyncValue(session::scrollPosition, session::setScrollPosition)
            .allowC2S();
        InteractionSyncHandler listScroll = createListScrollHandler(session);
        int headerOffset = machine.hasCircuitSelector() ? CIRCUIT_SELECTOR_HEIGHT : 0;
        int listY = LIST_Y + headerOffset;
        int bottomY = listY + LIST_HEIGHT + 5;

        ParentWidget<?> content = getEmptyContent().child(
            createTemplateIcon(template).pos(0, 7)
                .size(18, 18))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> machine.hasCircuitSelector()
                            ? StatCollector.translateToLocalFormatted(
                                "tenkeitech.gui.auto_template.current_circuit",
                                circuitConfiguration(selectedCircuitIndex.getIntValue()))
                            : StatCollector.translateToLocalFormatted(
                                "tenkeitech.gui.auto_template.template",
                                templateName(template.getValue())))).textAlign(Alignment.CenterLeft)
                                    .pos(22, 7)
                                    .size(172, 18)
                                    .maxWidth(172))
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.auto_template.machine_tier",
                        () -> new Object[] { machineTierName(machineTier.getIntValue()) }))
                            .textAlign(Alignment.CenterRight)
                            .pos(198, 7)
                            .size(CONTENT_WIDTH - 198, 18)
                            .maxWidth(CONTENT_WIDTH - 198))
            .child(
                new TextWidget<>(
                    IKey.dynamic(() -> formatModeStatus(modeStatus.getIntValue(), modeTarget.getStringValue())))
                        .textAlign(Alignment.CenterLeft)
                        .pos(0, 27 + headerOffset)
                        .size(SEARCH_X - 4, 9)
                        .maxWidth(SEARCH_X - 4))
            .child(
                new AutoTemplateSearchField().setMaxLength(AutoTemplateSearchSyncHandler.MAX_QUERY_LENGTH)
                    .autoUpdateOnChange(true)
                    .value(new StringValue.Dynamic(search::query, search::setQuery))
                    .hintText(StatCollector.translateToLocal("tenkeitech.gui.auto_template.search"))
                    .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.auto_template.search_tooltip"))
                    .pos(SEARCH_X, 26 + headerOffset)
                    .size(CONTENT_WIDTH - SEARCH_X, 11))
            .child(
                createListScrollArea(listScroll).pos(0, listY)
                    .size(CONTENT_WIDTH, LIST_HEIGHT));

        if (machine.hasCircuitSelector()) {
            content.child(
                createCircuitSelector(session, selectedCircuitIndex).pos(0, 27)
                    .size(CONTENT_WIDTH, CIRCUIT_SELECTOR_HEIGHT - 2));
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            content.child(
                createTierRow(syncManager, session, search, row, machineTier, highlightedTier, modeAllowsPatterns)
                    .pos(0, listY + row * ROW_HEIGHT)
                    .size(SCROLL_X - 2, ROW_HEIGHT - 1));
        }

        content.child(
            new TextWidget<>(IKey.lang("tenkeitech.gui.auto_template.search_empty")).textAlign(Alignment.Center)
                .setEnabledIf(
                    widget -> totalCount.getIntValue() > 0 && rowCount.getIntValue() == 0 && !search.isPending())
                .pos(CELL_START_X, listY + LIST_HEIGHT / 2 - 5)
                .size(SCROLL_X - CELL_START_X, 10));

        // Search acknowledgements follow visible-cell sync so stale search clicks stay disabled.
        syncManager.syncValue("auto_template_search", 0, search);

        content.child(
            createScrollSlider(listScroll).value(scrollPosition)
                .bounds(0, 1)
                .setAxis(GuiAxis.Y)
                .sliderSize(5, 14)
                .background(GuiTextures.DISPLAY_SMALL)
                .setEnabledIf(widget -> rowCount.getIntValue() > VISIBLE_ROWS)
                .pos(SCROLL_X, listY)
                .size(7, LIST_HEIGHT));

        return content
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.auto_template.count",
                        () -> new Object[] { enabledCount.getIntValue(), totalCount.getIntValue() }))
                            .pos(0, bottomY + 5)
                            .maxWidth(116))
            .child(
                createRefundButton(session).pos(REFUND_X, bottomY)
                    .size(18, 18))
            .child(
                createSetAllButton(session, true, totalCount).pos(ENABLE_ALL_X, bottomY)
                    .size(68, 18))
            .child(
                createSetAllButton(session, false, totalCount).pos(DISABLE_ALL_X, bottomY)
                    .size(68, 18))
            .child(makeLogoWidget().pos(GT_LOGO_X, bottomY));
    }

    private int circuitConfiguration(int selectedIndex) {
        TemplateType[] modes = machine.getSelectableCircuitModes();
        return selectedIndex >= 0 && selectedIndex < modes.length ? modes[selectedIndex].getCircuitConfiguration() : 0;
    }

    private ParentWidget<?> createCircuitSelector(BrowserSession session, IntSyncValue selectedCircuitIndex) {
        ParentWidget<?> selector = new ParentWidget<>().background(GuiTextures.DISPLAY_SMALL);
        TemplateType[] modes = machine.getSelectableCircuitModes();
        for (int index = 0; index < modes.length; index++) {
            TemplateType mode = modes[index];
            ItemStack circuit = mode.getTemplateStack();
            if (circuit != null) circuit.stackSize = 1;
            ItemStack displayedCircuit = circuit;
            ItemDrawable item = new ItemDrawable();
            int slotIndex = index;
            IDrawable selected = new DrawableStack(
                GuiTextures.SLOT_ITEM,
                new Rectangle().color(0x663879B5)
                    .solid());
            InteractionSyncHandler selectMode = new InteractionSyncHandler().setOnMousePressed(
                mouseDelta -> {
                    if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.selectCircuitMode(mode);
                });
            selector.child(
                new ButtonWidget<>().syncHandler(selectMode)
                    .disableThemeBackground(true)
                    .disableHoverThemeBackground(true)
                    .background(
                        new DynamicDrawable(
                            () -> selectedCircuitIndex.getIntValue() == slotIndex ? selected : GuiTextures.SLOT_ITEM))
                    .overlay(
                        displayedCircuit == null ? IKey.str("?")
                            : item.setItem(displayedCircuit)
                                .asIcon()
                                .size(16)
                                .center())
                    .addTooltipLine(
                        StatCollector
                            .translateToLocalFormatted(
                                "tenkeitech.gui.auto_template.toggle_circuit",
                                mode.getCircuitConfiguration())
                            .replace("\\n", "\n"))
                    .pos(3 + index * 21, 2)
                    .size(18, 18));
        }
        return selector;
    }

    private ParentWidget<?> createTierRow(PanelSyncManager syncManager, BrowserSession session,
        AutoTemplateSearchSyncHandler search, int row, IntSyncValue machineTier, IntSyncValue highlightedTier,
        BooleanSyncValue modeAllowsPatterns) {
        IntSyncValue tier = new IntSyncValue(() -> session.rowTier(row));
        BooleanSyncValue tierEnabled = new BooleanSyncValue(() -> session.rowTierEnabled(row));
        BooleanSyncValue tierButtonVisible = new BooleanSyncValue(() -> session.rowTierButtonVisible(row));
        syncManager.syncValue("auto_template_row_tier", row, tier);
        syncManager.syncValue("auto_template_row_enabled", row, tierEnabled);
        syncManager.syncValue("auto_template_row_button_visible", row, tierButtonVisible);

        ParentWidget<?> tierRow = new ParentWidget<>().background(
            new DynamicDrawable(() -> rowBackground(tier.getIntValue(), highlightedTier.getIntValue(), row)));
        InteractionSyncHandler toggleTier = new InteractionSyncHandler() {

            @Override
            public boolean onMousePressed(int button) {
                if (button != 0) return false;
                search.requestTierToggle(tier.getIntValue());
                return true;
            }
        }.setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
        tierRow.child(
            new ButtonWidget<>().syncHandler(toggleTier)
                .disableThemeBackground(true)
                .disableHoverThemeBackground(true)
                .background(
                    new DynamicDrawable(
                        () -> tierButtonVisible.getBoolValue() ? GuiTextures.MC_BUTTON : IDrawable.EMPTY))
                .overlay(
                    IKey.dynamic(
                        () -> tierButtonVisible.getBoolValue()
                            ? formatTier(
                                tier.getIntValue(),
                                machineTier.getIntValue(),
                                highlightedTier.getIntValue(),
                                tierEnabled.getBoolValue(),
                                modeAllowsPatterns.getBoolValue())
                            : ""))
                .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.auto_template.toggle_tier"))
                .setEnabledIf(
                    widget -> tierButtonVisible.getBoolValue() && tier.getIntValue() >= 0 && !search.isPending())
                .pos(0, 1)
                .size(TIER_WIDTH, CELL_SIZE));

        for (int column = 0; column < PATTERNS_PER_ROW; column++) {
            tierRow.child(
                createPatternCell(
                    syncManager,
                    session,
                    search,
                    row,
                    column,
                    tier,
                    machineTier,
                    tierEnabled,
                    modeAllowsPatterns).pos(CELL_START_X + column * CELL_STEP, 1)
                        .size(CELL_SIZE, CELL_SIZE));
        }
        return tierRow;
    }

    private ButtonWidget<?> createPatternCell(PanelSyncManager syncManager, BrowserSession session,
        AutoTemplateSearchSyncHandler search, int row, int column, IntSyncValue tier, IntSyncValue machineTier,
        BooleanSyncValue tierEnabled, BooleanSyncValue modeAllowsPatterns) {
        int cellId = row * PATTERNS_PER_ROW + column;
        GenericSyncValue<ItemStack, ?> output = GenericSyncValue.builder(ItemStack.class)
            .getter(() -> session.cellOutput(row, column))
            .adapter(ByteBufAdapters.ITEM_STACK)
            .nullable()
            .build();
        GenericSyncValue<ItemStack, ?> itemInput = GenericSyncValue.builder(ItemStack.class)
            .getter(() -> session.cellItemInput(row, column))
            .adapter(ByteBufAdapters.ITEM_STACK)
            .nullable()
            .build();
        GenericSyncValue<FluidStack, ?> fluidInput = GenericSyncValue.builder(FluidStack.class)
            .getter(() -> session.cellFluidInput(row, column))
            .adapter(ByteBufAdapters.FLUID_STACK)
            .nullable()
            .build();
        LongSyncValue recipeEUt = new LongSyncValue(() -> session.cellRecipeEUt(row, column));
        BooleanSyncValue enabled = new BooleanSyncValue(() -> session.cellEnabled(row, column));
        IntSyncValue catalogIndex = new IntSyncValue(() -> session.cellCatalogIndex(row, column));
        syncManager.syncValue("auto_template_cell_output", cellId, output);
        syncManager.syncValue("auto_template_cell_item_input", cellId, itemInput);
        syncManager.syncValue("auto_template_cell_fluid_input", cellId, fluidInput);
        syncManager.syncValue("auto_template_cell_eut", cellId, recipeEUt);
        syncManager.syncValue("auto_template_cell_enabled", cellId, enabled);
        syncManager.syncValue("auto_template_cell_catalog_index", cellId, catalogIndex);

        InteractionSyncHandler togglePattern = new InteractionSyncHandler() {

            @Override
            public boolean onMousePressed(int button) {
                if (button != 0) return false;
                search.requestPatternToggle(catalogIndex.getIntValue());
                return true;
            }
        }.setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
        IDrawable hover = new DrawableStack(
            GuiTextures.SLOT_ITEM,
            new Rectangle().color(0x33FFFFFF)
                .solid());
        return new ButtonWidget<>().syncHandler(togglePattern)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .background(new DynamicDrawable(() -> output.getValue() == null ? IDrawable.EMPTY : GuiTextures.SLOT_ITEM))
            .hoverBackground(new DynamicDrawable(() -> output.getValue() == null ? IDrawable.EMPTY : hover))
            .overlay(patternIcon(output, enabled, tierEnabled, tier, machineTier, modeAllowsPatterns))
            .addTooltipLine(
                IKey.dynamic(
                    () -> formatTooltip(
                        output.getValue(),
                        itemInput.getValue(),
                        fluidInput.getValue(),
                        tier.getIntValue(),
                        recipeEUt.getLongValue(),
                        enabled.getBoolValue(),
                        tierEnabled.getBoolValue(),
                        modeAllowsPatterns.getBoolValue(),
                        tier.getIntValue() >= 0 && tier.getIntValue() <= machineTier.getIntValue())))
            .setEnabledIf(widget -> output.getValue() != null && !search.isPending());
    }

    private static Widget<?> createTemplateIcon(GenericSyncValue<ItemStack, ?> template) {
        ItemDrawable item = new ItemDrawable();
        IDrawable icon = new DynamicDrawable(
            () -> template.getValue() == null ? IKey.str("?") : item.setItem(template.getValue()));
        return new Widget<>().disableThemeBackground(true)
            .background(GuiTextures.SLOT_ITEM)
            .overlay(
                icon.asIcon()
                    .size(14)
                    .center());
    }

    private static ButtonWidget<?> createListScrollArea(InteractionSyncHandler scrollHandler) {
        return new ButtonWidget<>().syncHandler(scrollHandler)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .background(
                new Rectangle().color(0x22101010)
                    .solid())
            .hoverBackground(IDrawable.EMPTY)
            .playClickSound(false);
    }

    private static InteractionSyncHandler createListScrollHandler(BrowserSession session) {
        return new InteractionSyncHandler()
            .setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
    }

    private static SliderWidget createScrollSlider(InteractionSyncHandler scrollHandler) {
        return new SliderWidget() {

            @Override
            public boolean onMouseScroll(UpOrDown direction, int amount) {
                return scrollHandler.onMouseScroll((int) Math.copySign(amount, direction.modifier));
            }
        };
    }

    private static ButtonWidget<?> createSetAllButton(BrowserSession session, boolean enabled,
        IntSyncValue totalCount) {
        InteractionSyncHandler setAll = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.setAll(enabled); });
        IKey label = IKey
            .lang(enabled ? "tenkeitech.gui.auto_template.enable_all" : "tenkeitech.gui.auto_template.disable_all");
        if (!enabled) label = label.style(IKey.RED);
        return new ButtonWidget<>().syncHandler(setAll)
            .overlay(label)
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.auto_template.refund_tooltip"))
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.auto_template.search_all_tooltip"))
            .setEnabledIf(widget -> totalCount.getIntValue() > 0);
    }

    private static ButtonWidget<?> createRefundButton(BrowserSession session) {
        InteractionSyncHandler refund = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.refund(); });
        return new ButtonWidget<>().syncHandler(refund)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_EXPORT)
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.auto_template.refund"));
    }

    private static IDrawable rowBackground(int tier, int highlightedTier, int row) {
        if (tier < 0) return IDrawable.EMPTY;
        if (tier == highlightedTier) return new Rectangle().color(0x553879B5)
            .solid();
        return new Rectangle().color(row % 2 == 0 ? 0x18101010 : 0x28101010)
            .solid();
    }

    private static IDrawable patternIcon(GenericSyncValue<ItemStack, ?> output, BooleanSyncValue enabled,
        BooleanSyncValue tierEnabled, IntSyncValue tier, IntSyncValue machineTier,
        BooleanSyncValue modeAllowsPatterns) {
        ItemDrawable itemDrawable = new ItemDrawable();
        IDrawable item = new DynamicDrawable(
            () -> output.getValue() == null ? IDrawable.EMPTY : itemDrawable.setItem(output.getValue()));
        IDrawable shade = new Rectangle().color(0x99555555)
            .solid();
        IDrawable disabled = GuiTextures.CROSS_TINY.asIcon()
            .size(7)
            .alignment(Alignment.BottomLeft)
            .margin(1);
        IDrawable normal = new DrawableStack(item);
        IDrawable shaded = new DrawableStack(item, shade);
        IDrawable userDisabled = new DrawableStack(item, disabled);
        IDrawable shadedAndDisabled = new DrawableStack(item, shade, disabled);
        return new DynamicDrawable(() -> {
            if (output.getValue() == null) return IDrawable.EMPTY;
            boolean available = modeAllowsPatterns.getBoolValue() && tier.getIntValue() >= 0
                && tier.getIntValue() <= machineTier.getIntValue();
            if (tierEnabled.getBoolValue() && available) {
                return enabled.getBoolValue() ? normal : userDisabled;
            }
            return enabled.getBoolValue() ? shaded : shadedAndDisabled;
        });
    }

    private static String formatTooltip(ItemStack output, ItemStack itemInput, FluidStack fluidInput, int tier,
        long recipeEUt, boolean enabled, boolean tierEnabled, boolean modeAllowsPatterns, boolean available) {
        if (output == null) return "";
        String input;
        if (itemInput != null) {
            input = itemInput.getDisplayName() + " x" + itemInput.stackSize;
        } else if (fluidInput != null && fluidInput.getFluid() != null) {
            input = fluidInput.getFluid()
                .getLocalizedName(fluidInput) + " "
                + fluidInput.amount
                + " mB";
        } else {
            input = "?";
        }
        String tooltip = StatCollector
            .translateToLocalFormatted(
                "tenkeitech.gui.auto_template.recipe_tooltip",
                output.getDisplayName(),
                output.stackSize,
                input,
                tierName(tier),
                recipeEUt)
            .replace("\\n", "\n");
        return tooltip + '\n' + formatStatus(enabled, tierEnabled, modeAllowsPatterns, available);
    }

    private static String formatStatus(boolean enabled, boolean tierEnabled, boolean modeAllowsPatterns,
        boolean available) {
        if (!enabled || !tierEnabled) {
            return EnumChatFormatting.RED
                + StatCollector.translateToLocal("tenkeitech.gui.auto_template.player_disabled")
                + EnumChatFormatting.RESET;
        }
        if (!modeAllowsPatterns) {
            return EnumChatFormatting.RED
                + StatCollector.translateToLocal("tenkeitech.gui.auto_template.mode_unavailable")
                + EnumChatFormatting.RESET;
        }
        if (!available) {
            return StatCollector.translateToLocal("tenkeitech.gui.auto_template.voltage_unsatisfied");
        }
        return EnumChatFormatting.GREEN + StatCollector.translateToLocal("tenkeitech.gui.auto_template.display")
            + EnumChatFormatting.RESET;
    }

    private static String tierName(int tier) {
        return tier >= 0 && tier < GTValues.VN.length ? GTValues.VN[tier] : "-";
    }

    private static String machineTierName(int tier) {
        return tier == MTEHatchAutoTemplateInputME.SPECIAL_MODE_TIER
            ? StatCollector.translateToLocal("tenkeitech.gui.auto_template.special_mode")
            : tierName(tier);
    }

    private static String formatModeStatus(int ordinal, String targetId) {
        DisplayStatus[] statuses = DisplayStatus.values();
        DisplayStatus status = ordinal >= 0 && ordinal < statuses.length ? statuses[ordinal] : DisplayStatus.ERROR;
        String target = targetId == null || targetId.isEmpty() ? "-" : StatCollector.translateToLocal(targetId);
        String key;
        EnumChatFormatting color;
        switch (status) {
            case UNBOUND -> {
                key = "tenkeitech.gui.auto_template.mode.unbound";
                color = EnumChatFormatting.GRAY;
            }
            case WAITING_FOR_MACHINE -> {
                key = "tenkeitech.gui.auto_template.mode.waiting_machine";
                color = EnumChatFormatting.GRAY;
            }
            case MULTIPLE_CONTROLLERS -> {
                key = "tenkeitech.gui.auto_template.mode.multiple_controllers";
                color = EnumChatFormatting.RED;
            }
            case READY -> {
                key = "tenkeitech.gui.auto_template.mode.ready";
                color = EnumChatFormatting.GREEN;
            }
            case WILL_SWITCH -> {
                key = "tenkeitech.gui.auto_template.mode.will_switch";
                color = EnumChatFormatting.GOLD;
            }
            case SWITCHING -> {
                key = "tenkeitech.gui.auto_template.mode.switching";
                color = EnumChatFormatting.GOLD;
            }
            case WAITING_FOR_BATCH -> {
                key = "tenkeitech.gui.auto_template.mode.waiting_batch";
                color = EnumChatFormatting.GRAY;
            }
            case MISSING_MODE -> {
                key = "tenkeitech.gui.auto_template.mode.missing";
                color = EnumChatFormatting.RED;
            }
            case AMBIGUOUS_MODE -> {
                key = "tenkeitech.gui.auto_template.mode.ambiguous";
                color = EnumChatFormatting.RED;
            }
            default -> {
                key = "tenkeitech.gui.auto_template.mode.error";
                color = EnumChatFormatting.RED;
            }
        }
        return color + StatCollector.translateToLocalFormatted(key, target) + EnumChatFormatting.RESET;
    }

    private static String formatTier(int tier, int machineTier, int highlightedTier, boolean enabled,
        boolean modeAllowsPatterns) {
        if (tier < 0) return "";
        boolean available = modeAllowsPatterns && tier <= machineTier;
        EnumChatFormatting color = enabled && available
            ? tier == highlightedTier ? EnumChatFormatting.GOLD : EnumChatFormatting.GREEN
            : EnumChatFormatting.GRAY;
        String marker = tier == highlightedTier ? "> " : "";
        return marker + color + tierName(tier) + EnumChatFormatting.RESET;
    }

    private static String templateName(ItemStack template) {
        return template == null ? StatCollector.translateToLocal("tenkeitech.gui.auto_template.invalid")
            : template.getDisplayName();
    }

    private static void notifyToggleFailure(EntityPlayer player) {
        if (player != null) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.gui.auto_template.toggle_failed"));
        }
    }

    private static void notifyRefundFailure(EntityPlayer player) {
        if (player != null) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.gui.auto_template.refund_failed"));
        }
    }

    private static final class BrowserSession {

        private final MTEHatchAutoTemplateInputME machine;
        private final boolean client;
        private final EntityPlayer player;
        private List<DisplayRow> rows = Collections.emptyList();
        private List<DisplayCell> catalogCells = Collections.emptyList();
        private List<ItemStack> searchOutputs = Collections.emptyList();
        private BitSet searchFilter;
        private TemplateType templateType;
        private TemplateType selectedCircuitMode;
        private TemplateRecipeCatalog catalog;
        private ItemStack templateStack;
        private int totalPatternCount;
        private int offset;
        private int refreshTicks;
        private double clientScrollPosition;

        private BrowserSession(MTEHatchAutoTemplateInputME machine, boolean client, EntityPlayer player) {
            this.machine = machine;
            this.client = client;
            this.player = player;
            TemplateType[] modes = machine.getSelectableCircuitModes();
            selectedCircuitMode = modes.length == 0 ? TemplateType.NONE : modes[0];
            refreshCatalog();
        }

        private void tick() {
            if (client || ++refreshTicks < 10) return;
            refreshTicks = 0;
            refreshCatalog();
        }

        private void refreshCatalog() {
            if (client) return;
            TemplateType nextType = machine.hasCircuitSelector() ? selectedCircuitMode : machine.getTemplateType();
            if (nextType == null) nextType = TemplateType.NONE;
            TemplateRecipeCatalog nextCatalog = nextType == TemplateType.NONE ? null
                : AutoTemplateRecipeRegistry.get(nextType);
            if (nextType == templateType && nextCatalog == catalog) return;
            templateType = nextType;
            catalog = nextCatalog;
            templateStack = nextType.getTemplateStack();
            if (templateStack != null) templateStack.stackSize = 1;

            List<VirtualPattern> patterns = new ArrayList<VirtualPattern>(machine.getAvailablePatterns(nextType));
            patterns.sort(
                Comparator.comparingInt(VirtualPattern::getRecipeTier)
                    .thenComparing(
                        pattern -> pattern.getKey()
                            .toString()));
            totalPatternCount = patterns.size();
            List<DisplayCell> cells = new ArrayList<DisplayCell>(patterns.size());
            List<ItemStack> outputs = new ArrayList<ItemStack>(patterns.size());
            for (int index = 0; index < patterns.size(); index++) {
                DisplayCell cell = new DisplayCell(patterns.get(index), index);
                cells.add(cell);
                ItemStack searchableOutput = cell.output.copy();
                searchableOutput.stackSize = 1;
                outputs.add(searchableOutput);
            }
            catalogCells = Collections.unmodifiableList(cells);
            searchOutputs = Collections.unmodifiableList(outputs);
            searchFilter = null;
            rebuildRows();
        }

        private List<ItemStack> searchOutputs() {
            refreshCatalog();
            return searchOutputs;
        }

        private void setSearchFilter(BitSet filter) {
            if (client) return;
            searchFilter = filter == null ? null : (BitSet) filter.clone();
            offset = 0;
            rebuildRows();
        }

        private void rebuildRows() {
            List<DisplayCell> patterns = new ArrayList<DisplayCell>();
            for (DisplayCell cell : catalogCells) {
                if (searchFilter == null || searchFilter.get(cell.catalogIndex)) patterns.add(cell);
            }
            List<DisplayRow> rebuiltRows = new ArrayList<DisplayRow>();
            int tierStart = 0;
            while (tierStart < patterns.size()) {
                int tier = patterns.get(tierStart).tier;
                int tierEnd = tierStart + 1;
                while (tierEnd < patterns.size() && patterns.get(tierEnd).tier == tier) {
                    tierEnd++;
                }
                for (int rowStart = tierStart; rowStart < tierEnd; rowStart += PATTERNS_PER_ROW) {
                    int rowEnd = Math.min(rowStart + PATTERNS_PER_ROW, tierEnd);
                    List<DisplayCell> cells = new ArrayList<DisplayCell>(rowEnd - rowStart);
                    for (int index = rowStart; index < rowEnd; index++) {
                        cells.add(patterns.get(index));
                    }
                    rebuiltRows.add(new DisplayRow(tier, cells, rowStart == tierStart));
                }
                tierStart = tierEnd;
            }
            rows = Collections.unmodifiableList(rebuiltRows);
            offset = clampOffset(offset);
        }

        private ItemStack templateStack() {
            return templateStack;
        }

        private int totalPatternCount() {
            return totalPatternCount;
        }

        private int effectivePatternCount() {
            return templateType == null ? 0 : machine.getEffectivePatternCount(templateType);
        }

        private int selectedCircuitIndex() {
            if (!machine.hasCircuitSelector()) return -1;
            TemplateType[] modes = machine.getSelectableCircuitModes();
            for (int index = 0; index < modes.length; index++) {
                if (modes[index] == selectedCircuitMode) return index;
            }
            return -1;
        }

        private int rowCount() {
            return rows.size();
        }

        private int highlightedTier() {
            if (!machine.isPatternModeAvailable()) return -1;
            int machineTier = machine.getCurrentMachineTier();
            int highlighted = -1;
            for (DisplayRow row : rows) {
                if (row.tier <= machineTier) highlighted = Math.max(highlighted, row.tier);
            }
            return highlighted;
        }

        private int rowTier(int visibleRow) {
            DisplayRow row = row(visibleRow);
            return row == null ? -1 : row.tier;
        }

        private boolean rowTierEnabled(int visibleRow) {
            DisplayRow row = row(visibleRow);
            return row != null && machine.isTierUserEnabled(templateType, row.tier);
        }

        private boolean rowTierButtonVisible(int visibleRow) {
            DisplayRow row = row(visibleRow);
            return row != null && row.firstInTier;
        }

        private ItemStack cellOutput(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell == null ? null : cell.output;
        }

        private ItemStack cellItemInput(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell == null ? null : cell.itemInput;
        }

        private FluidStack cellFluidInput(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell == null ? null : cell.fluidInput;
        }

        private long cellRecipeEUt(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell == null ? 0 : cell.recipeEUt;
        }

        private boolean cellEnabled(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell != null && machine.isPatternUserEnabled(cell.key);
        }

        private int cellCatalogIndex(int visibleRow, int column) {
            DisplayCell cell = cell(visibleRow, column);
            return cell == null ? -1 : cell.catalogIndex;
        }

        private void togglePattern(int index) {
            if (client || index < 0
                || index >= catalogCells.size()
                || (searchFilter != null && !searchFilter.get(index))) return;
            DisplayCell cell = catalogCells.get(index);
            boolean enabled = !machine.isPatternUserEnabled(cell.key);
            if (!machine.setPatternEnabled(cell.key, enabled)) {
                notifyToggleFailure(player);
            }
        }

        private void toggleTier(int tier) {
            if (client) return;
            boolean present = false;
            for (DisplayRow row : rows) {
                if (row.tier == tier) {
                    present = true;
                    break;
                }
            }
            if (!present) return;
            boolean enabled = !machine.isTierUserEnabled(templateType, tier);
            if (!machine.setTierEnabled(templateType, tier, enabled)) {
                notifyToggleFailure(player);
            }
        }

        private void setAll(boolean enabled) {
            if (client) return;
            if (!machine.setAllPatternsEnabled(templateType, enabled)) notifyToggleFailure(player);
        }

        private void selectCircuitMode(TemplateType mode) {
            if (client || mode == null || mode == selectedCircuitMode) return;
            boolean allowed = false;
            for (TemplateType candidate : machine.getSelectableCircuitModes()) {
                if (candidate == mode) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) return;
            selectedCircuitMode = mode;
            offset = 0;
            refreshCatalog();
        }

        private void refund() {
            if (!client && !machine.refundSharedPoolToME()) notifyRefundFailure(player);
        }

        private void scroll(int amount) {
            if (client || amount == 0) return;
            refreshCatalog();
            offset = clampOffset(offset + (amount > 0 ? -1 : 1));
        }

        private double scrollPosition() {
            if (client) return clientScrollPosition;
            int maxOffset = maxOffset();
            return maxOffset == 0 ? 0 : (double) offset / maxOffset;
        }

        private void setScrollPosition(double position) {
            double clamped = Math.max(0, Math.min(1, position));
            if (client) {
                clientScrollPosition = clamped;
                return;
            }
            refreshCatalog();
            offset = clampOffset((int) Math.round(clamped * maxOffset()));
        }

        private int maxOffset() {
            return Math.max(0, rows.size() - VISIBLE_ROWS);
        }

        private int clampOffset(int requestedOffset) {
            return Math.max(0, Math.min(requestedOffset, maxOffset()));
        }

        private DisplayRow row(int visibleRow) {
            int index = offset + visibleRow;
            return visibleRow < 0 || visibleRow >= VISIBLE_ROWS || index < 0 || index >= rows.size() ? null
                : rows.get(index);
        }

        private DisplayCell cell(int visibleRow, int column) {
            DisplayRow row = row(visibleRow);
            return row == null || column < 0 || column >= row.cells.size() ? null : row.cells.get(column);
        }
    }

    private static final class DisplayRow {

        private final int tier;
        private final List<DisplayCell> cells;
        private final boolean firstInTier;

        private DisplayRow(int tier, List<DisplayCell> cells, boolean firstInTier) {
            this.tier = tier;
            this.cells = Collections.unmodifiableList(cells);
            this.firstInTier = firstInTier;
        }
    }

    private static final class DisplayCell {

        private final PatternKey key;
        private final ItemStack itemInput;
        private final FluidStack fluidInput;
        private final ItemStack output;
        private final long recipeEUt;
        private final int catalogIndex;
        private final int tier;

        private DisplayCell(VirtualPattern pattern, int catalogIndex) {
            this.catalogIndex = catalogIndex;
            tier = pattern.getRecipeTier();
            key = pattern.getKey();
            itemInput = pattern.getItemInput();
            fluidInput = pattern.getFluidInput();
            output = pattern.getOutput();
            recipeEUt = pattern.getRecipeEUt();
        }
    }
}
