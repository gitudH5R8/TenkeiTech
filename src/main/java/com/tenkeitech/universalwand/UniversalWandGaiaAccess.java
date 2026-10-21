/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

/** Optional Gaia bridge that remains loadable when Botania is absent. */
public interface UniversalWandGaiaAccess {

    int tenkeitech$getGaiaInvulnerability();

    void tenkeitech$setGaiaInvulnerability(int ticks);
}
