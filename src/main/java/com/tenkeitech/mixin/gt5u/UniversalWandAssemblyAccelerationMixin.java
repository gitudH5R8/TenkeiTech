/* SPDX-License-Identifier: GPL-3.0-only */
package com.tenkeitech.mixin.gt5u;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.integration.gt5u.WandAssemblyAcceleration;
import com.tenkeitech.universalwand.UniversalWandAccelerationHandler;

import ggfab.mte.MTEAdvAssLine;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;

@Mixin(value = MTEAdvAssLine.class, remap = false)
public abstract class UniversalWandAssemblyAccelerationMixin extends MTEExtendedPowerMultiBlockBase<MTEAdvAssLine>
    implements WandAssemblyAcceleration {

    @Shadow
    private boolean stuck;

    protected UniversalWandAssemblyAccelerationMixin(int id, String name, String regional) {
        super(id, name, regional);
    }

    @Override
    public boolean tenkeitech$isAssemblyBlocked() {
        return stuck;
    }

    @Inject(
        method = "onRunningTick",
        at = @At(value = "FIELD", target = "Lggfab/mte/MTEAdvAssLine;baseEUt:J", ordinal = 0),
        cancellable = true)
    private void tenkeitech$skipAdditionalEnergy(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (UniversalWandAccelerationHandler.isUpdating((net.minecraft.tileentity.TileEntity) getBaseMetaTileEntity()))
            callback.setReturnValue(true);
    }
}
