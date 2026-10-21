/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import net.minecraft.item.ItemStack;

import com.tenkeitech.universalwand.building.BuildingContext.Position;

/** Material-provider boundary used by the core and the job. */
public interface BuildingSupplier {

    boolean isSupported();

    boolean hasRemaining();

    PlacementSnapshot getPlaceSnapshot(BuildingContext context, Position source, Position destination);

    MaterialLease takeOne(ItemStack required);

    default void onFinished() {}
}
