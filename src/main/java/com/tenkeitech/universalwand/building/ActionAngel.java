/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayList;
import java.util.List;

import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Upstream angel core searches behind a clicked surface and places exactly one block. */
public final class ActionAngel implements BuildingAction {

    @Override
    public List<BuildingSnapshot> getSnapshots(BuildingContext context, BuildingSupplier supplier, int limit) {
        List<BuildingSnapshot> snapshots = new ArrayList<>();
        if (limit <= 0 || !supplier.hasRemaining()) return snapshots;
        Position position = context.origin;
        int distance = context.air ? 1 : BuildingContext.ANGEL_DISTANCE;
        for (int step = 0; step < distance; step++) {
            if (!context.air) position = position.offset(context.face.getOpposite());
            if (!BuildingContext.loaded(context.world, position.x, position.y, position.z)) break;
            PlacementSnapshot snapshot = supplier.getPlaceSnapshot(context, context.origin, position);
            if (snapshot != null) {
                snapshots.add(snapshot);
                break;
            }
        }
        return snapshots;
    }
}
