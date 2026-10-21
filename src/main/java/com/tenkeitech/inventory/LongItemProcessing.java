/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/** Long-aware item parallel calculation and atomic consumption. */
public final class LongItemProcessing {

    private LongItemProcessing() {}

    public static boolean canHandle(GTRecipe recipe) {
        if (recipe == null || recipe.mInputs == null || recipe.mInputs.length == 0) return false;
        if (recipe.mInputChances != null) {
            for (int chance : recipe.mInputChances) if (chance < 10_000) return false;
        }
        return true;
    }

    public static double calculate(ILongItemInputInventory inventory, GTRecipe recipe, int limit) {
        if (inventory == null || limit <= 0 || !canHandle(recipe)) return 0;
        MatchingContext context = new MatchingContext(inventory.longItemSnapshot());
        GTRecipe.RecipeItemInput[] requirements = recipe.getCachedCombinedItemInputs();
        int lower = 0;
        int upper = limit;
        while (lower < upper) {
            // Godforge prediction can request Integer.MAX_VALUE parallels; keep the midpoint arithmetic wide.
            int candidate = lower + (int) (((long) upper - lower + 1) / 2);
            if (createPlan(context, requirements, candidate) == null) upper = candidate - 1;
            else lower = candidate;
        }
        return lower;
    }

    public static void consume(ILongItemInputInventory inventory, GTRecipe recipe, int parallel) {
        if (inventory == null || parallel <= 0 || !canHandle(recipe)) return;
        MatchingContext context = new MatchingContext(inventory.longItemSnapshot());
        Map<ItemInputKey, PlannedConsumption> plan = createPlan(
            context,
            recipe.getCachedCombinedItemInputs(),
            parallel);
        if (plan == null) throw new IllegalStateException("Long item inventory changed during recipe calculation");

        for (PlannedConsumption planned : plan.values()) {
            if (inventory.getLongItemAmount(planned.identity) < planned.amount) {
                throw new IllegalStateException("Long item inventory changed during recipe calculation");
            }
        }
        for (PlannedConsumption planned : plan.values()) inventory.consumeLongItem(planned.identity, planned.amount);
    }

    private static Map<ItemInputKey, PlannedConsumption> createPlan(MatchingContext context,
        GTRecipe.RecipeItemInput[] requirements, int parallel) {
        long[] remaining = context.amounts.clone();
        Map<ItemInputKey, PlannedConsumption> plan = new HashMap<>();
        for (GTRecipe.RecipeItemInput requirement : requirements) {
            if (requirement == null || requirement.inputAmount <= 0) continue;
            final long requested;
            try {
                requested = Math.multiplyExact(requirement.inputAmount, (long) parallel);
            } catch (ArithmeticException ignored) {
                return null;
            }
            long needed = requested;
            for (int index = 0; index < context.snapshots.size() && needed > 0; index++) {
                ItemStack identity = context.identities[index];
                if (remaining[index] <= 0 || !requirement.matchesRecipe(context.associations[index], identity)) {
                    continue;
                }
                long used = Math.min(needed, remaining[index]);
                ItemInputKey key = ItemInputKey.of(identity);
                PlannedConsumption planned = plan.get(key);
                if (planned == null) {
                    planned = new PlannedConsumption(identity);
                    plan.put(key, planned);
                }
                planned.amount += used;
                remaining[index] -= used;
                needed -= used;
            }
            if (needed > 0) return null;
        }
        return plan;
    }

    private static final class MatchingContext {

        private final List<LongItemStore.Snapshot> snapshots;
        private final ItemStack[] identities;
        private final ItemData[] associations;
        private final long[] amounts;

        private MatchingContext(List<LongItemStore.Snapshot> snapshots) {
            this.snapshots = snapshots;
            this.identities = new ItemStack[snapshots.size()];
            this.associations = new ItemData[snapshots.size()];
            this.amounts = new long[snapshots.size()];
            for (int index = 0; index < snapshots.size(); index++) {
                identities[index] = snapshots.get(index)
                    .getIdentity();
                associations[index] = GTOreDictUnificator.getAssociation(identities[index]);
                amounts[index] = snapshots.get(index)
                    .getAmount();
            }
        }
    }

    private static final class PlannedConsumption {

        private final ItemStack identity;
        private long amount;

        private PlannedConsumption(ItemStack identity) {
            this.identity = identity;
        }
    }
}
