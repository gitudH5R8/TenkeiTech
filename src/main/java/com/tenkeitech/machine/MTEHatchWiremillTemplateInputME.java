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

/** Fixed seven-page Wiremill circuit variant of the shared template input implementation. */
@IMetaTileEntity.SkipGenerateDescription
public final class MTEHatchWiremillTemplateInputME extends MTEHatchAutoTemplateInputME {

    public MTEHatchWiremillTemplateInputME(int id, String name, String regionalName) {
        super(id, name, regionalName, TemplateType.WIREMILL_WIRE_1X);
    }

    private MTEHatchWiremillTemplateInputME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures, TemplateType.WIREMILL_WIRE_1X);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchWiremillTemplateInputME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {}
}
