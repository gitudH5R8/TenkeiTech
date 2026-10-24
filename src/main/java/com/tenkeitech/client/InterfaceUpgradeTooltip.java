/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.glodblock.github.loader.ItemAndBlockHolder;
import com.tenkeitech.ModItems;
import com.tenkeitech.resonating.ResonatingInterfaceHosts;

import appeng.api.AEApi;
import appeng.api.implementations.IUpgradeableHost;
import appeng.helpers.IInterfaceHost;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Read-only descriptions for the three upgrades restricted to the audited interface hosts. */
@SideOnly(Side.CLIENT)
public final class InterfaceUpgradeTooltip {

    private InterfaceUpgradeTooltip() {}

    public static void addDevices(List<String> lines) {
        var definitions = AEApi.instance()
            .definitions();
        addDevice(
            lines,
            definitions.blocks()
                .iface()
                .maybeStack(1)
                .orNull());
        addDevice(lines, ItemAndBlockHolder.INTERFACE.stack());
    }

    private static void addDevice(List<String> lines, ItemStack stack) {
        if (stack == null) return;
        lines.add(stack.getDisplayName() + " (1)");
    }

    public static String[] appendAccepted(IUpgradeableHost host, String[] original) {
        if (!(host instanceof IInterfaceHost interfaceHost) || !ResonatingInterfaceHosts.supported(interfaceHost))
            return original;
        List<String> lines = new ArrayList<>(Arrays.asList(original));
        for (var card : new ItemStack[] { new ItemStack(ModItems.AUTO_EXTRACTION_CARD),
            new ItemStack(ModItems.BLOCKING_FILTER_CARD_PRECISE),
            new ItemStack(ModItems.BLOCKING_FILTER_CARD_FUZZY) }) {
            lines.add("- " + card.getDisplayName());
        }
        return lines.toArray(new String[0]);
    }
}
