/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.betterquesting;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.tenkeitech.questskip.QuestSkipNetwork;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.context.IQuestContextMenuEntry;

@Pseudo
@Mixin(targets = "betterquesting.client.gui2.GuiQuestLines$1", remap = false)
public abstract class QuestSkipMenuMixin {

    @ModifyExpressionValue(
        method = "onMouseClick",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/api2/client/gui/context/QuestContextMenuRegistry;getEntries()Ljava/util/List;"),
        remap = false,
        require = 0)
    private List<IQuestContextMenuEntry> tenkeitech$filter(List<IQuestContextMenuEntry> entries, @Local IQuest quest) {
        if (QuestSkipNetwork.compatible())
            com.tenkeitech.integration.betterquesting.QuestSkipClient.filter(entries, quest);
        return entries;
    }
}
