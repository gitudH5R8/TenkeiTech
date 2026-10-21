/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable catalog shared by every machine instance using one TemplateType. */
public final class TemplateRecipeCatalog {

    private final TemplateType templateType;
    private final List<VirtualPattern> patterns;
    private final Map<PatternKey, VirtualPattern> byKey;
    private final Map<TemplateInputKey, List<VirtualPattern>> byInput;
    private final Map<TemplateOutputKey, List<VirtualPattern>> byOutput;

    private TemplateRecipeCatalog(TemplateType templateType, List<VirtualPattern> source) {
        if (templateType == null || templateType == TemplateType.NONE) {
            throw new IllegalArgumentException("Catalog template type is invalid");
        }
        this.templateType = templateType;
        List<VirtualPattern> copied = new ArrayList<VirtualPattern>();
        Map<PatternKey, VirtualPattern> keyMap = new LinkedHashMap<PatternKey, VirtualPattern>();
        Map<TemplateInputKey, List<VirtualPattern>> inputMap = new LinkedHashMap<TemplateInputKey, List<VirtualPattern>>();
        Map<TemplateOutputKey, List<VirtualPattern>> outputMap = new LinkedHashMap<TemplateOutputKey, List<VirtualPattern>>();
        for (VirtualPattern pattern : source) {
            if (pattern == null || pattern.getKey()
                .getTemplateType() != templateType) {
                throw new IllegalArgumentException("Catalog contains a pattern for another template");
            }
            if (keyMap.put(pattern.getKey(), pattern) != null) {
                throw new IllegalArgumentException("Catalog contains a duplicate pattern identity");
            }
            inputMap.computeIfAbsent(
                pattern.getKey()
                    .getInput(),
                ignored -> new ArrayList<VirtualPattern>())
                .add(pattern);
            outputMap.computeIfAbsent(
                pattern.getKey()
                    .getOutput(),
                ignored -> new ArrayList<VirtualPattern>())
                .add(pattern);
            copied.add(pattern);
        }
        for (Map.Entry<TemplateInputKey, List<VirtualPattern>> entry : inputMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        for (Map.Entry<TemplateOutputKey, List<VirtualPattern>> entry : outputMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.patterns = Collections.unmodifiableList(copied);
        this.byKey = Collections.unmodifiableMap(keyMap);
        this.byInput = Collections.unmodifiableMap(inputMap);
        this.byOutput = Collections.unmodifiableMap(outputMap);
    }

    public static TemplateRecipeCatalog of(TemplateType templateType, List<VirtualPattern> patterns) {
        if (patterns == null) throw new IllegalArgumentException("Catalog patterns cannot be null");
        return new TemplateRecipeCatalog(templateType, patterns);
    }

    public static TemplateRecipeCatalog empty(TemplateType templateType) {
        return new TemplateRecipeCatalog(templateType, Collections.<VirtualPattern>emptyList());
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public List<VirtualPattern> getPatterns() {
        return patterns;
    }

    public Map<PatternKey, VirtualPattern> getByKey() {
        return byKey;
    }

    public Map<TemplateInputKey, List<VirtualPattern>> getByInput() {
        return byInput;
    }

    public Map<TemplateOutputKey, List<VirtualPattern>> getByOutput() {
        return byOutput;
    }

    public VirtualPattern find(PatternKey key) {
        return key == null ? null : byKey.get(key);
    }

    public VirtualPattern findByInput(TemplateInputKey key) {
        List<VirtualPattern> patterns = key == null ? null : byInput.get(key);
        return patterns == null || patterns.isEmpty() ? null : patterns.get(0);
    }

    public VirtualPattern findByOutput(TemplateOutputKey key) {
        List<VirtualPattern> patterns = key == null ? null : byOutput.get(key);
        return patterns == null || patterns.isEmpty() ? null : patterns.get(0);
    }
}
