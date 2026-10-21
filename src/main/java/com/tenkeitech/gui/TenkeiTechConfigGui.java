/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;

/** Top-level in-game configuration screen exposed by the Forge Mods menu. */
public final class TenkeiTechConfigGui extends GuiConfig {

    private final File file;
    private final Configuration configuration;

    public TenkeiTechConfigGui(GuiScreen parent) {
        this(parent, new Configuration(configurationFile()));
    }

    private TenkeiTechConfigGui(GuiScreen parent, Configuration configuration) {
        super(
            parent,
            createConfigElements(configuration),
            TenkeiTech.MOD_ID,
            "tenkeitech",
            false,
            false,
            I18n.format("tenkeitech.config.title"));
        this.file = configurationFile();
        this.configuration = configuration;
    }

    static List<IConfigElement> createConfigElements(Configuration configuration) {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        Config.configureAllCategories(configuration);
        List<IConfigElement> otherElements = new ArrayList<IConfigElement>();
        otherElements.add(
            new DummyConfigElement(
                "questSkip",
                Config.getQuestSkipProperty(configuration)
                    .getBoolean(true),
                ConfigGuiType.BOOLEAN,
                "tenkeitech.config.questskip.enabled").setCustomListEntryClass(QuestSkipReadOnlyEntry.class));
        String[] categories = { "patternupload", "wirelessae", "universalwand" };
        for (String category : categories) {
            if (configuration.hasCategory(category)) {
                elements.add(new ConfigElement(configuration.getCategory(category)));
            }
        }
        elements.add(new DummyConfigElement.DummyCategoryElement("etc", "tenkeitech.config.other", otherElements));
        return elements;
    }

    private static File configurationFile() {
        File configured = Config.getConfigurationFile();
        return configured != null ? configured
            : new File(Minecraft.getMinecraft().mcDataDir, "config/" + TenkeiTech.MOD_ID + ".cfg");
    }

    public static final class QuestSkipReadOnlyEntry extends GuiConfigEntries.ButtonEntry {

        public QuestSkipReadOnlyEntry(GuiConfig screen, GuiConfigEntries entries, IConfigElement element) {
            super(screen, entries, element);
            this.btnValue.enabled = false;
            updateValueButtonText();
        }

        @Override
        public void updateValueButtonText() {
            this.btnValue.displayString = I18n.format(String.valueOf(configElement.get()));
        }

        @Override
        public void valueButtonPressed(int slotIndex) {}

        @Override
        public boolean isDefault() {
            return true;
        }

        @Override
        public boolean isChanged() {
            return false;
        }

        @Override
        public Object getCurrentValue() {
            return configElement.get();
        }

        @Override
        public Object[] getCurrentValues() {
            return new Object[] { getCurrentValue() };
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void setToDefault() {}

        @Override
        public void undoChanges() {}

        @Override
        public boolean saveConfigElement() {
            return false;
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (configuration.hasChanged()) configuration.save();
        Config.synchronizeConfiguration(file);
    }
}
