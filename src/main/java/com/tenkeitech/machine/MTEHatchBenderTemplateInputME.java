/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.tenkeitech.autotemplate.TemplateType;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/** Fixed seven-page Bender circuit variant of the shared template input implementation. */
@IMetaTileEntity.SkipGenerateDescription
public final class MTEHatchBenderTemplateInputME extends MTEHatchAutoTemplateInputME {

    public MTEHatchBenderTemplateInputME(int id, String name, String regionalName) {
        super(id, name, regionalName, TemplateType.BENDER_PLATE);
    }

    private MTEHatchBenderTemplateInputME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures, TemplateType.BENDER_PLATE);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchBenderTemplateInputME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {}
}
