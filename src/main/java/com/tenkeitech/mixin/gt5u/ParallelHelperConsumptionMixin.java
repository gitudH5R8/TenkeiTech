/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.tenkeitech.integration.gt5u.ParallelHelperConsumptionAccessor;

import gregtech.api.util.ParallelHelper;

/** Keeps custom input consumers read-only when GT5U builds a simulated helper. */
@Mixin(value = ParallelHelper.class, remap = false)
public abstract class ParallelHelperConsumptionMixin implements ParallelHelperConsumptionAccessor {

    @Shadow
    protected boolean consume;

    @Override
    public boolean tenkeitech$isConsumptionEnabled() {
        return consume;
    }
}
