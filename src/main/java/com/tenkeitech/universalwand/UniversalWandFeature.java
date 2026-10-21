/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import net.minecraft.nbt.NBTTagCompound;

/** Shared policy for config entries, item settings and their dependent GUI controls. */
public enum UniversalWandFeature {

    FORCE_KILL("combat", "forceKill", "force_kill"),
    BEHEADING("combat", "beheading", "beheading"),
    CAPTURE("combat", "spawnEgg", "spawn_egg"),
    AREA_ATTACK("combat", "aoeSize", "aoe", "aoe_size"),
    PLAYER_PROTECTION("auxiliary", "playerProtection", "player_protection"),
    NO_AGGRO("auxiliary", "noAggro", "no_aggro"),
    TELEPORT("auxiliary", "teleportEnabled", "teleport"),
    ACCELERATION("auxiliary", "timeAcceleration", "time_acceleration"),
    CHAIN("mining", "enhancedChain", "enhanced_chain", "gt_small_chain", "radius", "max_count"),
    CROPS("mining", "cropHarvest", "crop_harvest", "crop_sticks"),
    BEDROCK("mining", "forceBedrock", "force_bedrock"),
    COLLECTION("mining", "collectToInventory", "collect_items", "ae_priority"),
    BUILDING("mining", "buildingEnabled", "building", "building_ae", "building_normal", "building_angel",
        "building_destruction"),
    GT_TOOLS("mining", "gtToolMode", "gt_mode", "gt_wrench", "gt_cutter", "gt_off"),
    SHEARS("vanilla", "shearsEnabled", "shears"),
    HOE("vanilla", "hoeEnabled", "hoe"),
    FLIGHT("auxiliary", null, "flight");

    private static final long ALL_MASK = (1L << values().length) - 1;

    public final String category;
    public final String setting;
    public final String[] controls;

    UniversalWandFeature(String category, String setting, String... controls) {
        this.category = category;
        this.setting = setting;
        this.controls = controls;
    }

    public String configKey() {
        return "enable" + name().toLowerCase(java.util.Locale.ROOT)
            .replace("_", "");
    }

    public boolean allowed() {
        return UniversalWandFeaturePolicy.allowed(this);
    }

    public static UniversalWandFeature forControl(String key) {
        for (UniversalWandFeature feature : values())
            for (String control : feature.controls) if (control.equals(key)) return feature;
        return null;
    }

    public static UniversalWandFeature forSetting(String key) {
        for (UniversalWandFeature feature : values())
            if (feature.setting != null && feature.setting.equals(key)) return feature;
        switch (key) {
            case "gtSmallChain":
            case "enhancedChainRadius":
            case "maxMiningCount":
                return CHAIN;
            case "cropSticks":
                return CROPS;
            case "aeStoragePriority":
                return COLLECTION;
            case "buildingAeEnabled":
            case "buildingCore":
                return BUILDING;
            case "accelerationRate":
            case "accelerationMode":
            case "accelerationGtMode":
                return ACCELERATION;
            default:
                return null;
        }
    }

    public static boolean canChange(String key) {
        UniversalWandFeature feature = forSetting(key);
        return feature == null || feature.allowed();
    }

    /** Operates on a settings copy; item update persists this result on the server. */
    public static void restrict(NBTTagCompound settings) {
        long mask = UniversalWandFeaturePolicy.mask();
        if ((mask & ALL_MASK) == ALL_MASK) return;
        for (UniversalWandFeature feature : values()) {
            if ((mask & (1L << feature.ordinal())) != 0 || feature.setting == null) continue;
            if (feature == AREA_ATTACK) settings.setInteger(feature.setting, 1);
            else if (feature == GT_TOOLS) settings.setInteger(feature.setting, 0);
            else settings.setBoolean(feature.setting, false);
            if (feature == CHAIN) settings.setBoolean("gtSmallChain", false);
            if (feature == CROPS) settings.setBoolean("cropSticks", false);
            if (feature == COLLECTION) settings.setBoolean("aeStoragePriority", false);
            if (feature == BUILDING) settings.setBoolean("buildingAeEnabled", false);
            if (feature == ACCELERATION) settings.setInteger("accelerationRate", 0);
        }
    }
}
