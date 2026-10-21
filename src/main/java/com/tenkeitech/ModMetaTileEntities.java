/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import net.minecraft.item.ItemStack;

import com.tenkeitech.autotemplate.TemplateType;
import com.tenkeitech.machine.MTECondensateAssemblyDebug;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputProxy;
import com.tenkeitech.machine.MTEHatchBenderTemplateInputME;
import com.tenkeitech.machine.MTEHatchWiremillTemplateInputME;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTESuperCraftingInputProxy;
import com.tenkeitech.machine.MTEWirelessNetworkBookmark;
import com.tenkeitech.machine.MTEWirelessNetworkCore;

import gregtech.api.GregTechAPI;

public final class ModMetaTileEntities {

    public static ItemStack superPatternBuffer;
    public static ItemStack superPatternBufferProxy;
    public static ItemStack wirelessNetworkCore;
    public static ItemStack wirelessNetworkBookmark;
    public static ItemStack autoTemplateInput;
    public static ItemStack autoTemplateInputProxy;
    public static ItemStack wiremillTemplateInput;
    public static ItemStack benderTemplateInput;
    public static ItemStack condensateAssemblyDebug;

    private ModMetaTileEntities() {}

    public static void init() {
        assertMetaTileIdsDistinct();

        assertMetaTileIdAvailable(Config.superPatternBufferMetaTileId, "ME Super Pattern Buffer");
        assertMetaTileIdAvailable(Config.superPatternBufferProxyMetaTileId, "ME Super Pattern Buffer Proxy");
        assertMetaTileIdAvailable(Config.wirelessNetworkCoreMetaTileId, "Wireless ME Network Core");
        assertMetaTileIdAvailable(Config.wirelessNetworkBookmarkMetaTileId, "Wireless ME Network Bookmark");
        assertMetaTileIdAvailable(Config.autoTemplateInputMetaTileId, "Auto Template ME Input Bus");
        assertMetaTileIdAvailable(Config.autoTemplateInputProxyMetaTileId, "Auto Template ME Input Bus Mirror");
        assertMetaTileIdAvailable(Config.wiremillTemplateInputMetaTileId, "Wiremill Template ME Input Bus");
        assertMetaTileIdAvailable(Config.benderTemplateInputMetaTileId, "Bender Template ME Input Bus");
        assertMetaTileIdAvailable(Config.condensateAssemblyDebugMetaTileId, "Condensate Assembly Debug");

        superPatternBuffer = new MTESuperCraftingInputME(
            Config.superPatternBufferMetaTileId,
            "hatch.tenkeitech.super_pattern_buffer",
            "ME Super Pattern Buffer",
            true).getStackForm(1L);
        superPatternBufferProxy = new MTESuperCraftingInputProxy(
            Config.superPatternBufferProxyMetaTileId,
            "hatch.tenkeitech.super_pattern_buffer_proxy",
            "ME Super Pattern Buffer Proxy").getStackForm(1L);
        wirelessNetworkCore = new MTEWirelessNetworkCore(
            Config.wirelessNetworkCoreMetaTileId,
            "hatch.tenkeitech.wireless_network_core",
            "Wireless ME Network Core").getStackForm(1L);
        wirelessNetworkBookmark = new MTEWirelessNetworkBookmark(
            Config.wirelessNetworkBookmarkMetaTileId,
            "hatch.tenkeitech.wireless_network_bookmark",
            "Wireless ME Network Bookmark").getStackForm(1L);
        autoTemplateInput = new MTEHatchAutoTemplateInputME(
            Config.autoTemplateInputMetaTileId,
            "hatch.tenkeitech.auto_template_input",
            "Auto Template ME Input Bus").getStackForm(1L);
        autoTemplateInputProxy = new MTEHatchAutoTemplateInputProxy(
            Config.autoTemplateInputProxyMetaTileId,
            "hatch.tenkeitech.auto_template_input_proxy",
            "Auto Template ME Input Bus Mirror").getStackForm(1L);
        wiremillTemplateInput = new MTEHatchWiremillTemplateInputME(
            Config.wiremillTemplateInputMetaTileId,
            "hatch.tenkeitech.wiremill_template_input",
            "Wiremill Template ME Input Bus").getStackForm(1L);
        benderTemplateInput = new MTEHatchBenderTemplateInputME(
            Config.benderTemplateInputMetaTileId,
            "hatch.tenkeitech.bender_template_input",
            "Bender Template ME Input Bus").getStackForm(1L);
        condensateAssemblyDebug = new MTECondensateAssemblyDebug(
            Config.condensateAssemblyDebugMetaTileId,
            "multimachine.tenkeitech.condensate_assembly_debug",
            "Condensate Assembly Debug").getStackForm(1L);
    }

    private static void assertMetaTileIdsDistinct() {
        int[] ids = { Config.superPatternBufferMetaTileId, Config.superPatternBufferProxyMetaTileId,
            Config.wirelessNetworkCoreMetaTileId, Config.wirelessNetworkBookmarkMetaTileId,
            Config.autoTemplateInputMetaTileId, Config.autoTemplateInputProxyMetaTileId,
            Config.wiremillTemplateInputMetaTileId, Config.benderTemplateInputMetaTileId,
            Config.condensateAssemblyDebugMetaTileId };
        for (int first = 0; first < ids.length; first++) {
            for (int second = first + 1; second < ids.length; second++) {
                if (ids[first] == ids[second]) {
                    throw new IllegalArgumentException("TenkeiTech MetaTileEntity IDs must be different");
                }
            }
        }
    }

    /** Returns one fixed-template item variant. The variant is still the same registered MTE item. */
    public static ItemStack autoTemplateInput(TemplateType type) {
        return MTEHatchAutoTemplateInputME.bindTemplateItem(autoTemplateInput, type);
    }

    private static void assertMetaTileIdAvailable(int id, String name) {
        if (GregTechAPI.METATILEENTITIES[id] != null) {
            throw new IllegalStateException(
                "Cannot register " + name
                    + ": MetaTileEntity ID "
                    + id
                    + " is already used by "
                    + GregTechAPI.METATILEENTITIES[id].getClass()
                        .getName());
        }
    }
}
