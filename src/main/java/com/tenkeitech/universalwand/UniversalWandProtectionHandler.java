/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.mixin.minecraft.UniversalWandPotionAccessor;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Protection never owns creative, damage-disable, invisibility or flight flags. */
public final class UniversalWandProtectionHandler {

    private static final Map<EntityPlayer, State> STATES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Long> CLIENT_PROTECTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CLIENT_HIDDEN = new ConcurrentHashMap<>();
    private static final ThreadLocal<java.util.Deque<Entity>> REMOVING = ThreadLocal
        .withInitial(java.util.ArrayDeque::new);

    public static boolean isProtected(Entity entity) {
        if (!(entity instanceof EntityPlayer player) || player.inventory == null || player.worldObj == null)
            return false;
        if (REMOVING.get()
            .contains(entity)) return false;
        if (player.worldObj.isRemote) {
            Long until = CLIENT_PROTECTED.get(player.getUniqueID());
            if (until != null && until <= System.nanoTime()) CLIENT_PROTECTED.remove(player.getUniqueID(), until);
            return until != null && until > System.nanoTime();
        }
        return hasSetting(player, false);
    }

    public static boolean ignoresTarget(Entity entity) {
        return entity instanceof EntityPlayer player && player.worldObj != null
            && !player.worldObj.isRemote
            && hasSetting(player, true);
    }

    private static boolean hasSetting(EntityPlayer player, boolean aggro) {
        if (player.inventory == null) return false;
        // EntityPlayer's constructor invokes position/health hooks before the MP manager is assigned.
        // Hand queries can invoke other mods' spectator checks, which require this manager.
        if (player instanceof EntityPlayerMP serverPlayer && serverPlayer.theItemInWorldManager == null) return false;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (matches(player.inventory.getStackInSlot(slot), aggro)) return true;
        }
        return Loader.isModLoaded("backhand")
            && (matches(UniversalWandBackhandIntegration.getOffhandItem(player), aggro)
                || matches(UniversalWandBackhandIntegration.getMainhandItem(player), aggro));
    }

    private static boolean matches(ItemStack stack, boolean aggro) {
        return stack != null && stack.getItem() instanceof ItemUniversalWand
            && (aggro ? ItemUniversalWand.isNoAggroEnabled(stack) : ItemUniversalWand.isPlayerProtectionEnabled(stack));
    }

    public static void receiveClientProtection(UUID player, boolean enabled) {
        if (enabled) CLIENT_PROTECTED.put(player, System.nanoTime() + 3_000_000_000L);
        else CLIENT_PROTECTED.remove(player);
    }

    public static void receiveClientStealth(UUID player, boolean enabled) {
        if (enabled) CLIENT_HIDDEN.put(player, System.nanoTime() + 3_000_000_000L);
        else CLIENT_HIDDEN.remove(player);
    }

    public static boolean isHidden(EntityPlayer player) {
        if (player == null || player.worldObj == null || !player.worldObj.isRemote) return false;
        Long until = CLIENT_HIDDEN.get(player.getUniqueID());
        if (until == null) return false;
        if (until > System.nanoTime()) return true;
        CLIENT_HIDDEN.remove(player.getUniqueID(), until);
        return false;
    }

    public static void clearAll() {
        STATES.clear();
        CLIENT_PROTECTED.clear();
        CLIENT_HIDDEN.clear();
        REMOVING.remove();
    }

    @SubscribeEvent
    public void onDisconnect(cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        CLIENT_PROTECTED.clear();
        CLIENT_HIDDEN.clear();
    }

    public static void beginRemoval(Entity entity) {
        REMOVING.get()
            .push(entity);
    }

    public static void endRemoval() {
        java.util.Deque<Entity> removing = REMOVING.get();
        removing.pop();
        if (removing.isEmpty()) REMOVING.remove();
    }

    public static float validMaximum(EntityPlayer player, float value) {
        State state = STATES.computeIfAbsent(player, ignored -> new State());
        if (Float.isFinite(value) && value >= 1) {
            state.maximum = value;
            return value;
        }
        return state.maximum;
    }

    public static float protectedHealth(EntityPlayer player, float requested) {
        float maximum = player.getMaxHealth();
        float current = player.getHealth();
        if (!Float.isFinite(requested) || !Float.isFinite(current) || current <= 0) return maximum;
        return Math.min(maximum, Math.max(current, requested));
    }

    public static boolean validPosition(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y)
            && Double.isFinite(z)
            && Math.abs(x) <= 30_000_000
            && Math.abs(z) <= 30_000_000;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (isProtected(event.entityLiving)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (isProtected(event.entityLiving)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (isProtected(event.entityLiving)) {
            event.setCanceled(true);
            event.entityLiving.setHealth(event.entityLiving.getMaxHealth());
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity.worldObj.isRemote) return;
        if (entity instanceof EntityLiving living && ignoresTarget(living.getAttackTarget()))
            living.setAttackTarget(null);
        if (ignoresTarget(entity.getAITarget())) entity.setRevengeTarget(null);
        if (entity instanceof EntityCreature creature && ignoresTarget(creature.getEntityToAttack()))
            creature.setTarget(null);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof EntityPlayerMP player) || event.phase != TickEvent.Phase.START) return;
        boolean enabled = isProtected(player);
        boolean hidden = !player.isDead && ignoresTarget(player);
        State state = STATES.computeIfAbsent(player, ignored -> new State());
        if (enabled != state.enabled || hidden != state.hidden || player.ticksExisted % 20 == 0) {
            UniversalWandNetwork.syncProtection(player, enabled, hidden);
            state.enabled = enabled;
            state.hidden = hidden;
        }
        if (!enabled) return;
        double base = player.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .getBaseValue();
        if (Double.isFinite(base) && base >= 1) state.base = base;
        else player.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(state.base);
        player.setHealth(player.getMaxHealth());
        player.extinguish();
        player.fallDistance = 0;
        player.setAir(300);
        player.deathTime = 0;
        player.hurtTime = 0;
        for (Object value : new ArrayList<>(player.getActivePotionEffects())) {
            PotionEffect effect = (PotionEffect) value;
            if (isNegative(effect)) player.removePotionEffect(effect.getPotionID());
        }
    }

    public static boolean isNegative(PotionEffect effect) {
        int id = effect.getPotionID();
        return id >= 0 && id < Potion.potionTypes.length
            && Potion.potionTypes[id] != null
            && ((UniversalWandPotionAccessor) Potion.potionTypes[id]).tenkeitech$isBadEffect();
    }

    @SubscribeEvent
    public void onLogout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.player);
        CLIENT_PROTECTED.remove(event.player.getUniqueID());
        CLIENT_HIDDEN.remove(event.player.getUniqueID());
        if (event.player instanceof EntityPlayerMP player) UniversalWandNetwork.syncProtection(player, false, false);
    }

    private static final class State {

        float maximum = 20;
        double base = 20;
        boolean enabled;
        boolean hidden;
    }
}
