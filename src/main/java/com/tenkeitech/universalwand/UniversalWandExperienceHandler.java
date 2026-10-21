/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Collects newly generated mob XP using the recorded killer, without a proximity magnet or death-method patches. */
public final class UniversalWandExperienceHandler {

    private final Map<EntityLivingBase, DeathRecord> deaths = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote || victim instanceof EntityPlayer) return;
        deaths.remove(victim);
        EntityPlayerMP player = null;
        if (event.source != null && event.source.getEntity() instanceof EntityPlayerMP sourcePlayer)
            player = sourcePlayer;
        if (player == null && victim.func_94060_bK() instanceof EntityPlayerMP creditedPlayer) player = creditedPlayer;
        if (player == null || player.isDead
            || player.worldObj != victim.worldObj
            || !ItemUniversalWand.isHeldBy(player)) return;
        ItemStack wand = player.getHeldItem();
        if (ItemUniversalWand.isInventoryCollection(wand) || ItemUniversalWand.isAePriority(wand))
            deaths.put(victim, new DeathRecord(player.getUniqueID(), victim.worldObj.getTotalWorldTime()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || deaths.isEmpty()
            || !(event.entity instanceof EntityXPOrb orb)
            || orb.isDead
            || orb.xpOrbAge != 0
            || orb.getXpValue() <= 0) return;
        UUID owner = null;
        // Only inspect the spawn location. Vanilla emits XP before removing the dying mob from its chunk.
        for (Object entity : event.world
            .getEntitiesWithinAABB(EntityLivingBase.class, orb.boundingBox.expand(0.25D, 0.25D, 0.25D))) {
            EntityLivingBase victim = (EntityLivingBase) entity;
            DeathRecord record = deaths.get(victim);
            if (record == null || victim.getHealth() > 0
                || victim.worldObj != event.world
                || event.world.getTotalWorldTime() - record.worldTick > 600
                || victim.getDistanceSq(orb.posX, orb.posY, orb.posZ) > 0.000001D) continue;
            if (owner != null && !owner.equals(record.player)) return;
            owner = record.player;
        }
        if (owner == null || !(event.world.func_152378_a(owner) instanceof EntityPlayerMP player)
            || player.isDead
            || player.worldObj != event.world) return;
        player.addExperience(orb.getXpValue());
        orb.forceSpawn = false;
        orb.setDead();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) deaths.keySet()
            .removeIf(victim -> victim.worldObj == event.world);
    }

    private static final class DeathRecord {

        final UUID player;
        final long worldTick;

        DeathRecord(UUID player, long worldTick) {
            this.player = player;
            this.worldTick = worldTick;
        }
    }
}
