/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.tenkeitech.Config;
import com.tenkeitech.ModItems;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.mixin.minecraft.UniversalWandFlightSpeedAccessor;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Long-lived effects and independently owned flight permission follow carried tools. */
public final class UniversalWandUtilityHandler {

    private static final String FLIGHT_OWNER = "tenkeitechWandFlightOwner";
    private static final String SPEED_OWNER = "tenkeitechWandFlightSpeed";
    private static final String FIRST_JOIN_GRANT = "tenkeitechUniversalWandGranted";
    private static final int[] SPEED_STEPS = { 0, 5, 10, 20, 40, 60 };
    private static final Map<java.util.UUID, Boolean> SPEED_REQUESTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile java.util.UUID clientSpeedPlayer;
    private static volatile float clientSpeed;
    private static volatile long clientSpeedNoticeUntil;
    private static volatile long clientAccelerationNoticeUntil;
    private static volatile int clientAccelerationRate;

    public static void showClientAccelerationNotice(int rate) {
        clientAccelerationRate = Math.max(0, rate);
        clientAccelerationNoticeUntil = System.nanoTime() + 2_000_000_000L;
    }

    public static long getClientAccelerationNoticeRemaining() {
        return Math.max(0, clientAccelerationNoticeUntil - System.nanoTime());
    }

    public static int getClientAccelerationRate() {
        return clientAccelerationRate;
    }

    private static final int EFFECT_DURATION = 20_000;
    private static final Map<EntityPlayerMP, State> STATES = new HashMap<>();
    private static final ThreadLocal<EntityPlayerMP> SYNCING = new ThreadLocal<>();
    private static String[] cachedEntries;
    private static Map<Integer, Integer> configuredEffects = new LinkedHashMap<>();

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
        State state = STATES.computeIfAbsent(player, ignored -> new State());
        boolean carried = isCarrying(player);
        boolean flight = carried && UniversalWandFeature.FLIGHT.allowed();
        Boolean decrease = SPEED_REQUESTS.remove(player.getUniqueID());
        int speed = getSpeedSetting(player);
        if (flight && decrease != null) {
            speed = Math.max(1, Math.min(5, speed + (decrease ? -1 : 1)));
            net.minecraft.nbt.NBTTagCompound persisted = player.getEntityData()
                .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
            persisted.setInteger("tenkeitechWandFlightSpeedStep", speed);
            player.getEntityData()
                .setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        state.desiredSpeed = SPEED_STEPS[speed] / 200F;
        float syncedSpeed = flight ? state.desiredSpeed : 0;
        if (flight && decrease != null || Float.compare(state.syncedSpeed, syncedSpeed) != 0
            || player.ticksExisted % 20 == 0) {
            UniversalWandNetwork.syncFlightSpeed(player, syncedSpeed, flight && decrease != null);
            state.syncedSpeed = syncedSpeed;
        }
        updateFlight(player, state, flight);
        updateEffects(player, carried ? effects() : java.util.Collections.emptyMap());
    }

    public static void requestSpeed(EntityPlayerMP player, boolean decrease) {
        if (!UniversalWandFeature.FLIGHT.allowed()) return;
        SPEED_REQUESTS.put(player.getUniqueID(), decrease);
    }

    public static void receiveClientSpeed(java.util.UUID player, float speed) {
        clientSpeed = Float.isFinite(speed) && speed >= 0.025F && speed <= 0.3F ? speed : 0;
        clientSpeedPlayer = player;
        if (player == null || clientSpeed == 0) clientSpeedNoticeUntil = 0;
        if (player == null) clientAccelerationNoticeUntil = 0;
    }

    public static void showClientSpeedNotice() {
        clientSpeedNoticeUntil = System.nanoTime() + 2_000_000_000L;
    }

    public static long getClientSpeedNoticeRemaining() {
        return Math.max(0, clientSpeedNoticeUntil - System.nanoTime());
    }

    public static float getClientSpeed(net.minecraft.entity.player.EntityPlayer player) {
        return UniversalWandFeature.FLIGHT.allowed() && player.worldObj.isRemote
            && player.getUniqueID()
                .equals(clientSpeedPlayer)
            && isCarrying(player) ? clientSpeed : 0;
    }

    public static boolean hasFlight(net.minecraft.entity.player.EntityPlayer player) {
        return UniversalWandFeature.FLIGHT.allowed() && isCarrying(player);
    }

    public static boolean isCarrying(net.minecraft.entity.player.EntityPlayer player) {
        if (player.isDead || player.getHealth() <= 0) return false;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemUniversalWand) return true;
        }
        if (!Loader.isModLoaded("backhand")) return false;
        ItemStack offhand = UniversalWandBackhandIntegration.getOffhandItem(player);
        ItemStack mainhand = UniversalWandBackhandIntegration.getMainhandItem(player);
        return offhand != null && offhand.getItem() instanceof ItemUniversalWand
            || mainhand != null && mainhand.getItem() instanceof ItemUniversalWand;
    }

    private static int getSpeedSetting(EntityPlayerMP player) {
        net.minecraft.nbt.NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey("tenkeitechWandFlightSpeedStep", 3)) return 2;
        return Math.max(1, Math.min(5, persisted.getInteger("tenkeitechWandFlightSpeedStep")));
    }

    private static void updateFlight(EntityPlayerMP player, State state, boolean enabled) {
        updateSpeed(player, state, enabled);
        if (player.capabilities.isCreativeMode) {
            state.flightOwned = false;
            player.getEntityData()
                .removeTag(FLIGHT_OWNER);
            return;
        }
        if (enabled) {
            if (!player.capabilities.allowFlying) {
                player.capabilities.allowFlying = true;
                state.flightOwned = true;
                player.getEntityData()
                    .setBoolean(FLIGHT_OWNER, true);
                syncAbilities(player);
            }
        } else if (state.flightOwned) {
            state.flightOwned = false;
            player.getEntityData()
                .removeTag(FLIGHT_OWNER);
            player.capabilities.allowFlying = false;
            player.capabilities.isFlying = false;
            player.fallDistance = 0;
            syncAbilities(player);
        }
    }

    private static void updateSpeed(EntityPlayerMP player, State state, boolean enabled) {
        float current = player.capabilities.getFlySpeed();
        if (state.speedOwned && Float.compare(current, state.appliedSpeed) != 0) {
            // Carrying the wand owns the global speed; retain an external update for later restoration.
            state.originalSpeed = current;
        }
        if (enabled) {
            float desired = state.desiredSpeed;
            if (!state.speedOwned && Float.compare(current, desired) != 0) {
                state.originalSpeed = current;
                state.speedOwned = true;
            }
            if (state.speedOwned && Float.compare(current, desired) != 0) {
                state.appliedSpeed = desired;
                net.minecraft.nbt.NBTTagCompound marker = new net.minecraft.nbt.NBTTagCompound();
                marker.setFloat("original", state.originalSpeed);
                marker.setFloat("applied", desired);
                player.getEntityData()
                    .setTag(SPEED_OWNER, marker);
                ((UniversalWandFlightSpeedAccessor) player.capabilities).tenkeitech$setFlySpeed(desired);
                syncAbilities(player);
            }
        } else if (!enabled) {
            if (state.speedOwned) {
                ((UniversalWandFlightSpeedAccessor) player.capabilities).tenkeitech$setFlySpeed(state.originalSpeed);
                state.speedOwned = false;
                player.getEntityData()
                    .removeTag(SPEED_OWNER);
                syncAbilities(player);
            }
        }
    }

    private static void syncAbilities(EntityPlayerMP player) {
        EntityPlayerMP previous = SYNCING.get();
        SYNCING.set(player);
        try {
            player.sendPlayerAbilities();
        } finally {
            if (previous == null) SYNCING.remove();
            else SYNCING.set(previous);
        }
    }

    public static void observeAbilitySync(EntityPlayerMP player) {
        if (SYNCING.get() == player) return;
        State state = STATES.get(player);
        if (state != null && state.flightOwned && player.capabilities.allowFlying) {
            state.flightOwned = false;
            player.getEntityData()
                .removeTag(FLIGHT_OWNER);
        }
    }

    private static void updateEffects(EntityPlayerMP player, Map<Integer, Integer> wanted) {
        // Scan inventory each tick, but only renew/create potion objects once per second.
        if (player.ticksExisted % 20 != 0) return;
        for (Map.Entry<Integer, Integer> entry : wanted.entrySet()) {
            Potion potion = Potion.potionTypes[entry.getKey()];
            PotionEffect active = player.getActivePotionEffect(potion);
            if (active != null && active.getDuration() >= 200) continue;
            PotionEffect effect = new PotionEffect(entry.getKey(), EFFECT_DURATION, entry.getValue(), true);
            player.addPotionEffect(effect);
        }
    }

    private static Map<Integer, Integer> effects() {
        String[] entries = Config.universalWandEffects;
        if (cachedEntries == entries) return configuredEffects;
        cachedEntries = entries;
        Map<Integer, Integer> resolved = new LinkedHashMap<>();
        for (String entry : entries) {
            try {
                String[] parts = entry.split(",", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                int level = Integer.parseInt(parts[1].trim());
                Potion potion = resolvePotion(parts[0].trim());
                if (potion == null || level < 1 || level > 256) throw new IllegalArgumentException();
                resolved.merge(potion.id, level - 1, Math::max);
            } catch (IllegalArgumentException invalid) {
                TenkeiTech.LOG.warn("Skipping invalid Universal Wand potion entry: {}", entry);
            }
        }
        configuredEffects = resolved;
        return resolved;
    }

    private static Potion resolvePotion(String name) {
        try {
            int id = Integer.parseInt(name);
            return id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
        } catch (NumberFormatException ignored) {
            for (Potion potion : Potion.potionTypes) {
                if (potion != null && name.equals(potion.getName())) return potion;
            }
            return null;
        }
    }

    private static void grantFirstJoinWand(EntityPlayerMP player) {
        if (!Config.universalWandGrantOnFirstJoin || player.worldObj.isRemote) return;
        net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
        net.minecraft.nbt.NBTTagCompound persisted = data
            .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.hasKey(FIRST_JOIN_GRANT)) return;
        ItemStack gift = new ItemStack(ModItems.UNIVERSAL_WAND);
        if (!player.inventory.addItemStackToInventory(gift) && player.entityDropItem(gift, 0.0F) == null) return;
        persisted.setBoolean(FIRST_JOIN_GRANT, true);
        data.setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG, persisted);
        player.inventory.markDirty();
        player.sendContainerToPlayer(player.inventoryContainer);
        player.addChatMessage(
            new net.minecraft.util.ChatComponentTranslation("tenkeitech.universalwand.first_join_gift"));
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        grantFirstJoinWand(player);
        if (player.getEntityData()
            .hasKey(SPEED_OWNER, 10)) {
            net.minecraft.nbt.NBTTagCompound marker = player.getEntityData()
                .getCompoundTag(SPEED_OWNER);
            float original = marker.getFloat("original");
            if (Float.compare(player.capabilities.getFlySpeed(), marker.getFloat("applied")) == 0
                && Float.isFinite(original)
                && original > 0) {
                ((UniversalWandFlightSpeedAccessor) player.capabilities).tenkeitech$setFlySpeed(original);
                syncAbilities(player);
            }
            player.getEntityData()
                .removeTag(SPEED_OWNER);
        }
        // Ability flags survive saves; discard only a grant explicitly tagged by this tool.
        if (player.getEntityData()
            .getBoolean(FLIGHT_OWNER)) {
            State state = new State();
            state.flightOwned = true;
            updateFlight(player, state, false);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP player) release(player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        for (EntityPlayerMP old : new java.util.ArrayList<>(STATES.keySet())) {
            if (!old.getUniqueID()
                .equals(player.getUniqueID())) continue;
            State oldState = STATES.get(old);
            boolean owned = oldState.flightOwned;
            if (oldState.speedOwned && Float.compare(player.capabilities.getFlySpeed(), oldState.appliedSpeed) == 0) {
                ((UniversalWandFlightSpeedAccessor) player.capabilities).tenkeitech$setFlySpeed(oldState.originalSpeed);
                player.getEntityData()
                    .removeTag(SPEED_OWNER);
                syncAbilities(player);
            }
            release(old);
            if (owned && !player.capabilities.isCreativeMode) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.getEntityData()
                    .removeTag(FLIGHT_OWNER);
                syncAbilities(player);
            }
        }
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP player) syncAbilities(player);
    }

    public static void clearAll() {
        for (EntityPlayerMP player : new java.util.ArrayList<>(STATES.keySet())) release(player);
        cachedEntries = null;
        SPEED_REQUESTS.clear();
    }

    private static void release(EntityPlayerMP player) {
        SPEED_REQUESTS.remove(player.getUniqueID());
        State state = STATES.remove(player);
        if (state == null) return;
        updateFlight(player, state, false);
        // Effects intentionally expire on their own after the last renewal, matching the
        // high-version carried-effect behavior.
    }

    private static final class State {

        boolean flightOwned;
        boolean speedOwned;
        float originalSpeed;
        float appliedSpeed;
        float desiredSpeed;
        float syncedSpeed = Float.NaN;
    }
}
