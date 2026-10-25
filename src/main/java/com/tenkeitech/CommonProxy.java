/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import com.tenkeitech.autotemplate.AutoTemplateInteractionHandler;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.integration.waila.WirelessMeHatchWailaIntegration;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.patternupload.PatternUploadNetwork;
import com.tenkeitech.universalwand.UniversalWandBuildingHandler;
import com.tenkeitech.universalwand.UniversalWandCombatHandler;
import com.tenkeitech.universalwand.UniversalWandExperienceHandler;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.wireless.WirelessAeEventHandler;

import appeng.api.AEApi;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLModIdMappingEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        com.tenkeitech.questskip.QuestSkipNetwork.init();
        ModItems.init();
        com.tenkeitech.maintainer.MaintainerRegistration.preInit();
        com.tenkeitech.resonating.ResonatingNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.tenkeitech.resonating.ResonatingInteractionHandler());
        PatternUploadNetwork.init();
        UniversalWandNetwork.init();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new com.tenkeitech.universalwand.UniversalWandFeaturePolicy());
        com.tenkeitech.autotemplate.AutoTemplateCatalogSync.init();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new com.tenkeitech.universalwand.UniversalWandRightClick());
        com.tenkeitech.universalwand.UniversalWandAccelerationHandler accelerationHandler = new com.tenkeitech.universalwand.UniversalWandAccelerationHandler();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(accelerationHandler);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(accelerationHandler);
        UniversalWandMiningHandler miningHandler = new UniversalWandMiningHandler();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(miningHandler);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandExperienceHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandCombatHandler());
        com.tenkeitech.universalwand.UniversalWandProtectionHandler protectionHandler = new com.tenkeitech.universalwand.UniversalWandProtectionHandler();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(protectionHandler);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(protectionHandler);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(miningHandler);
        UniversalWandBuildingHandler buildingHandler = new UniversalWandBuildingHandler();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new com.tenkeitech.universalwand.UniversalWandUtilityHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(buildingHandler);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(buildingHandler);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        com.tenkeitech.maintainer.MaintainerRegistration.init();
        ModMetaTileEntities.init();
        ModItems.registerRecipes();
        AutoTemplateInteractionHandler.register();
        WirelessAeEventHandler.register();
        WirelessMeHatchWailaIntegration.init();
        AEApi.instance()
            .registries()
            .interfaceTerminal()
            .register(MTESuperCraftingInputME.class);
        TenkeiTech.LOG.info("{} version {} initialized", TenkeiTech.MOD_NAME, Tags.VERSION);
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        com.tenkeitech.recipe.MachineRecipes.register();
        com.tenkeitech.maintainer.MaintainerRegistration.recipe();
    }

    public void loadComplete(FMLLoadCompleteEvent event) {}

    public Object createMaintainerGui(net.minecraft.entity.player.EntityPlayer player,
        com.tenkeitech.maintainer.TileBetterLevelMaintainer tile) {
        return null;
    }

    public void receiveMaintainerData(com.tenkeitech.maintainer.MaintainerNetwork.Data data) {}

    public void receiveAutoTemplateCatalog(com.tenkeitech.autotemplate.AutoTemplateCatalogSync.Page page,
        net.minecraft.network.INetHandler connection) {}

    public void modIdMapping(FMLModIdMappingEvent event) {
        // Loading a world and restoring registries on exit both fire this event.
        // Discard stale numeric identities now; the first world load prepares the new catalog.
        AutoTemplateRecipeRegistry.invalidate();
    }

    public void enqueuePatternUploadPage(PatternUploadNetwork.TargetsPageMessage message) {}

    public void allowWandTravel(int id) {}

    public void receiveQuestSkip(com.tenkeitech.questskip.QuestSkipNetwork.Reply reply,
        net.minecraft.network.INetHandler connection) {}

    public void useWandTravelIfImmediate(net.minecraft.item.ItemStack stack,
        net.minecraft.entity.player.EntityPlayer player) {}

    public void enqueuePatternUploadResult(PatternUploadNetwork.UploadResultMessage message) {}

    public void enqueueWandUndoPreview(UniversalWandNetwork.UndoPreviewMessage message) {}

    public void enqueueWandBuildingSupply(UniversalWandNetwork.BuildingSupplyMessage message) {}

    public int getWandBuildingSupply(com.tenkeitech.universalwand.building.BuildingContext context,
        net.minecraft.item.ItemStack material) {
        return 0;
    }

    public boolean isWandUndoKeyHeld() {
        return false;
    }

    public void receiveWandAcceleration(UniversalWandNetwork.AccelerationSyncMessage message) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
