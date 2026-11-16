/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import com.tenkeitech.integration.nechar.NotEnoughCharactersSearch;

import cpw.mods.fml.common.Loader;

/** Client display filtering only; callers keep their own normalization and routing rules. */
public final class ClientTextSearch {

    private static final boolean PINYIN_AVAILABLE = Loader.isModLoaded("nechar");

    private ClientTextSearch() {}

    public static boolean contains(String text, String query) {
        return text.contains(query) || PINYIN_AVAILABLE && NotEnoughCharactersSearch.contains(text, query);
    }
}
