/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.tenkeitech.mixin.ae2.InterfaceTerminalNameInvoker;

/** Client-only, bounded cache of actual interface-terminal display names. */
final class PatternUploadNames {

    private static final Map<String, String> CACHE = new LinkedHashMap<>();
    private static String language;

    private PatternUploadNames() {}

    static String display(PatternUploadNetwork.TargetEntry entry) {
        return display(entry.terminalName, entry.terminalSuffix, entry.terminalDisplayRep);
    }

    static String display(String name, String suffix) {
        return display(name, suffix, null);
    }

    private static String display(String name, String suffix, ItemStack displayRep) {
        String currentLanguage = Minecraft.getMinecraft().gameSettings.language;
        if (!currentLanguage.equals(language)) {
            CACHE.clear();
            language = currentLanguage;
        }
        String iconName = displayRep == null ? "" : displayRep.getDisplayName();
        String key = name.length() + ":" + name + suffix.length() + ":" + suffix + iconName;
        String display = CACHE.get(key);
        if (display == null) {
            display = InterfaceTerminalNameInvoker.translate(name, suffix.isEmpty() ? null : suffix, displayRep);
            if (CACHE.size() >= 1024) CACHE.clear();
            CACHE.put(key, display);
        }
        return display;
    }
}
