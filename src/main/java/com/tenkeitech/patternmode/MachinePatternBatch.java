/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputProxy;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTESuperCraftingInputME.PatternSlot;
import com.tenkeitech.machine.MTESuperCraftingInputProxy;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;

public final class MachinePatternBatch {

    public enum State {
        IDLE,
        COLLECTING,
        SEALED,
        PROCESSING,
        PAUSED,
        ERROR
    }

    private final MTEMultiBlockBase controller;
    private final Set<MTESuperCraftingInputME> members = new LinkedHashSet<>();
    private final Set<MTEHatchAutoTemplateInputME> autoTemplateMembers = new LinkedHashSet<>();
    private State state = State.IDLE;
    private String target = "";
    private String diagnostic = "";
    private long openedTick;
    private long switchedTick = Long.MIN_VALUE;
    private long retryTick;
    private boolean ready;
    private boolean probeBroken;
    private String mirrorTarget = "";
    private String mirrorDiagnostic = "";
    private long mirrorSwitchedTick = Long.MIN_VALUE;
    private long mirrorRetryTick;
    private boolean mirrorReady;
    private boolean mirrorProbeBroken;
    private final Set<String> groups = new LinkedHashSet<>();

    public MachinePatternBatch(MTEMultiBlockBase controller) {
        this.controller = controller;
    }

    public boolean active() {
        return state != State.IDLE && usesModeScheduling();
    }

    public boolean usesModeScheduling() {
        try {
            return controller.supportsMachineModeSwitch();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public String status() {
        if (!mirrorTarget.isEmpty() || !mirrorDiagnostic.isEmpty()) {
            return "MIRROR" + (mirrorTarget.isEmpty() ? "" : " / " + mirrorTarget)
                + (mirrorDiagnostic.isEmpty() ? "" : " / " + mirrorDiagnostic);
        }
        return state + (target.isEmpty() ? "" : " / " + target) + (diagnostic.isEmpty() ? "" : " / " + diagnostic);
    }

    public State state() {
        return state;
    }

    public String target() {
        return target;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public boolean busy() {
        return priorityMirrorTarget() != null || active() && (state != State.COLLECTING || tick() != openedTick);
    }

    /** A read-only mode probe still cycles the public mode API, so only run it while the machine is safe to inspect. */
    public boolean canInspectModes() {
        return priorityMirrorTarget() == null && !active() && usable() && idle();
    }

    public boolean canAccept(MTESuperCraftingInputME host) {
        if (priorityMirrorTarget() != null) return false;
        if (!usable() || !linked(host)) return false;
        if (active()) return state == State.COLLECTING && tick() == openedTick && members.contains(host);
        if (!idle()) return false;
        for (MTESuperCraftingInputME peer : linkedBuffers()) {
            if (!exclusivelyLinked(peer) || peer.getPatternMode()
                .owner() != null
                || !peer.getPatternMode()
                    .pending()
                    .isEmpty())
                return false;
        }
        for (MTEHatchAutoTemplateInputME peer : linkedAutoTemplateBuffers()) {
            if (!exclusivelyLinked(peer) || peer.getPatternMode()
                .owner() != null || peer.hasPatternModeInputs()) return false;
        }
        return true;
    }

    public boolean canAccept(MTEHatchAutoTemplateInputME host) {
        if (priorityMirrorTarget() != null) return false;
        if (!usable() || !linked(host)) return false;
        if (active()) return state == State.COLLECTING && tick() == openedTick && autoTemplateMembers.contains(host);
        if (!idle()) return false;
        for (MTESuperCraftingInputME peer : linkedBuffers()) {
            if (!exclusivelyLinked(peer) || peer.getPatternMode()
                .owner() != null
                || !peer.getPatternMode()
                    .pending()
                    .isEmpty())
                return false;
        }
        for (MTEHatchAutoTemplateInputME peer : linkedAutoTemplateBuffers()) {
            if (!exclusivelyLinked(peer) || peer.getPatternMode()
                .owner() != null || peer.hasPatternModeInputs()) return false;
        }
        return true;
    }

    public boolean canAcceptLegacy() {
        if (priorityMirrorTarget() != null) return false;
        if (active()) return false;
        for (MTESuperCraftingInputME peer : linkedBuffers()) {
            if (peer.getPatternMode()
                .pending()
                .stream()
                .anyMatch(
                    slot -> !slot.getRecipeMapId()
                        .isEmpty())) {
                recover(peer);
                return false;
            }
        }
        for (MTEHatchAutoTemplateInputME peer : linkedAutoTemplateBuffers()) {
            if (peer.hasPatternModeInputs()) {
                recover(peer);
                return false;
            }
        }
        return true;
    }

    public void opened(MTESuperCraftingInputME host) {
        if (!active()) {
            openedTick = tick();
            change(State.COLLECTING, "");
        }
        members.add(host);
        members.addAll(linkedBuffers());
        autoTemplateMembers.addAll(linkedAutoTemplateBuffers());
        for (MTESuperCraftingInputME member : members) member.getPatternMode()
            .attach(controller);
        for (MTEHatchAutoTemplateInputME member : autoTemplateMembers) member.getPatternMode()
            .attach(controller);
        controller.getBaseMetaTileEntity()
            .markDirty();
    }

    public void opened(MTEHatchAutoTemplateInputME host) {
        if (!active()) {
            openedTick = tick();
            change(State.COLLECTING, "");
        }
        autoTemplateMembers.add(host);
        autoTemplateMembers.addAll(linkedAutoTemplateBuffers());
        members.addAll(linkedBuffers());
        for (MTESuperCraftingInputME member : members) member.getPatternMode()
            .attach(controller);
        for (MTEHatchAutoTemplateInputME member : autoTemplateMembers) member.getPatternMode()
            .attach(controller);
        controller.getBaseMetaTileEntity()
            .markDirty();
    }

    public void recover(MTESuperCraftingInputME host) {
        if (!active()) change(State.SEALED, "");
        members.add(host);
        host.getPatternMode()
            .attach(controller);
        controller.scheduleRecipeCheckImmediate();
    }

    public void recover(MTEHatchAutoTemplateInputME host) {
        if (!active()) change(State.SEALED, "");
        autoTemplateMembers.add(host);
        host.getPatternMode()
            .attach(controller);
        controller.scheduleRecipeCheckImmediate();
    }

    public void restore(boolean pending) {
        if (pending) change(State.SEALED, "");
    }

    public void tickController() {
        if (!usesModeScheduling()) {
            setTarget("");
            members.clear();
            autoTemplateMembers.clear();
            ready = false;
            probeBroken = false;
            resetMirrorPriority();
            change(State.IDLE, "");
            return;
        }
        String priorityTarget = priorityMirrorTarget();
        if (priorityTarget != null) {
            ready = false;
            boolean wasReady = mirrorReady;
            if (followPriorityMirror(priorityTarget) && !wasReady) controller.scheduleRecipeCheckImmediate();
            return;
        }
        resetMirrorPriority();
        if (!active() || controller.getBaseMetaTileEntity() == null) return;
        boolean wasReady = ready;
        if (evaluate() && !wasReady) controller.scheduleRecipeCheckImmediate();
    }

    public boolean beforeRecipeCheck() {
        if (!usesModeScheduling()) return true;
        String priorityTarget = priorityMirrorTarget();
        if (priorityTarget != null) {
            ready = false;
            return followPriorityMirror(priorityTarget);
        }
        resetMirrorPriority();
        return !active() || evaluate();
    }

    public boolean allows(MTESuperCraftingInputME host, String id) {
        if (!active() || !ready || !members.contains(host) || id.isEmpty() || !target.equals(id)) return false;
        try {
            return linked(host) && exclusivelyLinked(host) && MachineModeProbe.matches(controller, target);
        } catch (RuntimeException exception) {
            ready = false;
            probeBroken = true;
            change(
                State.ERROR,
                "machine API exception: " + exception.getClass()
                    .getSimpleName());
            return false;
        }
    }

    public boolean allows(MTEHatchAutoTemplateInputME host, String id) {
        if (!active() || !ready || !autoTemplateMembers.contains(host) || id.isEmpty() || !target.equals(id))
            return false;
        try {
            return linked(host) && exclusivelyLinked(host) && MachineModeProbe.matches(controller, target);
        } catch (RuntimeException exception) {
            ready = false;
            probeBroken = true;
            change(
                State.ERROR,
                "machine API exception: " + exception.getClass()
                    .getSimpleName());
            return false;
        }
    }

    private boolean evaluate() {
        try {
            return step();
        } catch (RuntimeException exception) {
            ready = false;
            probeBroken = true;
            change(
                State.ERROR,
                "machine API exception: " + exception.getClass()
                    .getSimpleName());
            return false;
        }
    }

    private boolean step() {
        if (!usable()) {
            ready = false;
            change(State.PAUSED, "machine unavailable or stopped");
            return false;
        }
        if (state == State.COLLECTING && tick() == openedTick) {
            ready = false;
            return false;
        }
        if (!idle()) {
            change(State.PROCESSING, "");
            return false;
        }
        ready = false;
        List<MTESuperCraftingInputME> connected = linkedBuffers();
        List<MTEHatchAutoTemplateInputME> connectedAutoTemplates = linkedAutoTemplateBuffers();
        members.removeIf(
            old -> connected.stream()
                .anyMatch(current -> current != old && samePosition(old, current)));
        autoTemplateMembers.removeIf(
            old -> connectedAutoTemplates.stream()
                .anyMatch(current -> current != old && samePosition(old, current)));
        members.removeIf(host -> linked(host) && !exclusivelyLinked(host));
        autoTemplateMembers.removeIf(host -> linked(host) && !exclusivelyLinked(host));
        members.removeIf(
            host -> !linked(host) && host.getPatternMode()
                .pending()
                .isEmpty());
        autoTemplateMembers.removeIf(host -> !linked(host) && !host.hasPatternModeInputs());
        members.addAll(connected);
        autoTemplateMembers.addAll(connectedAutoTemplates);
        for (MTESuperCraftingInputME host : members) {
            if (!linked(host) || !exclusivelyLinked(host)
                || !host.getPatternMode()
                    .attach(controller)) {
                change(State.PAUSED, "buffer detached or shared by multiple controllers");
                return false;
            }
        }
        for (MTEHatchAutoTemplateInputME host : autoTemplateMembers) {
            if (!linked(host) || !exclusivelyLinked(host)
                || !host.getPatternMode()
                    .attach(controller)) {
                change(State.PAUSED, "buffer detached or shared by multiple controllers");
                return false;
            }
        }
        groups.clear();
        for (MTESuperCraftingInputME host : members) {
            for (PatternSlot<MTESuperCraftingInputME> slot : host.getPatternMode()
                .pending()) {
                groups.add(slot.getRecipeMapId());
            }
        }
        for (MTEHatchAutoTemplateInputME host : autoTemplateMembers) {
            if (host.hasPatternModeInputs()) groups.add(host.getPatternModeRecipeMapId());
        }
        if (groups.isEmpty()) {
            setTarget("");
            members.clear();
            autoTemplateMembers.clear();
            probeBroken = false;
            change(State.IDLE, "");
            controller.getBaseMetaTileEntity()
                .markDirty();
            return true;
        }
        if (groups.contains("")) {
            change(State.ERROR, "untagged inputs mixed with a managed batch; refund before continuing");
            return false;
        }
        if (!groups.contains(target)) {
            setTarget(
                groups.iterator()
                    .next());
            retryTick = 0;
            change(State.SEALED, "");
        }
        if (!PatternRecipeMapData.registered(target)) {
            change(State.ERROR, "unknown RecipeMap");
            return false;
        }
        if (switchedTick == tick()) return false;
        if (!MachineModeProbe.matches(controller, target)) {
            if (probeBroken) return false;
            if (tick() < retryTick) return false;
            retryTick = tick() + 20;
            MachineModeProbe.Result result = MachineModeProbe.select(controller, target);
            if (result == MachineModeProbe.Result.ERROR) probeBroken = true;
            if (result == MachineModeProbe.Result.UNIQUE_MATCH) {
                switchedTick = tick();
                controller.getBaseMetaTileEntity()
                    .markDirty();
                change(State.SEALED, "mode selected; waiting for next tick");
            } else change(State.ERROR, result.name());
            return false;
        }
        ready = true;
        change(State.SEALED, "");
        return true;
    }

    private String priorityMirrorTarget() {
        Set<String> targets = new LinkedHashSet<>();
        boolean found = false;
        boolean waiting = false;
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            if (!(hatch instanceof MTESuperCraftingInputProxy proxy) || !SuperPatternModeBuffer.isLoaded(proxy)) {
                continue;
            }
            MTESuperCraftingInputME host = proxy.getMaster();
            if (host == null || !SuperPatternModeBuffer.isLoaded(host) || exclusivelyLinked(host)) continue;
            MTEMultiBlockBase owner = host.getPatternMode()
                .owner();
            if (owner == null) continue;
            found = true;
            String ownerTarget = SuperPatternModeBuffer.batch(owner)
                .target();
            if (ownerTarget.isEmpty()) waiting = true;
            else targets.add(ownerTarget);
        }
        if (!found) return null;
        if (waiting) {
            mirrorTarget = "";
            mirrorDiagnostic = "waiting for mirror owner target";
            mirrorReady = false;
            return "";
        }
        if (targets.size() != 1) {
            mirrorTarget = "";
            mirrorDiagnostic = "conflicting mirror RecipeMaps";
            mirrorReady = false;
            return "";
        }
        return targets.iterator()
            .next();
    }

    private boolean followPriorityMirror(String requestedTarget) {
        if (requestedTarget.isEmpty() || !usable()) return false;
        if (!requestedTarget.equals(mirrorTarget)) {
            mirrorTarget = requestedTarget;
            mirrorDiagnostic = "";
            mirrorRetryTick = 0;
            mirrorSwitchedTick = Long.MIN_VALUE;
            mirrorProbeBroken = false;
            mirrorReady = false;
        }
        try {
            if (MachineModeProbe.matches(controller, mirrorTarget)) {
                mirrorDiagnostic = "";
                mirrorReady = true;
                return true;
            }
            if (!idle()) {
                mirrorDiagnostic = "waiting for machine idle";
                return false;
            }
            if (mirrorProbeBroken || mirrorSwitchedTick == tick() || tick() < mirrorRetryTick) return false;
            mirrorRetryTick = tick() + 20;
            MachineModeProbe.Result result = MachineModeProbe.select(controller, mirrorTarget);
            if (result == MachineModeProbe.Result.ERROR) mirrorProbeBroken = true;
            if (result == MachineModeProbe.Result.UNIQUE_MATCH) {
                mirrorSwitchedTick = tick();
                mirrorDiagnostic = "mode selected; waiting for next tick";
                controller.getBaseMetaTileEntity()
                    .markDirty();
            } else mirrorDiagnostic = result.name();
            return false;
        } catch (RuntimeException exception) {
            mirrorReady = false;
            mirrorProbeBroken = true;
            mirrorDiagnostic = "machine API exception: " + exception.getClass()
                .getSimpleName();
            return false;
        }
    }

    private void resetMirrorPriority() {
        mirrorTarget = "";
        mirrorDiagnostic = "";
        mirrorSwitchedTick = Long.MIN_VALUE;
        mirrorRetryTick = 0;
        mirrorReady = false;
        mirrorProbeBroken = false;
    }

    private void setTarget(String next) {
        if (target.equals(next)) return;
        target = next;
        for (MTESuperCraftingInputME member : members) member.scheduleProxyRecipeChecks();
    }

    private boolean usable() {
        return SuperPatternModeBuffer.isLoaded(controller) && controller.getBaseMetaTileEntity()
            .isServerSide()
            && controller.mMachine
            && controller.getBaseMetaTileEntity()
                .isAllowedToWork();
    }

    private boolean idle() {
        if (controller.mMaxProgresstime > 0) return false;
        if (controller.mOutputItems != null) {
            for (net.minecraft.item.ItemStack item : controller.mOutputItems)
                if (item != null && item.stackSize > 0) return false;
        }
        if (controller.mOutputFluids != null) {
            for (net.minecraftforge.fluids.FluidStack fluid : controller.mOutputFluids)
                if (fluid != null && fluid.amount > 0) return false;
        }
        return true;
    }

    private long tick() {
        return controller.getBaseMetaTileEntity()
            .getWorld()
            .getTotalWorldTime();
    }

    private boolean linked(MTESuperCraftingInputME host) {
        if (!SuperPatternModeBuffer.isLoaded(host)) return false;
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            if (hatch == host
                || hatch instanceof MTESuperCraftingInputProxy proxy && SuperPatternModeBuffer.isLoaded(proxy)
                    && proxy.getMaster() == host)
                return true;
        }
        return false;
    }

    private boolean linked(MTEHatchAutoTemplateInputME host) {
        if (!SuperPatternModeBuffer.isLoaded(host)) return false;
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            if (resolveAutoTemplateHost(hatch) == host) return true;
        }
        return false;
    }

    private boolean exclusivelyLinked(MTESuperCraftingInputME host) {
        List<MTEMultiBlockBase> owners = host.getPatternModeControllers();
        return owners.size() == 1 && owners.get(0) == controller;
    }

    private boolean exclusivelyLinked(MTEHatchAutoTemplateInputME host) {
        List<MTEMultiBlockBase> owners = host.getPatternModeControllers();
        return owners.size() == 1 && owners.get(0) == controller;
    }

    private List<MTESuperCraftingInputME> linkedBuffers() {
        List<MTESuperCraftingInputME> buffers = new ArrayList<>();
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            MTESuperCraftingInputME host = hatch instanceof MTESuperCraftingInputME direct ? direct
                : hatch instanceof MTESuperCraftingInputProxy proxy && SuperPatternModeBuffer.isLoaded(proxy)
                    ? proxy.getMaster()
                    : null;
            if (host != null && SuperPatternModeBuffer.isLoaded(host)
                && exclusivelyLinked(host)
                && !buffers.contains(host)) buffers.add(host);
        }
        return buffers;
    }

    private List<MTEHatchAutoTemplateInputME> linkedAutoTemplateBuffers() {
        List<MTEHatchAutoTemplateInputME> buffers = new ArrayList<>();
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            MTEHatchAutoTemplateInputME host = resolveAutoTemplateHost(hatch);
            if (host != null && SuperPatternModeBuffer.isLoaded(host)
                && exclusivelyLinked(host)
                && !buffers.contains(host)) buffers.add(host);
        }
        return buffers;
    }

    private static MTEHatchAutoTemplateInputME resolveAutoTemplateHost(IDualInputHatch hatch) {
        if (hatch instanceof MTEHatchAutoTemplateInputME host) return host;
        if (hatch instanceof MTEHatchAutoTemplateInputProxy proxy && SuperPatternModeBuffer.isLoaded(proxy)) {
            return proxy.getMaster();
        }
        return null;
    }

    private static boolean samePosition(MetaTileEntity first, MetaTileEntity second) {
        var left = first.getBaseMetaTileEntity();
        var right = second.getBaseMetaTileEntity();
        return left != null && right != null
            && left.getWorld() == right.getWorld()
            && left.getXCoord() == right.getXCoord()
            && left.getYCoord() == right.getYCoord()
            && left.getZCoord() == right.getZCoord();
    }

    private void change(State next, String reason) {
        if (state == next && diagnostic.equals(reason)) return;
        state = next;
        diagnostic = reason;
        if (next == State.ERROR)
            TenkeiTech.LOG.warn("[PatternMode] {} at {}", status(), controller.getBaseMetaTileEntity());
        else TenkeiTech.LOG.debug("[PatternMode] {} at {}", status(), controller.getBaseMetaTileEntity());
    }
}
