/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.betterquesting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.questskip.QuestSkipNetwork;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;

@Pseudo
@Mixin(targets = "betterquesting.api2.client.gui.controls.PanelButtonQuest", remap = false)
public abstract class QuestSkipColorMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void tenkeitech$color(GuiRectangle rectangle, int id, String text, Map.Entry<UUID, IQuest> entry,
        CallbackInfo callback) {
        if (QuestSkipNetwork.compatible())
            com.tenkeitech.integration.betterquesting.QuestSkipClient.color((PanelButtonQuest) (Object) this, entry);
    }

    @Inject(method = "getStandardTooltip", at = @At("RETURN"), remap = false, require = 0)
    private void tenkeitech$tooltip(IQuest quest, EntityPlayer player, UUID id,
        CallbackInfoReturnable<List<String>> callback) {
        if (QuestSkipNetwork.compatible())
            com.tenkeitech.integration.betterquesting.QuestSkipClient.tooltip(quest, player, callback.getReturnValue());
    }
}
