/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import javax.annotation.Nullable;

import com.tenkeitech.TenkeiTech;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelChanged;

/** Owns runtime-only channel exemptions for target-side branches connected through wireless boundaries. */
public final class WirelessAeChannelExemption {

    private static final Set<IGridConnection> WIRELESS_BOUNDARIES = newIdentitySet();

    private WirelessAeChannelExemption() {}

    @Nullable
    public static Lease acquire(IGridNode targetNode, boolean branchScoped) {
        if (targetNode == null) return null;

        Lease lease = new Lease(targetNode, branchScoped);
        Set<IGridNode> initialNodes = newIdentitySet();
        if (branchScoped) {
            try {
                initialNodes.addAll(collectTargetBranch(targetNode, snapshotBoundaries()));
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.warn("Failed to inspect a wireless AE target-side branch", exception);
                return null;
            }
        } else {
            initialNodes.add(targetNode);
        }

        if (lease.reconcileTo(initialNodes)) return lease;
        lease.close();
        return null;
    }

    private static AcquireResult acquireNode(IGridNode node) {
        final boolean requiresChannel;
        try {
            requiresChannel = node.getGridBlock()
                .hasFlag(GridFlags.REQUIRE_CHANNEL);
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Failed to inspect a wireless AE branch node's native channel requirement", exception);
            return AcquireResult.FAILED;
        }
        if (!requiresChannel) return AcquireResult.NOT_REQUIRED;
        if (!(node instanceof WirelessAeChannelExemptNode exemptNode)) {
            TenkeiTech.LOG.error("AE2 GridNode wireless channel exemption mixin is unavailable");
            return AcquireResult.FAILED;
        }

        boolean changedRequirement = exemptNode.tenkeitech$acquireWirelessChannelExemption();
        if (!changedRequirement) return AcquireResult.ACQUIRED;
        try {
            notifyChannelRequirementChanged(node);
            return AcquireResult.ACQUIRED;
        } catch (RuntimeException exception) {
            exemptNode.tenkeitech$releaseWirelessChannelExemption();
            try {
                notifyChannelRequirementChanged(node);
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            TenkeiTech.LOG.warn("Failed to apply a wireless AE branch channel exemption", exception);
            return AcquireResult.FAILED;
        }
    }

    private static void releaseNode(IGridNode node) {
        if (!(node instanceof WirelessAeChannelExemptNode exemptNode)
            || !exemptNode.tenkeitech$isWirelessChannelExempt()
            || !exemptNode.tenkeitech$releaseWirelessChannelExemption()) {
            return;
        }

        try {
            notifyChannelRequirementChanged(node);
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Failed to restore a wireless AE branch node's native channel requirement", exception);
        }
    }

    private static Set<IGridNode> collectTargetBranch(IGridNode targetNode, Set<IGridConnection> boundaries) {
        Set<IGridNode> visited = newIdentitySet();
        ArrayDeque<IGridNode> pending = new ArrayDeque<>();
        pending.add(targetNode);
        while (!pending.isEmpty()) {
            IGridNode node = pending.removeFirst();
            if (!visited.add(node)) continue;

            for (IGridConnection connection : node.getConnections()) {
                if (boundaries.contains(connection)) continue;
                IGridNode otherNode = connection.getOtherSide(node);
                if (otherNode != null && !visited.contains(otherNode)) pending.addLast(otherNode);
            }
        }
        return visited;
    }

    private static void notifyChannelRequirementChanged(IGridNode node) {
        IGrid grid = node.getGrid();
        if (grid != null) grid.postEvent(new MENetworkChannelChanged(node));
    }

    private static synchronized void registerBoundary(IGridConnection connection) {
        WIRELESS_BOUNDARIES.add(connection);
    }

    private static synchronized void unregisterBoundary(IGridConnection connection) {
        WIRELESS_BOUNDARIES.remove(connection);
    }

    private static synchronized Set<IGridConnection> snapshotBoundaries() {
        Set<IGridConnection> snapshot = newIdentitySet();
        snapshot.addAll(WIRELESS_BOUNDARIES);
        return snapshot;
    }

    private static <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private enum AcquireResult {
        ACQUIRED,
        NOT_REQUIRED,
        FAILED
    }

    public static final class Lease {

        private final IGridNode targetNode;
        private final boolean branchScoped;
        private final Set<IGridNode> ownedNodes = newIdentitySet();

        private IGridNode bridgeNode;
        private IGridConnection boundary;
        private IGrid lastGrid;
        private int lastGridNodeCount = -1;
        private boolean closed;
        private boolean ambiguityLogged;
        private boolean reconciliationFailureLogged;

        private Lease(IGridNode targetNode, boolean branchScoped) {
            this.targetNode = targetNode;
            this.branchScoped = branchScoped;
        }

        public void attach(IGridNode bridgeNode, @Nullable IGridConnection boundary) {
            if (closed) throw new IllegalStateException("Cannot attach a closed wireless AE channel exemption");
            this.bridgeNode = bridgeNode;
            this.boundary = boundary;
            if (boundary != null) registerBoundary(boundary);
            lastGrid = null;
            lastGridNodeCount = -1;
        }

        public void reconcile() {
            if (closed || !branchScoped || boundary == null) return;

            try {
                IGrid grid = targetNode.getGrid();
                if (grid == null) return;
                int gridNodeCount = grid.getNodes()
                    .size();
                if (grid == lastGrid && gridNodeCount == lastGridNodeCount) return;

                Set<IGridNode> desiredNodes = collectTargetBranch(targetNode, snapshotBoundaries());
                if (desiredNodes.contains(bridgeNode)) {
                    if (!ambiguityLogged) {
                        TenkeiTech.LOG.warn(
                            "Wireless AE branch reached its core without crossing the owned virtual boundary; "
                                + "retaining the last safe channel-exemption scope");
                        ambiguityLogged = true;
                    }
                    return;
                }
                if (!reconcileTo(desiredNodes)) return;

                ambiguityLogged = false;
                reconciliationFailureLogged = false;
                lastGrid = grid;
                lastGridNodeCount = gridNodeCount;
            } catch (RuntimeException exception) {
                if (!reconciliationFailureLogged) {
                    TenkeiTech.LOG.warn("Failed to reconcile a wireless AE target-side channel exemption", exception);
                    reconciliationFailureLogged = true;
                }
            }
        }

        public void addCarriedDevicesTo(Set<IGridNode> devices) {
            if (closed || devices == null) return;
            for (IGridNode node : ownedNodes) {
                if (node != targetNode) devices.add(node);
            }
        }

        public void addExemptNodesTo(Set<IGridNode> nodes) {
            if (!closed && nodes != null) nodes.addAll(ownedNodes);
        }

        public void close() {
            if (closed) return;
            closed = true;
            if (boundary != null) unregisterBoundary(boundary);
            for (IGridNode node : new ArrayList<>(ownedNodes)) releaseNode(node);
            ownedNodes.clear();
            bridgeNode = null;
            boundary = null;
            lastGrid = null;
            lastGridNodeCount = -1;
        }

        private boolean reconcileTo(Set<IGridNode> desiredNodes) {
            Set<IGridNode> newlyOwned = newIdentitySet();
            for (IGridNode node : desiredNodes) {
                if (ownedNodes.contains(node)) continue;
                AcquireResult result = acquireNode(node);
                if (result == AcquireResult.FAILED) {
                    for (IGridNode acquiredNode : newlyOwned) releaseNode(acquiredNode);
                    return false;
                }
                if (result == AcquireResult.ACQUIRED) newlyOwned.add(node);
            }

            ownedNodes.addAll(newlyOwned);
            for (IGridNode node : new ArrayList<>(ownedNodes)) {
                if (desiredNodes.contains(node)) continue;
                ownedNodes.remove(node);
                releaseNode(node);
            }
            return true;
        }
    }
}
