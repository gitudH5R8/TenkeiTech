/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-FileCopyrightText: StructureLib contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.ChannelDescription;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native StructureLib channel completion adapted to ModularUI; writes use the validated server session. */
@SideOnly(Side.CLIENT)
final class BetterMachineProjectorChannelInput extends ParentWidget<BetterMachineProjectorChannelInput> {

    private static final int ROWS = 4;
    private final ChannelField field = new ChannelField();
    private final TextFieldWidget numberField;
    private final List<ButtonWidget<?>> rows = new ArrayList<>();
    private final RichTooltip description = new RichTooltip().parent(this)
        .pos(RichTooltip.Pos.RIGHT);
    private List<Option> options = Collections.emptyList();
    private int selected;
    private int first;
    private String described = "";

    BetterMachineProjectorChannelInput(StringSyncValue name, TextFieldWidget numberField, IDrawable inputBackground) {
        this.numberField = numberField;
        disableThemeBackground(true);
        disableHoverThemeBackground(true);
        child(
            field.setMaxLength(64)
                .value(name)
                .background(inputBackground)
                .disableThemeBackground(true)
                .disableHoverThemeBackground(true)
                .setTextAlignment(Alignment.CenterLeft)
                .setTextColor(0xFFE0E0E0)
                .padding(3, 1)
                .size(109, 12));
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            ButtonWidget<?> button = new ButtonWidget<>().background(new DynamicDrawable(() -> {
                IDrawable fill = new Rectangle().color(0xFF202020)
                    .canApplyTheme(false);
                if (first + row != selected) return fill;
                return new DrawableStack(
                    fill,
                    new Rectangle().color(0xFFAAAAAA)
                        .canApplyTheme(false)
                        .hollow(1));
            }))
                .hoverBackground(IDrawable.NONE)
                .disableThemeBackground(true)
                .disableHoverThemeBackground(true)
                .playClickSound(false)
                .overlay((context, x, y, width, height, theme) -> {
                    Option option = option(row);
                    if (option == null) return;
                    TextRenderer renderer = TextRenderer.SHARED;
                    renderer.setAlignment(Alignment.CenterLeft, width - 4, height);
                    renderer.setPos(x + 2, y);
                    renderer.setScale(1);
                    renderer.setColor(0xFFFFFFFF);
                    renderer.setShadow(false);
                    renderer.setSimulate(false);
                    renderer.drawCut(option.display);
                })
                .setEnabledIf(widget -> field.isFocused() && option(row) != null)
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || option(row) == null) return false;
                    selected = first + row;
                    complete(false);
                    return true;
                })
                .onMouseScrolled((direction, amount) -> {
                    first = Math
                        .max(0, Math.min(Math.max(0, options.size() - ROWS), first - direction.modifier * amount));
                    selected = Math.max(first, Math.min(first + ROWS - 1, selected));
                    return true;
                })
                .pos(0, 12 + row * 14)
                .size(109, 14);
            rows.add(button);
            child(button);
        }
    }

    @Override
    public boolean canHover() {
        return false;
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        String channel = field.getText();
        if (field.isFocused() && !options.isEmpty()) {
            channel = options.get(selected).name;
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row)
                    .isHovering() && option(row) != null) channel = option(row).name;
            }
        } else if (!field.isHovering()) return;
        if (!channel.equals(described)) {
            described = channel;
            description.clearText();
            // StructureLibAPI exposes registration only. Read the same public registry as its native editor.
            ChannelDescription entry = ChannelDescription.getAll()
                .get(channel);
            if (entry != null) {
                description.addLine(channel);
                for (Map.Entry<String, String> source : entry.getDescriptions()
                    .entrySet()) {
                    ModContainer mod = Loader.instance()
                        .getIndexedModList()
                        .get(source.getKey());
                    description.addLine(
                        IKey.lang(
                            "item.structurelib.constructableTrigger.gui.channels.from",
                            mod == null ? source.getKey() : mod.getName()));
                    description.addLine(IKey.lang(source.getValue()));
                }
            }
        }
        description.draw(context);
    }

    private Option option(int row) {
        int index = first + row;
        return index < options.size() ? options.get(index) : null;
    }

    private void refresh() {
        String term = field.getText()
            .toLowerCase(Locale.ROOT);
        options = new ArrayList<>();
        List<String> subjects = new ArrayList<>(
            ChannelDescription.getAll()
                .keySet());
        Collections.sort(subjects);
        // Match native priority: alphabetical prefix matches, followed by alphabetical subsequence matches.
        for (String subject : subjects) {
            if (subject.startsWith(term))
                options.add(new Option(subject, term + EnumChatFormatting.GRAY + subject.substring(term.length())));
        }
        if (!term.isEmpty()) {
            for (String subject : subjects) {
                if (subject.startsWith(term)) continue;
                int matched = 0;
                StringBuilder display = new StringBuilder();
                for (int i = 0; i < subject.length(); i++) {
                    boolean match = matched < term.length() && subject.charAt(i) == term.charAt(matched);
                    display.append(match ? EnumChatFormatting.WHITE : EnumChatFormatting.GRAY)
                        .append(subject.charAt(i));
                    if (match) matched++;
                }
                if (matched == term.length()) options.add(new Option(subject, display.toString()));
            }
        }
        selected = 0;
        first = 0;
    }

    private void complete(boolean nextField) {
        if (options.isEmpty()) return;
        field.setText(options.get(selected).name);
        field.endCursor();
        if (nextField) {
            numberField.setText("1");
            numberField.getStringValue()
                .setStringValue("1");
            getContext().focus(numberField);
        }
    }

    private final class ChannelField extends TextFieldWidget {

        @Override
        public void setText(String text) {
            super.setText(text.toLowerCase(Locale.ROOT));
            refresh();
        }

        @Override
        protected void onTextChanged() {
            super.onTextChanged();
            setText(getText());
        }

        @Override
        public void onFocus(ModularGuiContext context) {
            super.onFocus(context);
            refresh();
        }

        @Override
        public Result onKeyPressed(char character, int keyCode) {
            if (!isFocused()) return Result.IGNORE;
            if (keyCode == Keyboard.KEY_TAB) {
                if (options.isEmpty()) getContext().focus(numberField);
                else complete(false);
                return Result.SUCCESS;
            }
            if (!options.isEmpty()) {
                switch (keyCode) {
                    case Keyboard.KEY_UP:
                    case Keyboard.KEY_DOWN:
                        selected = Math
                            .max(0, Math.min(options.size() - 1, selected + (keyCode == Keyboard.KEY_UP ? -1 : 1)));
                        first = Math.max(0, Math.min(first, selected));
                        if (selected >= first + ROWS) first = selected - ROWS + 1;
                        return Result.SUCCESS;
                    case Keyboard.KEY_RETURN:
                    case Keyboard.KEY_NUMPADENTER:
                        complete(true);
                        return Result.SUCCESS;
                }
            }
            return super.onKeyPressed(character, keyCode);
        }

        private void endCursor() {
            handler.setCursor(0, getText().length(), true, true);
        }

        @Override
        public void drawForeground(ModularGuiContext context) {
            // The enclosing input draws the registry description rather than the base field's duplicate text tip.
        }
    }

    private static final class Option {

        private final String name;
        private final String display;

        private Option(String name, String display) {
            this.name = name;
            this.display = display;
        }
    }
}
