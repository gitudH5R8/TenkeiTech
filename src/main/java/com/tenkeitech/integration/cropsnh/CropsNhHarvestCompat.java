/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.integration.cropsnh;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.gtnewhorizon.cropsnh.api.ICropStickTile;

/** Optional Crops NH API references, entered only after the mod-presence check. */
public final class CropsNhHarvestCompat {

    private CropsNhHarvestCompat() {}

    public static boolean canHarvest(World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof ICropStickTile crop && crop.canHarvest();
    }

    public static boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        return player.worldObj.getTileEntity(x, y, z) instanceof ICropStickTile crop && crop.canHarvest()
            && crop.doPlayerHarvest(player, false);
    }
}
