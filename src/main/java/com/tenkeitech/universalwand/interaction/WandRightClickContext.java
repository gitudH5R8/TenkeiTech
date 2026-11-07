/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand.interaction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.building.BuildingUndoHistory;

/** The original target and modifiers for one native right-click phase; never retargeted in place. */
public final class WandRightClickContext {

    public enum Phase {
        BLOCK_FIRST,
        BLOCK_ITEM,
        ENTITY,
        AIR,
        COMPLETE
    }

    public final Phase phase;
    public final ItemStack stack;
    public final EntityPlayer player;
    public final World world;
    public final int x, y, z, side, interactionId;
    public final float hitX, hitY, hitZ;
    public final boolean sneaking, modifier;
    public final boolean building, acceleration;
    public final ItemUniversalWand.GtToolMode gtToolMode;
    public final ItemUniversalWand.BuildingCore buildingCore;
    public final EntityLivingBase entity;

    public WandRightClickContext(Phase phase, ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        int side, float hitX, float hitY, float hitZ, EntityLivingBase entity, int interactionId) {
        this.phase = phase;
        this.stack = stack;
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.entity = entity;
        this.interactionId = interactionId;
        sneaking = player.isSneaking();
        modifier = world.isRemote ? TenkeiTech.proxy.isWandUndoKeyHeld() : BuildingUndoHistory.isKeyHeld(player);
        building = ItemUniversalWand.isBuildingEnabled(stack);
        acceleration = ItemUniversalWand.isTimeAccelerationEnabled(stack);
        gtToolMode = ItemUniversalWand.getGtToolMode(stack);
        buildingCore = ItemUniversalWand.getBuildingCore(stack);
    }

    public static WandRightClickContext air(Phase phase, EntityPlayer player, ItemStack stack, int id) {
        return new WandRightClickContext(phase, stack, player, player.worldObj, 0, 0, 0, -1, 0, 0, 0, null, id);
    }
}
