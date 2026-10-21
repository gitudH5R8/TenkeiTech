/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** Public Waila provider for the wireless-network state of supported GT ME hatches. */
public final class WirelessMeHatchWailaProvider implements IWailaDataProvider {

    public static void callbackRegister(IWailaRegistrar registrar) {
        WirelessMeHatchWailaProvider provider = new WirelessMeHatchWailaProvider();
        registrar.registerBodyProvider(provider, BaseMetaTileEntity.class);
        registrar.registerNBTProvider(provider, BaseMetaTileEntity.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currentTip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        WirelessMeHatchWailaSupport.appendBody(getMachine(accessor.getTileEntity()), currentTip, accessor);
        return currentTip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currentTip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        WirelessMeHatchWailaSupport.appendServerData(getMachine(tile), player, tag, world, x, y, z);
        return tag;
    }

    private static MetaTileEntity getMachine(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity gtTile)) return null;
        IMetaTileEntity machine = gtTile.getMetaTileEntity();
        return machine instanceof MetaTileEntity metaTileEntity ? metaTileEntity : null;
    }
}
