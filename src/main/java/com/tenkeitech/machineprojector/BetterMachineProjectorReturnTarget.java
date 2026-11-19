/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machineprojector;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Implemented only on material extractors whose public backend also supports insertion. */
public interface BetterMachineProjectorReturnTarget {

    ItemStack tenkeitech$returnItems(ItemStack source, EntityPlayerMP player, ItemStack items);
}
