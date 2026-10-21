/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import appeng.api.implementations.ICraftingPatternItem;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class PatternModeTooltip {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new PatternModeTooltip());
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null || !(event.itemStack.getItem() instanceof ICraftingPatternItem)) return;
        String id = PatternRecipeMapData.read(event.itemStack);
        if (id.isEmpty()) return;
        if (!PatternRecipeMapData.registered(id)) {
            event.toolTip
                .add(EnumChatFormatting.RED + StatCollector.translateToLocal("tenkeitech.patternmode.tooltip.invalid"));
            return;
        }
        String name = StatCollector.translateToLocal(id);
        event.toolTip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocalFormatted("tenkeitech.patternmode.tooltip.recipe_map", name));
        if (event.showAdvancedItemTooltips && !name.equals(id)) event.toolTip.add(EnumChatFormatting.DARK_GRAY + id);
    }
}
