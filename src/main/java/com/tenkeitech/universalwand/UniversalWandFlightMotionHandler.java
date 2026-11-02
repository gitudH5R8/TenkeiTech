/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Keep item and living-update thrust separate from the selected vanilla flight speed. */
public final class UniversalWandFlightMotionHandler {

    private final Map<LivingUpdateEvent, HorizontalMotion> eventMotion = Collections
        .synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeLivingUpdate(LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer player)) return;
        HorizontalMotion motion = capture(player);
        if (motion != null) eventMotion.put(event, motion);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void afterLivingUpdate(LivingUpdateEvent event) {
        HorizontalMotion motion = eventMotion.remove(event);
        if (motion != null && !event.isCanceled()) motion.restore();
    }

    public static HorizontalMotion capture(EntityPlayer player) {
        return player.capabilities.isFlying && UniversalWandUtilityHandler.shouldOverrideFlightSpeed(player)
            ? new HorizontalMotion(player)
            : null;
    }

    public static final class HorizontalMotion {

        private final EntityPlayer player;
        private final World world;
        private final Entity mount;
        private final double posX;
        private final double posY;
        private final double posZ;
        private final double motionX;
        private final double motionZ;

        private HorizontalMotion(EntityPlayer player) {
            this.player = player;
            world = player.worldObj;
            mount = player.ridingEntity;
            posX = player.posX;
            posY = player.posY;
            posZ = player.posZ;
            motionX = player.motionX;
            motionZ = player.motionZ;
        }

        public void restore() {
            if (player.worldObj != world || player.ridingEntity != mount
                || player.posX != posX
                || player.posY != posY
                || player.posZ != posZ
                || !player.capabilities.isFlying
                || !UniversalWandUtilityHandler.shouldOverrideFlightSpeed(player)) return;
            player.motionX = motionX;
            player.motionZ = motionZ;
        }
    }
}
