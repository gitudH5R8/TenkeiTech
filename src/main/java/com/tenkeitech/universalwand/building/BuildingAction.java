/*
 * SPDX-FileCopyrightText: 2021 ThetaDev
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 */
package com.tenkeitech.universalwand.building;

import java.util.List;

/** Core-specific geometry, separate from inventory ownership and world changes. */
public interface BuildingAction {

    List<BuildingSnapshot> getSnapshots(BuildingContext context, BuildingSupplier supplier, int limit);
}
