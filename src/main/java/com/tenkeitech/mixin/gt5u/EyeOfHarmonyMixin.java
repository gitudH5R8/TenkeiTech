/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTESuperCraftingInputME.EyeOfHarmonyInputView;
import com.tenkeitech.machine.MTESuperCraftingInputProxy;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.common.tileentities.machines.IDualInputHatch;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyMixin {

    @Unique
    private static final String TENKEITECH$CRIB_NOT_ALLOWED = "GT5U.gui.text.structure_error.crib_not_allowed";

    @Shadow
    @Final
    private Map<Fluid, Long> validFluidMap;

    @Unique
    private MTESuperCraftingInputME tenkeitech$inputHost;

    @Unique
    private EyeOfHarmonyInputView tenkeitech$inputView;

    @Unique
    private int[] tenkeitech$itemAmounts;

    @Inject(method = "checkMachine", at = @At("TAIL"))
    private void tenkeitech$allowOptionalSuperInput(IGregTechTileEntity tile, ItemStack controllerStack,
        List<StructureError> errors, CallbackInfo callback) {
        MTEEyeOfHarmony machine = (MTEEyeOfHarmony) (Object) this;
        if (machine.mInputBusses.size() != 1 || machine.mDualInputHatches.size() != 1) return;
        if (tenkeitech$resolveHost(machine.mDualInputHatches.get(0)) == null) return;
        errors.remove(StructureErrors.of(TENKEITECH$CRIB_NOT_ALLOWED));
    }

    @Inject(method = "drainFluidFromHatchesAndStoreInternally", at = @At("TAIL"))
    private void tenkeitech$drainSuperInputFluids(CallbackInfo callback) {
        tenkeitech$clearInputView();
        tenkeitech$prepareInputView();
        if (tenkeitech$inputView == null) return;

        boolean changed = false;
        for (FluidStack stack : tenkeitech$inputView.getFluids()) {
            if (stack == null || stack.amount <= 0 || !validFluidMap.containsKey(stack.getFluid())) continue;
            validFluidMap.merge(stack.getFluid(), (long) stack.amount, Long::sum);
            stack.amount = 0;
            changed = true;
        }
        if (changed) tenkeitech$inputHost.acknowledgeEyeOfHarmonyInputChanges();
    }

    @Inject(method = "processRecipe", at = @At("HEAD"))
    private void tenkeitech$beforeProcessRecipe(EyeOfHarmonyRecipe recipe,
        CallbackInfoReturnable<CheckRecipeResult> callback) {
        if (tenkeitech$inputView == null) tenkeitech$prepareInputView();
        if (tenkeitech$inputView == null) return;

        ItemStack[] items = tenkeitech$inputView.getItems();
        tenkeitech$itemAmounts = new int[items.length];
        for (int index = 0; index < items.length; index++) {
            tenkeitech$itemAmounts[index] = items[index] == null ? 0 : items[index].stackSize;
        }
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchInputBus;getRealInventory()[Lnet/minecraft/item/ItemStack;"))
    private ItemStack[] tenkeitech$appendSuperInputItems(MTEHatchInputBus inputBus) {
        ItemStack[] original = inputBus.getRealInventory();
        if (tenkeitech$inputView == null || tenkeitech$inputView.getItems().length == 0) return original;

        ItemStack[] combined = Arrays.copyOf(original, original.length + tenkeitech$inputView.getItems().length);
        System.arraycopy(
            tenkeitech$inputView.getItems(),
            0,
            combined,
            original.length,
            tenkeitech$inputView.getItems().length);
        return combined;
    }

    @Inject(method = "processRecipe", at = @At("RETURN"))
    private void tenkeitech$afterProcessRecipe(EyeOfHarmonyRecipe recipe,
        CallbackInfoReturnable<CheckRecipeResult> callback) {
        if (tenkeitech$inputHost != null && tenkeitech$itemsChanged()) {
            tenkeitech$inputHost.acknowledgeEyeOfHarmonyInputChanges();
        }
        tenkeitech$clearInputView();
    }

    @Unique
    private void tenkeitech$prepareInputView() {
        MTEEyeOfHarmony machine = (MTEEyeOfHarmony) (Object) this;
        if (machine.mDualInputHatches.size() != 1) return;
        tenkeitech$inputHost = tenkeitech$resolveHost(machine.mDualInputHatches.get(0));
        if (tenkeitech$inputHost != null) {
            tenkeitech$inputView = tenkeitech$inputHost.createEyeOfHarmonyInputView();
        }
    }

    @Unique
    private boolean tenkeitech$itemsChanged() {
        if (tenkeitech$inputView == null || tenkeitech$itemAmounts == null) return false;
        ItemStack[] items = tenkeitech$inputView.getItems();
        if (items.length != tenkeitech$itemAmounts.length) return true;
        for (int index = 0; index < items.length; index++) {
            int amount = items[index] == null ? 0 : items[index].stackSize;
            if (amount != tenkeitech$itemAmounts[index]) return true;
        }
        return false;
    }

    @Unique
    private void tenkeitech$clearInputView() {
        tenkeitech$inputHost = null;
        tenkeitech$inputView = null;
        tenkeitech$itemAmounts = null;
    }

    @Unique
    private static MTESuperCraftingInputME tenkeitech$resolveHost(IDualInputHatch hatch) {
        if (hatch instanceof MTESuperCraftingInputME host) return host;
        if (hatch instanceof MTESuperCraftingInputProxy proxy) return proxy.getMaster();
        return null;
    }
}
