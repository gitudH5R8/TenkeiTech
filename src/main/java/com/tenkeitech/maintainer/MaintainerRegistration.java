/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public final class MaintainerRegistration implements IGuiHandler {

    public static final Block BLOCK = new BlockBetterLevelMaintainer();
    public static final int GUI_ID = 1;

    public static void preInit() {
        GameRegistry.registerBlock(BLOCK, BlockBetterLevelMaintainer.MaintainerItem.class, "better_level_maintainer");
        GameRegistry.registerTileEntity(TileBetterLevelMaintainer.class, "tenkeitech.better_level_maintainer");
        MaintainerNetwork.init();
    }

    public static void init() {
        NetworkRegistry.INSTANCE.registerGuiHandler(TenkeiTech.instance, new MaintainerRegistration());
        if (Loader.isModLoaded("Waila")) FMLInterModComms.sendMessage(
            "Waila",
            "register",
            "com.tenkeitech.integration.waila.MaintainerWailaProvider.callbackRegister");
    }

    public static void recipe() {
        Block original = GameRegistry.findBlock("ae2fc", "level_maintainer");
        if (original == null) throw new IllegalStateException("AE2FC level maintainer is missing");
        GameRegistry.addShapelessRecipe(new ItemStack(BLOCK), new ItemStack(original));
        GameRegistry.addShapelessRecipe(new ItemStack(original), new ItemStack(BLOCK));
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return id == GUI_ID && world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile
            && tile.canUse(player) ? new ContainerBetterLevelMaintainer(player, tile) : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return id == GUI_ID && world.getTileEntity(x, y, z) instanceof TileBetterLevelMaintainer tile
            ? TenkeiTech.proxy.createMaintainerGui(player, tile)
            : null;
    }
}
