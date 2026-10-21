/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.gui.MTEWirelessNetworkCoreGui;
import com.tenkeitech.wireless.WirelessAeAccessPolicy;
import com.tenkeitech.wireless.WirelessAeChannelExemption.Lease;
import com.tenkeitech.wireless.WirelessAeConnectionFactory;
import com.tenkeitech.wireless.WirelessAeConnectionFactory.Result;
import com.tenkeitech.wireless.WirelessAeConnectionFactory.Status;
import com.tenkeitech.wireless.WirelessAeRuntimeRegistry;
import com.tenkeitech.wireless.WirelessAeTargetLocation;
import com.tenkeitech.wireless.WirelessAeTargetResolver;
import com.tenkeitech.wireless.WirelessAeWorldData;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;

/** Wireless ME network core. Persistent identity and runtime connections stay separate. */
@IMetaTileEntity.SkipGenerateDescription
public final class MTEWirelessNetworkCore extends MTEHatch implements IGridProxyable {

    public static final int MAX_NETWORK_NAME_LENGTH = 32;
    private static final int NBT_SCHEMA_VERSION = 4;
    private static final int NBT_COMPOUND = 10;
    private static final int DIAGNOSTIC_WINDOW_SECONDS = 30;
    private static final int DIAGNOSTIC_TICKS_PER_BUCKET = 20;
    private static final String NBT_SCHEMA = "tenkeitechWirelessCoreSchema";
    private static final String NBT_FREQUENCY_MOST = "wirelessFrequencyMost";
    private static final String NBT_FREQUENCY_LEAST = "wirelessFrequencyLeast";
    private static final String NBT_OWNER_MOST = "wirelessOwnerMost";
    private static final String NBT_OWNER_LEAST = "wirelessOwnerLeast";
    private static final String NBT_NETWORK_NAME = "wirelessNetworkName";
    private static final String NBT_MEMBERS = "wirelessMembers";
    private static final String NBT_MEMBER_TARGET = "target";
    private static final String NBT_MEMBER_TOKEN_MOST = "tokenMost";
    private static final String NBT_MEMBER_TOKEN_LEAST = "tokenLeast";
    private static final IIconContainer WIRELESS_CORE_FRONT = Textures.BlockIcons
        .custom("tenkeitech:machine/wireless_network_core_front");

    private final List<MemberBinding> members = new ArrayList<>();
    private AENetworkProxy gridProxy;
    private UUID frequency;
    private UUID owner;
    private String networkName;
    private boolean gridProxyInvalidated;
    private int maintenanceCursor;
    private final RollingWindowCounter diagnosticConnectionAttempts = new RollingWindowCounter();
    private final RollingWindowCounter diagnosticConnectionFailures = new RollingWindowCounter();
    private final RollingWindowCounter diagnosticResolverFailures = new RollingWindowCounter();
    private long diagnosticLastConnectionTick = Long.MIN_VALUE;
    private long diagnosticLastConnectionMicros;
    private Status diagnosticLastConnectionStatus;

    public MTEWirelessNetworkCore(int id, String name, String regionalName) {
        super(id, name, regionalName, 1, 0, (String) null);
    }

    public MTEWirelessNetworkCore(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEWirelessNetworkCore(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture getCasingTexture() {
        return WirelessMachineTextures.evCasing(ForgeDirection.NORTH);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture casing = WirelessMachineTextures.evCasing(side);
        return side == facing ? getTexturesInactive(casing) : new ITexture[] { casing };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return getTexturesInactive(baseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(WIRELESS_CORE_FRONT) };
    }

    @Override
    public void onFirstTick(IGregTechTileEntity baseMetaTileEntity) {
        super.onFirstTick(baseMetaTileEntity);
        if (!baseMetaTileEntity.isServerSide()) return;
        ensureFrequency();
        ensureNetworkName();
        claimOwnerFromBase(baseMetaTileEntity);
        syncNetworkRecord();
        WirelessAeRuntimeRegistry.registerCore(this, frequency);
        validateAndRegisterMembers();
        if (Config.enableWirelessAe) getProxy().onReady();
        maintenanceCursor = 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseMetaTileEntity, long timer) {
        super.onPostTick(baseMetaTileEntity, timer);
        if (!baseMetaTileEntity.isServerSide() || !Config.enableWirelessAe || members.isEmpty()) return;

        int memberBudget = Math.min(members.size(), Config.wirelessMembersProcessedPerTick);
        for (int processed = 0; processed < memberBudget && !members.isEmpty(); processed++) {
            maintainNextMember(baseMetaTileEntity.getWorld(), timer);
        }
    }

    private void maintainNextMember(World world, long timer) {
        if (maintenanceCursor >= members.size()) maintenanceCursor = 0;
        MemberBinding member = members.get(maintenanceCursor);
        boolean removed = maintainMember(world, member, timer);
        if (members.isEmpty()) {
            maintenanceCursor = 0;
        } else if (!removed) {
            maintenanceCursor = (maintenanceCursor + 1) % members.size();
        } else if (maintenanceCursor >= members.size()) {
            maintenanceCursor = 0;
        }
    }

    @Override
    public void onUnload() {
        WirelessAeRuntimeRegistry.unregisterCore(this, frequency);
        unregisterAndDisconnectAllMembers();
        if (gridProxy != null && !gridProxyInvalidated) gridProxy.onChunkUnload();
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        removeNetworkRecord();
        WirelessAeRuntimeRegistry.unregisterCore(this, frequency);
        releaseAllMembers(true);
        invalidateGridProxy();
        super.onRemoval();
    }

    @Override
    public void onBlockDestroyed() {
        removeNetworkRecord();
        WirelessAeRuntimeRegistry.unregisterCore(this, frequency);
        releaseAllMembers(true);
        invalidateGridProxy();
        super.onBlockDestroyed();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player, ForgeDirection side,
        float hitX, float hitY, float hitZ) {
        return super.onRightclick(baseMetaTileEntity, player, side, hitX, hitY, hitZ);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        if (baseMetaTileEntity.isServerSide()) claimOrCheckOwner(player);
        openGui(player);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEWirelessNetworkCoreGui(this, data.getPlayer()).build(data, syncManager, uiSettings);
    }

    public BindResult bindTarget(EntityPlayer player, WirelessAeTargetLocation newTarget) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !Config.enableWirelessAe) return BindResult.CORE_OFFLINE;
        if (!canUseNetwork(player)) return BindResult.ACCESS_DENIED;
        if (newTarget == null || isOwnLocation(newTarget)) return BindResult.INVALID_TARGET;
        if (!isTargetDimensionAllowed(newTarget)) return BindResult.CROSS_DIMENSION_DISABLED;

        WirelessAeWorldData worldData = WirelessAeWorldData.get(base.getWorld());
        MemberBinding existingMember = findMemberAtBlock(newTarget);
        if (existingMember != null) {
            if (worldData.matches(existingMember.target, frequency, existingMember.bindingToken)) {
                return BindResult.MEMBER_ALREADY_BOUND;
            }
            removeMember(existingMember, true);
        }
        if (members.size() >= Config.wirelessMaxMembers) return BindResult.MEMBER_LIMIT;
        if (!worldData.canBind(newTarget, frequency)) return BindResult.TARGET_ALREADY_BOUND;

        IGridNode candidateNode = WirelessAeTargetResolver.resolveLoaded(base.getWorld(), newTarget);
        IGridNode bridgeNode = getGridNode(ForgeDirection.UNKNOWN);
        if (candidateNode == null) recordDiagnosticResolverFailure();
        if (candidateNode == null || bridgeNode == null
            || candidateNode.getMachine() instanceof MTEWirelessNetworkCore) {
            return BindResult.INVALID_TARGET;
        }

        Result result = connectWithDiagnostics(bridgeNode, candidateNode);
        if (!result.accepted()) return mapFailure(result.status());

        UUID bindingToken = null;
        MemberBinding member = null;
        boolean committed = false;
        try {
            bindingToken = worldData.bind(newTarget, frequency);
            if (bindingToken == null) return BindResult.TARGET_ALREADY_BOUND;

            member = new MemberBinding(newTarget, bindingToken);
            installRuntimeConnection(member, result, bridgeNode, candidateNode);
            member.nextReconnectTick = base.getTimer() + Config.wirelessReconnectIntervalTicks;
            members.add(member);
            WirelessAeRuntimeRegistry.register(this, member.target);
            markDirty();
            committed = true;
            return result.status() == Status.ALREADY_SAME_GRID ? BindResult.ALREADY_SAME_GRID : BindResult.BOUND;
        } finally {
            if (!committed) {
                rollbackUncommittedBinding(worldData, newTarget, bindingToken, member, result);
            }
        }
    }

    /** Removes one loaded member only after revalidating team access and its persistent membership token. */
    public UnbindResult unbindTarget(EntityPlayer player, WirelessAeTargetLocation target) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !Config.enableWirelessAe) return UnbindResult.CORE_OFFLINE;
        if (!canUseNetwork(player)) return UnbindResult.ACCESS_DENIED;
        if (target == null) return UnbindResult.NOT_BOUND;

        MemberBinding member = findMemberAtBlock(target);
        if (member == null || !WirelessAeWorldData.get(base.getWorld())
            .matches(member.target, frequency, member.bindingToken)) {
            return UnbindResult.NOT_BOUND;
        }
        removeMember(member, true);
        return UnbindResult.UNBOUND;
    }

    /** Read-only runtime status for Waila and diagnostics; never attempts a reconnect or loads a chunk. */
    public boolean isTargetConnected(WirelessAeTargetLocation target) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || target == null) {
            return false;
        }
        MemberBinding member = findMemberAtBlock(target);
        return member != null && isRuntimeConnectionAlive(member);
    }

    public boolean matchesFrequency(long most, long least) {
        return frequency != null && frequency.getMostSignificantBits() == most
            && frequency.getLeastSignificantBits() == least;
    }

    public boolean isOwner(EntityPlayer player) {
        return player != null && owner != null && owner.equals(player.getUniqueID());
    }

    public boolean canUseNetwork(EntityPlayer player) {
        return player != null && WirelessAeAccessPolicy.canUseNetwork(player.getUniqueID(), owner);
    }

    public String getOwnerGroupDisplayName() {
        return WirelessAeAccessPolicy.getOwnerGroupDisplayName(owner);
    }

    public String getNetworkName() {
        return networkName == null ? "" : networkName;
    }

    public boolean renameNetwork(EntityPlayer player, String requestedName) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        String normalized = normalizeNetworkName(requestedName);
        if (base == null || !base.isServerSide() || !isOwner(player) || normalized == null) return false;
        ensureFrequency();
        ensureNetworkName();
        if (normalized.equals(networkName)) return true;

        WirelessAeWorldData worldData = WirelessAeWorldData.get(base.getWorld());
        syncNetworkRecord();
        if (!worldData.updateNetworkName(
            frequency,
            owner,
            normalized,
            base.getWorld().provider.dimensionId,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord())) {
            return false;
        }
        networkName = normalized;
        markDirty();
        return true;
    }

    public static String sanitizeNetworkNameInput(String requestedName) {
        String sanitized = stripUnsupportedNameCharacters(requestedName);
        return sanitized.length() <= MAX_NETWORK_NAME_LENGTH ? sanitized
            : sanitized.substring(0, MAX_NETWORK_NAME_LENGTH);
    }

    public boolean clearDestroyedTarget(World world, int x, int y, int z) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || world == null || world.isRemote) return false;

        boolean removed = false;
        Iterator<MemberBinding> iterator = members.iterator();
        while (iterator.hasNext()) {
            MemberBinding member = iterator.next();
            if (!isSameBlock(member.target, world.provider.dimensionId, x, y, z)) continue;
            releaseMember(member, true);
            iterator.remove();
            removed = true;
        }
        if (removed) {
            clampMaintenanceCursor();
            markDirty();
        }
        return removed;
    }

    /** Runtime-only cleanup for a target dimension unload; persistent membership remains reconnectable. */
    public void disconnectRuntimeTargetsInDimension(int dimensionId) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        for (MemberBinding member : members) {
            if (member.target != null && member.target.dimensionId() == dimensionId) disconnectRuntime(member);
        }
    }

    private boolean claimOrCheckOwner(EntityPlayer player) {
        if (player == null) return false;
        claimOwnerFromBase(getBaseMetaTileEntity());
        if (owner == null) {
            owner = player.getUniqueID();
            getProxy().setOwner(player);
            syncNetworkRecord();
            markDirty();
            return true;
        }
        if (!owner.equals(player.getUniqueID())) return false;
        getProxy().setOwner(player);
        return true;
    }

    private void claimOwnerFromBase(@Nullable IGregTechTileEntity base) {
        if (owner != null || base == null) return;
        UUID tileOwner = base.getOwnerUuid();
        if (tileOwner == null || tileOwner.equals(GTUtility.defaultUuid)) return;
        owner = tileOwner;
        markDirty();
    }

    private void ensureFrequency() {
        if (frequency != null) return;
        frequency = UUID.randomUUID();
        markDirty();
    }

    private void ensureNetworkName() {
        String normalized = normalizeNetworkName(networkName);
        if (normalized != null) {
            if (!normalized.equals(networkName)) markDirty();
            networkName = normalized;
            return;
        }
        ensureFrequency();
        networkName = "Wireless " + frequency.toString()
            .substring(0, 8);
        markDirty();
    }

    private void syncNetworkRecord() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || frequency == null || owner == null || networkName == null) return;
        WirelessAeWorldData.get(base.getWorld())
            .upsertNetwork(
                frequency,
                owner,
                networkName,
                base.getWorld().provider.dimensionId,
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord());
    }

    private void removeNetworkRecord() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || frequency == null) return;
        WirelessAeWorldData.get(base.getWorld())
            .removeNetwork(
                frequency,
                base.getWorld().provider.dimensionId,
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord());
    }

    @Nullable
    private static String normalizeNetworkName(String requestedName) {
        String sanitized = sanitizeNetworkNameInput(requestedName).trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static String stripUnsupportedNameCharacters(String requestedName) {
        if (requestedName == null || requestedName.isEmpty()) return "";
        StringBuilder result = new StringBuilder(requestedName.length());
        for (int index = 0; index < requestedName.length(); index++) {
            char character = requestedName.charAt(index);
            if (character != '\u00a7' && !Character.isISOControl(character)) result.append(character);
        }
        return result.toString();
    }

    private void validateAndRegisterMembers() {
        boolean changed = false;
        int index = 0;
        while (index < members.size()) {
            MemberBinding member = members.get(index);
            MemberBinding duplicate = findMemberAtSameBlockBefore(index, member.target);
            if (duplicate != null || !validatePersistentMembership(member)) {
                if (duplicate == null || !duplicate.target.equals(member.target)) {
                    unbindPersistentMembership(member);
                }
                disconnectRuntime(member);
                members.remove(index);
                changed = true;
                continue;
            }
            WirelessAeRuntimeRegistry.register(this, member.target);
            index++;
        }
        if (changed) markDirty();
    }

    private boolean validatePersistentMembership(MemberBinding member) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || frequency == null || member.target == null || member.bindingToken == null) {
            return false;
        }
        return WirelessAeWorldData.get(base.getWorld())
            .matches(member.target, frequency, member.bindingToken);
    }

    private boolean maintainMember(World world, MemberBinding member, long timer) {
        if (!isTargetDimensionAllowed(member.target)) {
            disconnectRuntime(member);
            return false;
        }
        if (hasRuntimeState(member)) {
            if (isRuntimeConnectionAlive(member)) {
                if (member.runtimeChannelExemption != null) member.runtimeChannelExemption.reconcile();
                return false;
            }
            if (WirelessAeTargetResolver
                .isLoadedTargetMissingOrReplaced(world, member.target, member.runtimeTargetNode)) {
                removeMember(member, true);
                return true;
            }
            disconnectRuntime(member);
        }
        if (!hasRuntimeState(member) && timer >= member.nextReconnectTick) {
            if (attemptReconnect(member)) return true;
            member.nextReconnectTick = timer + Config.wirelessReconnectIntervalTicks;
        }
        return false;
    }

    /** @return true when the member was removed because its persistent identity is no longer valid. */
    private boolean attemptReconnect(MemberBinding member) {
        if (!Config.enableWirelessAe || isRuntimeConnectionAlive(member)) return false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !isTargetDimensionAllowed(member.target)) return false;
        if (member.bindingToken == null || !WirelessAeWorldData.get(base.getWorld())
            .matches(member.target, frequency, member.bindingToken)) {
            removeMember(member, true);
            return true;
        }

        IGridNode bridgeNode = getGridNode(ForgeDirection.UNKNOWN);
        IGridNode targetNode = WirelessAeTargetResolver.resolveLoaded(base.getWorld(), member.target);
        if (targetNode == null) recordDiagnosticResolverFailure();
        if (bridgeNode == null || targetNode == null || targetNode.getMachine() instanceof MTEWirelessNetworkCore)
            return false;

        Result result = connectWithDiagnostics(bridgeNode, targetNode);
        if (result.accepted()) {
            installRuntimeConnection(member, result, bridgeNode, targetNode);
            return false;
        }
        if (base.getTimer() - member.lastFailureLogTick >= 1_000) {
            TenkeiTech.LOG.warn(
                "Wireless AE reconnect for core {} member {} failed with status {}",
                getLocation(),
                member.target,
                result.status());
            member.lastFailureLogTick = base.getTimer();
        }
        return false;
    }

    private Result connectWithDiagnostics(IGridNode bridgeNode, IGridNode targetNode) {
        if (!Config.debugWirelessAe) return WirelessAeConnectionFactory.connect(bridgeNode, targetNode);

        long started = System.nanoTime();
        Result result = WirelessAeConnectionFactory.connect(bridgeNode, targetNode);
        long diagnosticTick = getDiagnosticTick();
        diagnosticLastConnectionTick = diagnosticTick;
        diagnosticLastConnectionMicros = Math.max(0, (System.nanoTime() - started) / 1_000L);
        diagnosticLastConnectionStatus = result.status();
        diagnosticConnectionAttempts.increment(diagnosticTick);
        if (!result.accepted()) diagnosticConnectionFailures.increment(diagnosticTick);
        return result;
    }

    private void recordDiagnosticResolverFailure() {
        if (Config.debugWirelessAe) diagnosticResolverFailures.increment(getDiagnosticTick());
    }

    private long getDiagnosticTick() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null ? 0 : base.getTimer();
    }

    private void installRuntimeConnection(MemberBinding member, Result result, IGridNode bridgeNode,
        IGridNode targetNode) {
        member.runtimeConnection = result.connection();
        member.runtimeBridgeNode = bridgeNode;
        member.runtimeTargetNode = targetNode;
        member.runtimeSameGrid = result.status() == Status.ALREADY_SAME_GRID;
        member.runtimeChannelExemption = result.channelExemption();
    }

    private static boolean hasRuntimeState(MemberBinding member) {
        return member.runtimeConnection != null || member.runtimeSameGrid;
    }

    private static boolean isRuntimeConnectionAlive(MemberBinding member) {
        if (!hasRuntimeState(member) || member.runtimeBridgeNode == null || member.runtimeTargetNode == null)
            return false;
        try {
            if (member.runtimeSameGrid) {
                return member.runtimeBridgeNode.getGrid() != null
                    && member.runtimeBridgeNode.getGrid() == member.runtimeTargetNode.getGrid();
            }
            return member.runtimeBridgeNode.getConnections()
                .contains(member.runtimeConnection)
                && member.runtimeTargetNode.getConnections()
                    .contains(member.runtimeConnection);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void disconnectRuntime(MemberBinding member) {
        IGridConnection connection = member.runtimeConnection;
        Lease channelExemption = member.runtimeChannelExemption;
        clearRuntimeReferences(member);
        destroyConnection(connection);
        if (channelExemption != null) channelExemption.close();
    }

    private static void destroyConnection(IGridConnection connection) {
        if (connection == null) return;
        try {
            connection.destroy();
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Failed to destroy a wireless AE connection cleanly", exception);
        }
    }

    private static void clearRuntimeReferences(MemberBinding member) {
        member.runtimeConnection = null;
        member.runtimeBridgeNode = null;
        member.runtimeTargetNode = null;
        member.runtimeSameGrid = false;
        member.runtimeChannelExemption = null;
    }

    private void rollbackUncommittedBinding(WirelessAeWorldData worldData, WirelessAeTargetLocation target,
        UUID bindingToken, MemberBinding member, Result result) {
        if (member != null) {
            WirelessAeRuntimeRegistry.unregister(this, member.target);
            members.remove(member);
            disconnectRuntime(member);
        } else {
            destroyConnection(result.connection());
            if (result.channelExemption() != null) result.channelExemption()
                .close();
        }
        if (bindingToken != null) worldData.unbind(target, frequency, bindingToken);
    }

    private void removeMember(MemberBinding member, boolean unbindPersistent) {
        releaseMember(member, unbindPersistent);
        members.remove(member);
        clampMaintenanceCursor();
        markDirty();
    }

    private void releaseMember(MemberBinding member, boolean unbindPersistent) {
        WirelessAeRuntimeRegistry.unregister(this, member.target);
        if (unbindPersistent) unbindPersistentMembership(member);
        disconnectRuntime(member);
    }

    private void unbindPersistentMembership(MemberBinding member) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()
            || base.getWorld() == null
            || member == null
            || member.target == null
            || frequency == null
            || member.bindingToken == null) {
            return;
        }
        WirelessAeWorldData.get(base.getWorld())
            .unbind(member.target, frequency, member.bindingToken);
    }

    private void unregisterAndDisconnectAllMembers() {
        for (MemberBinding member : members) {
            WirelessAeRuntimeRegistry.unregister(this, member.target);
            disconnectRuntime(member);
        }
    }

    private void releaseAllMembers(boolean unbindPersistent) {
        for (MemberBinding member : members) releaseMember(member, unbindPersistent);
        members.clear();
        maintenanceCursor = 0;
    }

    private MemberBinding findMemberAtBlock(WirelessAeTargetLocation location) {
        for (MemberBinding member : members) {
            if (isSameBlock(member.target, location)) return member;
        }
        return null;
    }

    private MemberBinding findMemberAtSameBlockBefore(int exclusiveEnd, WirelessAeTargetLocation location) {
        for (int index = 0; index < exclusiveEnd; index++) {
            MemberBinding member = members.get(index);
            if (isSameBlock(member.target, location)) return member;
        }
        return null;
    }

    private static boolean isSameBlock(WirelessAeTargetLocation first, WirelessAeTargetLocation second) {
        return first != null && second != null
            && isSameBlock(first, second.dimensionId(), second.x(), second.y(), second.z());
    }

    private static boolean isSameBlock(WirelessAeTargetLocation location, int dimensionId, int x, int y, int z) {
        return location != null && location.dimensionId() == dimensionId
            && location.x() == x
            && location.y() == y
            && location.z() == z;
    }

    private boolean isOwnLocation(WirelessAeTargetLocation location) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return location.dimensionId() == base.getWorld().provider.dimensionId && location.x() == base.getXCoord()
            && location.y() == base.getYCoord()
            && location.z() == base.getZCoord();
    }

    private boolean isTargetDimensionAllowed(WirelessAeTargetLocation location) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World coreWorld = base == null ? null : base.getWorld();
        return coreWorld != null && location != null
            && (location.dimensionId() == coreWorld.provider.dimensionId || Config.enableWirelessCrossDimension);
    }

    private void clampMaintenanceCursor() {
        if (members.isEmpty() || maintenanceCursor >= members.size()) maintenanceCursor = 0;
    }

    private void invalidateGridProxy() {
        if (gridProxy == null || gridProxyInvalidated) return;
        gridProxyInvalidated = true;
        gridProxy.invalidate();
    }

    private static BindResult mapFailure(Status status) {
        return switch (status) {
            case CONTROLLER_CONFLICT -> BindResult.CONTROLLER_CONFLICT;
            case SECURITY_REJECTED -> BindResult.SECURITY_REJECTED;
            case INVALID_NODE -> BindResult.INVALID_TARGET;
            default -> BindResult.FAILED;
        };
    }

    public String getFrequencyString() {
        return frequency == null ? "-" : frequency.toString();
    }

    public String getRuntimeStatusSummary() {
        if (!Config.enableWirelessAe) return StatCollector.translateToLocal("tenkeitech.wireless.status.disabled");

        DeviceStatistics statistics = getDeviceStatistics();
        return StatCollector.translateToLocalFormatted(
            "tenkeitech.wireless.status.members",
            statistics.totalDevices(),
            statistics.wirelessBindings(),
            statistics.carriedDevices());
    }

    public DeviceStatistics getDeviceStatistics() {
        Set<IGridNode> carriedDevices = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MemberBinding member : members) {
            if (!isRuntimeConnectionAlive(member)) continue;
            if (member.runtimeChannelExemption != null) {
                member.runtimeChannelExemption.addCarriedDevicesTo(carriedDevices);
            }
        }
        carriedDevices.removeIf(this::isBoundTargetNode);
        int wirelessBindings = members.size();
        return new DeviceStatistics(wirelessBindings + carriedDevices.size(), wirelessBindings, carriedDevices.size());
    }

    public DiagnosticStatistics getDiagnosticStatistics() {
        long diagnosticTick = getDiagnosticTick();
        long connectionAttempts = diagnosticConnectionAttempts.count(diagnosticTick);
        boolean hasRecentConnection = diagnosticLastConnectionTick != Long.MIN_VALUE
            && diagnosticTick >= diagnosticLastConnectionTick
            && diagnosticTick - diagnosticLastConnectionTick < DIAGNOSTIC_WINDOW_SECONDS * DIAGNOSTIC_TICKS_PER_BUCKET;
        int activeBindings = 0;
        Set<IGridNode> exemptNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MemberBinding member : members) {
            if (isRuntimeConnectionAlive(member)) activeBindings++;
            if (member.runtimeChannelExemption != null) member.runtimeChannelExemption.addExemptNodesTo(exemptNodes);
        }
        return new DiagnosticStatistics(
            activeBindings,
            members.size() - activeBindings,
            exemptNodes.size(),
            connectionAttempts,
            diagnosticConnectionFailures.count(diagnosticTick),
            diagnosticResolverFailures.count(diagnosticTick),
            hasRecentConnection && diagnosticLastConnectionStatus != null ? diagnosticLastConnectionStatus.name() : "-",
            hasRecentConnection ? diagnosticLastConnectionMicros : 0);
    }

    public boolean isConnectedToMeNetwork() {
        if (!Config.enableWirelessAe || gridProxyInvalidated) return false;
        try {
            return getProxy().isActive();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static final class DeviceStatistics {

        private final int totalDevices;
        private final int wirelessBindings;
        private final int carriedDevices;

        public DeviceStatistics(int totalDevices, int wirelessBindings, int carriedDevices) {
            this.totalDevices = totalDevices;
            this.wirelessBindings = wirelessBindings;
            this.carriedDevices = carriedDevices;
        }

        public int totalDevices() {
            return totalDevices;
        }

        public int wirelessBindings() {
            return wirelessBindings;
        }

        public int carriedDevices() {
            return carriedDevices;
        }
    }

    public static final class DiagnosticStatistics {

        private final int activeBindings;
        private final int offlineBindings;
        private final int exemptNodes;
        private final long connectionAttempts;
        private final long connectionFailures;
        private final long resolverFailures;
        private final String lastConnectionStatus;
        private final long lastConnectionMicros;

        private DiagnosticStatistics(int activeBindings, int offlineBindings, int exemptNodes, long connectionAttempts,
            long connectionFailures, long resolverFailures, String lastConnectionStatus, long lastConnectionMicros) {
            this.activeBindings = activeBindings;
            this.offlineBindings = offlineBindings;
            this.exemptNodes = exemptNodes;
            this.connectionAttempts = connectionAttempts;
            this.connectionFailures = connectionFailures;
            this.resolverFailures = resolverFailures;
            this.lastConnectionStatus = lastConnectionStatus;
            this.lastConnectionMicros = lastConnectionMicros;
        }

        public int activeBindings() {
            return activeBindings;
        }

        public int offlineBindings() {
            return offlineBindings;
        }

        public int exemptNodes() {
            return exemptNodes;
        }

        public long connectionAttempts() {
            return connectionAttempts;
        }

        public long connectionFailures() {
            return connectionFailures;
        }

        public long resolverFailures() {
            return resolverFailures;
        }

        public String lastConnectionStatus() {
            return lastConnectionStatus;
        }

        public long lastConnectionMicros() {
            return lastConnectionMicros;
        }
    }

    private static final class RollingWindowCounter {

        private final long[] bucketSeconds = new long[DIAGNOSTIC_WINDOW_SECONDS];
        private final long[] bucketCounts = new long[DIAGNOSTIC_WINDOW_SECONDS];

        private RollingWindowCounter() {
            clear();
        }

        private void increment(long tick) {
            long second = Math.max(0, tick) / DIAGNOSTIC_TICKS_PER_BUCKET;
            int bucket = (int) (second % DIAGNOSTIC_WINDOW_SECONDS);
            if (bucketSeconds[bucket] != second) {
                bucketSeconds[bucket] = second;
                bucketCounts[bucket] = 0;
            }
            bucketCounts[bucket]++;
        }

        private long count(long tick) {
            long currentSecond = Math.max(0, tick) / DIAGNOSTIC_TICKS_PER_BUCKET;
            long oldestSecond = currentSecond - DIAGNOSTIC_WINDOW_SECONDS + 1;
            long result = 0;
            for (int bucket = 0; bucket < DIAGNOSTIC_WINDOW_SECONDS; bucket++) {
                long second = bucketSeconds[bucket];
                if (second >= oldestSecond && second <= currentSecond) result += bucketCounts[bucket];
            }
            return result;
        }

        private void clear() {
            Arrays.fill(bucketSeconds, Long.MIN_VALUE);
            Arrays.fill(bucketCounts, 0);
        }
    }

    private boolean isBoundTargetNode(IGridNode node) {
        if (node == null) return false;
        try {
            DimensionalCoord location = node.getGridBlock()
                .getLocation();
            if (location == null) return false;
            for (MemberBinding member : members) {
                if (isSameBlock(member.target, location.getDimension(), location.x, location.y, location.z)) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }
        return false;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection side) {
        return Config.enableWirelessAe ? getProxy().getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        if (!Config.enableWirelessAe || getBaseMetaTileEntity() == null) return AECableType.NONE;
        return side == getBaseMetaTileEntity().getFrontFacing() ? AECableType.DENSE : AECableType.NONE;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != null && facing != ForgeDirection.UNKNOWN;
    }

    private void updateValidGridProxySides() {
        if (getBaseMetaTileEntity() == null) return;
        getProxy().setValidSides(EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
    }

    @Override
    public void onFacingChange() {
        updateValidGridProxySides();
    }

    @Override
    public void onColorChangeServer(byte color) {
        AENetworkProxy proxy = getProxy();
        byte paint = getColor();
        proxy.setColor(paint == -1 ? AEColor.Transparent : AEColor.values()[Dyes.transformDyeIndex(paint)]);
        if (proxy.getNode() != null) proxy.getNode()
            .updateState();
    }

    @Override
    public void securityBreak() {}

    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            gridProxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            gridProxy.setFlags(GridFlags.DENSE_CAPACITY);
            gridProxy.setIdlePowerUsage(0);
            updateValidGridProxySides();
        }
        return gridProxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return new DimensionalCoord(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void gridChanged() {
        for (MemberBinding member : members) member.nextReconnectTick = 0;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setInteger(NBT_SCHEMA, NBT_SCHEMA_VERSION);
        if (frequency != null) {
            tag.setLong(NBT_FREQUENCY_MOST, frequency.getMostSignificantBits());
            tag.setLong(NBT_FREQUENCY_LEAST, frequency.getLeastSignificantBits());
        }
        if (owner != null) {
            tag.setLong(NBT_OWNER_MOST, owner.getMostSignificantBits());
            tag.setLong(NBT_OWNER_LEAST, owner.getLeastSignificantBits());
        }
        if (networkName != null) tag.setString(NBT_NETWORK_NAME, networkName);

        NBTTagList memberList = new NBTTagList();
        for (MemberBinding member : members) {
            if (member.target == null || member.bindingToken == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(NBT_MEMBER_TARGET, member.target.writeToNBT());
            entry.setLong(NBT_MEMBER_TOKEN_MOST, member.bindingToken.getMostSignificantBits());
            entry.setLong(NBT_MEMBER_TOKEN_LEAST, member.bindingToken.getLeastSignificantBits());
            memberList.appendTag(entry);
        }
        tag.setTag(NBT_MEMBERS, memberList);
        getProxy().writeToNBT(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        members.clear();
        frequency = null;
        owner = null;
        networkName = null;
        maintenanceCursor = 0;
        diagnosticConnectionAttempts.clear();
        diagnosticConnectionFailures.clear();
        diagnosticResolverFailures.clear();
        diagnosticLastConnectionTick = Long.MIN_VALUE;
        diagnosticLastConnectionMicros = 0;
        diagnosticLastConnectionStatus = null;
        int schema = tag.getInteger(NBT_SCHEMA);
        if (schema != NBT_SCHEMA_VERSION) {
            TenkeiTech.LOG
                .warn("Ignoring unsupported wireless core schema {}; expected schema {}", schema, NBT_SCHEMA_VERSION);
            onColorChangeServer(getColor());
            return;
        }
        if (tag.hasKey(NBT_FREQUENCY_MOST) && tag.hasKey(NBT_FREQUENCY_LEAST)) {
            frequency = new UUID(tag.getLong(NBT_FREQUENCY_MOST), tag.getLong(NBT_FREQUENCY_LEAST));
        }
        if (tag.hasKey(NBT_OWNER_MOST) && tag.hasKey(NBT_OWNER_LEAST)) {
            owner = new UUID(tag.getLong(NBT_OWNER_MOST), tag.getLong(NBT_OWNER_LEAST));
        }
        if (tag.hasKey(NBT_NETWORK_NAME)) networkName = normalizeNetworkName(tag.getString(NBT_NETWORK_NAME));

        NBTTagList memberList = tag.getTagList(NBT_MEMBERS, NBT_COMPOUND);
        for (int index = 0; index < memberList.tagCount(); index++) {
            NBTTagCompound entry = memberList.getCompoundTagAt(index);
            WirelessAeTargetLocation memberTarget = WirelessAeTargetLocation
                .readFromNBT(entry.getCompoundTag(NBT_MEMBER_TARGET));
            if (memberTarget == null || !entry.hasKey(NBT_MEMBER_TOKEN_MOST) || !entry.hasKey(NBT_MEMBER_TOKEN_LEAST)) {
                continue;
            }
            UUID bindingToken = new UUID(entry.getLong(NBT_MEMBER_TOKEN_MOST), entry.getLong(NBT_MEMBER_TOKEN_LEAST));
            members.add(new MemberBinding(memberTarget, bindingToken));
        }

        if (tag.hasKey("proxy")) getProxy().readFromNBT(tag);
        onColorChangeServer(getColor());
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        List<String> information = new ArrayList<>();
        information
            .add(StatCollector.translateToLocalFormatted("tenkeitech.wireless.info.frequency", getFrequencyString()));
        information
            .add(StatCollector.translateToLocalFormatted("tenkeitech.wireless.info.status", getRuntimeStatusSummary()));
        if (Config.debugWirelessAe) {
            DiagnosticStatistics diagnostics = getDiagnosticStatistics();
            information.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.wireless.diagnostics.connections",
                    diagnostics.activeBindings(),
                    diagnostics.offlineBindings(),
                    diagnostics.exemptNodes()));
            information.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.wireless.diagnostics.attempts",
                    diagnostics.connectionAttempts(),
                    diagnostics.connectionFailures(),
                    diagnostics.resolverFailures()));
            information.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.wireless.diagnostics.last",
                    diagnostics.lastConnectionStatus(),
                    diagnostics.lastConnectionMicros()));
        }
        return information.toArray(new String[0]);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("tenkeitech.machine.wireless_network_core.desc");
    }

    public enum BindResult {
        BOUND,
        ALREADY_SAME_GRID,
        MEMBER_ALREADY_BOUND,
        MEMBER_LIMIT,
        ACCESS_DENIED,
        CORE_OFFLINE,
        CROSS_DIMENSION_DISABLED,
        INVALID_TARGET,
        TARGET_ALREADY_BOUND,
        CONTROLLER_CONFLICT,
        SECURITY_REJECTED,
        FAILED
    }

    public enum UnbindResult {
        UNBOUND,
        NOT_BOUND,
        ACCESS_DENIED,
        CORE_OFFLINE
    }

    private static final class MemberBinding {

        private final WirelessAeTargetLocation target;
        private final UUID bindingToken;
        private IGridConnection runtimeConnection;
        private IGridNode runtimeBridgeNode;
        private IGridNode runtimeTargetNode;
        private boolean runtimeSameGrid;
        private Lease runtimeChannelExemption;
        private long nextReconnectTick;
        private long lastFailureLogTick = -1_000;

        private MemberBinding(WirelessAeTargetLocation target, UUID bindingToken) {
            this.target = target;
            this.bindingToken = bindingToken;
        }
    }
}
