/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.eco;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenkeitech.integration.quantum.QuantumCpu;
import com.tenkeitech.integration.quantum.QuantumCpuSource;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu", remap = false)
public abstract class ECONativeCpuMixin implements QuantumCpu {

    @Inject(method = "trackRequester", at = @At("RETURN"))
    private void tenkeitech$tagOrder(CallbackInfo callback) {
        ((QuantumCpuSource) this).tenkeitech$quantumOrder()
            .submitted((CraftingCPUCluster) (Object) this);
    }

    @Inject(method = "writeTracking", at = @At("RETURN"))
    private void tenkeitech$saveOrder(NBTTagCompound tag, CallbackInfo callback) {
        ((QuantumCpuSource) this).tenkeitech$quantumOrder()
            .write(tag);
    }

    @Inject(method = "readTracking", at = @At("RETURN"))
    private void tenkeitech$loadOrder(NBTTagCompound tag, CallbackInfo callback) {
        ((QuantumCpuSource) this).tenkeitech$quantumOrder()
            .read(tag);
    }

    @Override
    public CraftingCPUCluster tenkeitech$childCpu() {
        // Non-null requester prevents ECO's standalone-order merge; the native pool allocates a fresh CPU.
        return (CraftingCPUCluster) (Object) this;
    }
}
