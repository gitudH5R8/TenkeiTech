/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import com.github.bsideup.jabel.Desugar;
import com.tenkeitech.Config;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;

/** Server-thread-only admission accounting for AE's cancellable futures. */
final class MaintainerCalculations {

    private static final List<Calculation> ACTIVE = new ArrayList<>();

    private MaintainerCalculations() {}

    static boolean available(IGrid grid) {
        int count = 0;
        Iterator<Calculation> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Calculation calculation = iterator.next();
            if (calculation.future.isDone()) iterator.remove();
            else if (calculation.grid == grid) count++;
        }
        return ACTIVE.size() < 256 && count < Config.maintainerCalculationsPerNetwork;
    }

    static void track(IGrid grid, Future<ICraftingJob> future) {
        ACTIVE.add(new Calculation(grid, future));
    }

    static void prune() {
        ACTIVE.removeIf(calculation -> calculation.future.isDone());
    }

    static void clear() {
        for (Calculation calculation : ACTIVE) calculation.future.cancel(true);
        ACTIVE.clear();
    }

    @Desugar
    private record Calculation(IGrid grid, Future<ICraftingJob> future) {}
}
