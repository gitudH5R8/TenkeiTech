/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.ae;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import gregtech.api.util.GTUtility;

public final class TransformedPatternRecovery {

    private static final String KEY = "tenkeitechTransformedTasks";

    private static final class Pending {

        private final ICraftingPatternDetails original;
        private final TransformedPatternDetails transformed;
        private final CraftingCPUCluster.TaskProgress progress;

        private Pending(ICraftingPatternDetails original, CraftingCPUCluster.TaskProgress progress) {
            this.original = original;
            this.transformed = new TransformedPatternDetails(original);
            this.progress = progress;
        }
    }

    public void write(Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks, NBTTagCompound tag) {
        NBTTagList saved = new NBTTagList();
        for (ICraftingPatternDetails details : tasks.keySet()) {
            if (!(details instanceof TransformedPatternDetails)) continue;
            ItemStack pattern = details.getPattern();
            if (pattern != null) saved.appendTag(pattern.writeToNBT(new NBTTagCompound()));
        }
        if (saved.tagCount() == 0) tag.removeTag(KEY);
        else tag.setTag(KEY, saved);
    }

    public void read(Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks, NBTTagCompound tag) {
        Set<GTUtility.ItemId> saved = new HashSet<>();
        NBTTagList list = tag.getTagList(KEY, 10);
        for (int index = 0; index < Math.min(list.tagCount(), tasks.size()); index++) {
            ItemStack pattern = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(index));
            if (pattern != null) saved.add(GTUtility.ItemId.create(pattern));
        }
        List<Pending> restore = new ArrayList<>();
        for (Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : tasks.entrySet()) {
            ICraftingPatternDetails details = entry.getKey();
            if (details instanceof TransformedPatternDetails) continue;
            ItemStack pattern = details.getPattern();
            if (pattern != null && saved.contains(GTUtility.ItemId.create(pattern)))
                restore.add(new Pending(details, entry.getValue()));
        }
        for (Pending candidate : restore) replace(tasks, candidate);
    }

    private static boolean replace(Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks,
        Pending candidate) {
        if (tasks.get(candidate.original) != candidate.progress) return false;
        tasks.remove(candidate.original);
        if (tasks.containsKey(candidate.transformed)) {
            tasks.put(candidate.original, candidate.progress);
            return false;
        }
        tasks.put(candidate.transformed, candidate.progress);
        return true;
    }
}
