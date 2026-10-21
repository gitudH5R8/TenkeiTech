/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

/** Runtime-only extension mixed into AE2 grid nodes. Persistent membership remains owned by wireless cores. */
public interface WirelessAeChannelExemptNode {

    boolean tenkeitech$isWirelessChannelExempt();

    /** @return true when this acquisition changed the node from native to channel-exempt. */
    boolean tenkeitech$acquireWirelessChannelExemption();

    /** @return true when this release restored the node's native channel requirement. */
    boolean tenkeitech$releaseWirelessChannelExemption();
}
