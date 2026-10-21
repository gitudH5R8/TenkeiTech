/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.parts.IPatternTerminal;
import appeng.container.implementations.ContainerPatternTerm;

public final class PatternModeEncodingContext {

    private static final Map<IPatternTerminal, WeakReference<PatternModeEncodingContext>> CONTEXTS = new WeakHashMap<>();
    private volatile Binding binding;

    private static final class Binding {

        private final String recipeMap;

        private Binding(String recipeMap) {
            this.recipeMap = recipeMap;
        }
    }

    public void attach(IPatternTerminal terminal) {
        synchronized (CONTEXTS) {
            CONTEXTS.put(terminal, new WeakReference<>(this));
        }
    }

    public void clear() {
        binding = null;
    }

    public void transferred(ContainerPatternTerm terminal, String id) {
        clear();
        attach(terminal.getPatternTerminal());
        if (terminal.isCraftingMode() || !PatternRecipeMapData.registered(id)) return;
        binding = new Binding(id);
    }

    public static void encoded(IPatternTerminal terminal, ItemStack pattern) {
        if (pattern == null || pattern.getTagCompound() == null) return;
        PatternModeEncodingContext context;
        synchronized (CONTEXTS) {
            WeakReference<PatternModeEncodingContext> reference = CONTEXTS.get(terminal);
            context = reference == null ? null : reference.get();
        }
        NBTTagCompound tag = pattern.getTagCompound();
        tag.removeTag(PatternRecipeMapData.KEY);
        if (context == null || terminal.isCraftingRecipe()) return;
        Binding current = context.binding;
        if (current == null) return;
        PatternRecipeMapData.write(tag, current.recipeMap);
    }
}
