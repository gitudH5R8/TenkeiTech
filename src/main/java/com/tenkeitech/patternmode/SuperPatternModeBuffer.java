/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTESuperCraftingInputME.PatternSlot;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class SuperPatternModeBuffer {

    private final MTESuperCraftingInputME host;
    private final List<PatternSlot<MTESuperCraftingInputME>> pending = new ArrayList<>();
    private WeakReference<MTEMultiBlockBase> owner = new WeakReference<>(null);
    private boolean dirty = true;

    public SuperPatternModeBuffer(MTESuperCraftingInputME host) {
        this.host = host;
    }

    public void invalidate() {
        dirty = true;
    }

    public List<PatternSlot<MTESuperCraftingInputME>> pending() {
        if (dirty) {
            pending.clear();
            for (PatternSlot<MTESuperCraftingInputME> slot : host.getPatternModeSlots()) {
                if (!slot.isEmpty()) pending.add(slot);
            }
            dirty = false;
        }
        pending.removeIf(PatternSlot::isEmpty);
        return pending;
    }

    public MTEMultiBlockBase owner() {
        MTEMultiBlockBase controller = owner.get();
        if (controller != null && (!isLoaded(controller) || !batch(controller).active())) {
            owner.clear();
            return null;
        }
        return controller;
    }

    public boolean attach(MTEMultiBlockBase controller) {
        MTEMultiBlockBase previous = owner();
        if (previous == controller) return true;
        if (previous != null) return false;
        owner = new WeakReference<>(controller);
        return true;
    }

    public boolean canAccept(PatternSlot<MTESuperCraftingInputME> slot) {
        if (passthrough()) return true;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        MTEMultiBlockBase previous = owner();
        if (slot.getRecipeMapId()
            .isEmpty()) {
            if (previous != null) return false;
            if (pending().stream()
                .anyMatch(
                    pendingSlot -> !pendingSlot.getRecipeMapId()
                        .isEmpty()))
                return false;
            for (MTEMultiBlockBase controller : controllers) if (!batch(controller).canAcceptLegacy()) return false;
            return true;
        }
        if (controllers.size() != 1) return false;
        MTEMultiBlockBase controller = controllers.get(0);
        if (previous != null && previous != controller) return false;
        return batch(controller).canAccept(host);
    }

    public void accepted(PatternSlot<MTESuperCraftingInputME> slot) {
        invalidate();
        if (passthrough()) return;
        if (slot.getRecipeMapId()
            .isEmpty()) return;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() == 1) batch(controllers.get(0)).opened(host);
    }

    public void tick() {
        if (passthrough() || owner() != null) return;
        if (pending().stream()
            .noneMatch(
                slot -> !slot.getRecipeMapId()
                    .isEmpty()))
            return;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() == 1) batch(controllers.get(0)).recover(host);
    }

    public boolean busy() {
        if (passthrough()) return false;
        MTEMultiBlockBase controller = owner();
        if (controller != null) return batch(controller).busy();
        for (MTEMultiBlockBase linked : host.getPatternModeControllers()) {
            if (batch(linked).busy()) return true;
        }
        return pending().stream()
            .anyMatch(
                slot -> !slot.getRecipeMapId()
                    .isEmpty());
    }

    public boolean allows(PatternSlot<?> slot) {
        if (passthrough()) return true;
        MTEMultiBlockBase controller = owner();
        if (controller != null) return batch(controller).allows(host, slot.getRecipeMapId());
        if (!slot.getRecipeMapId()
            .isEmpty()) return false;
        for (MTEMultiBlockBase linked : host.getPatternModeControllers()) {
            if (batch(linked).active()) return false;
        }
        return true;
    }

    public String status() {
        if (passthrough()) return "BYPASS / single-mode controller";
        MTEMultiBlockBase controller = owner();
        if (controller != null) return batch(controller).status();
        for (MTEMultiBlockBase linked : host.getPatternModeControllers()) {
            if (batch(linked).active()) return batch(linked).status();
        }
        return busy() ? "PAUSED / waiting for a unique loaded controller" : "IDLE";
    }

    private boolean passthrough() {
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.isEmpty()) return false;
        MTEMultiBlockBase previous = owner();
        if (previous != null) return false;
        for (MTEMultiBlockBase controller : controllers) {
            if (batch(controller).usesModeScheduling()) return false;
        }
        return true;
    }

    public static boolean isLoaded(IMetaTileEntity machine) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        return tile != null && tile.getWorld() != null
            && tile.getMetaTileEntity() == machine
            && tile.getWorld()
                .blockExists(tile.getXCoord(), tile.getYCoord(), tile.getZCoord())
            && tile.getWorld()
                .getTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()) == tile;
    }

    public static MachinePatternBatch batch(MTEMultiBlockBase controller) {
        return ((PatternModeController) controller).tenkeitech$patternBatch();
    }
}
