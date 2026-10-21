/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Manual exact mappings and automatic search over the full bounded candidate set. */
final class PatternUploadRouting {

    private static final Pattern FORMATTING = Pattern.compile("(?i)\u00a7[0-9a-fk-or]");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");

    private PatternUploadRouting() {}

    static List<String> searchTerms(String category, String label) {
        if (!PatternUploadPreferences.validText(category)) return Collections.emptyList();
        // AE interfaces facing molecular assemblers use the adjacent block's name. "crafting" also matches GT
        // processing buffers, so never use it as a fallback for workbench patterns.
        if ("crafting".equals(category)) return Collections.singletonList("molecularassembler");
        List<String> terms = new ArrayList<>();
        if (PatternUploadPreferences.validText(label)) terms.add(label);
        int separator = Math
            .max(category.lastIndexOf(':'), Math.max(category.lastIndexOf('.'), category.lastIndexOf('/')));
        String fallback = category.substring(separator + 1);
        if (!normalize(fallback).isEmpty() && !terms.contains(fallback)) terms.add(fallback);
        return terms;
    }

    static <T> List<T> select(List<T> candidates, Function<T, String> name, List<String> preferences,
        List<String> searchTerms) {
        if (!preferences.isEmpty()) return matches(candidates, name, preferences);
        // Try a viewer's localized category title first, then its stable type ID. Never narrow an ambiguous hit.
        for (String term : searchTerms) {
            String needle = normalize(term);
            if (needle.isEmpty()) continue;
            List<T> result = new ArrayList<>();
            for (T candidate : candidates) {
                if (normalize(name.apply(candidate)).contains(needle)) result.add(candidate);
            }
            if (!result.isEmpty()) return result;
        }
        return Collections.emptyList();
    }

    static <T> List<T> filter(List<T> candidates, Function<T, String> name, String query) {
        String needle = normalize(query);
        List<T> result = new ArrayList<>();
        for (T candidate : candidates) {
            if (normalize(name.apply(candidate)).contains(needle)) result.add(candidate);
        }
        return result;
    }

    static <T> String displayedSearch(List<T> matches, Function<T, String> name, List<String> preferences,
        List<String> searchTerms) {
        if (matches.isEmpty()) return "";
        if (!preferences.isEmpty()) return preferences.size() == 1 ? preferences.get(0) : "";
        for (String term : searchTerms) {
            if (!normalize(term).isEmpty() && !filter(matches, name, term).isEmpty()) return term;
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? ""
            : SEPARATORS.matcher(
                FORMATTING.matcher(value)
                    .replaceAll(""))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    static <T> List<T> matches(List<T> candidates, Function<T, String> name, List<String> preferences) {
        List<T> matches = new ArrayList<>();
        for (T candidate : candidates) {
            String key = name.apply(candidate);
            if (!key.isEmpty() && preferences.contains(key)) matches.add(candidate);
        }
        return matches;
    }

    static boolean canAutoUpload(boolean enabled, boolean complete, int matches) {
        return enabled && complete && matches == 1;
    }
}
