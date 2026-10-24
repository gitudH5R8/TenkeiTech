/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.NullInventory;

/** A per-interface entry point, never a listener on changes elsewhere in the grid. */
public final class InterfaceReturnMonitor<T extends IAEStack<T>> extends MEMonitorPassThrough<T> {

    private final AutoExtractionState state;

    public InterfaceReturnMonitor(IAEStackType<T> type, AutoExtractionState state) {
        super(new NullInventory<>(), type);
        this.state = state;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource source) {
        return state.inject(getInternal(), input, mode, source);
    }
}
