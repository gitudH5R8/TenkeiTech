/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

/** Adds feedback and optional shortage skipping only during this projector's native survival construction. */
public final class BetterMachineProjectorBuild implements AutoCloseable {

    private static final ThreadLocal<BetterMachineProjectorBuild> ACTIVE = new ThreadLocal<>();
    private final BetterMachineProjectorBuild previous;
    private final BetterMachineProjectorStorage source;
    private final EntityPlayerMP player;
    private final boolean skipMissing;
    private final Set<String> reportedMissing = new HashSet<>();
    private boolean reportedBlocked;
    private Attempt attempt;

    private BetterMachineProjectorBuild(BetterMachineProjectorStorage source, EntityPlayerMP player,
        boolean skipMissing) {
        this.previous = ACTIVE.get();
        this.source = source;
        this.player = player;
        this.skipMissing = skipMissing;
        ACTIVE.set(this);
    }

    public static int construct(ISurvivalConstructable constructable, ItemStack trigger, int budget,
        BetterMachineProjectorStorage source, EntityPlayerMP player) {
        try (BetterMachineProjectorBuild scope = new BetterMachineProjectorBuild(
            source,
            player,
            BetterMachineProjectorSettings.skipMissing(trigger))) {
            return constructable.survivalConstruct(trigger, budget, ISurvivalBuildEnvironment.create(source, player));
        }
    }

    public static Attempt beginElement(AutoPlaceEnvironment environment) {
        BetterMachineProjectorBuild scope = ACTIVE.get();
        return scope != null && environment.getActor() == scope.player ? scope.new Attempt(environment) : null;
    }

    public static void recordExtraction(BetterMachineProjectorStorage source, int requested, long taken,
        boolean simulate) {
        BetterMachineProjectorBuild scope = ACTIVE.get();
        if (scope == null || !scope.skipMissing || scope.source != source || scope.attempt == null || requested <= 0)
            return;
        // A successful later alternative must clear an earlier candidate's shortage.
        scope.attempt.missing = taken < requested;
        scope.attempt.extracted |= !simulate && taken > 0;
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    public final class Attempt implements AutoCloseable {

        private final Attempt parent;
        private final AutoPlaceEnvironment environment;
        private final List<IChatComponent> missingMessages = new ArrayList<>();
        private boolean missing;
        private boolean extracted;
        private boolean skipped;

        private Attempt(AutoPlaceEnvironment environment) {
            this.parent = attempt;
            this.environment = environment;
            attempt = this;
        }

        public AutoPlaceEnvironment environment() {
            if (!skipMissing || environment.getChatter() == null) return environment;
            return environment.withChatter(message -> {
                if (missing && !extracted) missingMessages.add(message);
                else environment.getChatter()
                    .accept(message);
            });
        }

        public boolean shouldSkip(PlaceResult result, World world, int x, int y, int z) {
            // Some native elements query materials before checking obstruction; shortage alone cannot bypass it.
            return skipMissing && result == PlaceResult.REJECT
                && missing
                && !extracted
                && StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, player);
        }

        public void rejected(World world, int x, int y, int z) {
            if (reportedBlocked || StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, player)) return;
            reportedBlocked = true;
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.better_machine_projector.message.blocked", x, y, z));
        }

        public void markSkipped() {
            skipped = true;
        }

        @Override
        public void close() {
            try {
                for (IChatComponent message : missingMessages) {
                    if (reportedMissing.add(message.getUnformattedText())) environment.getChatter()
                        .accept(
                            skipped ? message.createCopy()
                                .appendSibling(
                                    new ChatComponentTranslation(
                                        "tenkeitech.better_machine_projector.message.skipped_suffix"))
                                : message);
                }
            } finally {
                attempt = parent;
            }
        }
    }
}
