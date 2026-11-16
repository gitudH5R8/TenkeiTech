/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.nechar;

import net.vfyjxf.nechar.utils.Match;

/** Loaded only after the client detects NEC, sharing its configured PinIn context and dictionary. */
public final class NotEnoughCharactersSearch {

    private NotEnoughCharactersSearch() {}

    public static boolean contains(String text, String query) {
        return Match.contains(text, query);
    }
}
