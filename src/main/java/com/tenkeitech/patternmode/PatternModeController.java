/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;

public interface PatternModeController {

    MachinePatternBatch tenkeitech$patternBatch();

    void tenkeitech$invalidateRecipeCache(IDualInputInventoryWithPattern inventory);
}
