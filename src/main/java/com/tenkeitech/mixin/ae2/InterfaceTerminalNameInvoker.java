/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.client.gui.implementations.GuiInterfaceTerminal;

@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public interface InterfaceTerminalNameInvoker {

    @Invoker("translateRawName")
    static String translate(String name, String suffix) {
        throw new AssertionError();
    }
}
