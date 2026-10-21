/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.betterquesting;

import java.util.Collections;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.questskip.QuestSkipNetwork;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.cache.QuestCache;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import bq_standard.tasks.TaskCheckbox;
import bq_standard.tasks.TaskCrafting;
import bq_standard.tasks.TaskFluid;
import bq_standard.tasks.TaskOptionalRetrieval;
import bq_standard.tasks.TaskRetrieval;
import bq_standard.tasks.TaskXP;

/** Only audited built-in non-consuming task implementations can be skipped. */
public final class QuestSkipService {

    public static final String MARKER = "tenkeitechSkipped";

    private QuestSkipService() {}

    public static boolean eligible(IQuest quest, EntityPlayerMP player) {
        UUID id = QuestingAPI.getQuestingUUID(player);
        if (quest == null || quest.getClass() != QuestInstance.class
            || quest.getProperty(NativeProps.GLOBAL)
            || quest.getProperty(NativeProps.REPEAT_TIME) >= 0
            || quest.isComplete(id)
            || !quest.isUnlocked(id)
            || !QuestCache.isQuestShown(quest, id, player)
            || !quest.canSubmit(player)
            || quest.getTasks()
                .size() == 0)
            return false;
        for (var entry : quest.getTasks()
            .getEntries()) {
            var task = entry.getValue();
            if (task == null) return false;
            if (task.getClass() == TaskCheckbox.class || task.getClass() == TaskCrafting.class) {
                // Audited built-ins have no consumption path and inherit TaskBase.setComplete.
                continue;
            } else if (task.getClass() == TaskRetrieval.class || task.getClass() == TaskOptionalRetrieval.class) {
                if (((TaskRetrieval) task).consume) return false;
            } else if (task.getClass() == TaskFluid.class) {
                if (((TaskFluid) task).consume) return false;
            } else if (task.getClass() == TaskXP.class) {
                if (((TaskXP) task).consume) return false;
            } else return false;
        }
        return true;
    }

    public static void handle(EntityPlayerMP player, QuestSkipNetwork.Request request) {
        IQuest quest = QuestDatabase.INSTANCE.get(request.quest);
        if (quest != null && !quest.isUnlocked(QuestingAPI.getQuestingUUID(player))) {
            QuestSkipNetwork.reply(player, request.sequence, 5);
            return;
        }
        QuestCache cache = (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (cache == null || !eligible(quest, player)) {
            QuestSkipNetwork.reply(player, request.sequence, 2);
            return;
        }
        if (!request.commit) {
            QuestSkipNetwork.confirmable(player, request);
            QuestSkipNetwork.reply(player, request.sequence, 3);
            return;
        }
        if (!QuestSkipNetwork.consumeConfirmation(player, request)) {
            QuestSkipNetwork.reply(player, request.sequence, 2);
            return;
        }
        UUID id = QuestingAPI.getQuestingUUID(player);
        NBTTagCompound completion = new NBTTagCompound();
        completion.setBoolean("claimed", true);
        completion.setLong("timestamp", System.currentTimeMillis());
        completion.setBoolean(MARKER, true);
        // Install claimed first: even a later failure must never expose a reward claim window.
        quest.setCompletionInfo(id, completion);
        SaveLoadHandler.INSTANCE.markDirty();
        for (var entry : quest.getTasks()
            .getEntries())
            entry.getValue()
                .setComplete(id);
        cache.markQuestDirty(request.quest);
        cache.updateCache(player);
        NetQuestSync.sendSync(player, Collections.singletonList(request.quest), false, true);
        TenkeiTech.LOG.info("{} skipped BQ quest {} without rewards", id, request.quest);
        QuestSkipNetwork.reply(player, request.sequence, 4);
    }
}
