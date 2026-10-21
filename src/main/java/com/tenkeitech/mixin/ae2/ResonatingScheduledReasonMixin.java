/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.util.ScheduledReason;

/** Append one diagnostic without changing the ordinal of any AE reason. No reflection is used. */
@Mixin(value = ScheduledReason.class, remap = false)
public abstract class ResonatingScheduledReasonMixin {

    @Shadow
    @Final
    @Mutable
    private static ScheduledReason[] $VALUES;
    @Shadow
    @Final
    @Mutable
    public static ScheduledReason[] VALUES;

    @Invoker("<init>")
    private static ScheduledReason tenkeitech$create(String name, int ordinal) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void tenkeitech$appendReason(CallbackInfo ci) {
        ScheduledReason reason = tenkeitech$create("TENKEITECH_UNBOUND_INPUTS", $VALUES.length);
        $VALUES = Arrays.copyOf($VALUES, $VALUES.length + 1);
        $VALUES[$VALUES.length - 1] = reason;
        ScheduledReason unreachable = tenkeitech$create("TENKEITECH_TARGET_UNREACHABLE", $VALUES.length);
        $VALUES = Arrays.copyOf($VALUES, $VALUES.length + 1);
        $VALUES[$VALUES.length - 1] = unreachable;
        // AE packet decoding uses this cached array rather than calling values().
        VALUES = $VALUES.clone();
    }
}
