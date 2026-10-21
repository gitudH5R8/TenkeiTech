/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.tenkeitech.Config;
import com.tenkeitech.machine.MTEWirelessNetworkBookmark;
import com.tenkeitech.machine.MTEWirelessNetworkCore;
import com.tenkeitech.machine.MTEWirelessNetworkCore.DiagnosticStatistics;
import com.tenkeitech.wireless.WirelessAeRuntimeRegistry;
import com.tenkeitech.wireless.WirelessAeWorldData;
import com.tenkeitech.wireless.WirelessAeWorldData.MembershipRecord;
import com.tenkeitech.wireless.WirelessAeWorldData.NetworkRecord;

import appeng.api.networking.IGridHost;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import mcp.mobius.waila.api.IWailaDataAccessor;

/** Server-authored Waila projection for wireless GT ME hatch membership. */
public final class WirelessMeHatchWailaSupport {

    private static final String NBT_NETWORK_NAME = "tenkeitechWirelessNetworkName";
    private static final String NBT_CONNECTED = "tenkeitechWirelessNetworkConnected";
    private static final String NBT_DIAGNOSTICS = "tenkeitechWirelessDiagnostics";
    private static final String NBT_ACTIVE_BINDINGS = "tenkeitechWirelessActiveBindings";
    private static final String NBT_OFFLINE_BINDINGS = "tenkeitechWirelessOfflineBindings";
    private static final String NBT_EXEMPT_NODES = "tenkeitechWirelessExemptNodes";
    private static final String NBT_CONNECTION_ATTEMPTS = "tenkeitechWirelessConnectionAttempts";
    private static final String NBT_CONNECTION_FAILURES = "tenkeitechWirelessConnectionFailures";
    private static final String NBT_RESOLVER_FAILURES = "tenkeitechWirelessResolverFailures";
    private static final String NBT_LAST_CONNECTION_STATUS = "tenkeitechWirelessLastConnectionStatus";
    private static final String NBT_LAST_CONNECTION_MICROS = "tenkeitechWirelessLastConnectionMicros";
    private static final String EMPTY_NAME = "-";

    private WirelessMeHatchWailaSupport() {}

    public static void appendServerData(MetaTileEntity machine, EntityPlayerMP player, NBTTagCompound tag, World world,
        int x, int y, int z) {
        if (!Config.enableWirelessAe || machine == null || tag == null || world == null || world.isRemote) return;

        if (machine instanceof MTEWirelessNetworkCore core) {
            tag.setString(NBT_NETWORK_NAME, displayName(core.getNetworkName()));
            if (Config.debugWirelessAe) appendCoreDiagnostics(core, tag);
            return;
        }

        WirelessAeWorldData data = WirelessAeWorldData.getIfPresent(world);
        if (machine instanceof MTEWirelessNetworkBookmark) {
            NetworkRecord favorite = data == null || player == null ? null
                : data.getFavoriteNetwork(player.getUniqueID());
            tag.setString(NBT_NETWORK_NAME, favorite == null ? EMPTY_NAME : favorite.name());
            return;
        }

        if (!isWirelessMemberHatch(machine)) return;
        if (data == null) return;
        MembershipRecord membership = data.getMembershipAtBlock(world.provider.dimensionId, x, y, z);
        if (membership == null) return;
        NetworkRecord network = data.getNetwork(membership.frequency());
        if (network == null) return;

        MTEWirelessNetworkCore core = WirelessAeRuntimeRegistry.findLoadedCore(world, network);
        tag.setString(NBT_NETWORK_NAME, network.name());
        tag.setBoolean(NBT_CONNECTED, core != null && core.isTargetConnected(membership.target()));
    }

    public static void appendBody(MetaTileEntity machine, List<String> currentTip, IWailaDataAccessor accessor) {
        if (!Config.enableWirelessAe || machine == null || currentTip == null || accessor == null) return;
        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null || !tag.hasKey(NBT_NETWORK_NAME)) return;

        String networkName = displayName(tag.getString(NBT_NETWORK_NAME));
        if (machine instanceof MTEWirelessNetworkCore) {
            currentTip.add(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocalFormatted("tenkeitech.waila.wireless_core.network", networkName)
                    + EnumChatFormatting.RESET);
            appendCoreDiagnosticBody(currentTip, tag);
            return;
        }
        if (machine instanceof MTEWirelessNetworkBookmark) {
            currentTip.add(
                EnumChatFormatting.GOLD
                    + StatCollector
                        .translateToLocalFormatted("tenkeitech.waila.wireless_bookmark.favorite", networkName)
                    + EnumChatFormatting.RESET);
            return;
        }
        if (!isWirelessMemberHatch(machine)) return;

        boolean connected = tag.getBoolean(NBT_CONNECTED);
        String status = StatCollector.translateToLocal(
            connected ? "tenkeitech.waila.wireless_network.connected"
                : "tenkeitech.waila.wireless_network.disconnected");
        String line = StatCollector.translateToLocalFormatted("tenkeitech.waila.wireless_network", networkName, status);
        currentTip
            .add((connected ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + line + EnumChatFormatting.RESET);
    }

    private static boolean isWirelessMemberHatch(Object machine) {
        return Config.enableWirelessAe && machine instanceof MTEHatch
            && machine instanceof IGridHost
            && !(machine instanceof MTEWirelessNetworkCore);
    }

    private static String displayName(String name) {
        return name == null || name.isEmpty() ? EMPTY_NAME : name;
    }

    private static void appendCoreDiagnostics(MTEWirelessNetworkCore core, NBTTagCompound tag) {
        DiagnosticStatistics diagnostics = core.getDiagnosticStatistics();
        tag.setBoolean(NBT_DIAGNOSTICS, true);
        tag.setInteger(NBT_ACTIVE_BINDINGS, diagnostics.activeBindings());
        tag.setInteger(NBT_OFFLINE_BINDINGS, diagnostics.offlineBindings());
        tag.setInteger(NBT_EXEMPT_NODES, diagnostics.exemptNodes());
        tag.setLong(NBT_CONNECTION_ATTEMPTS, diagnostics.connectionAttempts());
        tag.setLong(NBT_CONNECTION_FAILURES, diagnostics.connectionFailures());
        tag.setLong(NBT_RESOLVER_FAILURES, diagnostics.resolverFailures());
        tag.setString(NBT_LAST_CONNECTION_STATUS, diagnostics.lastConnectionStatus());
        tag.setLong(NBT_LAST_CONNECTION_MICROS, diagnostics.lastConnectionMicros());
    }

    private static void appendCoreDiagnosticBody(List<String> currentTip, NBTTagCompound tag) {
        if (!tag.getBoolean(NBT_DIAGNOSTICS)) return;
        currentTip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tenkeitech.wireless.diagnostics.connections",
                tag.getInteger(NBT_ACTIVE_BINDINGS),
                tag.getInteger(NBT_OFFLINE_BINDINGS),
                tag.getInteger(NBT_EXEMPT_NODES)) + EnumChatFormatting.RESET);
        currentTip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tenkeitech.wireless.diagnostics.attempts",
                tag.getLong(NBT_CONNECTION_ATTEMPTS),
                tag.getLong(NBT_CONNECTION_FAILURES),
                tag.getLong(NBT_RESOLVER_FAILURES)) + EnumChatFormatting.RESET);
        if (tag.getLong(NBT_CONNECTION_ATTEMPTS) > 0) {
            currentTip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "tenkeitech.wireless.diagnostics.last",
                    tag.getString(NBT_LAST_CONNECTION_STATUS),
                    tag.getLong(NBT_LAST_CONNECTION_MICROS)) + EnumChatFormatting.RESET);
        }
    }
}
