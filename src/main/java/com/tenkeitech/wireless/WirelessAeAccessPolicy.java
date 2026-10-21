/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.wireless;

import java.util.UUID;

import serverutils.ServerUtilitiesConfig;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ServerUtilitiesAPI;
import serverutils.lib.data.Universe;

/** Server-side wireless network authorization backed by ServerUtilities' live team membership. */
public final class WirelessAeAccessPolicy {

    private WirelessAeAccessPolicy() {}

    public static boolean canUseNetwork(UUID player, UUID owner) {
        if (player == null || owner == null) return false;
        if (owner.equals(player)) return true;
        return !ServerUtilitiesConfig.teams.disable_teams && ServerUtilitiesAPI.arePlayersInSameTeam(player, owner);
    }

    public static String getOwnerGroupDisplayName(UUID owner) {
        if (owner == null || !Universe.loaded()) return "";
        ForgePlayer ownerPlayer = Universe.get()
            .getPlayer(owner);
        if (ownerPlayer == null) return "";
        if (!ServerUtilitiesConfig.teams.disable_teams && ownerPlayer.hasTeam()) {
            String title = ownerPlayer.team.getTitle()
                .getUnformattedText();
            if (title != null && !title.isEmpty()) return title;
        }
        String playerName = ownerPlayer.getName();
        return playerName == null ? "" : playerName;
    }
}
