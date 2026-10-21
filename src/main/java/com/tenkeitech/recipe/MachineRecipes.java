/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.recipe;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import com.tenkeitech.ModMetaTileEntities;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/** Base machine acquisition recipes, registered after dependent mods initialize their items. */
public final class MachineRecipes {

    private MachineRecipes() {}

    public static void register() {
        IBlocks ae = AEApi.instance()
            .definitions()
            .blocks();
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hatch_Input_Bus_EV.get(1),
                ItemList.Hatch_Input_EV.get(1),
                ae.controller()
                    .maybeStack(1)
                    .get(),
                ae.iface()
                    .maybeStack(1)
                    .get(),
                GTUtility.getIntegratedCircuit(13))
            .itemOutputs(ModMetaTileEntities.superPatternBuffer.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ModMetaTileEntities.superPatternBuffer.copy(),
                ItemList.Sensor_UV.get(1),
                ItemList.Emitter_UV.get(1),
                ae.quantumLink()
                    .maybeStack(1)
                    .get(),
                ae.quantumRing()
                    .maybeStack(8)
                    .get(),
                new Object[] { OrePrefixes.circuit.get(Materials.UHV), 2 })
            .itemOutputs(ModMetaTileEntities.superPatternBufferProxy.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                ae.wirelessConnector()
                    .maybeStack(4)
                    .get(),
                ae.quantumLink()
                    .maybeStack(4)
                    .get(),
                ae.quantumRing()
                    .maybeStack(36)
                    .get(),
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorEV, 2),
                new Object[] { OrePrefixes.circuit.get(Materials.LuV), 8 },
                ItemList.Field_Generator_EV.get(1))
            .fluidInputs(Materials.Radon.getGas(1000))
            .itemOutputs(ModMetaTileEntities.wirelessNetworkCore.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                ae.wirelessConnector()
                    .maybeStack(2)
                    .get(),
                new Object[] { OrePrefixes.circuit.get(Materials.EV), 2 },
                GTOreDictUnificator.get(OrePrefixes.wireFine, Materials.TungstenSteel, 16),
                ItemList.Emitter_EV.get(4))
            .itemOutputs(ModMetaTileEntities.wirelessNetworkBookmark.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hatch_Input_Bus_ME.get(1),
                ae.iface()
                    .maybeStack(1)
                    .get(),
                AEApi.instance()
                    .definitions()
                    .materials()
                    .blankPattern()
                    .maybeStack(64)
                    .get())
            .itemOutputs(ModMetaTileEntities.autoTemplateInput.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ModMetaTileEntities.autoTemplateInput.copy(),
                ItemList.Sensor_IV.get(1),
                ItemList.Emitter_IV.get(1))
            .itemOutputs(ModMetaTileEntities.autoTemplateInputProxy.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ModMetaTileEntities.autoTemplateInput.copy(),
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.Steel, 64))
            .itemOutputs(ModMetaTileEntities.wiremillTemplateInput.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ModMetaTileEntities.autoTemplateInput.copy(),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Steel, 64))
            .itemOutputs(ModMetaTileEntities.benderTemplateInput.copy())
            .duration(5 * SECONDS)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
    }
}
