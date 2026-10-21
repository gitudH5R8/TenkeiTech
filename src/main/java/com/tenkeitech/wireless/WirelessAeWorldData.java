/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;

import com.tenkeitech.TenkeiTech;

/** Persistent ownership ledger for wireless target identities. Runtime AE objects are never stored here. */
public final class WirelessAeWorldData extends WorldSavedData {

    private static final String DATA_NAME = "tenkeitech_wireless_ae_targets";
    private static final int NBT_SCHEMA_VERSION = 3;
    private static final String NBT_SCHEMA = "schema";
    private static final String NBT_MEMBERSHIPS = "memberships";
    private static final String NBT_NETWORKS = "networks";
    private static final String NBT_FAVORITES = "favorites";
    private static final String NBT_TARGET = "target";
    private static final String NBT_FREQUENCY_MOST = "frequencyMost";
    private static final String NBT_FREQUENCY_LEAST = "frequencyLeast";
    private static final String NBT_TOKEN_MOST = "tokenMost";
    private static final String NBT_TOKEN_LEAST = "tokenLeast";
    private static final String NBT_OWNER_MOST = "ownerMost";
    private static final String NBT_OWNER_LEAST = "ownerLeast";
    private static final String NBT_PLAYER_MOST = "playerMost";
    private static final String NBT_PLAYER_LEAST = "playerLeast";
    private static final String NBT_NAME = "name";
    private static final String NBT_DIMENSION = "dimension";
    private static final String NBT_X = "x";
    private static final String NBT_Y = "y";
    private static final String NBT_Z = "z";
    private static final int NBT_COMPOUND = 10;

    private final Map<WirelessAeTargetLocation, Membership> memberships = new HashMap<>();
    private final Map<UUID, NetworkRecord> networks = new HashMap<>();
    private final Map<CoreLocation, UUID> networkByCore = new HashMap<>();
    private final Map<UUID, UUID> favorites = new HashMap<>();

    public WirelessAeWorldData() {
        super(DATA_NAME);
    }

    public WirelessAeWorldData(String name) {
        super(name);
    }

    public static WirelessAeWorldData get(World world) {
        MapStorage storage = world.mapStorage;
        WirelessAeWorldData data = (WirelessAeWorldData) storage.loadData(WirelessAeWorldData.class, DATA_NAME);
        if (data == null) {
            data = new WirelessAeWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    @Nullable
    public static WirelessAeWorldData getIfPresent(World world) {
        return (WirelessAeWorldData) world.mapStorage.loadData(WirelessAeWorldData.class, DATA_NAME);
    }

    public synchronized boolean canBind(WirelessAeTargetLocation target, UUID frequency) {
        if (target == null || frequency == null) return false;
        return !hasForeignMembershipAtBlock(target, frequency);
    }

    @Nullable
    public synchronized UUID bind(WirelessAeTargetLocation target, UUID frequency) {
        if (target == null || frequency == null) return null;
        if (hasForeignMembershipAtBlock(target, frequency)) return null;
        Membership existing = memberships.get(target);
        if (existing != null) return existing.frequency.equals(frequency) ? existing.token : null;

        UUID token = UUID.randomUUID();
        memberships.put(target, new Membership(frequency, token));
        markDirty();
        return token;
    }

    public synchronized boolean matches(WirelessAeTargetLocation target, UUID frequency, UUID token) {
        if (target == null || frequency == null || token == null) return false;
        Membership existing = memberships.get(target);
        return existing != null && existing.frequency.equals(frequency) && existing.token.equals(token);
    }

    public synchronized void unbind(WirelessAeTargetLocation target, UUID frequency, UUID token) {
        if (target == null || frequency == null || token == null) return;
        Membership existing = memberships.get(target);
        if (existing == null || !existing.frequency.equals(frequency) || !existing.token.equals(token)) return;
        memberships.remove(target);
        markDirty();
    }

    public synchronized void upsertNetwork(UUID frequency, UUID owner, String name, int dimensionId, int x, int y,
        int z) {
        if (frequency == null || owner == null || name == null || name.isEmpty()) return;
        NetworkRecord replacement = new NetworkRecord(frequency, owner, name, dimensionId, x, y, z);
        if (replacement.equals(networks.get(frequency))) return;
        NetworkRecord previous = networks.get(frequency);
        if (previous != null) networkByCore.remove(previous.location(), frequency);
        UUID displaced = networkByCore.put(replacement.location(), frequency);
        if (displaced != null && !displaced.equals(frequency)) {
            networks.remove(displaced);
            removeFavoritesFor(displaced);
        }
        networks.put(frequency, replacement);
        markDirty();
    }

    public synchronized boolean updateNetworkName(UUID frequency, UUID owner, String name, int dimensionId, int x,
        int y, int z) {
        if (frequency == null || owner == null || name == null || name.isEmpty()) return false;
        NetworkRecord existing = networks.get(frequency);
        if (existing == null || !existing.owner.equals(owner) || !existing.isAt(dimensionId, x, y, z)) {
            return false;
        }
        if (!existing.name.equals(name)) {
            networks.put(frequency, new NetworkRecord(frequency, owner, name, dimensionId, x, y, z));
            markDirty();
        }
        return true;
    }

    public synchronized void removeNetwork(UUID frequency, int dimensionId, int x, int y, int z) {
        if (frequency == null) return;
        NetworkRecord existing = networks.get(frequency);
        if (existing == null || !existing.isAt(dimensionId, x, y, z)) return;
        networks.remove(frequency);
        networkByCore.remove(existing.location(), frequency);
        removeFavoritesFor(frequency);
        markDirty();
    }

    public synchronized List<NetworkRecord> listAccessibleNetworks(UUID player) {
        List<NetworkRecord> result = new ArrayList<>();
        if (player == null) return result;
        for (NetworkRecord network : networks.values()) {
            if (hasAccess(player, network)) result.add(network);
        }
        result.sort(
            Comparator.comparing(NetworkRecord::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(NetworkRecord::dimensionId)
                .thenComparingInt(NetworkRecord::x)
                .thenComparingInt(NetworkRecord::y)
                .thenComparingInt(NetworkRecord::z)
                .thenComparing(network -> network.frequency.toString()));
        return result;
    }

    @Nullable
    public synchronized NetworkRecord getAccessibleNetwork(UUID player, UUID frequency) {
        NetworkRecord network = frequency == null ? null : networks.get(frequency);
        return network != null && hasAccess(player, network) ? network : null;
    }

    /** Public read-only network metadata used for in-world status displays. */
    @Nullable
    public synchronized NetworkRecord getNetwork(UUID frequency) {
        return frequency == null ? null : networks.get(frequency);
    }

    /** Returns the persistent wireless membership at a block without exposing its mutation token. */
    @Nullable
    public synchronized MembershipRecord getMembershipAtBlock(int dimensionId, int x, int y, int z) {
        for (ForgeDirection side : ForgeDirection.values()) {
            WirelessAeTargetLocation target = new WirelessAeTargetLocation(dimensionId, x, y, z, side);
            Membership membership = memberships.get(target);
            if (membership != null) return new MembershipRecord(target, membership.frequency);
        }
        return null;
    }

    @Nullable
    public synchronized UUID getFavorite(UUID player) {
        if (player == null) return null;
        UUID frequency = favorites.get(player);
        if (frequency == null) return null;
        NetworkRecord network = networks.get(frequency);
        if (network != null && hasAccess(player, network)) return frequency;
        favorites.remove(player);
        markDirty();
        return null;
    }

    /** Returns the player's accessible favorite without cleaning or otherwise mutating persistent state. */
    @Nullable
    public synchronized NetworkRecord getFavoriteNetwork(UUID player) {
        UUID frequency = player == null ? null : favorites.get(player);
        NetworkRecord network = frequency == null ? null : networks.get(frequency);
        return network != null && hasAccess(player, network) ? network : null;
    }

    public synchronized boolean setFavorite(UUID player, UUID frequency) {
        if (player == null || frequency == null) return false;
        NetworkRecord network = networks.get(frequency);
        if (network == null || !hasAccess(player, network)) return false;
        if (!frequency.equals(favorites.put(player, frequency))) markDirty();
        return true;
    }

    public synchronized void clearFavorite(UUID player) {
        if (player != null && favorites.remove(player) != null) markDirty();
    }

    public static void removeDestroyedBlock(World world, int x, int y, int z) {
        WirelessAeWorldData data = getIfPresent(world);
        if (data != null) data.removeBlock(world.provider.dimensionId, x, y, z);
    }

    private synchronized void removeBlock(int dimensionId, int x, int y, int z) {
        boolean changed = false;
        for (ForgeDirection side : ForgeDirection.values()) {
            changed |= memberships.remove(new WirelessAeTargetLocation(dimensionId, x, y, z, side)) != null;
        }
        UUID frequency = networkByCore.remove(new CoreLocation(dimensionId, x, y, z));
        if (frequency != null) {
            networks.remove(frequency);
            removeFavoritesFor(frequency);
            changed = true;
        }
        if (changed) markDirty();
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound tag) {
        memberships.clear();
        networks.clear();
        networkByCore.clear();
        favorites.clear();
        int schema = tag.getInteger(NBT_SCHEMA);
        if (schema != NBT_SCHEMA_VERSION) {
            TenkeiTech.LOG.warn(
                "Ignoring unsupported wireless AE world-data schema {}; expected schema {}",
                schema,
                NBT_SCHEMA_VERSION);
            return;
        }
        NBTTagList list = tag.getTagList(NBT_MEMBERSHIPS, NBT_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            WirelessAeTargetLocation target = WirelessAeTargetLocation.readFromNBT(entry.getCompoundTag(NBT_TARGET));
            if (target == null || !hasIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST)
                || !hasIdentity(entry, NBT_TOKEN_MOST, NBT_TOKEN_LEAST)) {
                continue;
            }
            UUID frequency = new UUID(entry.getLong(NBT_FREQUENCY_MOST), entry.getLong(NBT_FREQUENCY_LEAST));
            UUID token = new UUID(entry.getLong(NBT_TOKEN_MOST), entry.getLong(NBT_TOKEN_LEAST));
            if (!hasForeignMembershipAtBlock(target, frequency)) {
                memberships.put(target, new Membership(frequency, token));
            }
        }

        NBTTagList networkList = tag.getTagList(NBT_NETWORKS, NBT_COMPOUND);
        for (int index = 0; index < networkList.tagCount(); index++) {
            NBTTagCompound entry = networkList.getCompoundTagAt(index);
            if (!hasIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST)
                || !hasIdentity(entry, NBT_OWNER_MOST, NBT_OWNER_LEAST)) {
                continue;
            }
            UUID frequency = readIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST);
            UUID owner = readIdentity(entry, NBT_OWNER_MOST, NBT_OWNER_LEAST);
            String name = entry.getString(NBT_NAME);
            if (name == null || name.isEmpty()) continue;
            NetworkRecord network = new NetworkRecord(
                frequency,
                owner,
                name,
                entry.getInteger(NBT_DIMENSION),
                entry.getInteger(NBT_X),
                entry.getInteger(NBT_Y),
                entry.getInteger(NBT_Z));
            NetworkRecord previous = networks.get(frequency);
            if (previous != null) networkByCore.remove(previous.location(), frequency);
            UUID displaced = networkByCore.put(network.location(), frequency);
            if (displaced != null) networks.remove(displaced);
            networks.put(frequency, network);
        }

        NBTTagList favoriteList = tag.getTagList(NBT_FAVORITES, NBT_COMPOUND);
        for (int index = 0; index < favoriteList.tagCount(); index++) {
            NBTTagCompound entry = favoriteList.getCompoundTagAt(index);
            if (!hasIdentity(entry, NBT_PLAYER_MOST, NBT_PLAYER_LEAST)
                || !hasIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST)) {
                continue;
            }
            UUID player = readIdentity(entry, NBT_PLAYER_MOST, NBT_PLAYER_LEAST);
            UUID frequency = readIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST);
            if (networks.containsKey(frequency)) favorites.put(player, frequency);
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_SCHEMA, NBT_SCHEMA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<WirelessAeTargetLocation, Membership> membership : memberships.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                NBT_TARGET,
                membership.getKey()
                    .writeToNBT());
            entry.setLong(NBT_FREQUENCY_MOST, membership.getValue().frequency.getMostSignificantBits());
            entry.setLong(NBT_FREQUENCY_LEAST, membership.getValue().frequency.getLeastSignificantBits());
            entry.setLong(NBT_TOKEN_MOST, membership.getValue().token.getMostSignificantBits());
            entry.setLong(NBT_TOKEN_LEAST, membership.getValue().token.getLeastSignificantBits());
            list.appendTag(entry);
        }
        tag.setTag(NBT_MEMBERSHIPS, list);

        NBTTagList networkList = new NBTTagList();
        for (NetworkRecord network : networks.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            writeIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST, network.frequency);
            writeIdentity(entry, NBT_OWNER_MOST, NBT_OWNER_LEAST, network.owner);
            entry.setString(NBT_NAME, network.name);
            entry.setInteger(NBT_DIMENSION, network.dimensionId);
            entry.setInteger(NBT_X, network.x);
            entry.setInteger(NBT_Y, network.y);
            entry.setInteger(NBT_Z, network.z);
            networkList.appendTag(entry);
        }
        tag.setTag(NBT_NETWORKS, networkList);

        NBTTagList favoriteList = new NBTTagList();
        for (Map.Entry<UUID, UUID> favorite : favorites.entrySet()) {
            if (!networks.containsKey(favorite.getValue())) continue;
            NBTTagCompound entry = new NBTTagCompound();
            writeIdentity(entry, NBT_PLAYER_MOST, NBT_PLAYER_LEAST, favorite.getKey());
            writeIdentity(entry, NBT_FREQUENCY_MOST, NBT_FREQUENCY_LEAST, favorite.getValue());
            favoriteList.appendTag(entry);
        }
        tag.setTag(NBT_FAVORITES, favoriteList);
    }

    private static boolean hasIdentity(NBTTagCompound tag, String mostKey, String leastKey) {
        return tag.hasKey(mostKey) && tag.hasKey(leastKey);
    }

    private static UUID readIdentity(NBTTagCompound tag, String mostKey, String leastKey) {
        return new UUID(tag.getLong(mostKey), tag.getLong(leastKey));
    }

    private static void writeIdentity(NBTTagCompound tag, String mostKey, String leastKey, UUID identity) {
        tag.setLong(mostKey, identity.getMostSignificantBits());
        tag.setLong(leastKey, identity.getLeastSignificantBits());
    }

    private static boolean hasAccess(UUID player, NetworkRecord network) {
        return network != null && WirelessAeAccessPolicy.canUseNetwork(player, network.owner);
    }

    private void removeFavoritesFor(UUID frequency) {
        favorites.entrySet()
            .removeIf(
                favorite -> favorite.getValue()
                    .equals(frequency));
    }

    private boolean hasForeignMembershipAtBlock(WirelessAeTargetLocation target, UUID frequency) {
        for (ForgeDirection side : ForgeDirection.values()) {
            Membership existing = memberships
                .get(new WirelessAeTargetLocation(target.dimensionId(), target.x(), target.y(), target.z(), side));
            if (existing != null && !existing.frequency.equals(frequency)) return true;
        }
        return false;
    }

    private static final class Membership {

        private final UUID frequency;
        private final UUID token;

        private Membership(UUID frequency, UUID token) {
            this.frequency = frequency;
            this.token = token;
        }
    }

    /** Read-only projection used by server-authoritative management GUIs. */
    public static final class MembershipRecord {

        private final WirelessAeTargetLocation target;
        private final UUID frequency;

        private MembershipRecord(WirelessAeTargetLocation target, UUID frequency) {
            this.target = target;
            this.frequency = frequency;
        }

        public WirelessAeTargetLocation target() {
            return target;
        }

        public UUID frequency() {
            return frequency;
        }
    }

    private static final class CoreLocation {

        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;

        private CoreLocation(int dimensionId, int x, int y, int z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CoreLocation location)) return false;
            return dimensionId == location.dimensionId && x == location.x && y == location.y && z == location.z;
        }

        @Override
        public int hashCode() {
            int result = dimensionId;
            result = 31 * result + x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }

    public static final class NetworkRecord {

        private final UUID frequency;
        private final UUID owner;
        private final String name;
        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;

        private NetworkRecord(UUID frequency, UUID owner, String name, int dimensionId, int x, int y, int z) {
            this.frequency = frequency;
            this.owner = owner;
            this.name = name;
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public UUID frequency() {
            return frequency;
        }

        public UUID owner() {
            return owner;
        }

        public String name() {
            return name;
        }

        public int dimensionId() {
            return dimensionId;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        private boolean isAt(int dimensionId, int x, int y, int z) {
            return this.dimensionId == dimensionId && this.x == x && this.y == y && this.z == z;
        }

        private CoreLocation location() {
            return new CoreLocation(dimensionId, x, y, z);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NetworkRecord record)) return false;
            return dimensionId == record.dimensionId && x == record.x
                && y == record.y
                && z == record.z
                && frequency.equals(record.frequency)
                && owner.equals(record.owner)
                && name.equals(record.name);
        }

        @Override
        public int hashCode() {
            int result = frequency.hashCode();
            result = 31 * result + owner.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + dimensionId;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
