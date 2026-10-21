/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.CondensateType;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import tectech.recipe.TecTechRecipeMaps;

public class MTECondensateAssemblyDebug extends MTEExtendedPowerMultiBlockBase<MTECondensateAssemblyDebug>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final int MAX_PARALLEL = 2_100_000_000;
    private static IStructureDefinition<MTECondensateAssemblyDebug> structureDefinition;

    public MTECondensateAssemblyDebug(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    private MTECondensateAssemblyDebug(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTECondensateAssemblyDebug(mName);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tileEntity, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(mName, 1, 1, 0, errors)) return;
        checkHasEnergyHatch(errors);
        checkHasMaintenanceHatch(errors);
        checkHasInputBus(errors);
        checkHasInputHatch(errors);
        checkHasOutputBus(errors);
    }

    @Override
    public IStructureDefinition<MTECondensateAssemblyDebug> getStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = StructureDefinition.<MTECondensateAssemblyDebug>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C-C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    buildHatchAdder(MTECondensateAssemblyDebug.class)
                        .atLeast(InputBus, OutputBus, InputHatch, Maintenance, Energy)
                        .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                        .hint(1)
                        .buildAndChain(
                            buildHatchAdder(MTECondensateAssemblyDebug.class).anyOf(OutputHatch, Muffler)
                                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                                .hint(1)
                                .build(),
                            Casings.SolidSteelMachineCasing.asElement()))
                .build();
        }
        return structureDefinition;
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(mName, stack, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int elementBudget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(mName, stack, 1, 1, 0, elementBudget, environment, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addInfo(StatCollector.translateToLocal("tenkeitech.machine.condensate_assembly_debug.desc"))
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center, 2nd layer")
            .addCasing("0+", Casings.SolidSteelMachineCasing.getLocalizedName(), false)
            .addEnergyHatch("1+", "Any casing", 1)
            .addMaintenanceHatch("1", "Any casing", 1)
            .addInputBus("1+", "Any casing", 1)
            .addOutputBus("1+", "Any casing", 1)
            .addInputHatch("1+", "Any casing", 1)
            .addOutputHatch("0+", "Any casing", 1)
            .addMufflerHatch("0+", "Any casing", 1)
            .addAir("Interior of the structure")
            .toolTipFinisher();
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tileEntity, int casingIndex) {
        return super.addToMachineList(tileEntity, casingIndex) || addMufflerToMachineList(tileEntity, casingIndex);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            facing,
            active,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_GLOW,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE,
            OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.SolidSteelMachineCasing.getCasingTexture();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TecTechRecipeMaps.condensateAssemblingRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        return MAX_PARALLEL;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Override
            protected CheckRecipeResult validateRecipe(GTRecipe recipe) {
                return sourceFluidInputs(recipe) == null ? CheckRecipeResultRegistry.NO_RECIPE
                    : super.validateRecipe(recipe);
            }

            @Override
            protected ParallelHelper createParallelHelper(GTRecipe recipe) {
                GTRecipe sourceRecipe = recipe.copy();
                sourceRecipe.mFluidInputs = sourceFluidInputs(recipe);
                sourceRecipe.mAltFluidInputs = null;
                sourceRecipe.mFluidInputChances = null;
                return super.createParallelHelper(sourceRecipe);
            }

            @Override
            protected double calculateDuration(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator) {
                return 1;
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
    }

    private static FluidStack[] sourceFluidInputs(GTRecipe recipe) {
        FluidStack[] condensates = recipe.getMetadata(GTRecipeConstants.CONDENSATE_INPUT);
        if (condensates == null || condensates.length == 0) return null;

        FluidStack[] sources = new FluidStack[condensates.length];
        for (int i = 0; i < condensates.length; i++) {
            FluidStack condensate = condensates[i];
            if (condensate == null || condensate.getFluid() == null || condensate.amount <= 0) return null;

            CondensateType type = CondensateType.getCondensateType(condensate.getFluid());
            if (type == null) return null;
            FluidStack source = type.getSourceFluid();
            if (source == null || source.getFluid() == null) return null;
            sources[i] = source.copy();
            sources[i].amount = condensate.amount;
        }
        return sources;
    }
}
