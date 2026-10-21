/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.tenkeitech.gui.MTEWirelessNetworkBookmarkGui;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

/** Stateless terminal for browsing the opening player's accessible wireless network records. */
@IMetaTileEntity.SkipGenerateDescription
public final class MTEWirelessNetworkBookmark extends MTEHatch {

    private static final IIconContainer BOOKMARK_FRONT = Textures.BlockIcons
        .custom("tenkeitech:machine/wireless_network_bookmark_front");

    public MTEWirelessNetworkBookmark(int id, String name, String regionalName) {
        super(id, name, regionalName, 1, 0, (String) null);
    }

    public MTEWirelessNetworkBookmark(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEWirelessNetworkBookmark(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture getCasingTexture() {
        return WirelessMachineTextures.evCasing(ForgeDirection.NORTH);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture casing = WirelessMachineTextures.evCasing(side);
        return side == facing ? getTexturesInactive(casing) : new ITexture[] { casing };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return getTexturesInactive(baseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(BOOKMARK_FRONT) };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != null && facing != ForgeDirection.UNKNOWN;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        openGui(player);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEWirelessNetworkBookmarkGui(this, data.getPlayer()).build(data, syncManager, uiSettings);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("tenkeitech.machine.wireless_network_bookmark.desc");
    }
}
