/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.gt5u;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTESuperCraftingInputProxy;
import com.tenkeitech.patternmode.MachinePatternBatch;
import com.tenkeitech.patternmode.PatternModeController;
import com.tenkeitech.patternmode.SuperPatternModeBuffer;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class PatternModeControllerMixin implements PatternModeController {

    @Shadow
    @Final
    protected ProcessingLogic processingLogic;

    @Unique
    private MachinePatternBatch tenkeitech$batch;

    @Override
    public MachinePatternBatch tenkeitech$patternBatch() {
        if (tenkeitech$batch == null) tenkeitech$batch = new MachinePatternBatch((MTEMultiBlockBase) (Object) this);
        return tenkeitech$batch;
    }

    @Override
    public void tenkeitech$invalidateRecipeCache(IDualInputInventoryWithPattern inventory) {
        if (processingLogic != null && inventory != null) processingLogic.removeInventoryRecipeCache(inventory);
    }

    @Inject(method = "checkRecipe()Z", at = @At("HEAD"), cancellable = true)
    private void tenkeitech$beforeRecipe(CallbackInfoReturnable<Boolean> callback) {
        if (tenkeitech$batch != null && !tenkeitech$batch.beforeRecipeCheck()) callback.setReturnValue(false);
    }

    @Inject(method = "checkRecipe()Z", at = @At("RETURN"))
    private void tenkeitech$afterRecipe(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) return;
        MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        List<MTESuperCraftingInputME> notified = new ArrayList<>();
        for (IDualInputHatch hatch : controller.mDualInputHatches) {
            MTESuperCraftingInputME host = hatch instanceof MTESuperCraftingInputME direct ? direct
                : hatch instanceof MTESuperCraftingInputProxy proxy && SuperPatternModeBuffer.isLoaded(proxy)
                    ? proxy.getMaster()
                    : null;
            if (host != null && SuperPatternModeBuffer.isLoaded(host) && !notified.contains(host)) {
                notified.add(host);
                host.onControllerRecipeStarted();
            }
        }
    }

    @Inject(method = "onPostTick", at = @At("TAIL"))
    private void tenkeitech$tick(IGregTechTileEntity tile, long timer, CallbackInfo callback) {
        if (tile.isServerSide() && tenkeitech$batch != null) tenkeitech$batch.tickController();
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void tenkeitech$save(NBTTagCompound tag, CallbackInfo callback) {
        if (tenkeitech$batch != null && tenkeitech$batch.active())
            tag.setBoolean("tenkeitechPatternBatchPending", true);
        else tag.removeTag("tenkeitechPatternBatchPending");
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void tenkeitech$load(NBTTagCompound tag, CallbackInfo callback) {
        if (tag.getBoolean("tenkeitechPatternBatchPending")) tenkeitech$patternBatch().restore(true);
    }
}
