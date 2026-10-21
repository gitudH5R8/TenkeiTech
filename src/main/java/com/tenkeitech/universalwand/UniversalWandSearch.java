/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Resumable 26-neighbor or concentric sphere search. All world access belongs to the caller's tick. */
public final class UniversalWandSearch {

    @FunctionalInterface
    public interface Matcher {

        boolean matches(int x, int y, int z);
    }

    @FunctionalInterface
    public interface Result {

        void accept(int x, int y, int z);
    }

    private final int sx, sy, sz, limit, radius;
    private final boolean enhanced;
    private final Matcher matcher;
    private final ArrayDeque<int[]> frontier = new ArrayDeque<>();
    private final Set<Long> visited = new HashSet<>();
    private int[] current;
    private int neighbor;
    private int distance = 1, dx = -1, dy = -1, dz, outerZ = -1, innerZ;
    private int found;
    private boolean finished;
    private static final int[] SKIP = new int[0];

    UniversalWandSearch(int sx, int sy, int sz, int limit, boolean enhanced, int radius, Matcher matcher) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.limit = limit;
        this.enhanced = enhanced;
        this.radius = radius;
        this.matcher = matcher;
        finished = limit <= 0;
        if (!enhanced) {
            frontier.add(new int[] { sx, sy, sz });
            visited.add(key(sx, sy, sz));
        }
    }

    public int getFound() {
        return found;
    }

    public boolean isFinished() {
        return finished;
    }

    boolean matches(int x, int y, int z) {
        return matcher.matches(x, y, z);
    }

    public int advance(int budget, Result result) {
        int scanned = 0;
        while (!finished && scanned < budget) {
            int[] position = enhanced ? nextEnhanced() : nextConnected();
            if (position == null) {
                finished = true;
                break;
            }
            scanned++;
            if (position.length == 0) continue;
            int x = position[0], y = position[1], z = position[2];
            if (!enhanced && !visited.add(key(x, y, z))) continue;
            if (!matcher.matches(x, y, z)) continue;
            found++;
            if (!enhanced) frontier.addLast(position);
            result.accept(x, y, z);
            if (found >= limit) finished = true;
        }
        return scanned;
    }

    private int[] nextConnected() {
        if (current == null) {
            current = frontier.pollFirst();
            neighbor = 0;
            if (current == null) return null;
        }
        int index = neighbor++;
        int x = current[0] + index / 9 - 1;
        int y = current[1] + index / 3 % 3 - 1;
        int z = current[2] + index % 3 - 1;
        if (neighbor == 27) current = null;
        return new int[] { x, y, z };
    }

    private int[] nextEnhanced() {
        if (distance <= radius) {
            if (outerZ < 0) {
                int planar = dx * dx + dy * dy;
                int outer = distance * distance;
                int inner = (distance - 1) * (distance - 1);
                if (planar <= outer) {
                    outerZ = (int) Math.sqrt(outer - planar);
                    innerZ = planar > inner ? 0 : (int) Math.sqrt(inner - planar) + 1;
                    dz = -outerZ;
                }
                if (outerZ < 0 || innerZ > outerZ) {
                    nextRow();
                    return SKIP;
                }
            }
            if (dz > -innerZ && dz < innerZ) dz = innerZ;
            int[] position = new int[] { sx + dx, sy + dy, sz + dz++ };
            if (dz > outerZ) nextRow();
            return position;
        }
        return null;
    }

    private void nextRow() {
        outerZ = -1;
        if (++dy > distance) {
            dy = -distance;
            if (++dx > distance) {
                distance++;
                dx = dy = -distance;
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }
}
