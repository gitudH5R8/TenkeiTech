/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.tenkeitech.ModMetaTileEntities;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class MachineItemTooltip {

    private static final String TOOLTIP_SEPARATOR = EnumChatFormatting.DARK_GRAY + "------------------------------";

    private enum MachineType {

        SUPER_PATTERN_BUFFER("gt.blockmachines.hatch.tenkeitech.super_pattern_buffer.name"),
        SUPER_PATTERN_BUFFER_PROXY("gt.blockmachines.hatch.tenkeitech.super_pattern_buffer_proxy.name"),
        WIRELESS_NETWORK_CORE("gt.blockmachines.hatch.tenkeitech.wireless_network_core.name"),
        WIRELESS_NETWORK_BOOKMARK("gt.blockmachines.hatch.tenkeitech.wireless_network_bookmark.name"),
        AUTO_TEMPLATE_INPUT("gt.blockmachines.hatch.tenkeitech.auto_template_input.name"),
        AUTO_TEMPLATE_INPUT_PROXY("gt.blockmachines.hatch.tenkeitech.auto_template_input_proxy.name"),
        WIREMILL_TEMPLATE_INPUT("gt.blockmachines.hatch.tenkeitech.wiremill_template_input.name"),
        BENDER_TEMPLATE_INPUT("gt.blockmachines.hatch.tenkeitech.bender_template_input.name"),
        CONDENSATE_ASSEMBLY_DEBUG("gt.blockmachines.multimachine.tenkeitech.condensate_assembly_debug.name");

        private final String translationKey;

        MachineType(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private MachineItemTooltip() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MachineItemTooltip());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        MachineType machineType = getMachineType(event.itemStack);
        if (machineType == null) return;

        event.toolTip.add(
            getMachineTypeLineIndex(event.toolTip),
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.tooltip.type",
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal(machineType.translationKey)));
        event.toolTip.add(TOOLTIP_SEPARATOR);
        event.toolTip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.tooltip.added_mod",
                EnumChatFormatting.AQUA + TenkeiTech.MOD_NAME));
    }

    private static MachineType getMachineType(ItemStack stack) {
        if (matches(stack, ModMetaTileEntities.superPatternBuffer)) return MachineType.SUPER_PATTERN_BUFFER;
        if (matches(stack, ModMetaTileEntities.superPatternBufferProxy)) return MachineType.SUPER_PATTERN_BUFFER_PROXY;
        if (matches(stack, ModMetaTileEntities.wirelessNetworkCore)) return MachineType.WIRELESS_NETWORK_CORE;
        if (matches(stack, ModMetaTileEntities.wirelessNetworkBookmark)) return MachineType.WIRELESS_NETWORK_BOOKMARK;
        if (matches(stack, ModMetaTileEntities.autoTemplateInput)) return MachineType.AUTO_TEMPLATE_INPUT;
        if (matches(stack, ModMetaTileEntities.autoTemplateInputProxy)) return MachineType.AUTO_TEMPLATE_INPUT_PROXY;
        if (matches(stack, ModMetaTileEntities.wiremillTemplateInput)) return MachineType.WIREMILL_TEMPLATE_INPUT;
        if (matches(stack, ModMetaTileEntities.benderTemplateInput)) return MachineType.BENDER_TEMPLATE_INPUT;
        if (matches(stack, ModMetaTileEntities.condensateAssemblyDebug)) return MachineType.CONDENSATE_ASSEMBLY_DEBUG;
        return null;
    }

    private static boolean matches(ItemStack stack, ItemStack registeredStack) {
        return stack != null && registeredStack != null && stack.isItemEqual(registeredStack);
    }

    private static int getMachineTypeLineIndex(List<String> tooltip) {
        int index = tooltip.isEmpty() ? 0 : 1;
        while (index < tooltip.size()) {
            String text = EnumChatFormatting.getTextWithoutFormattingCodes(tooltip.get(index));
            if (text == null || !text.trim()
                .startsWith("[")) {
                break;
            }
            index++;
        }
        return index;
    }
}
