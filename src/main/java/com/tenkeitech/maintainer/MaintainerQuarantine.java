/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

/** Diagnostic credentials only: this ledger never creates, refunds or replays an unknown ME commit. */
public final class MaintainerQuarantine extends WorldSavedData {

    private NBTTagList records = new NBTTagList();

    public MaintainerQuarantine(String name) {
        super(name);
    }

    static void record(TileBetterLevelMaintainer tile, MaintainerEntry entry) {
        String name = "tenkeitech_maintainer_quarantine";
        MaintainerQuarantine data = (MaintainerQuarantine) tile.getWorldObj().mapStorage
            .loadData(MaintainerQuarantine.class, name);
        if (data == null) {
            data = new MaintainerQuarantine(name);
            tile.getWorldObj().mapStorage.setData(name, data);
        }
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("dimension", tile.getWorldObj().provider.dimensionId);
        record.setInteger("x", tile.xCoord);
        record.setInteger("y", tile.yCoord);
        record.setInteger("z", tile.zCoord);
        record.setLong(
            "time",
            tile.getWorldObj()
                .getTotalWorldTime());
        record.setTag("entry", entry.save());
        data.records.appendTag(record);
        data.markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        records = tag.getTagList("records", 10);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("records", records);
    }
}
