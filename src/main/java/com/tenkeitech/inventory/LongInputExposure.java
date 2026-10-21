/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import com.tenkeitech.Config;

/** Defines the bounded stack window exposed to legacy GT inventory APIs. */
public final class LongInputExposure {

    public static final int MAX_STACK_AMOUNT = Integer.MAX_VALUE;

    private LongInputExposure() {}

    public static int stackLimit() {
        return Math.max(1, Math.min(65_535, Config.longInputExposureStackLimit));
    }

    public static int segmentCount(long amount) {
        if (amount <= 0) return 0;
        long required = (amount - 1) / MAX_STACK_AMOUNT + 1;
        return (int) Math.min(stackLimit(), required);
    }

    public static int segmentAmount(long amount, int index) {
        if (amount <= 0 || index < 0 || index >= stackLimit()) return 0;
        long remaining = amount - (long) index * MAX_STACK_AMOUNT;
        return (int) Math.min(MAX_STACK_AMOUNT, Math.max(0, remaining));
    }

    public static long visibleAmount(long amount) {
        if (amount <= 0) return 0;
        return Math.min(amount, (long) stackLimit() * MAX_STACK_AMOUNT);
    }
}
