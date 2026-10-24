/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.mojang.authlib.GameProfile;
import com.tenkeitech.resonating.ResonatingPatternData.Target;

import appeng.api.AEApi;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.helpers.IInterfaceHost;
import cpw.mods.fml.common.eventhandler.Event;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Only loaded endpoints with a matching identity and current interaction permission may receive materials. */
public final class ResonatingTargetAccess {

    private ResonatingTargetAccess() {}

    public static boolean allowed(World world, Target target, EntityPlayer player, ItemStack held) {
        if (world == null || world.isRemote
            || player == null
            || !world.blockExists(target.x, target.y, target.z)
            || !player.canPlayerEdit(target.x, target.y, target.z, target.side, held)
            || !(world instanceof WorldServer server)
            || MinecraftServer.getServer()
                .isBlockProtected(server, target.x, target.y, target.z, player))
            return false;
        TileEntity tile = world.getTileEntity(target.x, target.y, target.z);
        if (tile == null || tile.isInvalid() || tile instanceof IInterfaceHost) return false;
        if (tile instanceof IGregTechTileEntity gt && (!gt.canAccessData() || gt.getMetaTileEntity() == null
            || !gt.getMetaTileEntity()
                .isAccessAllowed(player)))
            return false;
        PlayerInteractEvent event = ForgeEventFactory.onPlayerInteract(
            player,
            PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
            target.x,
            target.y,
            target.z,
            target.side,
            world);
        return !event.isCanceled() && event.useBlock != Event.Result.DENY && event.useItem != Event.Result.DENY;
    }

    @SuppressWarnings("unchecked")
    public static IMEInventory inventory(Target target, ItemStack pattern, BaseActionSource source,
        appeng.api.storage.data.IAEStack<?> material) {
        WorldServer world = DimensionManager.getWorld(target.dimension);
        if (world == null || !world.blockExists(target.x, target.y, target.z)) return null;
        TileEntity tile = world.getTileEntity(target.x, target.y, target.z);
        String currentBlock = String.valueOf(
            net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(target.x, target.y, target.z)));
        boolean furnaceState = tile instanceof net.minecraft.tileentity.TileEntityFurnace
            && (target.block.equals("minecraft:furnace") || target.block.equals("minecraft:lit_furnace"))
            && (currentBlock.equals("minecraft:furnace") || currentBlock.equals("minecraft:lit_furnace"));
        if (tile == null || !tile.getClass()
            .getName()
            .equals(target.tileClass)
            || !target.block.equals(currentBlock) && !furnaceState
            || (tile instanceof IGregTechTileEntity gt ? gt.getMetaTileID() : -1) != target.metaTileId
            || world.getBlockMetadata(target.x, target.y, target.z) != target.metadata) return null;
        EntityPlayer player = FakePlayerFactory.get(world, new GameProfile(target.owner, target.ownerName));
        ItemStack previous = player.getHeldItem();
        int previousSlot = player.inventory.currentItem;
        double px = player.posX, py = player.posY, pz = player.posZ;
        try {
            player.setPosition(target.x + 0.5, target.y + 0.5, target.z + 0.5);
            player.inventory.setInventorySlotContents(player.inventory.currentItem, pattern.copy());
            if (!allowed(world, target, player, pattern)) return null;
            var registry = AEApi.instance()
                .registries()
                .externalStorage();
            var handler = registry
                .getHandler(tile, ForgeDirection.getOrientation(target.side), material.getStackType(), source);
            return handler == null ? null
                : handler
                    .getInventory(tile, ForgeDirection.getOrientation(target.side), material.getStackType(), source);
        } finally {
            player.inventory.currentItem = previousSlot;
            player.inventory.setInventorySlotContents(previousSlot, previous);
            player.setPosition(px, py, pz);
        }
    }

    public static boolean blocked(Target target, appeng.api.storage.data.IAEStack<?> material,
        com.tenkeitech.blockingfilter.BlockingFilter filter) {
        WorldServer world = DimensionManager.getWorld(target.dimension);
        if (world == null || !world.blockExists(target.x, target.y, target.z)) return true;
        if (material.isFluid()) {
            var tile = world.getTileEntity(target.x, target.y, target.z);
            if (!(tile instanceof net.minecraftforge.fluids.IFluidHandler handler)) return true;
            if (!filter.empty()) return filter.hasBlockingFluid(handler, ForgeDirection.getOrientation(target.side));
            var tanks = handler.getTankInfo(ForgeDirection.getOrientation(target.side));
            if (tanks == null) return true;
            for (var tank : tanks) if (tank != null && tank.fluid != null && tank.fluid.amount > 0) return true;
            return false;
        }
        var adaptor = appeng.util.InventoryAdaptor
            .getAdaptor(world.getTileEntity(target.x, target.y, target.z), ForgeDirection.getOrientation(target.side));
        // Match AE's side-aware inventory test, not an extraction-only storage listing that may omit inputs.
        if (adaptor == null) return true;
        if (filter.empty()) return adaptor.containsItems();
        return filter.hasBlockingContents(adaptor);
    }
}
