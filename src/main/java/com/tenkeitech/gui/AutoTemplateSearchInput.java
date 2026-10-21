/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Keeps template search input out of the container and recipe viewer shortcut handlers. */
@SideOnly(Side.CLIENT)
public final class AutoTemplateSearchInput {

    private AutoTemplateSearchInput() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new AutoTemplateSearchInput());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardInput(KeyboardInputEvent.Pre event) {
        if (event.gui != Minecraft.getMinecraft().currentScreen || !(event.gui instanceof IMuiScreen)) return;
        ModularGuiContext context = ((IMuiScreen) event.gui).getScreen()
            .getContext();
        IWidget focused = context.getFocusedWidget()
            .getElement();
        if (!(focused instanceof AutoTemplateSearchField) || !focused.isValid()) return;

        // Cancel this physical event even when Enter/Escape removes focus during its handling.
        // Otherwise the same event can reach NEI or the container's inventory-close shortcut.
        event.setCanceled(true);
        context.updateEventState();
        int key = Keyboard.getEventKey();
        char character = Keyboard.getEventCharacter();
        AutoTemplateSearchField field = (AutoTemplateSearchField) focused;
        if (Keyboard.getEventKeyState() || key == Keyboard.KEY_NONE && Character.isDefined(character)) {
            // The native field keeps editing shortcuts and the LWJGL3 text-input path intact.
            field.onKeyPressed(character, key);
        } else {
            field.onKeyRelease(character, key);
        }
    }
}
