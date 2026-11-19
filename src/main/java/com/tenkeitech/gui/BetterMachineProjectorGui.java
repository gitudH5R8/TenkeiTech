/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.tenkeitech.machineprojector.BetterMachineProjectorSettings;

import gregtech.api.enums.Materials;
import gregtech.common.items.IDMetaTool01;
import gregtech.common.items.MetaGeneratedTool01;

/** The native channel editor layout with projector operation controls and the wand palette. */
public final class BetterMachineProjectorGui {

    private static final int ROWS = 6;
    private static final int TEXT = 0xFF414156;
    private static final int LIST_BACKGROUND = 0xFF202020;
    private static final IDrawable REMOVE_ICON = UITexture.builder()
        .location("structurelib", "textures/gui/channels.png")
        .imageSize(256, 256)
        .subAreaXYWH(251, 251, 5, 5)
        .build()
        .asIcon()
        .size(5);

    private BetterMachineProjectorGui() {}

    public static ModularPanel build(EntityPlayer player, ItemStack stack, PanelSyncManager manager) {
        Session session = new Session();
        StringSyncValue channels = new StringSyncValue(
            () -> ChannelDataAccessor.iterateChannelData(stack)
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n")));
        StringSyncValue name = new StringSyncValue(() -> session.name, s -> session.name = s).allowC2S();
        IntSyncValue value = new IntSyncValue(() -> session.value, n -> session.value = n).allowC2S();
        BooleanSyncValue clearPending = new BooleanSyncValue(session::isClearPending);
        DoubleSyncValue scroll = new DoubleSyncValue(
            () -> session.scroll,
            n -> session.scroll = Double.isFinite(n) ? Math.max(0, Math.min(1, n)) : 0).allowC2S();
        InteractionSyncHandler listScroll = new InteractionSyncHandler().setOnMouseScroll(
            mouse -> { if (!mouse.isClient()) session.scroll(channels.getStringValue(), mouse.mouseButton); });
        IntSyncValue mode = new IntSyncValue(
            () -> BetterMachineProjectorSettings.mode(stack)
                .ordinal());
        IntSyncValue repeat = new IntSyncValue(
            () -> BetterMachineProjectorSettings.repeat(stack),
            n -> BetterMachineProjectorSettings.set(player, stack, "repeat", n)).allowC2S();
        manager.syncValue("machine_projector_channels", 0, channels);
        manager.syncValue("machine_projector_name", 0, name);
        manager.syncValue("machine_projector_value", 0, value);
        manager.syncValue("machine_projector_clear_pending", 0, clearPending);
        manager.syncValue("machine_projector_scroll", 0, scroll);
        manager.syncValue("machine_projector_mode", 0, mode);
        manager.syncValue("machine_projector_repeat", 0, repeat);
        IPanelHandler settings = manager.syncedPanel(
            "machine_projector_execution_settings",
            true,
            (sync, handler) -> settings(player, stack, sync));
        ModularPanel panel = ModularPanel.defaultPanel("tenkeitech_better_machine_projector", 176, 230)
            .background(background());
        panel.child(
            label("channels").pos(12, 7)
                .size(134, 12));
        panel.child(
            new ButtonWidget<>().background(button(false))
                .overlay(
                    new ItemDrawable(
                        MetaGeneratedTool01.INSTANCE
                            .getToolWithStats(IDMetaTool01.WRENCH.ID, 1, Materials.Steel, Materials.Steel, null))
                                .asIcon()
                                .size(9))
                .pos(152, 7)
                .size(11, 11)
                .addTooltipLine(key("execution_settings"))
                .onMousePressed(mouse -> {
                    settings.openPanel();
                    return true;
                }));
        panel.child(
            plainButton(listScroll).background(rectangle(LIST_BACKGROUND))
                .pos(12, 21)
                .size(152, 91));
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            IKey rowName = IKey.dynamic(() -> rowPart(channels.getStringValue(), scroll.getDoubleValue(), row, false));
            IKey rowValue = IKey.dynamic(() -> rowPart(channels.getStringValue(), scroll.getDoubleValue(), row, true));
            InteractionSyncHandler select = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (!mouse.isClient() && mouse.mouseButton == 0 && BetterMachineProjectorSettings.owns(player, stack)) {
                    String snapshot = channels.getStringValue();
                    String selected = rowPart(snapshot, session.scroll, row, false);
                    if (!selected.isEmpty()) {
                        session.name = selected;
                        session.value = Integer.parseInt(rowPart(snapshot, session.scroll, row, true));
                    }
                }
            })
                .setOnMouseScroll(
                    mouse -> { if (!mouse.isClient()) session.scroll(channels.getStringValue(), mouse.mouseButton); });
            ButtonWidget<?> rowButton = plainButton(select).background(new DynamicDrawable(() -> {
                if (rowName.get()
                    .isEmpty()) return IDrawable.EMPTY;
                return rowName.get()
                    .equals(name.getStringValue()) ? rectangle(0xFFAAAAAA).hollow(1) : rowSeparator();
            }))
                .pos(13, 24 + i * 14)
                .size(150, 14)
                .setEnabledIf(
                    widget -> !rowName.get()
                        .isEmpty())
                .addTooltipLine(IKey.dynamic(() -> rowName.get() + ": " + rowValue.get()));
            InteractionSyncHandler remove = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (!mouse.isClient() && mouse.mouseButton == 0) {
                    String channel = rowPart(channels.getStringValue(), session.scroll, row, false);
                    session.remove(player, stack, channel);
                }
            })
                .setOnMouseScroll(
                    mouse -> { if (!mouse.isClient()) session.scroll(channels.getStringValue(), mouse.mouseButton); });
            // ButtonWidget replaces its single child; group both columns and the delete control first.
            rowButton.child(
                new ParentWidget<>().size(150, 14)
                    .child(
                        new ColumnWidget(rowName).pos(10, 0)
                            .size(94, 14))
                    .child(
                        new ColumnWidget(rowValue).pos(107, 0)
                            .size(32, 14))
                    .child(
                        plainButton(remove)
                            .overlay(
                                new DynamicDrawable(() -> rowButton.isBelowMouse() ? REMOVE_ICON : IDrawable.EMPTY))
                            .addTooltipLine(key("remove"))
                            .pos(2, 4)
                            .size(7, 7)));
            panel.child(rowButton);
        }
        panel.child(
            new ParentWidget<>().background(rectangle(0xFFAAAAAA))
                .pos(118, 22)
                .size(1, 89));
        panel.child(new SliderWidget() {

            @Override
            public boolean onMouseScroll(UpOrDown direction, int amount) {
                return listScroll.onMouseScroll((int) Math.copySign(amount, direction.modifier));
            }
        }.value(scroll)
            .bounds(0, 1)
            .setAxis(GuiAxis.Y)
            .sliderSize(5, 12)
            .sliderTexture(rectangle(0xFF9D9FB3))
            .background(rectangle(0xFF101010))
            .setEnabledIf(widget -> entries(channels.getStringValue()).length > ROWS)
            .pos(157, 24)
            .size(5, 84));
        panel.child(
            label("channel_name").pos(12, 119)
                .size(40, 12));
        panel.child(
            label("channel_value").pos(12, 139)
                .size(40, 12));
        TextFieldWidget numberField = field().formatAsInteger(true)
            .numbersInt(1, Integer.MAX_VALUE)
            .setMaxLength(10)
            .value(value)
            .pos(54, 139)
            .size(109, 12);
        panel.child(numberField);
        panel.child(action(player, stack, () -> {
            String channel = session.name.trim()
                .toLowerCase(Locale.ROOT);
            if (!channel.matches("[a-z0-9_.:-]{1,64}") || session.value < 1) return;
            if (!ChannelDataAccessor.hasSubChannel(stack, channel) && ChannelDataAccessor.countChannelData(stack) >= 64)
                return;
            BetterMachineProjectorSettings
                .edit(player, stack, tag -> ChannelDataAccessor.setChannelData(stack, channel, session.value));
        }).overlay(
            IKey.dynamicKey(() -> key(hasChannel(channels.getStringValue(), name.getStringValue()) ? "update" : "add"))
                .color(TEXT)
                .shadow(false))
            .pos(12, 157)
            .size(47, 20));
        panel.child(action(player, stack, () -> {
            String channel = session.name.trim()
                .toLowerCase(Locale.ROOT);
            session.remove(player, stack, channel);
        }).overlay(
            key("remove").color(TEXT)
                .shadow(false))
            .pos(65, 157)
            .size(47, 20));
        panel.child(
            action(player, stack, () -> session.clear(player, stack))
                .overlay(
                    IKey.dynamicKey(
                        () -> clearPending.getBoolValue()
                            ? IKey.lang("item.structurelib.constructableTrigger.gui.wipe.confirm")
                            : key("clear"))
                        .color(0xFFC00000)
                        .shadow(false))
                .pos(118, 157)
                .size(47, 20));
        for (BetterMachineProjectorSettings.Mode operation : BetterMachineProjectorSettings.Mode.values()) {
            int index = operation.ordinal();
            panel.child(
                action(player, stack, () -> BetterMachineProjectorSettings.set(player, stack, "mode", index))
                    .background(new DynamicDrawable(() -> button(mode.getIntValue() == index)))
                    .overlay(
                        IKey.lang(
                            "tenkeitech.better_machine_projector.mode." + operation.name()
                                .toLowerCase(Locale.ROOT))
                            .color(operation == BetterMachineProjectorSettings.Mode.DISMANTLE ? 0xFFC00000 : TEXT)
                            .shadow(false))
                    .pos(12 + 53 * index, 181)
                    .size(47, 18));
        }
        panel.child(
            label("repeat").pos(12, 207)
                .size(80, 12));
        panel.child(
            field().formatAsInteger(true)
                .numbersInt(1, BetterMachineProjectorSettings.MAX_REPEAT)
                .setMaxLength(4)
                .value(repeat)
                .pos(94, 207)
                .size(69, 12));
        // Add last so the client completion list covers the controls below the input without changing their layout.
        if (manager.isClient()) {
            panel.child(
                new BetterMachineProjectorChannelInput(name, numberField, inputBackground()).pos(54, 119)
                    .size(109, 68));
        } else {
            panel.child(
                field().setMaxLength(64)
                    .value(name)
                    .pos(54, 119)
                    .size(109, 12));
        }
        return panel;
    }

    private static ModularPanel settings(EntityPlayer player, ItemStack stack, PanelSyncManager manager) {
        IntSyncValue budget = new IntSyncValue(
            () -> BetterMachineProjectorSettings.budget(stack),
            n -> BetterMachineProjectorSettings.set(player, stack, "budget", n)).allowC2S();
        IntSyncValue cooldown = new IntSyncValue(
            () -> BetterMachineProjectorSettings.cooldown(stack),
            n -> BetterMachineProjectorSettings.set(player, stack, "cooldown", n)).allowC2S();
        BooleanSyncValue skipMissing = new BooleanSyncValue(
            () -> BetterMachineProjectorSettings.skipMissing(stack),
            enabled -> BetterMachineProjectorSettings.setSkipMissing(player, stack, enabled)).allowC2S();
        manager.syncValue("machine_projector_budget", 0, budget);
        manager.syncValue("machine_projector_cooldown", 0, cooldown);
        manager.syncValue("machine_projector_skip_missing", 0, skipMissing);
        return gregtech.api.modularui2.GTGuis.createPopUpPanel("machine_projector_execution_settings", true, false)
            .background(background())
            .size(176, 106)
            .child(
                label("execution_settings").pos(12, 8)
                    .size(140, 12))
            .child(
                label("budget").pos(12, 32)
                    .size(100, 12))
            .child(
                field().formatAsInteger(true)
                    .numbersInt(1, BetterMachineProjectorSettings.MAX_BUDGET)
                    .setMaxLength(3)
                    .value(budget)
                    .pos(114, 32)
                    .size(49, 12))
            .child(
                label("cooldown").pos(12, 56)
                    .size(100, 12))
            .child(
                field().formatAsInteger(true)
                    .numbersInt(0, BetterMachineProjectorSettings.MAX_COOLDOWN)
                    .setMaxLength(5)
                    .value(cooldown)
                    .pos(114, 56)
                    .size(49, 12))
            .child(
                label("skip_missing").pos(12, 80)
                    .size(132, 12))
            .child(
                new ToggleButton().value(skipMissing)
                    .background(IDrawable.EMPTY)
                    .hoverBackground(IDrawable.NONE)
                    .disableThemeBackground(true)
                    .disableHoverThemeBackground(true)
                    .overlay(true, GuiTextures.CHECK_BOX_FULL)
                    .overlay(false, GuiTextures.CHECK_BOX_EMPTY)
                    .addTooltipLine(key("skip_missing.tooltip"))
                    .pos(150, 80)
                    .size(13, 13));
    }

    private static ButtonWidget<?> action(EntityPlayer player, ItemStack stack, Runnable action) {
        ButtonWidget<?> button = new ButtonWidget<>().background(button(false));
        return button.onMousePressed(mouseButton -> {
            // Flush the focused field's value packet before the action packet reads the server session.
            if (mouseButton == 0) button.getContext()
                .removeFocus();
            return false;
        })
            .syncHandler(
                new InteractionSyncHandler().setOnMousePressed(
                    mouse -> {
                        if (!mouse.isClient() && mouse.mouseButton == 0
                            && BetterMachineProjectorSettings.owns(player, stack)) action.run();
                    }));
    }

    private static String[] entries(String snapshot) {
        return snapshot.isEmpty() ? new String[0] : snapshot.split("\n");
    }

    private static int firstRow(String snapshot, double scroll) {
        return (int) Math.round(scroll * Math.max(0, entries(snapshot).length - ROWS));
    }

    private static String rowPart(String snapshot, double scroll, int row, boolean value) {
        String[] entries = entries(snapshot);
        int index = firstRow(snapshot, scroll) + row;
        if (index >= entries.length) return "";
        int split = entries[index].lastIndexOf('=');
        return value ? entries[index].substring(split + 1) : entries[index].substring(0, split);
    }

    private static boolean hasChannel(String snapshot, String name) {
        String prefix = name.trim()
            .toLowerCase(Locale.ROOT) + "=";
        for (String entry : entries(snapshot)) {
            if (entry.startsWith(prefix)) return true;
        }
        return false;
    }

    private static ButtonWidget<?> plainButton(InteractionSyncHandler handler) {
        return new ButtonWidget<>().syncHandler(handler)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.NONE)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .playClickSound(false);
    }

    private static TextFieldWidget field() {
        return new TextFieldWidget().background(inputBackground())
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .setTextAlignment(Alignment.CenterLeft)
            .setTextColor(0xFFE0E0E0)
            .padding(3, 1);
    }

    private static IDrawable clippedText(IKey key) {
        return (context, x, y, width, height, theme) -> {
            TextRenderer renderer = TextRenderer.SHARED;
            renderer.setAlignment(Alignment.CenterLeft, width, height);
            renderer.setPos(x, y);
            renderer.setScale(1);
            renderer.setColor(0xFFFFFFFF);
            renderer.setShadow(false);
            renderer.setSimulate(false);
            renderer.drawCut(key.getFormatted());
        };
    }

    private static IDrawable rowSeparator() {
        Rectangle line = rectangle(0xFFAAAAAA);
        return (context, x, y, width, height, theme) -> line.draw(context, x, y + height - 1, width, 1, theme);
    }

    private static IDrawable inputBackground() {
        Rectangle border = rectangle(0xFFA0A0A0);
        Rectangle fill = rectangle(0xFF000000);
        return (context, x, y, width, height, theme) -> {
            border.draw(context, x, y, width, height, theme);
            fill.draw(context, x + 1, y + 1, width - 2, height - 2, theme);
        };
    }

    private static IKey key(String key) {
        return IKey.lang("tenkeitech.better_machine_projector.gui." + key);
    }

    private static TextWidget<?> label(String key) {
        return new TextWidget<>(key(key)).color(TEXT)
            .shadow(false)
            .textAlign(Alignment.CenterLeft);
    }

    private static IDrawable button(boolean active) {
        return new DrawableStack(GuiTextures.MC_BUTTON, rectangle(active ? 0x668ED4FF : 0x339D9FB3));
    }

    private static Rectangle rectangle(int color) {
        return new Rectangle().color(color)
            .canApplyTheme(false);
    }

    private static IDrawable background() {
        Rectangle edge = rectangle(0xFF28282C);
        Rectangle light = rectangle(0xFFF4F4F6);
        Rectangle shadow = rectangle(0xFF55555D);
        Rectangle fill = rectangle(0xFFCDCDD5);
        return (context, x, y, width, height, theme) -> {
            edge.draw(context, x, y, width, height, theme);
            light.draw(context, x + 1, y + 1, width - 2, height - 2, theme);
            shadow.draw(context, x + 4, y + 4, width - 5, height - 5, theme);
            fill.draw(context, x + 4, y + 4, width - 8, height - 8, theme);
        };
    }

    private static final class ColumnWidget extends Widget<ColumnWidget> {

        private ColumnWidget(IKey key) {
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            overlay(clippedText(key));
        }

        @Override
        public boolean canHover() {
            return false;
        }
    }

    private static final class Session {

        private static final long CLEAR_CONFIRM_NANOS = 3_000_000_000L;
        private String name = "";
        private int value = 1;
        private double scroll;
        private long clearDeadline;

        private boolean isClearPending() {
            return clearDeadline != 0 && System.nanoTime() - clearDeadline < 0;
        }

        private void clear(EntityPlayer player, ItemStack stack) {
            if (!isClearPending()) {
                clearDeadline = System.nanoTime() + CLEAR_CONFIRM_NANOS;
                return;
            }
            clearDeadline = 0;
            BetterMachineProjectorSettings.edit(player, stack, tag -> ChannelDataAccessor.wipeChannelData(stack));
            name = "";
            value = 1;
            scroll = 0;
        }

        private void remove(EntityPlayer player, ItemStack stack, String channel) {
            if (channel.isEmpty() || !BetterMachineProjectorSettings.owns(player, stack)) return;
            int first = (int) Math.round(scroll * Math.max(0, ChannelDataAccessor.countChannelData(stack) - ROWS));
            BetterMachineProjectorSettings
                .edit(player, stack, tag -> ChannelDataAccessor.unsetChannelData(stack, channel));
            int max = Math.max(0, ChannelDataAccessor.countChannelData(stack) - ROWS);
            scroll = max == 0 ? 0 : Math.min(first, max) / (double) max;
        }

        private void scroll(String snapshot, int delta) {
            int max = Math.max(0, entries(snapshot).length - ROWS);
            scroll = max == 0 ? 0
                : Math.max(0, Math.min(max, firstRow(snapshot, scroll) - Integer.signum(delta))) / (double) max;
        }
    }
}
