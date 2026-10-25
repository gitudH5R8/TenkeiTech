/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import codechicken.nei.ItemPanels;
import codechicken.nei.PanelWidget;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;

/** Loaded only when NEI is installed. */
public final class MaintainerNEI implements INEIGuiHandler {

    public static void init() {
        API.registerNEIGuiHandler(new MaintainerNEI());
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData visibility) {
        return visibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.emptyList();
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int x, int y, ItemStack item, int button) {
        if (!(gui instanceof GuiBetterLevelMaintainer maintainer)) return false;
        if (drop(maintainer, x, y, item)) return true;
        return maintainer.covers(x, y, 1, 1);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return gui instanceof GuiBetterLevelMaintainer maintainer && maintainer.covers(x, y, w, h);
    }

    private static boolean drop(GuiBetterLevelMaintainer gui, int x, int y, ItemStack ghost) {
        if (!gui.materialDropAt(x, y) || ghost == null || ghost.getItem() == null || ghost.stackSize <= 0) return false;
        ItemStack identity = ghost.copy();
        identity.stackSize = 1;
        IAEStack<?> item = AEApi.instance()
            .storage()
            .createItemStack(identity);
        FluidStack fluid = StackInfo.getFluid(identity);
        IAEStack<?> fluidIdentity = fluid == null ? null
            : AEApi.instance()
                .storage()
                .createFluidStack(fluid);
        gui.chooseGhost(item, fluidIdentity);
        // NEI supplies its display stack (or a copy of the real cursor stack), never consume inventory.
        ghost.stackSize = 0;
        return true;
    }

    static boolean mouseClicked(GuiBetterLevelMaintainer gui, int x, int y, int button) {
        return GuiContainerManager.getManager(gui)
            .mouseClicked(x, y, button);
    }

    static void mouseDragged(GuiBetterLevelMaintainer gui, int x, int y, int button, long elapsed) {
        GuiContainerManager.getManager(gui)
            .mouseDragged(x, y, button, elapsed);
    }

    static void mouseUp(GuiBetterLevelMaintainer gui, int x, int y, int button) {
        if (button < 0) return;
        for (PanelWidget<?> panel : new PanelWidget<?>[] { ItemPanels.itemPanel, ItemPanels.bookmarkPanel }) {
            if (drop(gui, x, y, panel.draggedStack)) panel.draggedStack = null;
        }
        GuiContainerManager manager = GuiContainerManager.getManager(gui);
        if (!manager.overrideMouseUp(x, y, button)) manager.mouseUp(x, y, button);
    }

    static void mouseScrolled(GuiBetterLevelMaintainer gui, int direction) {
        GuiContainerManager.getManager(gui)
            .mouseScrolled(direction);
    }

    static void drawGhost(int x, int y) {
        // NEI's renderer enables depth testing internally, so drawing last alone cannot lift the preview.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, 0, GuiContainerManager.TOOLTIP_Z_OFFSET + 100);
            ItemPanels.itemPanel.postDraw(x, y);
            ItemPanels.bookmarkPanel.postDraw(x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
