/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class Config {

    private static File configurationFile;
    private static boolean questSkipConfigLoaded;
    public static boolean enableQuestSkip = true;

    private static final String CATEGORY_MACHINES = "machines";
    private static final String CATEGORY_PATTERN_UPLOAD = "patternUpload";
    private static final String CATEGORY_WIRELESS_AE = "wirelessAe";
    private static final String CATEGORY_UNIVERSAL_WAND = "universalwand";
    public static volatile long universalWandFeatureMask = -1L;
    private static final int DEFAULT_WAND_BLOCKS_PER_TICK = 16;
    private static final double DEFAULT_WAND_MINING_EXHAUSTION = 0.025D;
    private static final double MAX_WAND_MINING_EXHAUSTION = 40D;
    private static final int DEFAULT_WAND_UNDO_HISTORY = 3;
    public static final int MAX_WAND_UNDO_HISTORY = 10;

    public static int superPatternBufferMetaTileId = 32600;
    public static int superPatternBufferProxyMetaTileId = 32601;
    public static int wirelessNetworkCoreMetaTileId = 32602;
    public static int wirelessNetworkBookmarkMetaTileId = 32603;
    public static int autoTemplateInputMetaTileId = 32604;
    public static int autoTemplateInputProxyMetaTileId = 32605;
    public static int wiremillTemplateInputMetaTileId = 32606;
    public static int benderTemplateInputMetaTileId = 32607;
    public static int condensateAssemblyDebugMetaTileId = 32608;
    public static int longInputExposureStackLimit = 1_024;
    public static boolean enablePatternUpload = true;
    public static boolean enableWirelessAe = true;
    public static boolean enableWirelessSneakAutoBind = true;
    public static boolean enableWirelessCrossDimension = true;
    public static boolean debugWirelessAe;
    public static int wirelessReconnectIntervalTicks = 100;
    public static int wirelessMembersProcessedPerTick = 16;
    public static int wirelessMaxMembers = 1_024;
    public static volatile int universalWandBlocksPerTick = DEFAULT_WAND_BLOCKS_PER_TICK;
    public static volatile double universalWandMiningExhaustion = DEFAULT_WAND_MINING_EXHAUSTION;
    public static volatile boolean universalWandDropToPlayer = true;
    public static volatile boolean universalWandGrantOnFirstJoin = true;
    public static volatile int universalWandSearchPositionsPerTick = 4096;
    public static volatile int universalWandGlobalSearchPositionsPerTick = 16384;
    public static volatile int universalWandGlobalBlocksPerTick = 128;
    public static volatile int universalWandUndoHistory = DEFAULT_WAND_UNDO_HISTORY;
    public static volatile int universalWandAoeMaxTargets = 64;
    public static final int[] DEFAULT_WAND_ACCELERATION_RATES = { 2, 4, 16, 32, 64, 128, 256, 512, 1024 };
    public static volatile int[] universalWandAccelerationRates = DEFAULT_WAND_ACCELERATION_RATES.clone();
    private static final String[] DEFAULT_WAND_EFFECTS = { "23,1", "10,6", "16,1", "12,1", "13,1", "11,6" };
    public static volatile String[] universalWandEffects = DEFAULT_WAND_EFFECTS.clone();

    public static void synchronizeConfiguration(File configFile) {
        configurationFile = configFile;
        Configuration configuration = new Configuration(configFile);
        readConfiguration(configuration);
        for (String category : configuration.getCategoryNames()) {
            if (!"etc".equals(category) && configuration.hasCategory(category)) {
                configuration.removeCategory(configuration.getCategory(category));
            }
        }
        readConfiguration(configuration);
        configuration.save();
    }

    /** Shared startup and read-only GUI definition for the Other category. */
    public static Property getQuestSkipProperty(Configuration configuration) {
        Property questSkip = configuration.get(
            "etc",
            "enableQuestSkip",
            true,
            "Enable optional BetterQuesting skipping. Edit this file and restart. In-game display is read-only.");
        questSkip.setRequiresMcRestart(true);
        return questSkip;
    }

    private static void readConfiguration(Configuration configuration) {
        Property questSkip = getQuestSkipProperty(configuration);
        if (!questSkipConfigLoaded) {
            enableQuestSkip = questSkip.getBoolean(true);
            questSkipConfigLoaded = true;
        }
        superPatternBufferMetaTileId = configuration.getInt(
            "superPatternBufferMetaTileId",
            CATEGORY_MACHINES,
            superPatternBufferMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the ME Super Pattern Buffer. Change this before creating worlds if it conflicts.");
        superPatternBufferProxyMetaTileId = configuration.getInt(
            "superPatternBufferProxyMetaTileId",
            CATEGORY_MACHINES,
            superPatternBufferProxyMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the ME Super Pattern Buffer Proxy. Change this before creating worlds if it conflicts.");
        wirelessNetworkCoreMetaTileId = configuration.getInt(
            "wirelessNetworkCoreMetaTileId",
            CATEGORY_MACHINES,
            wirelessNetworkCoreMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the Wireless ME Network Core. Change this before creating worlds if it conflicts.");
        wirelessNetworkBookmarkMetaTileId = configuration.getInt(
            "wirelessNetworkBookmarkMetaTileId",
            CATEGORY_MACHINES,
            wirelessNetworkBookmarkMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the Wireless ME Network Bookmark. Change this before creating worlds if it conflicts.");
        autoTemplateInputMetaTileId = configuration.getInt(
            "autoTemplateInputMetaTileId",
            CATEGORY_MACHINES,
            autoTemplateInputMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the fixed-template automatic ME input hatch. Change this before creating worlds if it conflicts.");
        autoTemplateInputProxyMetaTileId = configuration.getInt(
            "autoTemplateInputProxyMetaTileId",
            CATEGORY_MACHINES,
            autoTemplateInputProxyMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the fixed-template automatic ME input bus mirror. Change this before creating worlds if it conflicts.");
        wiremillTemplateInputMetaTileId = configuration.getInt(
            "wiremillTemplateInputMetaTileId",
            CATEGORY_MACHINES,
            wiremillTemplateInputMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the wiremill template ME input bus. Change this before creating worlds if it conflicts.");
        benderTemplateInputMetaTileId = configuration.getInt(
            "benderTemplateInputMetaTileId",
            CATEGORY_MACHINES,
            benderTemplateInputMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the bender template ME input bus. Change this before creating worlds if it conflicts.");
        condensateAssemblyDebugMetaTileId = configuration.getInt(
            "condensateAssemblyDebugMetaTileId",
            CATEGORY_MACHINES,
            condensateAssemblyDebugMetaTileId,
            1,
            32765,
            "MetaTileEntity ID for the Condensate Assembly Debug controller. Change this before creating worlds if it conflicts.");
        longInputExposureStackLimit = configuration.getInt(
            "longInputExposureStackLimit",
            CATEGORY_MACHINES,
            longInputExposureStackLimit,
            1,
            65_535,
            "Maximum number of Integer.MAX_VALUE-sized item or fluid stacks exposed to GT controllers per "
                + "resource identity. The authoritative stored amount remains a long value.");

        enablePatternUpload = configuration.getBoolean(
            "enablePatternUpload",
            CATEGORY_PATTERN_UPLOAD,
            enablePatternUpload,
            "Enable uploading encoded patterns from AE2 pattern terminals. Requires a restart after changing.");

        enableWirelessAe = configuration.getBoolean(
            "enableWirelessAe",
            CATEGORY_WIRELESS_AE,
            enableWirelessAe,
            "Enable the wireless AE runtime. Existing core blocks remain registered when disabled.");
        enableWirelessSneakAutoBind = configuration.getBoolean(
            "enableSneakAutoBind",
            CATEGORY_WIRELESS_AE,
            enableWirelessSneakAutoBind,
            "Automatically bind supported ME devices placed while sneaking to the player's favorite wireless network.");
        enableWirelessCrossDimension = configuration.getBoolean(
            "allowCrossDimension",
            CATEGORY_WIRELESS_AE,
            enableWirelessCrossDimension,
            "Allow wireless AE links between already-loaded dimensions. Never loads dimensions or chunks.");
        debugWirelessAe = configuration.getBoolean(
            "debugWirelessAe",
            CATEGORY_WIRELESS_AE,
            debugWirelessAe,
            "Collect runtime-only wireless AE diagnostics and expose them through core Waila/information output.");
        wirelessReconnectIntervalTicks = configuration.getInt(
            "reconnectIntervalTicks",
            CATEGORY_WIRELESS_AE,
            wirelessReconnectIntervalTicks,
            20,
            1_200,
            "Ticks between bounded reconnect attempts for an offline wireless target.");
        wirelessMembersProcessedPerTick = configuration.getInt(
            "membersProcessedPerTick",
            CATEGORY_WIRELESS_AE,
            wirelessMembersProcessedPerTick,
            1,
            1_024,
            "Maximum wireless members checked per core each tick for reconnects and runtime maintenance.");
        wirelessMaxMembers = configuration.getInt(
            "maxMembers",
            CATEGORY_WIRELESS_AE,
            wirelessMaxMembers,
            1,
            Integer.MAX_VALUE,
            "Maximum saved wireless target devices per core.");

        configureAllCategories(configuration);
        long features = 0L;
        for (com.tenkeitech.universalwand.UniversalWandFeature feature : com.tenkeitech.universalwand.UniversalWandFeature
            .values()) {
            if (configuration.getCategory(CATEGORY_UNIVERSAL_WAND + "." + feature.category)
                .get(feature.configKey())
                .getBoolean(true)) features |= 1L << feature.ordinal();
        }
        universalWandFeatureMask = features;
        Property blocksPerTick = configuration.getCategory(wandCategory("blocksPerTick"))
            .get("blocksPerTick");
        universalWandBlocksPerTick = Math.max(1, blocksPerTick.getInt(DEFAULT_WAND_BLOCKS_PER_TICK));
        blocksPerTick.set(universalWandBlocksPerTick);
        Property miningExhaustion = configuration.getCategory(wandCategory("miningExhaustion"))
            .get("miningExhaustion");
        double exhaustion = miningExhaustion.getDouble(DEFAULT_WAND_MINING_EXHAUSTION);
        universalWandMiningExhaustion = Double.isFinite(exhaustion)
            ? Math.max(0D, Math.min(MAX_WAND_MINING_EXHAUSTION, exhaustion))
            : DEFAULT_WAND_MINING_EXHAUSTION;
        miningExhaustion.set(universalWandMiningExhaustion);
        universalWandDropToPlayer = configuration.getCategory(wandCategory("dropToPlayer"))
            .get("dropToPlayer")
            .getBoolean(true);
        universalWandGrantOnFirstJoin = configuration.getCategory(wandCategory("grantOnFirstJoin"))
            .get("grantOnFirstJoin")
            .getBoolean(true);
        universalWandSearchPositionsPerTick = readWandBudget(configuration, "searchPositionsPerTick", 4096, 65536);
        universalWandGlobalSearchPositionsPerTick = readWandBudget(
            configuration,
            "globalSearchPositionsPerTick",
            16384,
            262144);
        universalWandGlobalBlocksPerTick = readWandBudget(configuration, "globalBlocksPerTick", 128, 65536);
        Property undoHistory = configuration.getCategory(wandCategory("undoHistory"))
            .get("undoHistory");
        universalWandUndoHistory = Math
            .max(0, Math.min(MAX_WAND_UNDO_HISTORY, undoHistory.getInt(DEFAULT_WAND_UNDO_HISTORY)));
        undoHistory.set(universalWandUndoHistory);
        universalWandAoeMaxTargets = readWandBudget(configuration, "aoeMaxTargets", 64, Integer.MAX_VALUE);
        universalWandEffects = configuration.getCategory(wandCategory("potionEffects"))
            .get("potionEffects")
            .getStringList();
        Property rates = configuration.getCategory(wandCategory("accelerationRates"))
            .get("accelerationRates");
        universalWandAccelerationRates = Arrays.stream(rates.getIntList())
            .filter(value -> value > 0)
            .distinct()
            .sorted()
            .limit(32)
            .toArray();
        if (universalWandAccelerationRates.length == 0)
            universalWandAccelerationRates = DEFAULT_WAND_ACCELERATION_RATES.clone();
        rates.set(universalWandAccelerationRates);
    }

    private static int readWandBudget(Configuration configuration, String key, int defaultValue, int max) {
        Property property = configuration.getCategory(wandCategory(key))
            .get(key);
        int value = Math.max(1, Math.min(max, property.getInt(defaultValue)));
        property.set(value);
        return value;
    }

    /** Config files do not persist language keys, so both loading paths must apply them. */
    public static void configureAllCategories(Configuration configuration) {
        localizeCategory(configuration, "etc", "tenkeitech.config.other");
        localizeCategory(configuration, CATEGORY_PATTERN_UPLOAD, "tenkeitech.config.patternupload");
        localizeCategory(configuration, CATEGORY_WIRELESS_AE, "tenkeitech.config.wirelessae");
        configureUniversalWandCategory(configuration);
    }

    private static void localizeCategory(Configuration configuration, String name, String languageKey) {
        String categoryName = name.toLowerCase(Locale.ROOT);
        if (!configuration.hasCategory(categoryName)) return;
        ConfigCategory category = configuration.getCategory(categoryName);
        category.setLanguageKey(languageKey);
        for (Property property : category.getOrderedValues()) {
            property.setLanguageKey(languageKey + "." + property.getName());
        }
    }

    /** Applies the universal wand types, ranges and reset defaults. */
    public static void configureUniversalWandCategory(Configuration configuration) {
        for (String category : Arrays.asList("tools", "combat", "auxiliary", "mining", "vanilla")) {
            configuration.getCategory(CATEGORY_UNIVERSAL_WAND + "." + category)
                .setLanguageKey("tenkeitech.config.universalwand." + category);
        }
        for (com.tenkeitech.universalwand.UniversalWandFeature feature : com.tenkeitech.universalwand.UniversalWandFeature
            .values()) {
            configuration.get(
                CATEGORY_UNIVERSAL_WAND + "." + feature.category,
                feature.configKey(),
                (universalWandFeatureMask & (1L << feature.ordinal())) != 0,
                "Allow this wand feature. Disabled features are forced off; the server controls multiplayer availability.")
                .setDefaultValue(true)
                .setLanguageKey("tenkeitech.config.universalwand." + feature.configKey());
        }
        configuration.getCategory(CATEGORY_UNIVERSAL_WAND)
            .setLanguageKey("tenkeitech.config.universalwand");
        configuration
            .get(
                wandCategory("dropToPlayer"),
                "dropToPlayer",
                universalWandDropToPlayer,
                "Release buffered chain mining drops at the player's feet when true, "
                    + "or at the first mined block when false. Applies to new chains.")
            .setDefaultValue(true)
            .setLanguageKey("tenkeitech.config.universalwand.dropToPlayer");
        configuration
            .get(
                wandCategory("blocksPerTick"),
                "blocksPerTick",
                universalWandBlocksPerTick,
                "Maximum chain mining harvest attempts per player per tick. Applies immediately after in-game saving.",
                1,
                Integer.MAX_VALUE)
            .setDefaultValue(DEFAULT_WAND_BLOCKS_PER_TICK)
            .setLanguageKey("tenkeitech.config.universalwand.blocksPerTick");
        configuration
            .get(
                wandCategory("searchPositionsPerTick"),
                "searchPositionsPerTick",
                universalWandSearchPositionsPerTick,
                "Maximum search candidates per player per server tick.",
                1,
                65536)
            .setDefaultValue(4096)
            .setLanguageKey("tenkeitech.config.universalwand.searchPositionsPerTick");
        configuration
            .get(
                wandCategory("globalBlocksPerTick"),
                "globalBlocksPerTick",
                universalWandGlobalBlocksPerTick,
                "Maximum chain harvest attempts across all players per tick.",
                1,
                65536)
            .setDefaultValue(128)
            .setLanguageKey("tenkeitech.config.universalwand.globalBlocksPerTick");
        configuration
            .get(
                wandCategory("globalSearchPositionsPerTick"),
                "globalSearchPositionsPerTick",
                universalWandGlobalSearchPositionsPerTick,
                "Maximum search candidates across all players per server tick.",
                1,
                262144)
            .setDefaultValue(16384)
            .setLanguageKey("tenkeitech.config.universalwand.globalSearchPositionsPerTick");
        configuration
            .get(
                wandCategory("miningExhaustion"),
                "miningExhaustion",
                universalWandMiningExhaustion,
                "Total exhaustion per successfully mined Universal Wand block, replacing vanilla mining exhaustion. "
                    + "Zero disables mining exhaustion. Applies immediately after in-game saving.",
                0D,
                MAX_WAND_MINING_EXHAUSTION)
            .setDefaultValue(DEFAULT_WAND_MINING_EXHAUSTION)
            .setLanguageKey("tenkeitech.config.universalwand.miningExhaustion");
        configuration.get(
            wandCategory("undoHistory"),
            "undoHistory",
            universalWandUndoHistory,
            "Recent building operations retained per player. Zero disables undo; maximum is 10. Applies immediately after in-game saving.",
            0,
            MAX_WAND_UNDO_HISTORY)
            .setDefaultValue(DEFAULT_WAND_UNDO_HISTORY)
            .setLanguageKey("tenkeitech.config.universalwand.undoHistory");
        configuration.getCategory(CATEGORY_UNIVERSAL_WAND + ".mining")
            .setPropertyOrder(
                Arrays.asList(
                    "enablechain",
                    "enablecrops",
                    "enablebedrock",
                    "enablecollection",
                    "enablebuilding",
                    "enablegttools",
                    "dropToPlayer",
                    "blocksPerTick",
                    "searchPositionsPerTick",
                    "globalBlocksPerTick",
                    "globalSearchPositionsPerTick",
                    "miningExhaustion",
                    "undoHistory"));
        configuration.get(
            wandCategory("grantOnFirstJoin"),
            "grantOnFirstJoin",
            universalWandGrantOnFirstJoin,
            "Give each unmarked player a Universal Wand on login. Recorded in persistent player NBT after delivery; disabling does not clear the marker.")
            .setDefaultValue(true)
            .setLanguageKey("tenkeitech.config.universalwand.grantOnFirstJoin");
        configuration
            .get(
                wandCategory("accelerationRates"),
                "accelerationRates",
                universalWandAccelerationRates,
                "Positive extra-update rates offered by the acceleration menu. Zero/off is always available.")
            .setDefaultValues(DEFAULT_WAND_ACCELERATION_RATES)
            .setMaxListLength(32)
            .setLanguageKey("tenkeitech.config.universalwand.accelerationRates");
        configuration
            .get(
                wandCategory("aoeMaxTargets"),
                "aoeMaxTargets",
                universalWandAoeMaxTargets,
                "Maximum secondary targets per Universal Wand area attack. Applies immediately after in-game saving.",
                1,
                Integer.MAX_VALUE)
            .setDefaultValue(64)
            .setLanguageKey("tenkeitech.config.universalwand.aoeMaxTargets");
        configuration
            .get(
                wandCategory("potionEffects"),
                "potionEffects",
                universalWandEffects,
                "One potion ID or unlocalized name and one-based level per entry: 10,6 or potion.regeneration,6. "
                    + "Levels 1..256. Invalid or missing effects are skipped. Existing external effects are preserved.")
            .setDefaultValues(DEFAULT_WAND_EFFECTS)
            .setLanguageKey("tenkeitech.config.universalwand.potionEffects");
    }

    private static String wandCategory(String key) {
        switch (key) {
            case "grantOnFirstJoin":
                return CATEGORY_UNIVERSAL_WAND + ".tools";
            case "aoeMaxTargets":
                return CATEGORY_UNIVERSAL_WAND + ".combat";
            case "potionEffects":
            case "accelerationRates":
                return CATEGORY_UNIVERSAL_WAND + ".auxiliary";
            case "dropToPlayer":
            case "blocksPerTick":
            case "searchPositionsPerTick":
            case "globalBlocksPerTick":
            case "globalSearchPositionsPerTick":
            case "miningExhaustion":
            case "undoHistory":
                return CATEGORY_UNIVERSAL_WAND + ".mining";
            default:
                return CATEGORY_UNIVERSAL_WAND;
        }
    }

    /** Returns the Forge configuration file currently used by the server/common side. */
    public static File getConfigurationFile() {
        return configurationFile;
    }
}
