/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.integration.gt5u.ParallelHelperConsumptionAccessor;
import com.tenkeitech.inventory.ILongFluidInputInventory;
import com.tenkeitech.inventory.ILongItemInputInventory;
import com.tenkeitech.inventory.LongFluidProcessing;
import com.tenkeitech.inventory.LongFluidStore;
import com.tenkeitech.inventory.LongItemProcessing;
import com.tenkeitech.inventory.LongItemStore;
import com.tenkeitech.inventory.LongProcessingSimulation;

import gregtech.api.interfaces.tileentity.IRecipeLockable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;

@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class LongFluidProcessingLogicMixin {

    @Shadow
    protected IDualInputInventoryWithPattern activeDualInv;

    @Shadow
    protected boolean isRecipeLocked;

    @Shadow
    protected IRecipeLockable recipeLockableMachine;

    @Shadow
    protected FluidStack[] inputFluids;

    @Inject(method = "createParallelHelper", at = @At("RETURN"), cancellable = true)
    private void tenkeitech$installLongFluidCallbacks(GTRecipe recipe,
        CallbackInfoReturnable<ParallelHelper> callback) {
        if (recipe == null) return;
        if (isRecipeLocked && (recipeLockableMachine == null || recipeLockableMachine.getSingleRecipeCheck() == null))
            return;

        ILongFluidInputInventory resolvedFluidInventory = activeDualInv instanceof ILongFluidInputInventory
            ? (ILongFluidInputInventory) activeDualInv
            : tenkeitech$resolveBackingOwner(inputFluids);
        ILongItemInputInventory resolvedItemInventory = activeDualInv instanceof ILongItemInputInventory
            ? (ILongItemInputInventory) activeDualInv
            : tenkeitech$resolveItemBackingOwner(tenkeitech$resolveInputItems());
        boolean hasFluids = recipe.mFluidInputs != null && recipe.mFluidInputs.length > 0;
        boolean hasItems = recipe.mInputs != null && recipe.mInputs.length > 0;
        if (!hasFluids || !LongFluidProcessing.canHandle(recipe)
            || resolvedFluidInventory == null
            || !resolvedFluidInventory.ownsAllFluidInputs(recipe)) {
            resolvedFluidInventory = null;
        }
        if (!hasItems || resolvedItemInventory == null
            || !LongItemProcessing.canHandle(recipe)
            || LongItemProcessing.calculate(resolvedItemInventory, recipe, 1) < 1) {
            resolvedItemInventory = null;
        }
        if (resolvedFluidInventory == null && resolvedItemInventory == null) return;
        final ILongFluidInputInventory longInventory = resolvedFluidInventory;
        final ILongItemInputInventory longItemInventory = resolvedItemInventory;
        final LongProcessingSimulation simulation = new LongProcessingSimulation(longInventory, longItemInventory);

        ParallelHelper helper = callback.getReturnValue();
        if (isRecipeLocked) helper.setRecipeLocked(recipeLockableMachine, false);
        helper.setMaxParallelCalculator((currentRecipe, limit, fluids, items) -> {
            if (!((ParallelHelperConsumptionAccessor) (Object) helper).tenkeitech$isConsumptionEnabled()) {
                return simulation.calculate(currentRecipe, limit, fluids, items);
            }
            return LongFluidProcessing.calculate(longInventory, longItemInventory, currentRecipe, limit, fluids, items);
        });
        helper.setInputConsumer((currentRecipe, parallel, fluids, items) -> {
            if (((ParallelHelperConsumptionAccessor) (Object) helper).tenkeitech$isConsumptionEnabled()) {
                LongFluidProcessing.consume(longInventory, longItemInventory, currentRecipe, parallel, fluids, items);
            } else {
                simulation.consume(currentRecipe, parallel, fluids, items);
            }
        });
        callback.setReturnValue(helper);
    }

    @Shadow
    protected ItemStack[] inputItems;

    private ItemStack[] tenkeitech$resolveInputItems() {
        return inputItems;
    }

    private static ILongFluidInputInventory tenkeitech$resolveBackingOwner(FluidStack[] fluids) {
        ILongFluidInputInventory resolved = null;
        if (fluids == null) return null;
        for (FluidStack fluid : fluids) {
            ILongFluidInputInventory owner = LongFluidStore.getOwner(fluid);
            if (owner == null) continue;
            if (resolved != null && resolved != owner) return null;
            resolved = owner;
        }
        return resolved;
    }

    private static ILongItemInputInventory tenkeitech$resolveItemBackingOwner(ItemStack[] items) {
        ILongItemInputInventory resolved = null;
        if (items == null) return null;
        for (ItemStack item : items) {
            ILongItemInputInventory owner = LongItemStore.getOwner(item);
            if (owner == null) continue;
            if (resolved != null && resolved != owner) return null;
            resolved = owner;
        }
        return resolved;
    }
}
