/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import javax.annotation.Nullable;

import com.tenkeitech.wireless.WirelessAeChannelExemption.Lease;

import appeng.api.AEApi;
import appeng.api.exceptions.ExistingConnectionException;
import appeng.api.exceptions.FailedConnection;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;

/** Narrow wrapper around AE2's public virtual-grid-connection API. */
public final class WirelessAeConnectionFactory {

    private WirelessAeConnectionFactory() {}

    public static Result connect(IGridNode bridgeNode, IGridNode targetNode) {
        Status preflight = preflight(bridgeNode, targetNode);
        if (preflight != Status.READY && preflight != Status.ALREADY_SAME_GRID) {
            return new Result(preflight, null, null);
        }

        Lease exemption = WirelessAeChannelExemption.acquire(targetNode, preflight == Status.READY);
        if (exemption == null) return new Result(Status.CHANNEL_EXEMPTION_FAILED, null, null);
        if (preflight == Status.ALREADY_SAME_GRID) {
            exemption.attach(bridgeNode, null);
            return new Result(Status.ALREADY_SAME_GRID, null, exemption);
        }
        boolean connected = false;

        try {
            IGridConnection connection = AEApi.instance()
                .createGridConnection(bridgeNode, targetNode);
            exemption.attach(bridgeNode, connection);
            Result result = new Result(Status.CONNECTED, connection, exemption);
            connected = true;
            return result;
        } catch (SecurityConnectionException exception) {
            return new Result(Status.SECURITY_REJECTED, null, null);
        } catch (ExistingConnectionException exception) {
            return new Result(Status.ALREADY_CONNECTED, null, null);
        } catch (FailedConnection exception) {
            return new Result(Status.FAILED, null, null);
        } finally {
            if (!connected) exemption.close();
        }
    }

    public static Status preflight(IGridNode bridgeNode, IGridNode targetNode) {
        if (bridgeNode == null || targetNode == null || bridgeNode == targetNode) return Status.INVALID_NODE;

        IGrid bridgeGrid = bridgeNode.getGrid();
        IGrid targetGrid = targetNode.getGrid();
        if (bridgeGrid != null && bridgeGrid == targetGrid) return Status.ALREADY_SAME_GRID;
        if (hasControllerConflictRisk(bridgeGrid, targetGrid)) return Status.CONTROLLER_CONFLICT;
        return Status.READY;
    }

    private static boolean hasControllerConflictRisk(IGrid bridgeGrid, IGrid targetGrid) {
        if (bridgeGrid == null || targetGrid == null || bridgeGrid == targetGrid) return false;
        ControllerState bridgeState = getControllerState(bridgeGrid);
        ControllerState targetState = getControllerState(targetGrid);
        if (bridgeState == ControllerState.CONTROLLER_CONFLICT || targetState == ControllerState.CONTROLLER_CONFLICT) {
            return true;
        }
        return bridgeState == ControllerState.CONTROLLER_ONLINE && targetState == ControllerState.CONTROLLER_ONLINE;
    }

    private static ControllerState getControllerState(IGrid grid) {
        IPathingGrid pathing = grid.getCache(IPathingGrid.class);
        return pathing == null ? ControllerState.NO_CONTROLLER : pathing.getControllerState();
    }

    public enum Status {
        READY,
        CONNECTED,
        ALREADY_SAME_GRID,
        ALREADY_CONNECTED,
        INVALID_NODE,
        CONTROLLER_CONFLICT,
        SECURITY_REJECTED,
        CHANNEL_EXEMPTION_FAILED,
        FAILED
    }

    public static final class Result {

        private final Status status;
        private final IGridConnection connection;
        private final Lease channelExemption;

        private Result(Status status, @Nullable IGridConnection connection, @Nullable Lease channelExemption) {
            this.status = status;
            this.connection = connection;
            this.channelExemption = channelExemption;
        }

        public Status status() {
            return status;
        }

        @Nullable
        public IGridConnection connection() {
            return connection;
        }

        @Nullable
        public Lease channelExemption() {
            return channelExemption;
        }

        public boolean accepted() {
            return status == Status.CONNECTED || status == Status.ALREADY_SAME_GRID;
        }
    }
}
