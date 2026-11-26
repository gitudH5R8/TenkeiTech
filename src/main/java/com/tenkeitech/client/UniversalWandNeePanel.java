/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.BoolValue;
import com.tenkeitech.gui.UniversalWandGui;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandNetwork;

import appeng.container.implementations.ContainerPatternTerm;

/** Reuses the wand's ModularUI panel inside the existing terminal screen and container. */
public final class UniversalWandNeePanel extends Gui {

    private static final AtomicLong REQUESTS = new AtomicLong();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ContainerPatternTerm container;
    private boolean open, waiting;
    private ItemStack expected;
    private ModularScreen view;
    private int slot, width, height;
    private long requestId, started;

    public UniversalWandNeePanel(ContainerPatternTerm container) {
        this.container = container;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        waiting = false;
        disposeView();
    }

    public void open() {
        int carried = UniversalWandNeeGuideClient.findWandSlot();
        if (carried < 0 || mc.thePlayer.openContainer != container || container.isCraftingMode()) return;
        disposeView();
        slot = carried;
        expected = mc.thePlayer.inventory.mainInventory[slot].copy();
        waiting = false;
        open = true;
        ModularPanel panel = UniversalWandGui.neeSettingsPanel(value(0), value(1), value(2), value(3), () -> !waiting)
            .onCloseAction(() -> open = false);
        view = new ModularScreen("tenkeitech", panel);
        // An embedded MUI view wraps the original GuiScreen; it never replaces Minecraft.currentScreen.
        view.constructOverlay(mc.currentScreen);
        resize(mc.currentScreen.width, mc.currentScreen.height);
    }

    public void tick() {
        if (open && (mc.thePlayer == null || mc.thePlayer.openContainer != container
            || container.isCraftingMode()
            || !isWand(mc.thePlayer.inventory.mainInventory[slot])
            || waiting && System.nanoTime() - started > 15_000_000_000L)) open = false;
        if (!open) disposeView();
        else view.onUpdate();
    }

    public void accept(UniversalWandNetwork.NeeSettingsReply reply) {
        if (!open || !waiting
            || reply.slot != slot
            || reply.windowId != container.windowId
            || reply.requestId != requestId) return;
        waiting = false;
        if (!reply.accepted || !isWand(reply.stack)) open = false;
        else expected = reply.stack.copy();
    }

    public void key(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) open = false;
    }

    public void click(int mouseX, int mouseY, int mouseButton) {
        if (!open || view == null) return;
        view.getContext()
            .updateState(mouseX, mouseY, 0);
        view.getContext()
            .updateEventState();
        view.onMousePressed(mouseButton);
    }

    public void release(int mouseButton) {
        if (open && view != null) {
            view.getContext()
                .updateEventState();
            view.onMouseRelease(mouseButton);
        }
    }

    public void draw(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks) {
        if (!open || view == null) return;
        if (width != screenWidth || height != screenHeight) resize(screenWidth, screenHeight);
        drawRect(0, 0, screenWidth, screenHeight, 0x88000000);
        view.onFrameUpdate();
        view.getContext()
            .updateState(mouseX, mouseY, partialTicks);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        view.drawScreen();
        view.drawForeground();
        view.getContext()
            .drawQueuedForeground();
    }

    private void resize(int width, int height) {
        this.width = width;
        this.height = height;
        view.onResize(width, height);
    }

    private void disposeView() {
        if (view == null) return;
        view.getPanelManager()
            .closeAll();
        view.getPanelManager()
            .dispose();
        view = null;
    }

    private BoolValue.Dynamic value(int setting) {
        return new BoolValue.Dynamic(() -> enabled(setting), enabled -> {
            if (!open || waiting) return;
            waiting = true;
            requestId = REQUESTS.incrementAndGet();
            started = System.nanoTime();
            UniversalWandNetwork.requestNeeSetting(slot, container.windowId, requestId, expected, setting, enabled);
        });
    }

    private boolean enabled(int setting) {
        return switch (setting) {
            case 0 -> ItemUniversalWand.isCircuitPreserverEnabled(expected);
            case 1 -> ItemUniversalWand.isCatalystRecorderEnabled(expected);
            case 2 -> ItemUniversalWand.isCondensateReplacementEnabled(expected);
            default -> ItemUniversalWand.isCutterFluidFilterEnabled(expected);
        };
    }

    private static boolean isWand(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemUniversalWand;
    }
}
