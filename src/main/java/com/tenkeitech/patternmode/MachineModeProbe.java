/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import java.util.LinkedHashSet;
import java.util.Set;

import com.tenkeitech.TenkeiTech;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.SingleRecipeCheck;

public final class MachineModeProbe {

    public enum Result {
        ALREADY_MATCHED,
        UNIQUE_MATCH,
        AMBIGUOUS,
        NOT_FOUND,
        UNSUPPORTED,
        ERROR
    }

    private static final int MAX_MODE_PROBES = 64;

    private MachineModeProbe() {}

    public static Result select(MTEMultiBlockBase controller, String target) {
        return probe(controller, target, true);
    }

    /** Inspects every advertised mode and always restores the controller's original state. */
    public static Result inspect(MTEMultiBlockBase controller, String target) {
        return probe(controller, target, false);
    }

    private static Result probe(MTEMultiBlockBase controller, String target, boolean keepUniqueMatch) {
        int original = 0;
        SingleRecipeCheck cache = null;
        boolean captured = false;
        boolean keepSelected = false;
        Result result = Result.ERROR;
        try {
            if (matches(controller, target)) return Result.ALREADY_MATCHED;
            if (!controller.supportsMachineModeSwitch()) return Result.UNSUPPORTED;
            original = controller.getMachineMode();
            cache = controller.getSingleRecipeCheck();
            captured = true;
            Set<Integer> visited = new LinkedHashSet<>();
            Set<Integer> matches = new LinkedHashSet<>();
            visited.add(original);
            boolean complete = false;
            for (int attempt = 0; attempt < MAX_MODE_PROBES; attempt++) {
                int before = controller.getMachineMode();
                int next = controller.nextMachineMode();
                if (controller.getMachineMode() != before) break;
                if (visited.contains(next)) {
                    complete = next == original;
                    break;
                }
                if (visited.size() >= MAX_MODE_PROBES) break;
                controller.setMachineMode(next);
                int actual = controller.getMachineMode();
                if (actual != next || !visited.add(actual)) break;
                if (matches(controller, target)) matches.add(actual);
            }
            if (!complete) result = Result.ERROR;
            else if (matches.isEmpty()) result = Result.NOT_FOUND;
            else if (matches.size() > 1) result = Result.AMBIGUOUS;
            else if (!keepUniqueMatch) result = Result.UNIQUE_MATCH;
            else {
                int selected = matches.iterator()
                    .next();
                controller.setMachineMode(selected);
                if (controller.getMachineMode() == selected && matches(controller, target)) {
                    // Some multi-recipe controllers override setMachineMode without clearing the
                    // recipe lock or scheduling another check. Enforce both parts of the switch
                    // contract before the batch is allowed to expose its inputs.
                    controller.setSingleRecipeCheck(null);
                    controller.scheduleRecipeCheckImmediate();
                    keepSelected = true;
                    result = Result.UNIQUE_MATCH;
                }
            }
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("[PatternMode] Mode probe failed", exception);
        } finally {
            if (captured && !keepSelected) {
                try {
                    if (controller.getMachineMode() != original) controller.setMachineMode(original);
                    if (controller.getMachineMode() != original) result = Result.ERROR;
                    else controller.setSingleRecipeCheck(cache);
                } catch (RuntimeException exception) {
                    result = Result.ERROR;
                    TenkeiTech.LOG.error("[PatternMode] Could not restore original machine mode", exception);
                }
            }
        }
        return result;
    }

    public static boolean matches(MTEMultiBlockBase controller, String target) {
        RecipeMap<?> map = controller.getRecipeMap();
        return map != null && map.unlocalizedName.equals(target);
    }
}
