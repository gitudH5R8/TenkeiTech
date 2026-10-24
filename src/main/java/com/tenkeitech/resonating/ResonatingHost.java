/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

/** Admission-only query; input ownership and dispatch remain private to the resonating route. */
public interface ResonatingHost {

    boolean tenkeitech$dispatchBusy();
}
