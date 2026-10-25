/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tenkeitech.universalwand.UniversalWandMiningHandler;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLModIdMappingEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = TenkeiTech.MOD_ID,
    version = Tags.VERSION,
    name = TenkeiTech.MOD_NAME,
    guiFactory = "com.tenkeitech.gui.TenkeiTechGuiFactory",
    dependencies = "required-after:gregtech@[5.09.54.133,);required-after:appliedenergistics2@[rv3-beta-1050-GTNH,);"
        + "required-after:ae2fc@[1.5.106-gtnh,);"
        + "required-after:serverutilities@[2.4.9,);after:backhand;after:cropsnh;after:betterquesting",
    acceptedMinecraftVersions = "[1.7.10]")
public class TenkeiTech {

    public static final String MOD_ID = "tenkeitech";
    public static final String MOD_NAME = "TenkeiTech";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @Mod.Instance(MOD_ID)
    public static TenkeiTech instance;

    @SidedProxy(clientSide = "com.tenkeitech.ClientProxy", serverSide = "com.tenkeitech.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadComplete(event);
    }

    @Mod.EventHandler
    public void modIdMapping(FMLModIdMappingEvent event) {
        proxy.modIdMapping(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        com.tenkeitech.maintainer.MaintainerNetwork.clear();
        com.tenkeitech.questskip.QuestSkipNetwork.clear();
        com.tenkeitech.universalwand.UniversalWandRightClick.clearAll();
        UniversalWandMiningHandler.finishAllChains();
        com.tenkeitech.universalwand.UniversalWandAccelerationHandler.clearAll();
        com.tenkeitech.universalwand.UniversalWandUtilityHandler.clearAll();
        com.tenkeitech.universalwand.UniversalWandProtectionHandler.clearAll();
        com.tenkeitech.universalwand.UniversalWandBuildingHandler.clearAll();
    }

}
