/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

/** Stable, debuggable key for one fixed-template virtual pattern. */
public final class PatternKey {

    private final TemplateType templateType;
    private final TemplateInputKey input;
    private final TemplateOutputKey output;
    private final int hashCode;

    public PatternKey(TemplateType templateType, TemplateInputKey input, TemplateOutputKey output) {
        if (templateType == null || templateType == TemplateType.NONE || input == null || output == null) {
            throw new IllegalArgumentException("Auto-template pattern key is incomplete");
        }
        this.templateType = templateType;
        this.input = input;
        this.output = output;
        this.hashCode = Objects.hash(templateType, input, output);
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public TemplateInputKey getInput() {
        return input;
    }

    public TemplateOutputKey getOutput() {
        return output;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("TemplateType", templateType.getStableId());
        input.writeToNBT(tag, "Input");
        output.writeToNBT(tag, "Output");
        return tag;
    }

    public static PatternKey readFromNBT(NBTTagCompound tag) {
        if (tag == null) return null;
        TemplateType type = TemplateType.fromId(tag.getInteger("TemplateType"));
        if (type == TemplateType.NONE) return null;
        TemplateInputKey inputKey = TemplateInputKey.readFromNBT(tag, "Input");
        TemplateOutputKey outputKey = TemplateOutputKey.readFromNBT(tag, "Output");
        if (inputKey == null || outputKey == null) return null;
        try {
            return new PatternKey(type, inputKey, outputKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PatternKey)) return false;
        PatternKey key = (PatternKey) other;
        return templateType == key.templateType && input.equals(key.input) && output.equals(key.output);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return templateType.name() + "[" + input + " -> " + output + "]";
    }
}
