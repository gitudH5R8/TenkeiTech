/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Adapted from ConstructionWand's surface destruction action at commit 15089d9. */
public final class ActionDestruction implements BuildingAction {

    @Override
    public List<BuildingSnapshot> getSnapshots(BuildingContext context, BuildingSupplier supplier, int limit) {
        List<BuildingSnapshot> snapshots = new ArrayList<>();
        ArrayDeque<Position> candidates = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        candidates.add(context.origin);
        int[][] plane = context.face.offsetY != 0 ? new int[][] { { 1, 0, 0 }, { 0, 0, 1 } }
            : context.face.offsetX != 0 ? new int[][] { { 0, 1, 0 }, { 0, 0, 1 } }
                : new int[][] { { 1, 0, 0 }, { 0, 1, 0 } };
        int countLimit = Math.min(BuildingContext.DESTRUCTION_BLOCKS, Math.max(0, limit));
        while (!candidates.isEmpty() && snapshots.size() < countLimit) {
            Position position = candidates.removeFirst();
            if (!seen.add(position.key())) continue;
            DestructionSnapshot snapshot = DestructionSnapshot.create(context, position);
            if (snapshot == null) continue;
            snapshots.add(snapshot);
            for (int first = -1; first <= 1; first++) {
                for (int second = -1; second <= 1; second++) {
                    if (first == 0 && second == 0) continue;
                    candidates.addLast(
                        new Position(
                            position.x + first * plane[0][0] + second * plane[1][0],
                            position.y + first * plane[0][1] + second * plane[1][1],
                            position.z + first * plane[0][2] + second * plane[1][2]));
                }
            }
        }
        return snapshots;
    }
}
