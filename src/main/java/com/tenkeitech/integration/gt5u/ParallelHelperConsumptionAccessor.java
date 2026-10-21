/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.integration.gt5u;

/** Exposes ParallelHelper's consumption mode to TenkeiTech input callbacks. */
public interface ParallelHelperConsumptionAccessor {

    boolean tenkeitech$isConsumptionEnabled();
}
