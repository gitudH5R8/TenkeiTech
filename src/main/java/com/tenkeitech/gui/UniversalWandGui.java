/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.util.Arrays;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.tenkeitech.Config;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandFeature;

/** Item settings always refer to the exact stack that opened this screen. */
public final class UniversalWandGui {

    private UniversalWandGui() {}

    public static ModularPanel build(GuiData data, PanelSyncManager syncManager) {
        EntityPlayer player = data.getPlayer();
        ItemStack expected = data instanceof com.cleanroommc.modularui.factory.PlayerInventoryGuiData inventoryData
            ? inventoryData.getUsedItemStack()
            : player.getHeldItem();
        BooleanSyncValue fortune = new BooleanSyncValue(
            () -> ItemUniversalWand.getEnchantMode(expected) == ItemUniversalWand.EnchantMode.FORTUNE,
            value -> ItemUniversalWand.setEnchantMode(
                player,
                expected,
                value ? ItemUniversalWand.EnchantMode.FORTUNE : ItemUniversalWand.EnchantMode.SILK)).allowC2S();
        BooleanSyncValue protection = new BooleanSyncValue(
            () -> ItemUniversalWand.isProtectionMode(expected),
            value -> ItemUniversalWand.setProtectionMode(player, expected, value)).allowC2S();
        BooleanSyncValue bedrock = new BooleanSyncValue(
            () -> ItemUniversalWand.isForceBedrock(expected),
            value -> ItemUniversalWand.setForceBedrock(player, expected, value)).allowC2S();
        BooleanSyncValue enhanced = new BooleanSyncValue(
            () -> ItemUniversalWand.isEnhancedChain(expected),
            value -> ItemUniversalWand.setEnhancedChain(player, expected, value)).allowC2S();
        BooleanSyncValue small = new BooleanSyncValue(
            () -> ItemUniversalWand.isGtSmallChainEnabled(expected),
            value -> ItemUniversalWand.setGtSmallChainEnabled(player, expected, value)).allowC2S();
        IntSyncValue cooldown = new IntSyncValue(
            () -> ItemUniversalWand.getBreakCooldown(expected),
            value -> ItemUniversalWand.setBreakCooldown(player, expected, value)).allowC2S();
        IntSyncValue radius = new IntSyncValue(
            () -> ItemUniversalWand.getEnhancedChainRadius(expected),
            value -> ItemUniversalWand.setEnhancedChainRadius(player, expected, value)).allowC2S();
        IntSyncValue max = new IntSyncValue(
            () -> ItemUniversalWand.getMaxMiningCount(expected),
            value -> ItemUniversalWand.setMaxMiningCount(player, expected, value)).allowC2S();
        IntSyncValue toolMode = new IntSyncValue(
            () -> ItemUniversalWand.getGtToolMode(expected)
                .ordinal());
        syncManager.syncValue("wand_tool_mode", 0, toolMode);

        BooleanSyncValue ae = new BooleanSyncValue(
            () -> ItemUniversalWand.isAePriority(expected),
            value -> ItemUniversalWand.setAePriority(player, expected, value)).allowC2S();
        BooleanSyncValue inventory = new BooleanSyncValue(
            () -> ItemUniversalWand.isInventoryCollection(expected),
            value -> ItemUniversalWand.setInventoryCollection(player, expected, value)).allowC2S();
        BooleanSyncValue shears = new BooleanSyncValue(
            () -> ItemUniversalWand.isShearsEnabled(expected),
            value -> ItemUniversalWand.setShearsEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue hoe = new BooleanSyncValue(
            () -> ItemUniversalWand.isHoeEnabled(expected),
            value -> ItemUniversalWand.setHoeEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue crops = new BooleanSyncValue(
            () -> ItemUniversalWand.isCropHarvestEnabled(expected),
            value -> ItemUniversalWand.setCropHarvestEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue cropSticks = new BooleanSyncValue(
            () -> ItemUniversalWand.isCropSticksEnabled(expected),
            value -> ItemUniversalWand.setCropSticksEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue building = new BooleanSyncValue(
            () -> ItemUniversalWand.isBuildingEnabled(expected),
            value -> ItemUniversalWand.setBuildingEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue forceKill = new BooleanSyncValue(
            () -> ItemUniversalWand.isForceKillEnabled(expected),
            value -> ItemUniversalWand.setForceKillEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue beheading = new BooleanSyncValue(
            () -> ItemUniversalWand.isBeheadingEnabled(expected),
            value -> ItemUniversalWand.setBeheadingEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue spawnEgg = new BooleanSyncValue(
            () -> ItemUniversalWand.isSpawnEggEnabled(expected),
            value -> ItemUniversalWand.setSpawnEggEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue teleport = new BooleanSyncValue(
            () -> ItemUniversalWand.isTeleportEnabled(expected),
            value -> ItemUniversalWand.setTeleportEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue playerProtection = new BooleanSyncValue(
            () -> ItemUniversalWand.isPlayerProtectionEnabled(expected),
            value -> ItemUniversalWand.setPlayerProtectionEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue noAggro = new BooleanSyncValue(
            () -> ItemUniversalWand.isNoAggroEnabled(expected),
            value -> ItemUniversalWand.setNoAggroEnabled(player, expected, value)).allowC2S();
        IntSyncValue aoeSize = new IntSyncValue(
            () -> (ItemUniversalWand.getAoeSize(expected) - 1) / 2,
            value -> ItemUniversalWand.setAoeSize(
                player,
                expected,
                Math.max(0, Math.min((ItemUniversalWand.MAX_AOE_SIZE - 1) / 2, value)) * 2 + 1)).allowC2S();
        BooleanSyncValue acceleration = new BooleanSyncValue(
            () -> ItemUniversalWand.isTimeAccelerationEnabled(expected),
            value -> ItemUniversalWand.setTimeAccelerationEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue buildingAe = new BooleanSyncValue(
            () -> ItemUniversalWand.isBuildingAeEnabled(expected),
            value -> ItemUniversalWand.setBuildingAeEnabled(player, expected, value)).allowC2S();
        IntSyncValue buildingCore = new IntSyncValue(
            () -> ItemUniversalWand.getBuildingCore(expected)
                .ordinal());
        syncManager.syncValue("wand_fortune", 0, fortune);
        syncManager.syncValue("wand_protection", 0, protection);
        syncManager.syncValue("wand_force_bedrock", 0, bedrock);
        syncManager.syncValue("wand_enhanced_chain", 0, enhanced);
        syncManager.syncValue("wand_gt_small_chain", 0, small);
        syncManager.syncValue("wand_break_cooldown", 0, cooldown);
        syncManager.syncValue("wand_enhanced_radius", 0, radius);
        syncManager.syncValue("wand_max_mining_count", 0, max);
        syncManager.syncValue("wand_collect_items", 0, inventory);
        syncManager.syncValue("wand_ae_priority", 0, ae);
        syncManager.syncValue("wand_shears", 0, shears);
        syncManager.syncValue("wand_hoe", 0, hoe);
        syncManager.syncValue("wand_crop_harvest", 0, crops);
        syncManager.syncValue("wand_crop_sticks", 0, cropSticks);
        syncManager.syncValue("wand_building", 0, building);
        syncManager.syncValue("wand_building_core", 0, buildingCore);
        syncManager.syncValue("wand_building_ae", 0, buildingAe);
        syncManager.syncValue("wand_force_kill", 0, forceKill);
        syncManager.syncValue("wand_beheading", 0, beheading);
        syncManager.syncValue("wand_spawn_egg", 0, spawnEgg);
        syncManager.syncValue("wand_teleport", 0, teleport);
        syncManager.syncValue("wand_player_protection", 0, playerProtection);
        syncManager.syncValue("wand_no_aggro", 0, noAggro);
        syncManager.syncValue("wand_time_acceleration", 0, acceleration);
        StringSyncValue accelerationRates = new StringSyncValue(
            () -> Arrays.stream(Config.universalWandAccelerationRates)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(",")));
        syncManager.syncValue("wand_acceleration_rates", 0, accelerationRates);
        IPanelHandler accelerationSettings = syncManager.syncedPanel(
            "tenkeitech_wand_acceleration_config",
            true,
            (manager, handler) -> accelerationPanel(player, expected, manager, accelerationRates.getStringValue()));
        IPanelHandler tenkeiSettings = syncManager.syncedPanel(
            "tenkeitech_wand_tenkei_config",
            true,
            (manager, handler) -> tenkeiPanel(player, expected, manager));
        syncManager.syncValue("wand_aoe_size", 0, aoeSize);
        ParentWidget<?> tools = section("Tools", 8, 30, 234, 84)
            .child(enchantButton(player, expected, fortune, false).pos(6, 22))
            .child(enchantButton(player, expected, fortune, true).pos(119, 22))
            .child(
                stateButton("protection", protection).pos(6, 42)
                    .size(222, 16))
            .child(numberRow("cooldown", cooldown, 0, 20, 62, false));
        ParentWidget<?> combat = section("Combat", 8, 120, 234, 84)
            .child(stateButton("force_kill", forceKill).pos(6, 22))
            .child(stateButton("beheading", beheading).pos(6, 42))
            .child(stateButton("spawn_egg", spawnEgg).pos(119, 42))
            .child(numberRow("aoe_size", aoeSize, 0, 13, 62, true));
        ParentWidget<?> auxiliary = section("Auxiliary", 8, 210, 234, 84)
            .child(stateButton("player_protection", playerProtection).pos(6, 22))
            .child(stateButton("no_aggro", noAggro).pos(119, 22))
            .child(stateButton("teleport", teleport).pos(6, 42))
            .child(
                new ButtonWidget<>()
                    .background(
                        new DynamicDrawable(
                            () -> !controlAllowed("time_acceleration") ? disabledButton()
                                : acceleration.getBoolValue() ? activeButton() : flatButton()))
                    .hoverBackground(
                        new DynamicDrawable(
                            () -> !controlAllowed("time_acceleration") ? disabledButton()
                                : acceleration.getBoolValue() ? activeButton() : flatButton()))
                    .overlay(
                        IKey.dynamic(
                            () -> (controlAllowed("time_acceleration") ? "§f" : "§7")
                                + StatCollector.translateToLocal("tenkeitech.gui.universal_wand.time_acceleration"))
                            .scale(1.0F)
                            .color(0xFFFFFFFF))
                    .onMousePressed(button -> {
                        if (button != 0 || !UniversalWandFeature.ACCELERATION.allowed()) return false;
                        accelerationSettings.openPanel();
                        return true;
                    })
                    .tooltipDynamic(tooltip -> featureTooltip(tooltip, "time_acceleration"))
                    .pos(6, 62)
                    .size(222, 16));
        ParentWidget<?> mining = section("Mining", 248, 30, 234, 204)
            .child(stateButton("enhanced_chain", enhanced).pos(6, 22))
            .child(stateButton("gt_small_chain", small).pos(119, 22))
            .child(stateButton("crop_harvest", crops).pos(6, 42))
            .child(stateButton("crop_sticks", cropSticks).pos(119, 42))
            .child(numberRow("radius", radius, 1, 128, 62, false))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.max_count")
                        .scale(1.0F)
                        .color(0xFF414156)).pos(6, 85)
                            .size(105, 16)
                            .maxWidth(105))
            .child(
                new TextFieldWidget().formatAsInteger(true)
                    .setEnabledIf(widget -> UniversalWandFeature.CHAIN.allowed())
                    .numbersInt(1, Integer.MAX_VALUE)
                    .setMaxLength(10)
                    .value(max)
                    .pos(119, 82)
                    .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.universal_wand.max_count.tooltip"))
                    .size(109, 16))
            .child(
                new ButtonWidget<>().background(disabledButton())
                    .hoverBackground(disabledButton())
                    .setEnabledIf(widget -> !UniversalWandFeature.CHAIN.allowed())
                    .overlay(IKey.dynamic(() -> "§7" + max.getIntValue()))
                    .onMousePressed(button -> true)
                    .tooltipDynamic(tooltip -> featureTooltip(tooltip, "max_count"))
                    .pos(119, 82)
                    .size(109, 16))
            .child(
                stateButton("force_bedrock", bedrock).pos(6, 102)
                    .size(222, 16))
            .child(stateButton("collect_items", inventory).pos(6, 122))
            .child(stateButton("ae_priority", ae).pos(119, 122))
            .child(stateButton("building", building).pos(6, 142))
            .child(stateButton("building_ae", buildingAe).pos(119, 142))
            .child(
                coreButton(player, expected, buildingCore, ItemUniversalWand.BuildingCore.NORMAL, "building_normal")
                    .background(flatButton())
                    .pos(6, 162)
                    .size(72, 16))
            .child(
                coreButton(player, expected, buildingCore, ItemUniversalWand.BuildingCore.ANGEL, "building_angel")
                    .background(flatButton())
                    .pos(81, 162)
                    .size(72, 16))
            .child(
                coreButton(
                    player,
                    expected,
                    buildingCore,
                    ItemUniversalWand.BuildingCore.DESTRUCTION,
                    "building_destruction").background(flatButton())
                        .pos(156, 162)
                        .size(72, 16))
            .child(
                toolModeButton(player, expected, toolMode, ItemUniversalWand.GtToolMode.WRENCH, "gt_wrench")
                    .background(flatButton())
                    .pos(6, 182)
                    .size(72, 16))
            .child(
                toolModeButton(player, expected, toolMode, ItemUniversalWand.GtToolMode.WIRE_CUTTER, "gt_cutter")
                    .background(flatButton())
                    .pos(81, 182)
                    .size(72, 16))
            .child(
                toolModeButton(player, expected, toolMode, ItemUniversalWand.GtToolMode.OFF, "gt_off")
                    .background(flatButton())
                    .pos(156, 182)
                    .size(72, 16));
        ParentWidget<?> vanilla = section("Vanilla", 248, 240, 234, 44).child(stateButton("shears", shears).pos(6, 22))
            .child(stateButton("hoe", hoe).pos(119, 22));
        ParentWidget<?> tenkei = section("TenkeiTech", 248, 290, 234, 44).child(
            new ButtonWidget<>().background(flatButton())
                .overlay(
                    IKey.lang("tenkeitech.gui.universal_wand.tenkei_settings")
                        .color(0xFFFFFFFF))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    tenkeiSettings.openPanel();
                    return true;
                })
                .pos(6, 22)
                .size(222, 16));
        return ModularPanel.defaultPanel("tenkeitech_universal_wand", 490, 342)
            .background(historyListBackground())
            .child(
                new ParentWidget<>().background(new Rectangle().color(0xFFB0B2C5))
                    .pos(4, 4)
                    .size(482, 18))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.title")
                        .scale(1.0F)
                        .color(0xFF414156)).pos(8, 7)
                            .size(330, 16))
            .child(tools)
            .child(combat)
            .child(auxiliary)
            .child(mining)
            .child(vanilla)
            .child(tenkei);
    }

    private static com.cleanroommc.modularui.api.drawable.IDrawable flatButton() {
        return new DrawableStack(GuiTextures.MC_BUTTON, new Rectangle().color(0x889D9FB3));
    }

    private static com.cleanroommc.modularui.api.drawable.IDrawable activeButton() {
        return new DrawableStack(GuiTextures.MC_BUTTON, new Rectangle().color(0x668ED4FF));
    }

    private static com.cleanroommc.modularui.api.drawable.IDrawable disabledButton() {
        return new DrawableStack(GuiTextures.MC_BUTTON, new Rectangle().color(0xBB444444));
    }

    private static ParentWidget<?> section(String title, int x, int y, int width, int height) {
        return new ParentWidget<>().pos(x, y)
            .size(width, height)
            .background(historyListBackground())
            .child(
                new TextWidget<>(
                    IKey.str(title)
                        .scale(1.0F)
                        .color(0xFF414156)).pos(6, 5)
                            .size(width - 12, 14));
    }

    private static ToggleButton stateButton(String key, BooleanSyncValue value) {
        return new ToggleButton() {

            @Override
            public Interactable.Result onMousePressed(int button) {
                return controlAllowed(key) ? super.onMousePressed(button) : Interactable.Result.STOP;
            }

            @Override
            public com.cleanroommc.modularui.api.drawable.IDrawable getCurrentBackground(
                com.cleanroommc.modularui.theme.WidgetThemeEntry<?> theme) {
                return controlAllowed(key) ? super.getCurrentBackground(theme) : disabledButton();
            }
        }.value(value)
            .background(false, flatButton())
            .background(true, activeButton())
            .overlay(true, stateLabel(key, true))
            .overlay(false, stateLabel(key, false))
            .tooltipDynamic(tooltip -> featureTooltip(tooltip, key))
            .size(109, 16);
    }

    private static ButtonWidget<?> enchantButton(EntityPlayer player, ItemStack expected, BooleanSyncValue fortune,
        boolean selectFortune) {
        String key = selectFortune ? "fortune" : "silk";
        return actionButton(
            key,
            () -> ItemUniversalWand.setEnchantMode(
                player,
                expected,
                selectFortune ? ItemUniversalWand.EnchantMode.FORTUNE : ItemUniversalWand.EnchantMode.SILK))
                    .background(flatButton())
                    .overlay(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key)
                                + (fortune.getBoolValue() == selectFortune
                                    ? StatCollector.translateToLocal("tenkeitech.gui.universal_wand.current")
                                    : ""))
                            .scale(1.0F))
                    .size(109, 16);
    }

    private static ParentWidget<?> numberRow(String key, IntSyncValue value, int min, int max, int y, boolean area) {
        ParentWidget<?> row = new ParentWidget<>().pos(6, y)
            .size(222, 16)
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand." + key)
                        .scale(1.0F)
                        .color(0xFF414156)).pos(0, 3)
                            .size(105, 16)
                            .maxWidth(105))
            .child(new SliderWidget() {

                @Override
                public Interactable.Result onMousePressed(int button) {
                    return controlAllowed(key) ? super.onMousePressed(button) : Interactable.Result.STOP;
                }

                @Override
                public void onMouseDrag(int button, long time) {
                    if (controlAllowed(key)) super.onMouseDrag(button, time);
                }

                @Override
                public com.cleanroommc.modularui.api.drawable.IDrawable getCurrentBackground(
                    com.cleanroommc.modularui.theme.WidgetThemeEntry<?> theme) {
                    return controlAllowed(key) ? super.getCurrentBackground(theme) : disabledButton();
                }
            }.background(flatButton())
                .tooltipDynamic(tooltip -> featureTooltip(tooltip, key))
                .bounds(min, max)
                .stopper(1)
                .value(value)
                .pos(113, 0)
                .size(109, 16));
        IKey number = IKey.dynamic(
            () -> area ? (value.getIntValue() * 2 + 1) + "x" + (value.getIntValue() * 2 + 1)
                : Integer.toString(value.getIntValue()));
        // Draw the outline first so the value remains legible over the slider ticks and thumb.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                row.child(
                    number.color(0xFF000000)
                        .shadow(false)
                        .asWidget()
                        .pos(113 + dx, dy)
                        .size(109, 16)
                        .textAlign(Alignment.Center));
            }
        }
        return row.child(
            number.color(0xFFFFFFFF)
                .shadow(false)
                .asWidget()
                .pos(113, 0)
                .size(109, 16)
                .textAlign(Alignment.Center));
    }

    private static ModularPanel tenkeiPanel(EntityPlayer player, ItemStack expected, PanelSyncManager manager) {
        BooleanSyncValue circuits = new BooleanSyncValue(
            () -> ItemUniversalWand.isCircuitPreserverEnabled(expected),
            value -> ItemUniversalWand.setCircuitPreserverEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue catalysts = new BooleanSyncValue(
            () -> ItemUniversalWand.isCatalystRecorderEnabled(expected),
            value -> ItemUniversalWand.setCatalystRecorderEnabled(player, expected, value)).allowC2S();
        BooleanSyncValue condensate = new BooleanSyncValue(
            () -> ItemUniversalWand.isCondensateReplacementEnabled(expected),
            value -> ItemUniversalWand.setCondensateReplacementEnabled(player, expected, value)).allowC2S();
        manager.syncValue("tenkei_circuits", 0, circuits);
        manager.syncValue("tenkei_catalysts", 0, catalysts);
        manager.syncValue("tenkei_condensate", 0, condensate);
        BooleanSyncValue cutterFluids = new BooleanSyncValue(
            () -> ItemUniversalWand.isCutterFluidFilterEnabled(expected),
            value -> ItemUniversalWand.setCutterFluidFilterEnabled(player, expected, value)).allowC2S();
        manager.syncValue("tenkei_cutter_fluids", 0, cutterFluids);
        return gregtech.api.modularui2.GTGuis.createPopUpPanel("tenkeitech_wand_tenkei_config", true, false)
            .background(new Rectangle().color(0xFFCDCDD5))
            .size(280, 146)
            .child(
                new ParentWidget<>().background(new Rectangle().color(0xFFB0B2C5))
                    .pos(4, 4)
                    .size(260, 18))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.tenkei_settings")
                        .color(0xFF414156)).pos(8, 7)
                            .size(244, 16))
            .child(
                new ParentWidget<>().background(historyListBackground())
                    .pos(6, 28)
                    .size(268, 112))
            .child(
                stateButton("tenkei_circuits", circuits).pos(12, 36)
                    .size(256, 18))
            .child(
                stateButton("tenkei_catalysts", catalysts).pos(12, 62)
                    .size(256, 18))
            .child(
                stateButton("tenkei_condensate", condensate).pos(12, 88)
                    .size(256, 18))
            .child(
                stateButton("tenkei_cutter_fluids", cutterFluids).pos(12, 114)
                    .size(256, 18));
    }

    private static ModularPanel accelerationPanel(EntityPlayer player, ItemStack expected, PanelSyncManager manager,
        String configuredRates) {
        BooleanSyncValue enabled = new BooleanSyncValue(
            () -> ItemUniversalWand.isTimeAccelerationEnabled(expected),
            value -> ItemUniversalWand.setTimeAccelerationEnabled(player, expected, value)).allowC2S();
        IntSyncValue rate = new IntSyncValue(() -> ItemUniversalWand.getAccelerationRate(expected));
        IntSyncValue mode = new IntSyncValue(() -> ItemUniversalWand.getAccelerationMode(expected));
        BooleanSyncValue gtMode = new BooleanSyncValue(() -> ItemUniversalWand.isAccelerationGtMode(expected));
        manager.syncValue("acceleration_enabled", 0, enabled);
        manager.syncValue("acceleration_rate", 0, rate);
        manager.syncValue("acceleration_mode", 0, mode);
        manager.syncValue("acceleration_gt_mode", 0, gtMode);
        IPanelHandler history = manager.syncedPanel(
            "tenkeitech_wand_acceleration_history",
            true,
            (historyManager, handler) -> historyPanel(player, historyManager));
        int[] rates = java.util.stream.IntStream
            .concat(
                java.util.stream.IntStream.of(0),
                Arrays.stream(configuredRates.split(","))
                    .filter(value -> !value.isEmpty())
                    .mapToInt(Integer::parseInt))
            .toArray();
        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> gears = new PagedWidget<>().controller(controller)
            .size(256, 44)
            .pos(12, 74);
        int pageCount = (rates.length + 9) / 10;
        for (int page = 0; page < pageCount; page++) {
            ParentWidget<?> row = new ParentWidget<>().size(256, 44);
            for (int index = page * 10; index < Math.min(rates.length, page * 10 + 10); index++) {
                int selected = rates[index], cell = index % 10;
                row.child(
                    actionButton(
                        "acceleration_off",
                        () -> ItemUniversalWand.setAccelerationRate(player, expected, selected)).background(
                            new DynamicDrawable(() -> rate.getIntValue() == selected ? activeButton() : flatButton()))
                            .overlay(
                                IKey.dynamic(
                                    () -> (rate.getIntValue() == selected ? "\u00a7b" : "\u00a77") + (selected == 0
                                        ? StatCollector
                                            .translateToLocal("tenkeitech.gui.universal_wand.acceleration_off")
                                        : selected <= 99999 ? "x" + selected
                                            : "x" + String.format(java.util.Locale.ROOT, "%.1e", (double) selected))))
                            .addTooltipLine(
                                selected == 0
                                    ? StatCollector.translateToLocal("tenkeitech.gui.universal_wand.acceleration_off")
                                    : "x" + selected)
                            .pos(cell % 5 * 52, cell / 5 * 22)
                            .size(48, 18));
            }
            gears.addPage(row);
        }
        int extraHeight = pageCount > 1 ? 22 : 0;
        ModularPanel panel = gregtech.api.modularui2.GTGuis
            .createPopUpPanel("tenkeitech_wand_acceleration_config", true, false)
            .background(new Rectangle().color(0xFFCDCDD5))
            .size(280, 260 + extraHeight)
            .child(
                new ParentWidget<>().background(new Rectangle().color(0xFFB0B2C5))
                    .pos(4, 4)
                    .size(260, 18))
            .child(
                new ParentWidget<>().background(historyListBackground())
                    .pos(6, 52)
                    .size(268, 70 + extraHeight))
            .child(
                new ParentWidget<>().background(historyListBackground())
                    .pos(6, 124 + extraHeight)
                    .size(268, 98))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.acceleration_title")
                        .color(0xFF414156)).pos(8, 7)
                            .size(244, 16))
            .child(
                new ToggleButton().value(enabled)
                    .background(false, flatButton())
                    .background(true, activeButton())
                    .overlay(true, IKey.lang("tenkeitech.gui.universal_wand.acceleration_on"))
                    .overlay(false, IKey.lang("tenkeitech.gui.universal_wand.acceleration_disabled"))
                    .pos(144, 30)
                    .size(124, 18))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.acceleration_multiplier")
                        .color(0xFF414156)).pos(12, 56))
            .child(gears)
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.acceleration_duration")
                        .color(0xFF414156)).pos(12, 128 + extraHeight));
        panel.onUpdateListener(widget -> { if (!UniversalWandFeature.ACCELERATION.allowed()) widget.closeIfOpen(); });
        for (int value = 0; value < 2; value++) {
            boolean selected = value == 1;
            String engineKey = selected ? "acceleration_engine_gt" : "acceleration_engine_normal";
            panel.child(
                actionButton(engineKey, () -> ItemUniversalWand.setAccelerationGtMode(player, expected, selected))
                    .background(
                        new DynamicDrawable(() -> gtMode.getBoolValue() == selected ? activeButton() : flatButton()))
                    .overlay(
                        IKey.dynamic(
                            () -> (gtMode.getBoolValue() == selected ? "\u00a7b" : "\u00a77")
                                + StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + engineKey)))
                    .addTooltipLine(IKey.lang("tenkeitech.gui.universal_wand." + engineKey + ".tooltip"))
                    .pos(12 + value * 44, 30)
                    .size(40, 18));
        }
        if (pageCount > 1) {
            for (int page = 0; page < pageCount; page++) panel.child(
                new PageButton(page, controller).background(flatButton())
                    .overlay(IKey.str(Integer.toString(page + 1)))
                    .pos(12 + page * 24, 118)
                    .size(22, 18));
        }
        for (int value = 0; value < 3; value++) {
            int selected = value;
            panel.child(
                actionButton(
                    "acceleration_mode_" + selected,
                    () -> ItemUniversalWand.setAccelerationMode(player, expected, selected))
                        .background(
                            new DynamicDrawable(() -> mode.getIntValue() == selected ? activeButton() : flatButton()))
                        .overlay(
                            IKey.dynamic(
                                () -> (mode.getIntValue() == selected ? "\u00a7b" : "\u00a77") + StatCollector
                                    .translateToLocal("tenkeitech.gui.universal_wand.acceleration_mode_" + selected)))
                        .pos(12, 148 + extraHeight + value * 26)
                        .size(256, 20));
        }
        return panel.child(
            new ButtonWidget<>().background(flatButton())
                .overlay(IKey.lang("tenkeitech.gui.universal_wand.acceleration_history"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    history.openPanel();
                    return true;
                })
                .pos(12, 228 + extraHeight)
                .size(256, 20));
    }

    private static ModularPanel historyPanel(EntityPlayer player, PanelSyncManager manager) {
        java.util.concurrent.atomic.AtomicInteger page = new java.util.concurrent.atomic.AtomicInteger();
        StringSyncValue history = new StringSyncValue(
            () -> com.tenkeitech.universalwand.UniversalWandAccelerationHandler
                .history(player.getUniqueID(), page.get()));
        manager.syncValue("permanent_history", 0, history);
        ModularPanel panel = gregtech.api.modularui2.GTGuis
            .createPopUpPanel("tenkeitech_wand_acceleration_history", true, false)
            .background(new Rectangle().color(0xFFCDCDD5))
            .size(420, 302)
            .child(
                new ParentWidget<>().background(new Rectangle().color(0xFFB0B2C5))
                    .pos(4, 4)
                    .size(400, 18))
            .child(
                new TextWidget<>(IKey.lang("tenkeitech.gui.universal_wand.history_subtitle")).pos(10, 26)
                    .size(390, 12))
            .child(
                new ParentWidget<>().background(historyListBackground())
                    .pos(6, 40)
                    .size(408, 228))
            .child(
                new TextWidget<>(
                    IKey.lang("tenkeitech.gui.universal_wand.acceleration_history")
                        .color(0xFF414156)).pos(10, 7)
                            .size(360, 16));
        for (int i = 0; i < 8; i++) {
            int row = i;
            panel.child(new TextWidget<>(IKey.dynamic(() -> {
                String entry = historyEntry(history, row);
                if (entry.isEmpty()) return "";
                String[] values = entry.split(",", -1);
                return StatCollector.translateToLocalFormatted(
                    "tenkeitech.gui.universal_wand.history_named_entry",
                    historyName(values),
                    values[4],
                    StatCollector.translateToLocal("tenkeitech.gui.universal_wand.history_mode_" + values[5]));
            })
                .color(0xFF414156)).pos(12, 48 + i * 26)
                    .size(334, 10));
            panel.child(new TextWidget<>(IKey.dynamic(() -> {
                String entry = historyEntry(history, row);
                if (entry.isEmpty()) return "";
                String[] values = entry.split(",", -1);
                return StatCollector.translateToLocalFormatted(
                    "tenkeitech.gui.universal_wand.history_location",
                    values[0],
                    values[1],
                    values[2],
                    values[3],
                    StatCollector.translateToLocal(
                        "tenkeitech.gui.universal_wand.acceleration_engine_"
                            + ("0".equals(values[8]) ? "normal" : "gt")));
            })
                .color(0xFF73778D)).pos(12, 59 + i * 26)
                    .size(334, 10));
            panel.child(
                new ButtonWidget<>()
                    .background(
                        new DynamicDrawable(
                            () -> historyEntry(history, row).isEmpty()
                                ? com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY
                                : flatButton()))
                    .hoverBackground(
                        new DynamicDrawable(
                            () -> historyEntry(history, row).isEmpty()
                                ? com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY
                                : flatButton()))
                    .overlay(
                        IKey.dynamic(
                            () -> historyEntry(history, row).isEmpty() ? ""
                                : StatCollector.translateToLocal("tenkeitech.gui.universal_wand.history_clear")))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        String entry = historyEntry(history, row);
                        if (entry.isEmpty()) return false;
                        String[] values = entry.split(",");
                        com.tenkeitech.universalwand.UniversalWandNetwork.requestAccelerationManagement(
                            2,
                            Integer.parseInt(values[0]),
                            Integer.parseInt(values[1]),
                            Integer.parseInt(values[2]),
                            Integer.parseInt(values[3]));
                        return true;
                    })
                    .pos(352, 50 + i * 26)
                    .size(56, 16));
        }
        InteractionSyncHandler previous = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!mouse.isClient() && mouse.mouseButton == 0) {
                int current = Integer.parseInt(
                    history.getStringValue()
                        .split(";")[0].split(",")[0]);
                page.set(Math.max(0, current - 1));
            }
        });
        InteractionSyncHandler next = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!mouse.isClient() && mouse.mouseButton == 0) {
                String[] header = history.getStringValue()
                    .split(";")[0].split(",");
                page.set(Math.min(Integer.parseInt(header[1]) - 1, Integer.parseInt(header[0]) + 1));
            }
        });
        panel.child(
            new ButtonWidget<>().syncHandler(previous)
                .background(GuiTextures.MC_BUTTON)
                .overlay(IKey.str("<"))
                .pos(12, 278)
                .size(24, 20));
        panel.child(new TextWidget<>(IKey.dynamic(() -> {
            String[] header = history.getStringValue()
                .split(";")[0].split(",");
            return header.length < 3 ? ""
                : (Integer.parseInt(header[0]) + 1) + " / " + header[1] + "  (" + header[2] + ")";
        })).pos(40, 280)
            .size(92, 16));
        panel.child(
            new ButtonWidget<>().syncHandler(next)
                .background(GuiTextures.MC_BUTTON)
                .overlay(IKey.str(">"))
                .pos(136, 278)
                .size(24, 20));
        panel.child(
            new ButtonWidget<>().background(flatButton())
                .overlay(IKey.lang("tenkeitech.gui.universal_wand.history_refresh"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    com.tenkeitech.universalwand.UniversalWandNetwork.requestAccelerationManagement(4, 0, 0, 0, 0);
                    return true;
                })
                .pos(310, 278)
                .size(46, 20));
        panel.child(
            new ButtonWidget<>().background(flatButton())
                .overlay(IKey.lang("tenkeitech.gui.universal_wand.history_back"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    panel.closeIfOpen();
                    return true;
                })
                .pos(362, 278)
                .size(46, 20));
        return panel.child(
            new ButtonWidget<>().background(flatButton())
                .setEnabledIf(
                    widget -> history.getStringValue()
                        .split(";").length > 1)
                .overlay(IKey.lang("tenkeitech.gui.universal_wand.history_clear_all"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    com.tenkeitech.universalwand.UniversalWandNetwork.requestAccelerationManagement(3, 0, 0, 0, 0);
                    return true;
                })
                .pos(188, 278)
                .size(116, 20));
    }

    private static String historyName(String[] values) {
        String key = new String(
            java.util.Base64.getDecoder()
                .decode(values[6]),
            java.nio.charset.StandardCharsets.UTF_8);
        String name = new String(
            java.util.Base64.getDecoder()
                .decode(values[7]),
            java.nio.charset.StandardCharsets.UTF_8);
        return !key.isEmpty() && StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key)
            : name.isEmpty() ? StatCollector.translateToLocal("tenkeitech.gui.universal_wand.history_unknown") : name;
    }

    private static String historyEntry(StringSyncValue history, int row) {
        String[] entries = history.getStringValue()
            .split(";");
        if (entries.length <= row + 1) return "";
        String entry = entries[row + 1];
        return entry.split(",", -1).length == 9 ? entry : "";
    }

    private static com.cleanroommc.modularui.api.drawable.IDrawable historyListBackground() {
        Rectangle border = new Rectangle().color(0xFFF4F4F6);
        Rectangle fill = new Rectangle().color(0xFFCDCDD5);
        // Keep both layers in the same background pass: a nested fill would cover sibling button backgrounds.
        return (context, x, y, width, height, theme) -> {
            border.draw(context, x, y, width, height, theme);
            fill.draw(context, x + 1, y + 1, width - 2, height - 2, theme);
        };
    }

    private static TextWidget<?> label(String key, int y, int width) {
        return new TextWidget<>(IKey.lang("tenkeitech.gui.universal_wand." + key)).pos(12, y + 2)
            .size(width, 16)
            .maxWidth(width);
    }

    private static ParentWidget<?> toggle(String key, BooleanSyncValue value, int y) {
        ToggleButton button = new ToggleButton().value(value)
            .overlay(true, GuiTextures.CHECK_BOX_FULL)
            .overlay(false, GuiTextures.CHECK_BOX_EMPTY)
            .size(18, 18)
            .pos(210, 0);
        if ("collect_items".equals(key) || "ae_priority".equals(key)
            || "shears".equals(key)
            || "hoe".equals(key)
            || "force_kill".equals(key)
            || "building_ae".equals(key)
            || "beheading".equals(key)
            || "spawn_egg".equals(key)
            || "player_protection".equals(key)
            || "no_aggro".equals(key)
            || "time_acceleration".equals(key)) button.tooltipDynamic(tooltip -> featureTooltip(tooltip, key));
        return new ParentWidget<>().size(240, 20)
            .pos(0, y)
            .child(label(key, 0, 186))
            .child(button);
    }

    private static ButtonWidget<?> coreButton(EntityPlayer player, ItemStack expected, IntSyncValue selected,
        ItemUniversalWand.BuildingCore core, String key) {
        return actionButton(key, () -> ItemUniversalWand.setBuildingCore(player, expected, core))
            .overlay(
                IKey.dynamic(
                    () -> (!controlAllowed(key) ? "§7" : selected.getIntValue() == core.ordinal() ? "§a" : "§7")
                        + StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key))
                    .scale(1.0F))
            .tooltipDynamic(tooltip -> featureTooltip(tooltip, key))
            .size(68, 18);
    }

    private static ParentWidget<?> slider(IntSyncValue value, int min, int max, int y) {
        return new ParentWidget<>().size(108, 18)
            .pos(120, y)
            .child(
                new SliderWidget().background(GuiTextures.MC_BUTTON)
                    .bounds(min, max)
                    .stopper(1)
                    .value(value)
                    .size(108, 18))
            .child(
                IKey.dynamic(() -> String.valueOf(value.getIntValue()))
                    .color(Color.WHITE.main)
                    .asWidget()
                    .size(108, 18)
                    .textAlign(Alignment.Center));
    }

    private static ButtonWidget<?> actionButton(String key, Runnable action) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(
            mouse -> { if (!mouse.isClient() && mouse.mouseButton == 0 && controlAllowed(key)) action.run(); });
        class FeatureButton extends ButtonWidget<FeatureButton> {

            @Override
            public Interactable.Result onMousePressed(int button) {
                return controlAllowed(key) ? super.onMousePressed(button) : Interactable.Result.STOP;
            }

            @Override
            public com.cleanroommc.modularui.api.drawable.IDrawable getCurrentBackground(
                com.cleanroommc.modularui.theme.WidgetThemeEntry<?> theme) {
                return controlAllowed(key) ? super.getCurrentBackground(theme) : disabledButton();
            }
        }
        return new FeatureButton().syncHandler(handler)
            .background(GuiTextures.MC_BUTTON)
            .overlay(IKey.lang("tenkeitech.gui.universal_wand." + key));
    }

    private static ButtonWidget<?> toolModeButton(EntityPlayer player, ItemStack expected, IntSyncValue selected,
        ItemUniversalWand.GtToolMode mode, String key) {
        return actionButton(key, () -> ItemUniversalWand.setGtToolMode(player, expected, mode))
            .overlay(
                IKey.dynamic(
                    () -> (!controlAllowed(key) ? "§7" : selected.getIntValue() == mode.ordinal() ? "§a" : "§7")
                        + StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key))
                    .scale(1.0F))
            .tooltipDynamic(tooltip -> featureTooltip(tooltip, key))
            .size(68, 18);
    }

    private static boolean controlAllowed(String key) {
        UniversalWandFeature feature = UniversalWandFeature.forControl(key);
        return feature == null || feature.allowed();
    }

    private static IKey stateLabel(String key, boolean enabled) {
        return IKey
            .dynamic(
                () -> (controlAllowed(key) ? "§f" : "§7") + StatCollector.translateToLocalFormatted(
                    "tenkeitech.gui.universal_wand." + (enabled ? "state_on" : "state_off"),
                    StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key)))
            .scale(1.0F);
    }

    private static void featureTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip, String key) {
        String translation = "tenkeitech.gui.universal_wand." + key + ".tooltip";
        if (StatCollector.canTranslate(translation)) tooltip.addLine(StatCollector.translateToLocal(translation));
        if (!controlAllowed(key)) tooltip.addLine(ItemUniversalWand.disabledFeatureTooltip());
    }
}
