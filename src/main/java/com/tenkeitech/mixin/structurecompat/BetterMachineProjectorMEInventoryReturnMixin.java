/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.structurecompat;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.tenkeitech.machineprojector.BetterMachineProjectorReturnTarget;

import appeng.api.AEApi;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;

/** Reuses the exact extractor backend, including wireless range, AE terminal power and native security. */
@Pseudo
@Mixin(targets = "net.glease.structurecompat.MEInventoryStackExtractor", remap = false)
public abstract class BetterMachineProjectorMEInventoryReturnMixin implements BetterMachineProjectorReturnTarget {

    @Invoker("fromItem")
    public abstract Pair<IEnergySource, IMEInventoryHandler<IAEItemStack>> tenkeitech$source(ItemStack source,
        EntityPlayerMP player);

    @Override
    public ItemStack tenkeitech$returnItems(ItemStack source, EntityPlayerMP player, ItemStack items) {
        Pair<IEnergySource, IMEInventoryHandler<IAEItemStack>> backend = tenkeitech$source(source, player);
        if (backend == null || backend.getLeft() == null || backend.getRight() == null) return items;
        IAEItemStack remainder = AEApi.instance()
            .storage()
            .poweredInsert(
                backend.getLeft(),
                backend.getRight(),
                AEApi.instance()
                    .storage()
                    .createItemStack(items),
                new PlayerSource(player, null));
        return remainder == null ? null : remainder.getItemStack();
    }
}
