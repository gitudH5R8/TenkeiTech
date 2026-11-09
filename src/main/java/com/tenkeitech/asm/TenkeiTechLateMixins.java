/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.asm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Registers mod targets after Forge discovers mods, without probing their classes during early bootstrap. */
@LateMixin
public final class TenkeiTechLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.tenkeitech.late.json";
    }

    @Nonnull
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        addIfLoaded(
            mixins,
            loadedMods,
            "neenergistics",
            "nee.PatternModePacketAccessor",
            "nee.PatternModeReceiveMixin");
        addIfLoaded(
            mixins,
            loadedMods,
            "EnderIO",
            "enderio.ToolUtilMixin",
            "enderio.UniversalWandTeleportStaffMixin",
            "enderio.UniversalWandLongDistanceTravelMixin");
        addIfLoaded(mixins, loadedMods, "Botania", "botania.UniversalWandGaiaAccess");
        addIfLoaded(mixins, loadedMods, "Thaumcraft", "thaumcraft.UniversalWandWarpMixin");
        addIfLoaded(
            mixins,
            loadedMods,
            "WarpTheory",
            "warptheory.UniversalWandWarpTheoryMixin",
            "warptheory.UniversalWandWarpTheoryCommandMixin");
        if (FMLLaunchHandler.side()
            .isClient()) {
            addIfLoaded(
                mixins,
                loadedMods,
                "neenergistics",
                "nee.PatternUploadTransferMixin",
                "nee.PatternModeTransferMixin");
            addIfLoaded(
                mixins,
                loadedMods,
                "betterquesting",
                "betterquesting.QuestSkipMenuMixin",
                "betterquesting.QuestSkipColorMixin");
            addIfLoaded(mixins, loadedMods, "duradisplay", "client.UniversalWandChargeOverlayMixin");
        }
        return mixins;
    }

    private static void addIfLoaded(List<String> mixins, Set<String> loadedMods, String modId, String... names) {
        if (loadedMods.contains(modId)) Collections.addAll(mixins, names);
    }
}
