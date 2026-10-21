/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import static gregtech.api.enums.GTValues.TIER_COLORS;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_CRAFTING_INPUT_SLAVE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;
import com.tenkeitech.autotemplate.TemplateType;
import com.tenkeitech.patternmode.SuperPatternModeBuffer;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.modularui2.ProxiedMteGui;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IHatchWatcher;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Controller-facing mirror for one loaded fixed-template input bus. The mirror owns only the saved
 * host location; the host remains the sole owner of AE patterns, configuration and real inputs.
 */
@IMetaTileEntity.SkipGenerateDescription
public final class MTEHatchAutoTemplateInputProxy extends MTEHatchInputBus implements IDualInputHatch, IDataCopyable {

    public static final String COPIED_DATA_IDENTIFIER = "tenkeiTechAutoTemplateInputProxy";
    private static final String WAILA_TEMPLATE_TYPE = "masterTemplateType";

    private MTEHatchAutoTemplateInputME master;
    private int masterX;
    private int masterY;
    private int masterZ;
    private int masterDim;
    private boolean masterSet;

    public MTEHatchAutoTemplateInputProxy(int id, String name, String regionalName) {
        super(id, name, regionalName, 5, 0, null);
        disableSort = true;
    }

    public MTEHatchAutoTemplateInputProxy(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
        disableSort = true;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchAutoTemplateInputProxy(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture controllerCasing = super.getCasingTexture();
        return controllerCasing == null ? WirelessMachineTextures.ivCasing(ForgeDirection.NORTH) : controllerCasing;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture controllerCasing = super.getCasingTexture();
        ITexture casing = controllerCasing == null ? WirelessMachineTextures.ivCasing(side) : controllerCasing;
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.of(OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseMetaTileEntity, long timer) {
        super.onPostTick(baseMetaTileEntity, timer);
        if (!baseMetaTileEntity.isServerSide() || timer % 100 != 0) return;
        if (getMaster() != null || !masterSet) {
            baseMetaTileEntity.tryDisableTicking();
        } else if (trySetMasterFromCoord(masterX, masterY, masterZ) != null) {
            baseMetaTileEntity.tryDisableTicking();
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        detachMaster();
        masterSet = false;
        if (!tag.hasKey("master")) return;
        NBTTagCompound masterTag = tag.getCompoundTag("master");
        masterX = masterTag.getInteger("x");
        masterY = masterTag.getInteger("y");
        masterZ = masterTag.getInteger("z");
        masterDim = masterTag.getInteger("dim");
        masterSet = true;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        if (!masterSet) return;
        NBTTagCompound masterTag = new NBTTagCompound();
        masterTag.setInteger("x", masterX);
        masterTag.setInteger("y", masterY);
        masterTag.setInteger("z", masterZ);
        masterTag.setInteger("dim", masterDim);
        tag.setTag("master", masterTag);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        List<String> result = new ArrayList<>();
        MTEHatchAutoTemplateInputME linked = getMaster();
        if (linked == null) {
            result.add("GT5U.infodata.hatch.crafting_input_slave.not_linked_to");
        } else {
            result.add(
                IGregTechDeviceInformation
                    .encode("GT5U.infodata.hatch.crafting_input_slave.linked_to", masterX, masterY, masterZ));
            result.addAll(Arrays.asList(linked.getInfoData()));
        }
        return result.toArray(new String[0]);
    }

    public MTEHatchAutoTemplateInputME getMaster() {
        if (master == null) return null;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        if (world.provider.dimensionId != masterDim || !world.blockExists(masterX, masterY, masterZ)) {
            detachMaster();
            base.enableTicking();
            return null;
        }
        TileEntity tileEntity = world.getTileEntity(masterX, masterY, masterZ);
        if (!(tileEntity instanceof IGregTechTileEntity gtTile) || gtTile.getMetaTileEntity() != master) {
            detachMaster();
            base.enableTicking();
        }
        return master;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity baseMetaTileEntity, int index, ForgeDirection side,
        ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity baseMetaTileEntity, int index, ForgeDirection side,
        ItemStack stack) {
        return false;
    }

    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        MTEHatchAutoTemplateInputME linked = getMaster();
        return linked == null ? Collections.emptyIterator() : linked.inventories();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        MTEHatchAutoTemplateInputME linked = getMaster();
        return linked == null ? Optional.empty() : linked.getFirstNonEmptyInventory();
    }

    @Override
    public boolean supportsFluids() {
        MTEHatchAutoTemplateInputME linked = getMaster();
        return linked != null && linked.supportsFluids();
    }

    @Override
    public ItemStack[] getSharedItems() {
        MTEHatchAutoTemplateInputME linked = getMaster();
        return linked == null ? GTValues.emptyItemStackArray : linked.getSharedItems();
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        watchers.add(watcher);
        MTEHatchAutoTemplateInputME linked = getMaster();
        if (linked != null) linked.onProxyControllersChanged();
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        watchers.remove(watcher);
        MTEHatchAutoTemplateInputME linked = getMaster();
        if (linked != null) linked.onProxyControllersChanged();
    }

    public void collectPatternModeControllers(Set<MTEMultiBlockBase> controllers) {
        for (IHatchWatcher watcher : watchers) {
            if (watcher instanceof MTEMultiBlockBase controller && controller.mMachine
                && SuperPatternModeBuffer.isLoaded(controller)) controllers.add(controller);
        }
    }

    public void onParentInvChange() {
        for (IHatchWatcher watcher : watchers) watcher.scheduleRecipeCheckImmediate();
    }

    public MTEHatchAutoTemplateInputME trySetMasterFromCoord(int x, int y, int z) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        if (masterSet && world.provider.dimensionId != masterDim) return null;
        if (!world.blockExists(x, y, z)) return null;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof IGregTechTileEntity gtTile)
            || !(gtTile.getMetaTileEntity() instanceof MTEHatchAutoTemplateInputME newMaster)) {
            return null;
        }
        if (master != newMaster) {
            detachMaster();
            master = newMaster;
            master.addProxyHatch(this);
        }
        masterX = x;
        masterY = y;
        masterZ = z;
        masterDim = world.provider.dimensionId;
        masterSet = true;
        base.markDirty();
        return master;
    }

    private void detachMaster() {
        MTEHatchAutoTemplateInputME previous = master;
        master = null;
        if (previous != null) previous.removeProxyHatch(this);
    }

    private boolean tryLinkDataStick(EntityPlayer player) {
        ItemStack dataStick = player.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)
            || !MTEHatchAutoTemplateInputME.LINK_DATA_TYPE.equals(ItemStackNBT.getString(dataStick, "type"))) {
            return false;
        }
        NBTTagCompound tag = dataStick.stackTagCompound;
        if (tag.getInteger("dim") != getBaseMetaTileEntity().getWorld().provider.dimensionId) {
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.machine.auto_template_input_proxy.cross_dimension"));
            return true;
        }
        masterDim = tag.getInteger("dim");
        if (trySetMasterFromCoord(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")) != null) {
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.machine.auto_template_input_proxy.link_success"));
        } else {
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.machine.auto_template_input_proxy.link_failed"));
        }
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP serverPlayer)) return true;
        if (tryLinkDataStick(player)) return true;
        MTEHatchAutoTemplateInputME linked = getMaster();
        if (linked == null) return false;
        if (baseMetaTileEntity.isServerSide()) ProxiedMteGui.open(linked, serverPlayer);
        return true;
    }

    @Override
    public void onLeftclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        ItemStack dataStick = player.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return;
        MTEHatchAutoTemplateInputME linked = getMaster();
        if (linked == null) {
            player.addChatMessage(
                new ChatComponentTranslation("tenkeitech.machine.auto_template_input_proxy.copy_unlinked"));
            return;
        }
        linked.saveToDataStick(player, dataStick);
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        detachMaster();
    }

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return COPIED_DATA_IDENTIFIER;
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound tag) {
        if (tag == null || !COPIED_DATA_IDENTIFIER.equals(tag.getString("type"))) return false;
        if (!tag.hasKey("master")) return true;
        NBTTagCompound masterTag = tag.getCompoundTag("master");
        int copiedDim = masterTag.getInteger("dim");
        if (copiedDim != getBaseMetaTileEntity().getWorld().provider.dimensionId) return false;
        return trySetMasterFromCoord(masterTag.getInteger("x"), masterTag.getInteger("y"), masterTag.getInteger("z"))
            != null;
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", COPIED_DATA_IDENTIFIER);
        if (!masterSet) return tag;
        NBTTagCompound masterTag = new NBTTagCompound();
        masterTag.setInteger("x", masterX);
        masterTag.setInteger("y", masterY);
        masterTag.setInteger("z", masterZ);
        masterTag.setInteger("dim", masterDim);
        tag.setTag("master", masterTag);
        return tag;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();
        currenttip.add(
            StatCollector.translateToLocal(
                tag.getBoolean("linked") ? "tenkeitech.machine.auto_template_input_proxy.waila.linked"
                    : "tenkeitech.machine.auto_template_input_proxy.waila.unlinked"));
        if (tag.hasKey("masterX")) {
            currenttip.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.machine.auto_template_input_proxy.waila.bound_to",
                    tag.getInteger("masterX"),
                    tag.getInteger("masterY"),
                    tag.getInteger("masterZ")));
        }
        if (tag.hasKey(WAILA_TEMPLATE_TYPE)) {
            TemplateType type = TemplateType.fromId(tag.getInteger(WAILA_TEMPLATE_TYPE));
            ItemStack template = type.getTemplateStack();
            String templateName = type.isCircuitMode() ? StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.auto_template_input.circuit_slots",
                type.getSelectableModes().length) : template == null ? type.name() : template.getDisplayName();
            currenttip.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "tenkeitech.machine.auto_template_input_proxy.waila.master",
                    StatCollector.translateToLocal(masterNameKey(type)),
                    templateName) + EnumChatFormatting.RESET);
        }
        super.getWailaBody(itemStack, currenttip, accessor, config);
    }

    private static String masterNameKey(TemplateType type) {
        return switch (type.getFamily()) {
            case WIREMILL -> "gt.blockmachines.hatch.tenkeitech.wiremill_template_input.name";
            case BENDER -> "gt.blockmachines.hatch.tenkeitech.bender_template_input.name";
            default -> "gt.blockmachines.hatch.tenkeitech.auto_template_input.name";
        };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        MTEHatchAutoTemplateInputME linked = getMaster();
        tag.setBoolean("linked", linked != null);
        if (masterSet) {
            tag.setInteger("masterX", masterX);
            tag.setInteger("masterY", masterY);
            tag.setInteger("masterZ", masterZ);
        }
        if (linked != null) tag.setInteger(
            WAILA_TEMPLATE_TYPE,
            linked.getTemplateType()
                .getStableId());
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
    }

    @Override
    public List<ItemStack> getItemsForHoloGlasses() {
        MTEHatchAutoTemplateInputME linked = getMaster();
        return linked == null ? Collections.emptyList() : linked.getItemsForHoloGlasses();
    }

    @Override
    public String[] getDescription() {
        return GTSplit
            .splitLocalizedFormatted("tenkeitech.machine.auto_template_input_proxy.desc", TIER_COLORS[5] + VN[5]);
    }
}
