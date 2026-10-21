/*
 * SPDX-FileCopyrightText: GT5-Unofficial contributors
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 * Adapted from GT5-Unofficial's MTEHatchCraftingInputSlave 5.09.54.105.
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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;
import com.tenkeitech.patternmode.SuperPatternModeBuffer;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
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
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatchWithPattern;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
import gregtech.common.tileentities.machines.IHatchWatcher;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@IMetaTileEntity.SkipGenerateDescription
public final class MTESuperCraftingInputProxy extends MTEHatchInputBus
    implements IDualInputHatchWithPattern, IDataCopyable {

    @Override
    protected boolean useMui2() {
        return false;
    }

    public static final String COPIED_DATA_IDENTIFIER = "tenkeiTechSuperPatternBufferProxy";
    private static final ITexture SUPER_PATTERN_BUFFER_CASING = TextureFactory
        .of(Textures.BlockIcons.NEUTRONIUM_ACTIVE_CASING);

    private MTESuperCraftingInputME master; // use getMaster() to access
    private int masterX, masterY, masterZ, masterDim;
    private boolean masterSet = false; // indicate if values of masterX, masterY, masterZ are valid
    private boolean reverseRecipes = false; // if true, the slave will show the recipes in reverse order compared to the
                                            // master CRIB

    public MTESuperCraftingInputProxy(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 11, 0, null);
        disableSort = true;
    }

    public MTESuperCraftingInputProxy(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        disableSort = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESuperCraftingInputProxy(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return getTexturesInactive(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { SUPER_PATTERN_BUFFER_CASING, TextureFactory.of(OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture controllerCasing = super.getCasingTexture();
        if (controllerCasing == null)
            return super.getTexture(baseMetaTileEntity, side, facing, colorIndex, active, redstoneLevel);
        if (side != facing) return new ITexture[] { controllerCasing };
        return new ITexture[] { controllerCasing, TextureFactory.of(OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        super.onPostTick(aBaseMetaTileEntity, aTimer);
        if (aBaseMetaTileEntity.isServerSide() && aTimer % 100 == 0) {
            if (getMaster() != null || !masterSet) {
                aBaseMetaTileEntity.tryDisableTicking();
            } else if (trySetMasterFromCoord(masterX, masterY, masterZ) != null) {
                aBaseMetaTileEntity.tryDisableTicking();
            }
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);

        if (aNBT.hasKey("master")) {
            NBTTagCompound masterNBT = aNBT.getCompoundTag("master");
            masterX = masterNBT.getInteger("x");
            masterY = masterNBT.getInteger("y");
            masterZ = masterNBT.getInteger("z");
            masterDim = masterNBT.getInteger("dim");
            masterSet = true;
        }
        reverseRecipes = aNBT.getBoolean("reverseRecipes");
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (masterSet) {
            NBTTagCompound masterNBT = new NBTTagCompound();
            masterNBT.setInteger("x", masterX);
            masterNBT.setInteger("y", masterY);
            masterNBT.setInteger("z", masterZ);
            masterNBT.setInteger("dim", masterDim);
            aNBT.setTag("master", masterNBT);
        }
        aNBT.setBoolean("reverseRecipes", reverseRecipes);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        var ret = new ArrayList<String>();
        if (getMaster() != null) {
            ret.add(
                IGregTechDeviceInformation
                    .encode("GT5U.infodata.hatch.crafting_input_slave.linked_to", masterX, masterY, masterZ));
            ret.addAll(Arrays.asList(getMaster().getInfoData()));
        } else ret.add("GT5U.infodata.hatch.crafting_input_slave.not_linked_to");
        ret.add(
            IGregTechDeviceInformation
                .encode("GT5U.infodata.hatch.crafting_input_slave.reverseRecipes", reverseRecipes ? "on" : "off"));
        return ret.toArray(new String[0]);
    }

    public MTESuperCraftingInputME getMaster() {
        if (master == null) return null;
        World world = getBaseMetaTileEntity().getWorld();
        if (world.provider.dimensionId != masterDim || !world.blockExists(masterX, masterY, masterZ)) {
            master = null;
            getBaseMetaTileEntity().enableTicking();
            return null;
        }
        TileEntity tileEntity = world.getTileEntity(masterX, masterY, masterZ);
        if (!(tileEntity instanceof IGregTechTileEntity gtte) || gtte.getMetaTileEntity() != master) {
            master.removeProxyHatch(this);
            master = null;
            getBaseMetaTileEntity().enableTicking();
        }
        return master;
    }

    @Override
    public byte getTierForStructure() {
        return getMaster() == null ? super.getTierForStructure() : getMaster().getTierForStructure();
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public Iterator<? extends IDualInputInventoryWithPattern> inventories() {
        MTESuperCraftingInputME linked = getMaster();
        return linked == null ? Collections.emptyIterator()
            : reverseRecipes ? linked.inventoriesReversed() : linked.inventories();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        MTESuperCraftingInputME linked = getMaster();
        return linked == null ? Optional.empty() : linked.getFirstNonEmptyInventory();
    }

    @Override
    public boolean supportsFluids() {
        return getMaster() != null && getMaster().supportsFluids();
    }

    @Override
    public ItemStack[] getSharedItems() {
        MTESuperCraftingInputME linked = getMaster();
        return linked == null ? GTValues.emptyItemStackArray : linked.getSharedItems();
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        if (master != null) master.invalidatePatternModeControllers();
        watchers.add(watcher);
        if (master != null && watcher instanceof MTEMultiBlockBase controller) activatePatternModeWatcher(controller);
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        if (master != null) master.invalidatePatternModeControllers();
        watchers.remove(watcher);
        if (watcher instanceof MTEMultiBlockBase controller) controller.scheduleRecipeCheckImmediate();
    }

    public void collectPatternModeControllers(Set<MTEMultiBlockBase> controllers) {
        for (IHatchWatcher watcher : watchers) {
            if (watcher instanceof MTEMultiBlockBase controller) controllers.add(controller);
        }
    }

    public void onParentInvChange() {
        for (IHatchWatcher watcher : watchers) {
            watcher.scheduleRecipeCheckImmediate();
        }
    }

    public MTESuperCraftingInputME trySetMasterFromCoord(int x, int y, int z) {
        World world = getBaseMetaTileEntity().getWorld();
        if (masterSet && world.provider.dimensionId != masterDim) return null;
        if (!world.blockExists(x, y, z)) return null;
        var tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity == null) return null;
        if (!(tileEntity instanceof IGregTechTileEntity GTTE)) return null;
        if (!(GTTE.getMetaTileEntity() instanceof MTESuperCraftingInputME newMaster)) return null;
        if (master != newMaster) {
            if (master != null) master.removeProxyHatch(this);
            master = newMaster;
            master.addProxyHatch(this);
            for (IHatchWatcher watcher : watchers) {
                if (watcher instanceof MTEMultiBlockBase controller) activatePatternModeWatcher(controller);
            }
        }
        masterX = x;
        masterY = y;
        masterZ = z;
        masterDim = getBaseMetaTileEntity().getWorld().provider.dimensionId;
        masterSet = true;
        return master;
    }

    private void activatePatternModeWatcher(MTEMultiBlockBase controller) {
        SuperPatternModeBuffer.batch(controller);
        master.invalidatePatternRecipeCache(controller);
        controller.scheduleRecipeCheckImmediate();
    }

    private boolean tryLinkDataStick(EntityPlayer aPlayer) {
        ItemStack dataStick = aPlayer.inventory.getCurrentItem();

        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) {
            return false;
        }
        if (!ItemStackNBT.getString(dataStick, "type")
            .equals("TenkeiTechSuperPatternBuffer")) {
            return false;
        }

        NBTTagCompound nbt = dataStick.stackTagCompound;
        int x = nbt.getInteger("x");
        int y = nbt.getInteger("y");
        int z = nbt.getInteger("z");
        if (nbt.getInteger("dim") != getBaseMetaTileEntity().getWorld().provider.dimensionId) {
            aPlayer.addChatMessage(new ChatComponentText("Link failed: the buffer is in another dimension"));
            return true;
        }
        if (trySetMasterFromCoord(x, y, z) != null) {
            aPlayer.addChatMessage(new ChatComponentText("Link successful"));
            return true;
        }
        aPlayer.addChatMessage(new ChatComponentText("Link failed"));
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP player)) {
            return true;
        }
        if (tryLinkDataStick(aPlayer)) {
            return true;
        }
        var master = getMaster();
        if (master != null) {
            if (aBaseMetaTileEntity.isServerSide()) {
                ProxiedMteGui.open(master, player);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP)) return;

        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return;
        var master = getMaster();
        if (master == null) {
            aPlayer.addChatMessage(new ChatComponentText("Can't copy an unlinked proxy!"));
            return;
        }

        master.saveToDataStick(aPlayer, dataStick);
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (master != null) master.removeProxyHatch(this);
    }

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return COPIED_DATA_IDENTIFIER;
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound nbt) {
        if (nbt == null || !COPIED_DATA_IDENTIFIER.equals(nbt.getString("type"))) return false;
        if (nbt.hasKey("master")) {
            NBTTagCompound masterNBT = nbt.getCompoundTag("master");
            int copiedDim = masterNBT.getInteger("dim");
            if (copiedDim != getBaseMetaTileEntity().getWorld().provider.dimensionId) return false;
            masterDim = copiedDim;
            masterSet = true;
            trySetMasterFromCoord(masterNBT.getInteger("x"), masterNBT.getInteger("y"), masterNBT.getInteger("z"));
        }
        reverseRecipes = nbt.getBoolean("reverseRecipes");
        return true;
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", COPIED_DATA_IDENTIFIER);
        if (masterSet) {
            NBTTagCompound masterNBT = new NBTTagCompound();
            masterNBT.setInteger("x", masterX);
            masterNBT.setInteger("y", masterY);
            masterNBT.setInteger("z", masterZ);
            masterNBT.setInteger("dim", masterDim);
            tag.setTag("master", masterNBT);
        }
        tag.setBoolean("reverseRecipes", reverseRecipes);
        return tag;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();
        currenttip.add(
            StatCollector.translateToLocal(
                tag.getBoolean("linked") ? "tenkeitech.machine.super_pattern_buffer_proxy.waila.linked"
                    : "tenkeitech.machine.super_pattern_buffer_proxy.waila.unlinked"));

        if (tag.hasKey("masterX")) {
            currenttip.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.machine.super_pattern_buffer_proxy.waila.bound_to",
                    tag.getInteger("masterX"),
                    tag.getInteger("masterY"),
                    tag.getInteger("masterZ")));
        }

        currenttip.add(
            StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.super_pattern_buffer_proxy.waila.recipe_order",
                StatCollector.translateToLocal(
                    tag.getBoolean("reverseRecipes")
                        ? "tenkeitech.machine.super_pattern_buffer_proxy.waila.recipe_order.reversed"
                        : "tenkeitech.machine.super_pattern_buffer_proxy.waila.recipe_order.normal")));

        if (tag.getBoolean("linked")) {
            currenttip
                .add(
                    EnumChatFormatting.GOLD
                        + StatCollector.translateToLocalFormatted(
                            "tenkeitech.machine.super_pattern_buffer_proxy.waila.master",
                            StatCollector
                                .translateToLocal("gt.blockmachines.hatch.tenkeitech.super_pattern_buffer.name"))
                        + EnumChatFormatting.RESET);
        }

        super.getWailaBody(itemStack, currenttip, accessor, config);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {

        tag.setBoolean("linked", getMaster() != null);
        tag.setBoolean("reverseRecipes", reverseRecipes);
        if (masterSet) {
            tag.setInteger("masterX", masterX);
            tag.setInteger("masterY", masterY);
            tag.setInteger("masterZ", masterZ);
        }
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
    }

    @Override
    public List<ItemStack> getItemsForHoloGlasses() {
        return getMaster() != null ? getMaster().getItemsForHoloGlasses() : Collections.emptyList();
    }

    private void toggleReverseRecipes() {
        reverseRecipes = !reverseRecipes;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity().isServerSide()) {
            toggleReverseRecipes();
            GTUtility.sendChatTrans(aPlayer, "chat.proxy.reverse." + reverseRecipes);
        }
        // not calling super.onScrewdriverRightClick, because input filter is irrelevant for a proxy hatch
    }

    @Override
    public String[] getDescription() {
        return GTSplit
            .splitLocalizedFormatted("tenkeitech.machine.super_pattern_buffer_proxy.desc", TIER_COLORS[11] + VN[11]);
    }
}
