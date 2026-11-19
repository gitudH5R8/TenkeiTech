/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

/** Collects the native projection on a detached controller; live construction fields are never reset. */
public final class BetterMachineProjectorProjection {

    private static final int MAX_POSITIONS = 262144;
    private static final ThreadLocal<BetterMachineProjectorProjection> ACTIVE = new ThreadLocal<>();
    private final Object controller;
    private final NBTTagCompound controllerState;
    private final Map<Position, Entry> entries = new LinkedHashMap<>();
    private int visited;
    private boolean captured;

    private BetterMachineProjectorProjection(Object controller, NBTTagCompound controllerState) {
        this.controller = controller;
        this.controllerState = controllerState;
    }

    public static List<Entry> collect(BaseMetaTileEntity original, ItemStack trigger) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested projector projection");
        NBTTagCompound saved = new NBTTagCompound();
        original.writeToNBT(saved);
        BaseMetaTileEntity detached = copyController(saved, original.getWorldObj());
        if (!(detached.getMetaTileEntity() instanceof MTEEnhancedMultiBlockBase<?>machine))
            throw new UnsupportedOperationException("Controller does not expose a native structure definition");
        BetterMachineProjectorProjection projection = new BetterMachineProjectorProjection(machine, saved);
        ACTIVE.set(projection);
        try {
            machine.construct(trigger, true);
            if (!projection.captured)
                throw new UnsupportedOperationException("Controller bypasses native projection pieces");
            return new ArrayList<>(projection.entries.values());
        } finally {
            ACTIVE.remove();
        }
    }

    private static BaseMetaTileEntity copyController(NBTTagCompound saved, World world) {
        BaseMetaTileEntity detached = new BaseMetaTileEntity();
        detached.setWorldObj(world);
        detached.readFromNBT((NBTTagCompound) saved.copy());
        return detached;
    }

    /** Called only by the scoped native buildPiece hook. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static boolean capture(MTEEnhancedMultiBlockBase<?> machine, String piece, ItemStack trigger, boolean hints,
        int a, int b, int c) {
        BetterMachineProjectorProjection projection = ACTIVE.get();
        if (projection == null || projection.controller != machine) return false;
        if (!hints) throw new IllegalStateException("Projection attempted creative construction");
        projection.captured = true;
        IStructureDefinition definition = machine.getStructureDefinition();
        ItemStack pieceTrigger = trigger.copy();
        definition.iterate(
            piece,
            machine.getBaseMetaTileEntity()
                .getWorld(),
            machine.getExtendedFacing(),
            machine.getBaseMetaTileEntity()
                .getXCoord(),
            machine.getBaseMetaTileEntity()
                .getYCoord(),
            machine.getBaseMetaTileEntity()
                .getZCoord(),
            a,
            b,
            c,
            new IStructureWalker<Object>() {

                @Override
                public boolean visit(IStructureElement<Object> element, World world, int x, int y, int z, int relativeA,
                    int relativeB, int relativeC) {
                    if (++projection.visited > MAX_POSITIONS)
                        throw new IllegalArgumentException("Projection is too large");
                    Position position = new Position(x, y, z);
                    projection.entries.putIfAbsent(
                        position,
                        new Entry(machine, projection.controllerState, element, pieceTrigger, position));
                    return true;
                }

                @Override
                public boolean blockNotLoaded(IStructureElement<Object> element, World world, int x, int y, int z,
                    int relativeA, int relativeB, int relativeC) {
                    if (++projection.visited > MAX_POSITIONS)
                        throw new IllegalArgumentException("Projection is too large");
                    return true;
                }
            });
        return true;
    }

    public static final class Entry {

        public final Object controller;
        public final IStructureElement<Object> element;
        public final ItemStack trigger;
        public final Position position;
        private final NBTTagCompound controllerState;

        private Entry(Object controller, NBTTagCompound controllerState, IStructureElement<Object> element,
            ItemStack trigger, Position position) {
            this.controller = controller;
            this.controllerState = controllerState;
            this.element = element;
            this.trigger = trigger;
            this.position = position;
        }

        /** Match the existing block at any tier without sharing stateful checks between positions or branches. */
        public boolean matches(IStructureElement<Object> candidate, World world) {
            Object isolated = copyController(controllerState, world).getMetaTileEntity();
            return candidate.check(isolated, world, position.x, position.y, position.z);
        }
    }

    public static final class Position {

        public final int x, y, z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Position p && p.x == x && p.y == y && p.z == z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
