/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.structurelib;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenkeitech.machineprojector.BetterMachineProjectorBuild;

/** Leaves the native walker in charge of placement, checks, stopping conditions and the block budget. */
@Mixin(targets = "com.gtnewhorizon.structurelib.structure.SurvivalBuildStructureWalker", remap = false)
public abstract class BetterMachineProjectorSurvivalBuildMixin {

    @Shadow
    private int built;

    @WrapOperation(
        method = "visit",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/structurelib/structure/IStructureElement;survivalPlaceBlock(Ljava/lang/Object;Lnet/minecraft/world/World;IIILnet/minecraft/item/ItemStack;Lcom/gtnewhorizon/structurelib/structure/AutoPlaceEnvironment;)Lcom/gtnewhorizon/structurelib/structure/IStructureElement$PlaceResult;"),
        require = 1)
    private PlaceResult tenkeitech$skipMissing(IStructureElement<?> element, Object controller, World world, int x,
        int y, int z, ItemStack trigger, AutoPlaceEnvironment environment, Operation<PlaceResult> original) {
        try (BetterMachineProjectorBuild.Attempt attempt = BetterMachineProjectorBuild.beginElement(environment)) {
            if (attempt == null) return original.call(element, controller, world, x, y, z, trigger, environment);
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            TileEntity tile = world.getTileEntity(x, y, z);
            PlaceResult result = original.call(element, controller, world, x, y, z, trigger, attempt.environment());
            boolean unchanged = world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == meta
                && world.getTileEntity(x, y, z) == tile;
            if (unchanged && attempt.shouldSkip(result, world, x, y, z)) {
                // An unfinished piece with no placements must report zero, never the native "complete" sentinel.
                if (built < 0) built = 0;
                attempt.markSkipped();
                return PlaceResult.REJECT_CONTINUE;
            }
            if (result == PlaceResult.REJECT && unchanged) attempt.rejected(world, x, y, z);
            return result;
        }
    }
}
