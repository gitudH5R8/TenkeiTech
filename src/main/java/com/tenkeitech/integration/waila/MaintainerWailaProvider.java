/*
 * SPDX-FileCopyrightText: 2026 Aedial
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 * Adapted from AE2-PowerTools MaintainerProbeHelper and PowerToolsWailaModule,
 * be56992b0ab8e64cf254f603dad22cca2931aa6b, for the Minecraft 1.7.10 Waila API.
 */
package com.tenkeitech.integration.waila;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.tenkeitech.maintainer.TileBetterLevelMaintainer;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** Loaded through IMC only when Waila is present; sends summary values, never machine or link NBT. */
public final class MaintainerWailaProvider implements IWailaDataProvider {

    private static final String DATA = "tenkeitechMaintainerProbe";

    public static void callbackRegister(IWailaRegistrar registrar) {
        MaintainerWailaProvider provider = new MaintainerWailaProvider();
        registrar.registerBodyProvider(provider, TileBetterLevelMaintainer.class);
        registrar.registerNBTProvider(provider, TileBetterLevelMaintainer.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (!(accessor.getTileEntity() instanceof TileBetterLevelMaintainer) || !accessor.getNBTData()
            .hasKey(DATA)) return tip;
        NBTTagCompound tag = accessor.getNBTData()
            .getCompoundTag(DATA);
        tip.add(translate("cpus", tag.getInteger("busyCpus"), tag.getInteger("cpus")));
        tip.add(
            translate(
                "recipes",
                tag.getInteger("running"),
                tag.getInteger("enabled"),
                tag.getInteger("errors"),
                tag.getInteger("blocked")));
        int network = tag.getInteger("network");
        tip.add(
            translate(
                "network." + (network == 2 ? "stable" : network == 1 ? "waiting" : "offline"),
                elapsed(tag.getLong("elapsed"))));
        tip.add(
            tag.getBoolean("sample")
                ? translate(
                    "timing",
                    duration(tag.getLong("last")),
                    duration(tag.getLong("average")),
                    duration(tag.getLong("max")))
                : translate("timing_none"));
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (tile instanceof TileBetterLevelMaintainer maintainer) tag.setTag(DATA, maintainer.probeSnapshot());
        return tag;
    }

    private static String translate(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("tenkeitech.maintainer.waila." + key, values);
    }

    private static String duration(long nanos) {
        return String.format(Locale.ROOT, "%.3f ms", Math.max(0, nanos) / 1000000.0);
    }

    private static String elapsed(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return (seconds >= 3600 ? seconds / 3600 + "h " : "") + (seconds >= 60 ? seconds % 3600 / 60 + "m " : "")
            + seconds % 60
            + "s";
    }
}
