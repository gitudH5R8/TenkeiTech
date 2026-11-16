/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.quantum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** CPU-owned lineage survives loading the CPU before its requesting provider. Contains no physical inventory. */
public final class QuantumOrderContext {

    private static final String NBT_KEY = "tenkeitechQuantumOrder";
    private static final ThreadLocal<List<ItemStack>> SUBMISSION = new ThreadLocal<>();
    private String linkId;
    private final List<ItemStack> ancestry = new ArrayList<>();

    public static List<ItemStack> enter(List<ItemStack> path) {
        List<ItemStack> previous = SUBMISSION.get();
        if (path == null) SUBMISSION.remove();
        else SUBMISSION.set(path);
        return previous;
    }

    public void submitted(CraftingCPUCluster cpu) {
        List<ItemStack> path = SUBMISSION.get();
        ICraftingLink link = cpu.getLastCraftingLink();
        if (link == null) return;
        ancestry.clear();
        linkId = null;
        if (path == null) return;
        linkId = link.getCraftingID();
        for (ItemStack item : path) ancestry.add(item.copy());
    }

    public List<ItemStack> ancestry(CraftingCPUCluster cpu) {
        ICraftingLink link = cpu.getLastCraftingLink();
        return link != null && link.getCraftingID()
            .equals(linkId) ? Collections.unmodifiableList(ancestry) : Collections.emptyList();
    }

    public void write(NBTTagCompound tag) {
        if (linkId == null || ancestry.isEmpty()) return;
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("schema", 1);
        saved.setString("link", linkId);
        NBTTagList path = new NBTTagList();
        for (ItemStack item : ancestry) path.appendTag(item.writeToNBT(new NBTTagCompound()));
        saved.setTag("ancestry", path);
        tag.setTag(NBT_KEY, saved);
    }

    public void read(NBTTagCompound tag) {
        ancestry.clear();
        NBTTagCompound saved = tag.getCompoundTag(NBT_KEY);
        linkId = null;
        if (saved.getInteger("schema") != 1) return;
        linkId = saved.getString("link");
        NBTTagList path = saved.getTagList("ancestry", 10);
        for (int index = 0; index < Math.min(path.tagCount(), 9); index++) {
            ItemStack item = ItemStack.loadItemStackFromNBT(path.getCompoundTagAt(index));
            if (item != null) {
                item.stackSize = 1;
                ancestry.add(item);
            }
        }
    }
}
