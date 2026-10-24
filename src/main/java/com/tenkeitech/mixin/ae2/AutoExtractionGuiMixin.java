/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.autoextract.AutoExtractionContainer;
import com.tenkeitech.autoextract.client.AutoExtractionLockButton;
import com.tenkeitech.autoextract.client.AutoExtractionStatus;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.localization.GuiText;

@Mixin(value = GuiInterface.class, remap = false)
public abstract class AutoExtractionGuiMixin extends GuiUpgradeable {

    @Shadow
    private GuiImgButton BlockMode;
    @Shadow
    private GuiImgButton lockCraftingMode;

    protected AutoExtractionGuiMixin(ContainerUpgradeable container) {
        super(container);
    }

    @Inject(method = "addButtons", at = @At("TAIL"), require = 1)
    @SuppressWarnings("unchecked")
    private void tenkeitech$buttons(CallbackInfo ci) {
        AutoExtractionContainer container = (AutoExtractionContainer) inventorySlots;
        int index = buttonList.indexOf(lockCraftingMode);
        lockCraftingMode = new AutoExtractionLockButton(
            lockCraftingMode.xPosition,
            lockCraftingMode.yPosition,
            container);
        buttonList.set(index, lockCraftingMode);
        int titleWidth = fontRendererObj.getStringWidth(getGuiDisplayName(GuiText.Interface.getLocal()));
        buttonList.add(new AutoExtractionStatus(guiLeft + 8 + titleWidth + 6, guiTop + 6, container));
    }

    @Inject(method = "drawFG", at = @At("TAIL"), require = 1)
    private void tenkeitech$fixed(int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        boolean fixed = ((AutoExtractionContainer) inventorySlots).tenkeitech$extractionView()
            .present();
        if (BlockMode != null) BlockMode.enabled = !fixed;
        if (lockCraftingMode != null) lockCraftingMode.enabled = !fixed;
    }
}
