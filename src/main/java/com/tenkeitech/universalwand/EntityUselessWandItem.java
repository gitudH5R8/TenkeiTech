/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Persistent dropped wand; pickup and deliberate entity removal keep their normal semantics. */
public final class EntityUselessWandItem extends EntityItem {

    public EntityUselessWandItem(World world) {
        super(world);
        isImmuneToFire = true;
    }

    public EntityUselessWandItem(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
        isImmuneToFire = true;
    }

    @Override
    public void onUpdate() {
        // Keep the vanilla animation clock without allowing expiry or eventual integer overflow.
        if (age >= 6000) age %= 6000;
        lifespan = Integer.MAX_VALUE;
        super.onUpdate();
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }
}
