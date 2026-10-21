/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import com.tenkeitech.client.UniversalWandBlockOverlayRenderer;
import com.tenkeitech.client.UniversalWandBuildingPreview;
import com.tenkeitech.client.UniversalWandChainHudRenderer;
import com.tenkeitech.client.UniversalWandFlightSpeedHudRenderer;
import com.tenkeitech.gui.AutoTemplateSearchInput;
import com.tenkeitech.integration.nee.NEEIntegration;
import com.tenkeitech.integration.nei.AutoTemplateNEIIntegration;
import com.tenkeitech.machine.MachineItemTooltip;
import com.tenkeitech.patternmode.PatternModeTooltip;
import com.tenkeitech.patternupload.PatternUploadClient;
import com.tenkeitech.patternupload.PatternUploadNetwork;
import com.tenkeitech.patternupload.PatternUploadPreferences;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        if (Config.enablePatternUpload) PatternUploadPreferences.configure(event.getModConfigurationDirectory());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (com.tenkeitech.questskip.QuestSkipNetwork.compatible())
            com.tenkeitech.integration.betterquesting.QuestSkipClient.init();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new com.tenkeitech.client.AutoTemplateCatalogClient());
        MachineItemTooltip.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.tenkeitech.client.ResonatingPatternClient());
        PatternModeTooltip.init();
        AutoTemplateSearchInput.init();
        UniversalWandKeyHandler.init();
        com.tenkeitech.client.UniversalWandTravelFallback travelFallback = new com.tenkeitech.client.UniversalWandTravelFallback();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(travelFallback);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(travelFallback);
        cpw.mods.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            com.tenkeitech.universalwand.EntityUselessWandItem.class,
            new net.minecraft.client.renderer.entity.RenderItem());
        net.minecraftforge.client.MinecraftForgeClient
            .registerItemRenderer(ModItems.UNIVERSAL_WAND, new com.tenkeitech.client.UniversalWandItemRenderer());
        com.tenkeitech.client.UniversalWandAccelerationRenderer accelerationRenderer = new com.tenkeitech.client.UniversalWandAccelerationRenderer();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(accelerationRenderer);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(accelerationRenderer);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandBlockOverlayRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandChainHudRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandFlightSpeedHudRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.tenkeitech.client.UniversalWandStealthRenderer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.tenkeitech.client.UniversalWandLavaVisionRenderer());
        UniversalWandBuildingPreview buildingPreview = new UniversalWandBuildingPreview();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(buildingPreview);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(buildingPreview);
        if (Config.enablePatternUpload) PatternUploadClient.init();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (Loader.isModLoaded("neenergistics")) NEEIntegration.init();
    }

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        super.loadComplete(event);
        if (Loader.isModLoaded("NotEnoughItems")) AutoTemplateNEIIntegration.init();
    }

    @Override
    public void modIdMapping(cpw.mods.fml.common.event.FMLModIdMappingEvent event) {
        // Local client handshakes share the integrated server's already prepared registry.
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()
            && net.minecraft.client.Minecraft.getMinecraft()
                .isIntegratedServerRunning())
            return;
        super.modIdMapping(event);
    }

    @Override
    public void receiveAutoTemplateCatalog(com.tenkeitech.autotemplate.AutoTemplateCatalogSync.Page page,
        net.minecraft.network.INetHandler connection) {
        com.tenkeitech.client.AutoTemplateCatalogClient.receive(page, connection);
    }

    @Override
    public void enqueuePatternUploadPage(PatternUploadNetwork.TargetsPageMessage message) {
        PatternUploadClient.enqueuePage(message);
    }

    @Override
    public void receiveQuestSkip(com.tenkeitech.questskip.QuestSkipNetwork.Reply reply,
        net.minecraft.network.INetHandler connection) {
        if (com.tenkeitech.questskip.QuestSkipNetwork.compatible())
            com.tenkeitech.integration.betterquesting.QuestSkipClient.receive(reply, connection);
    }

    @Override
    public void allowWandTravel(int id) {
        com.tenkeitech.client.UniversalWandTravelFallback.allow(id);
    }

    @Override
    public void useWandTravelIfImmediate(net.minecraft.item.ItemStack stack,
        net.minecraft.entity.player.EntityPlayer player) {
        com.tenkeitech.client.UniversalWandTravelFallback.useIfImmediate(stack, player);
    }

    @Override
    public void enqueuePatternUploadResult(PatternUploadNetwork.UploadResultMessage message) {
        PatternUploadClient.enqueueResult(message);
    }

    @Override
    public void enqueueWandUndoPreview(com.tenkeitech.universalwand.UniversalWandNetwork.UndoPreviewMessage message) {
        UniversalWandBuildingPreview.enqueueUndo(message);
    }

    @Override
    public void enqueueWandBuildingSupply(
        com.tenkeitech.universalwand.UniversalWandNetwork.BuildingSupplyMessage message) {
        UniversalWandBuildingPreview.enqueueSupply(message);
    }

    @Override
    public int getWandBuildingSupply(com.tenkeitech.universalwand.building.BuildingContext context,
        net.minecraft.item.ItemStack material) {
        return UniversalWandBuildingPreview.getSupply(context, material);
    }

    @Override
    public boolean isWandUndoKeyHeld() {
        return UniversalWandKeyHandler.isUndoKeyHeld();
    }

    @Override
    public void receiveWandAcceleration(
        com.tenkeitech.universalwand.UniversalWandNetwork.AccelerationSyncMessage message) {
        com.tenkeitech.client.UniversalWandAccelerationRenderer.receive(message);
    }
}
