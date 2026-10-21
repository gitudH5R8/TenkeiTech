/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.wildcardpattern;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import gregtech.api.util.GTUtility;

/** TenkeiTech-owned circuit data carried by a wildcard template and projected into each generated pattern. */
public final class WildcardPatternCircuitData {

    private static final String NBT_CIRCUIT = "tenkeitechWildcardCircuit";

    private WildcardPatternCircuitData() {}

    @Nullable
    public static ItemStack read(ItemStack pattern) {
        NBTTagCompound tag = pattern == null ? null : pattern.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_CIRCUIT, Constants.NBT.TAG_COMPOUND)) return null;
        ItemStack circuit = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(NBT_CIRCUIT));
        if (!GTUtility.isAnyIntegratedCircuit(circuit)) return null;
        circuit.stackSize = 1;
        return circuit;
    }

    public static ItemStack write(ItemStack pattern, @Nullable ItemStack circuit) {
        ItemStack rewritten = pattern.copy();
        NBTTagCompound tag = rewritten.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            rewritten.setTagCompound(tag);
        }
        if (circuit == null) {
            tag.removeTag(NBT_CIRCUIT);
            return rewritten;
        }

        ItemStack stored = circuit.copy();
        stored.stackSize = 1;
        NBTTagCompound circuitTag = new NBTTagCompound();
        stored.writeToNBT(circuitTag);
        tag.setTag(NBT_CIRCUIT, circuitTag);
        return rewritten;
    }

    public static void applyToGeneratedPattern(ItemStack generatedPattern) {
        ItemStack circuit = read(generatedPattern);
        NBTTagCompound patternTag = generatedPattern == null ? null : generatedPattern.getTagCompound();
        if (circuit == null || patternTag == null) return;

        IAEItemStack aeCircuit = AEApi.instance()
            .storage()
            .createItemStack(circuit);
        if (aeCircuit == null) return;
        aeCircuit.setStackSize(1);
        NBTTagCompound encodedCircuit = new NBTTagCompound();
        aeCircuit.writeToNBT(encodedCircuit);

        NBTTagList originalInputs = patternTag.getTagList("in", Constants.NBT.TAG_COMPOUND);
        NBTTagList rewrittenInputs = new NBTTagList();
        rewrittenInputs.appendTag(encodedCircuit);
        for (int index = 0; index < originalInputs.tagCount(); index++) {
            NBTTagCompound inputTag = originalInputs.getCompoundTagAt(index);
            ItemStack input = Platform.loadItemStackFromNBT(inputTag);
            if (!GTUtility.isAnyIntegratedCircuit(input)) rewrittenInputs.appendTag(inputTag.copy());
        }
        patternTag.setTag("in", rewrittenInputs);
    }
}
