/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternmode;

import java.lang.ref.WeakReference;
import java.util.List;

import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Connects one fixed-template shared input pool to the controller's RecipeMap batch scheduler. */
public final class AutoTemplateModeBuffer {

    public enum DisplayStatus {
        UNBOUND,
        WAITING_FOR_MACHINE,
        MULTIPLE_CONTROLLERS,
        READY,
        WILL_SWITCH,
        SWITCHING,
        WAITING_FOR_BATCH,
        MISSING_MODE,
        AMBIGUOUS_MODE,
        ERROR
    }

    private final MTEHatchAutoTemplateInputME host;
    private WeakReference<MTEMultiBlockBase> owner = new WeakReference<>(null);
    private WeakReference<MTEMultiBlockBase> inspectedController = new WeakReference<>(null);
    private String inspectedTarget = "";
    private MachineModeProbe.Result inspectedResult;

    public AutoTemplateModeBuffer(MTEHatchAutoTemplateInputME host) {
        this.host = host;
    }

    public MTEMultiBlockBase owner() {
        MTEMultiBlockBase controller = owner.get();
        if (controller != null && (!SuperPatternModeBuffer.isLoaded(controller) || !batch(controller).active())) {
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

    public boolean canAccept() {
        String target = host.getPatternModeRecipeMapId();
        if (target.isEmpty()) return false;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return false;
        MTEMultiBlockBase controller = controllers.get(0);
        MachinePatternBatch scheduler = batch(controller);
        if (!scheduler.usesModeScheduling()) {
            try {
                return MachineModeProbe.matches(controller, target);
            } catch (RuntimeException exception) {
                return false;
            }
        }
        MTEMultiBlockBase previous = owner();
        return (previous == null || previous == controller) && scheduler.canAccept(host);
    }

    public void accepted() {
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return;
        MachinePatternBatch scheduler = batch(controllers.get(0));
        if (scheduler.usesModeScheduling()) scheduler.opened(host);
    }

    public void tick(long timer) {
        if (timer % 10 == 0 && owner() == null) isPatternModeAvailable();
        if (owner() != null || !host.hasPatternModeInputs()) return;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return;
        MachinePatternBatch scheduler = batch(controllers.get(0));
        if (scheduler.usesModeScheduling()) scheduler.recover(host);
    }

    public void invalidateInspection() {
        inspectedController.clear();
        inspectedTarget = "";
        inspectedResult = null;
    }

    /** Returns whether this bus has a usable current or discoverable target mode. */
    public boolean isPatternModeAvailable() {
        String target = host.getPatternModeRecipeMapId();
        if (target.isEmpty()) return false;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return true;
        MTEMultiBlockBase controller = controllers.get(0);
        try {
            if (MachineModeProbe.matches(controller, target)) return true;
            MachinePatternBatch scheduler = batch(controller);
            if (scheduler.active() && target.equals(scheduler.target())) {
                String diagnostic = scheduler.diagnostic();
                return !"NOT_FOUND".equals(diagnostic) && !"UNSUPPORTED".equals(diagnostic);
            }
            MachineModeProbe.Result result = cachedInspection(controller, target);
            if (result == null && scheduler.canInspectModes()) result = inspect(controller, target);
            return result == null ? controller.supportsMachineModeSwitch() : !isMissingMode(result);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public boolean busy() {
        MTEMultiBlockBase controller = owner();
        if (controller != null) return batch(controller).busy();
        for (MTEMultiBlockBase linked : host.getPatternModeControllers()) {
            if (batch(linked).busy()) return true;
        }
        if (!host.hasPatternModeInputs()) return false;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return true;
        MachinePatternBatch scheduler = batch(controllers.get(0));
        if (scheduler.usesModeScheduling()) return true;
        try {
            return !MachineModeProbe.matches(controllers.get(0), host.getPatternModeRecipeMapId());
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public boolean allows() {
        String target = host.getPatternModeRecipeMapId();
        if (target.isEmpty()) return false;
        MTEMultiBlockBase controller = owner();
        if (controller != null) return batch(controller).allows(host, target);
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        if (controllers.size() != 1) return false;
        controller = controllers.get(0);
        MachinePatternBatch scheduler = batch(controller);
        if (scheduler.active()) return scheduler.allows(host, target);
        if (scheduler.usesModeScheduling()) return false;
        try {
            return MachineModeProbe.matches(controller, target);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public DisplayStatus displayStatus() {
        String target = host.getPatternModeRecipeMapId();
        if (target.isEmpty()) return DisplayStatus.UNBOUND;
        List<MTEMultiBlockBase> controllers = host.getPatternModeControllers();
        MTEMultiBlockBase controller = owner();
        if (controller == null) {
            if (controllers.isEmpty()) {
                invalidateInspection();
                return DisplayStatus.WAITING_FOR_MACHINE;
            }
            if (controllers.size() != 1) return DisplayStatus.MULTIPLE_CONTROLLERS;
            controller = controllers.get(0);
        }
        if (!controller.mMachine) return DisplayStatus.WAITING_FOR_MACHINE;
        try {
            MachinePatternBatch scheduler = batch(controller);
            if (scheduler.active()) {
                if (!target.equals(scheduler.target())) return DisplayStatus.WAITING_FOR_BATCH;
                String diagnostic = scheduler.diagnostic();
                if ("NOT_FOUND".equals(diagnostic) || "UNSUPPORTED".equals(diagnostic))
                    return DisplayStatus.MISSING_MODE;
                if ("AMBIGUOUS".equals(diagnostic)) return DisplayStatus.AMBIGUOUS_MODE;
                if (scheduler.state() == MachinePatternBatch.State.ERROR) return DisplayStatus.ERROR;
                return MachineModeProbe.matches(controller, target) ? DisplayStatus.READY : DisplayStatus.SWITCHING;
            }
            if (MachineModeProbe.matches(controller, target)) return DisplayStatus.READY;
            if (!scheduler.canInspectModes())
                return controller.supportsMachineModeSwitch() ? DisplayStatus.WILL_SWITCH : DisplayStatus.MISSING_MODE;
            return displayStatus(inspect(controller, target));
        } catch (RuntimeException exception) {
            return DisplayStatus.ERROR;
        }
    }

    private MachineModeProbe.Result inspect(MTEMultiBlockBase controller, String target) {
        if (inspectedController.get() != controller || !inspectedTarget.equals(target) || inspectedResult == null) {
            inspectedController = new WeakReference<>(controller);
            inspectedTarget = target;
            inspectedResult = MachineModeProbe.inspect(controller, target);
            host.onPatternModeInspectionChanged();
        }
        return inspectedResult;
    }

    private MachineModeProbe.Result cachedInspection(MTEMultiBlockBase controller, String target) {
        return inspectedController.get() == controller && inspectedTarget.equals(target) ? inspectedResult : null;
    }

    private static boolean isMissingMode(MachineModeProbe.Result result) {
        return result == MachineModeProbe.Result.NOT_FOUND || result == MachineModeProbe.Result.UNSUPPORTED;
    }

    private static DisplayStatus displayStatus(MachineModeProbe.Result result) {
        return switch (result) {
            case ALREADY_MATCHED -> DisplayStatus.READY;
            case UNIQUE_MATCH -> DisplayStatus.WILL_SWITCH;
            case AMBIGUOUS -> DisplayStatus.AMBIGUOUS_MODE;
            case NOT_FOUND, UNSUPPORTED -> DisplayStatus.MISSING_MODE;
            case ERROR -> DisplayStatus.ERROR;
        };
    }

    private static MachinePatternBatch batch(MTEMultiBlockBase controller) {
        return SuperPatternModeBuffer.batch(controller);
    }
}
