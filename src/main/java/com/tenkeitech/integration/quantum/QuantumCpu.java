/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.quantum;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Selects an independent order in the same physical quantum computer. */
public interface QuantumCpu {

    CraftingCPUCluster tenkeitech$childCpu();
}
