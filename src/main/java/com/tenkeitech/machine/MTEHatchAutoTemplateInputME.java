/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.machine;

import static gregtech.api.enums.GTValues.TIER_COLORS;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_HATCH;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_HATCH_ACTIVE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.autotemplate.AutoTemplateRecipeRegistry;
import com.tenkeitech.autotemplate.InputKind;
import com.tenkeitech.autotemplate.PatternKey;
import com.tenkeitech.autotemplate.SharedFluidInputPool;
import com.tenkeitech.autotemplate.SharedItemInputPool;
import com.tenkeitech.autotemplate.TemplateRecipeCatalog;
import com.tenkeitech.autotemplate.TemplateType;
import com.tenkeitech.autotemplate.VirtualPattern;
import com.tenkeitech.autotemplate.VirtualPatternDetails;
import com.tenkeitech.gui.MTEAutoTemplateInputGui;
import com.tenkeitech.inventory.ILongItemInputInventory;
import com.tenkeitech.inventory.LongFluidStore;
import com.tenkeitech.inventory.LongItemStore;
import com.tenkeitech.patternmode.AutoTemplateModeBuffer;
import com.tenkeitech.patternmode.SuperPatternModeBuffer;

import appeng.api.AEApi;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IHatchWatcher;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Shared implementation for template-bound AE crafting input hatches. This concrete type owns the
 * physical-template bus; dedicated subclasses select one fixed integrated-circuit family while
 * reusing the same AE, inventory, persistence and refund transactions.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchAutoTemplateInputME extends MTEHatchInputBus
    implements IPowerChannelState, ICraftingProvider, IGridProxyable, IDualInputHatch {

    public static final String LINK_DATA_TYPE = "TenkeiTechAutoTemplateInput";
    private static final String NBT_TEMPLATE_TYPE = "TemplateType";
    private static final String NBT_ENABLED_PATTERNS = "enabledPatterns";
    private static final String NBT_ITEMS = "sharedItems";
    private static final String NBT_FLUIDS = "sharedFluids";
    private static final String NBT_DISABLED_TIER_MASK = "disabledTierMask";
    private static final String NBT_CIRCUIT_SLOTS = "circuitSlots";
    private static final String NBT_CIRCUIT_SLOT_TYPE = "TemplateType";
    private static final String NBT_CIRCUIT_SLOT_ITEMS = "Items";
    private static final String NBT_CIRCUIT_SLOT_DISABLED_TIER_MASK = "DisabledTierMask";
    private static final String NBT_SCHEMA = "autoTemplateSchema";
    private static final String NBT_ENABLED_INITIALIZED = "enabledPatternsInitialized";
    private static final String WAILA_INVENTORY = "inventory";
    private static final String WAILA_NAME = "name";
    private static final String WAILA_AMOUNT = "amount";
    private static final int NBT_SCHEMA_VERSION = 4;
    public static final int SPECIAL_MODE_TIER = Integer.MAX_VALUE;
    private final TemplateType defaultTemplateType;
    private TemplateType templateType;
    private final SharedItemInputPool itemPool = new SharedItemInputPool();
    private final SharedFluidInputPool fluidPool = new SharedFluidInputPool();
    private final List<CircuitPatternSlot> circuitSlots = new ArrayList<CircuitPatternSlot>();
    private final Set<PatternKey> enabledPatterns = new LinkedHashSet<PatternKey>();
    private final List<MTEHatchAutoTemplateInputProxy> proxyHatches = new ArrayList<>();
    private final AutoTemplateModeBuffer patternMode = new AutoTemplateModeBuffer(this);
    private AENetworkProxy gridProxy;
    private boolean configTransactionActive;
    private boolean needPatternSync = true;
    private boolean enabledPatternsInitialized;
    private int disabledTierMask;
    private boolean justHadNewItems;
    private int lastMachineTier = Integer.MIN_VALUE;
    private ScheduledReason scheduledReason = ScheduledReason.UNDEFINED;

    public MTEHatchAutoTemplateInputME(int id, String name, String regionalName) {
        this(id, name, regionalName, TemplateType.NONE);
    }

    protected MTEHatchAutoTemplateInputME(int id, String name, String regionalName, TemplateType defaultType) {
        super(id, name, regionalName, 4, 0, null);
        defaultTemplateType = normalizeDefaultType(defaultType);
        templateType = defaultTemplateType;
        initializeCircuitSlots();
        disableSort = true;
    }

    public MTEHatchAutoTemplateInputME(String name, int tier, String[] description, ITexture[][][] textures) {
        this(name, tier, description, textures, TemplateType.NONE);
    }

    protected MTEHatchAutoTemplateInputME(String name, int tier, String[] description, ITexture[][][] textures,
        TemplateType defaultType) {
        super(name, tier, 0, description, textures);
        defaultTemplateType = normalizeDefaultType(defaultType);
        templateType = defaultTemplateType;
        initializeCircuitSlots();
        disableSort = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchAutoTemplateInputME(mName, mTier, mDescriptionArray, mTextures, defaultTemplateType);
    }

    private static TemplateType normalizeDefaultType(TemplateType type) {
        return type != null && type.isCircuitMode() ? type : TemplateType.NONE;
    }

    private void initializeCircuitSlots() {
        for (TemplateType mode : defaultTemplateType.getSelectableModes()) {
            circuitSlots.add(new CircuitPatternSlot(mode));
        }
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(OVERLAY_ME_INPUT_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(OVERLAY_ME_INPUT_HATCH) };
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture controllerCasing = super.getCasingTexture();
        return controllerCasing == null ? WirelessMachineTextures.evCasing(ForgeDirection.NORTH) : controllerCasing;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture controllerCasing = super.getCasingTexture();
        ITexture casing = controllerCasing == null ? WirelessMachineTextures.evCasing(side) : controllerCasing;
        if (side != facing) return new ITexture[] { casing };
        return active ? getTexturesActive(casing) : getTexturesInactive(casing);
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != null && facing != ForgeDirection.UNKNOWN;
    }

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        if (tag == null) return;
        tag.setInteger(NBT_SCHEMA, NBT_SCHEMA_VERSION);
        tag.setInteger(NBT_TEMPLATE_TYPE, templateType.getStableId());
        tag.setBoolean(NBT_ENABLED_INITIALIZED, enabledPatternsInitialized);
        NBTTagList enabled = new NBTTagList();
        for (PatternKey key : enabledPatterns) enabled.appendTag(key.writeToNBT());
        tag.setTag(NBT_ENABLED_PATTERNS, enabled);
        tag.setInteger(NBT_DISABLED_TIER_MASK, disabledTierMask);
        tag.setTag(NBT_CIRCUIT_SLOTS, writeCircuitSlots(false));
    }

    /** Creates one bound machine item and removes configuration that belonged to its previous template. */
    @Nullable
    public static ItemStack bindTemplateItem(ItemStack machineStack, TemplateType type) {
        if (machineStack == null || type == null || !type.isPhysicalTemplate() || !type.isConfigured()) return null;
        ItemStack result = machineStack.copy();
        result.stackSize = 1;
        NBTTagCompound tag = result.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();
        tag.setInteger(NBT_SCHEMA, NBT_SCHEMA_VERSION);
        tag.setInteger(NBT_TEMPLATE_TYPE, type.getStableId());
        tag.removeTag(NBT_ENABLED_PATTERNS);
        tag.setBoolean(NBT_ENABLED_INITIALIZED, false);
        tag.setInteger(NBT_DISABLED_TIER_MASK, 0);
        tag.removeTag(NBT_ITEMS);
        tag.removeTag(NBT_FLUIDS);
        tag.removeTag(NBT_CIRCUIT_SLOTS);
        result.setTagCompound(tag);
        return result;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity baseMetaTileEntity) {
        super.onFirstTick(baseMetaTileEntity);
        initializeEnabledPatterns();
        getProxy().onReady();
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseMetaTileEntity, long timer) {
        super.onPostTick(baseMetaTileEntity, timer);
        if (!baseMetaTileEntity.isServerSide()) return;
        boolean backingChanged = itemPool.observeBackingChanges() | fluidPool.observeBackingChanges();
        for (CircuitPatternSlot slot : circuitSlots) backingChanged |= slot.pool.observeBackingChanges();
        if (backingChanged) markDirty();
        patternMode.tick(timer);
        if (timer % 20 == 0) baseMetaTileEntity.setActive(isActive());
        if (timer % 10 == 0) {
            int machineTier = getCurrentMachineTier();
            if (machineTier != lastMachineTier) {
                lastMachineTier = machineTier;
                needPatternSync = true;
            }
            if (needPatternSync) needPatternSync = !postMEPatternChange();
        }
        if (justHadNewItems) {
            for (IHatchWatcher watcher : watchers) watcher.scheduleRecipeCheckImmediate();
            for (MTEHatchAutoTemplateInputProxy proxy : getProxyHatches()) proxy.onParentInvChange();
            justHadNewItems = false;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setInteger(NBT_SCHEMA, NBT_SCHEMA_VERSION);
        tag.setInteger(NBT_TEMPLATE_TYPE, templateType.getStableId());
        NBTTagList enabled = new NBTTagList();
        for (PatternKey key : enabledPatterns) enabled.appendTag(key.writeToNBT());
        tag.setTag(NBT_ENABLED_PATTERNS, enabled);
        tag.setBoolean(NBT_ENABLED_INITIALIZED, enabledPatternsInitialized);
        tag.setInteger(NBT_DISABLED_TIER_MASK, disabledTierMask);
        itemPool.writeToNBT(tag, NBT_ITEMS);
        fluidPool.writeToNBT(tag, NBT_FLUIDS);
        tag.setTag(NBT_CIRCUIT_SLOTS, writeCircuitSlots(true));
        getProxy().writeToNBT(tag);
    }

    private NBTTagList writeCircuitSlots(boolean includeItems) {
        NBTTagList savedCircuitSlots = new NBTTagList();
        for (CircuitPatternSlot slot : circuitSlots) {
            NBTTagCompound savedSlot = new NBTTagCompound();
            savedSlot.setInteger(NBT_CIRCUIT_SLOT_TYPE, slot.mode.getStableId());
            savedSlot.setInteger(NBT_CIRCUIT_SLOT_DISABLED_TIER_MASK, slot.disabledTierMask);
            if (includeItems) savedSlot.setTag(NBT_CIRCUIT_SLOT_ITEMS, slot.pool.writeToNBT());
            savedCircuitSlots.appendTag(savedSlot);
        }
        return savedCircuitSlots;
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        int schema = tag.getInteger(NBT_SCHEMA);
        if (schema != NBT_SCHEMA_VERSION) {
            TenkeiTech.LOG
                .warn("Ignoring unsupported auto-template input schema {}; expected {}", schema, NBT_SCHEMA_VERSION);
            tag = new NBTTagCompound();
        }
        TemplateType parsed = TemplateType.fromId(tag.getInteger(NBT_TEMPLATE_TYPE));
        templateType = isTemplateTypeAllowed(parsed) ? parsed : defaultTemplateType;
        enabledPatterns.clear();
        disabledTierMask = tag.getInteger(NBT_DISABLED_TIER_MASK);
        enabledPatternsInitialized = tag.getBoolean(NBT_ENABLED_INITIALIZED);
        NBTTagList enabled = tag.getTagList(NBT_ENABLED_PATTERNS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < enabled.tagCount(); index++) {
            PatternKey key = PatternKey.readFromNBT(enabled.getCompoundTagAt(index));
            if (key != null && isPatternTypeAllowed(key.getTemplateType())) enabledPatterns.add(key);
        }
        itemPool.readFromNBT(tag, NBT_ITEMS);
        fluidPool.readFromNBT(tag, NBT_FLUIDS);
        for (CircuitPatternSlot slot : circuitSlots) {
            slot.pool.readFromNBT((NBTTagList) null);
            slot.disabledTierMask = 0;
        }
        Set<TemplateType> loadedCircuitModes = EnumSet.noneOf(TemplateType.class);
        NBTTagList savedCircuitSlots = tag.getTagList(NBT_CIRCUIT_SLOTS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < savedCircuitSlots.tagCount(); index++) {
            NBTTagCompound savedSlot = savedCircuitSlots.getCompoundTagAt(index);
            TemplateType mode = TemplateType.fromId(savedSlot.getInteger(NBT_CIRCUIT_SLOT_TYPE));
            CircuitPatternSlot slot = circuitSlot(mode);
            if (slot != null && loadedCircuitModes.add(mode)) {
                slot.pool.readFromNBT(savedSlot.getTagList(NBT_CIRCUIT_SLOT_ITEMS, Constants.NBT.TAG_COMPOUND));
                if (savedSlot.hasKey(NBT_CIRCUIT_SLOT_DISABLED_TIER_MASK, Constants.NBT.TAG_INT)) {
                    slot.disabledTierMask = savedSlot.getInteger(NBT_CIRCUIT_SLOT_DISABLED_TIER_MASK);
                }
            }
        }
        if (hasCircuitSelector()) disabledTierMask = 0;
        if (tag.hasKey("proxy")) getProxy().readFromNBT(tag);
        updateAE2ProxyColor();
        needPatternSync = true;
    }

    private void initializeEnabledPatterns() {
        Set<PatternKey> available = availablePatternKeys();
        if (available.isEmpty() && !hasCircuitSelector() && templateType == TemplateType.NONE) return;
        if (!enabledPatternsInitialized) {
            enabledPatterns.addAll(available);
            enabledPatternsInitialized = true;
            markDirty();
        }
        int before = enabledPatterns.size();
        enabledPatterns.retainAll(available);
        if (before != enabledPatterns.size()) markDirty();
    }

    public List<VirtualPattern> getAvailablePatterns() {
        List<VirtualPattern> patterns = new ArrayList<VirtualPattern>();
        if (hasCircuitSelector()) {
            for (TemplateType mode : getSelectableCircuitModes()) {
                patterns.addAll(getAvailablePatterns(mode));
            }
        } else {
            patterns.addAll(getAvailablePatterns(templateType));
        }
        return Collections.unmodifiableList(patterns);
    }

    public List<VirtualPattern> getAvailablePatterns(TemplateType type) {
        if (type == null || !isPatternTypeAllowed(type)) return Collections.emptyList();
        TemplateRecipeCatalog catalog = AutoTemplateRecipeRegistry.get(type);
        return catalog == null ? Collections.emptyList() : catalog.getPatterns();
    }

    private Set<PatternKey> availablePatternKeys() {
        Set<PatternKey> keys = new LinkedHashSet<PatternKey>();
        for (VirtualPattern pattern : getAvailablePatterns()) keys.add(pattern.getKey());
        return keys;
    }

    private Set<PatternKey> availablePatternKeys(TemplateType type) {
        Set<PatternKey> keys = new LinkedHashSet<PatternKey>();
        for (VirtualPattern pattern : getAvailablePatterns(type)) keys.add(pattern.getKey());
        return keys;
    }

    @Nullable
    private VirtualPattern findPattern(PatternKey key) {
        if (key == null || !isPatternTypeAllowed(key.getTemplateType())) return null;
        return AutoTemplateRecipeRegistry.find(key.getTemplateType(), key);
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            gridProxy = new AENetworkProxy(this, "proxy", getTemplateMachineStack(), true);
            gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            gridProxy.setValidSides(EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
        }
        return gridProxy;
    }

    @Override
    public void gridChanged() {
        needPatternSync = true;
    }

    @Override
    public void onColorChangeServer(byte color) {
        updateAE2ProxyColor();
    }

    private void updateAE2ProxyColor() {
        AENetworkProxy proxy = getProxy();
        byte color = getColor();
        proxy.setColor(color == -1 ? AEColor.Transparent : AEColor.values()[Dyes.transformDyeIndex(color)]);
        if (proxy.getNode() != null) proxy.getNode()
            .updateState();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return side == getBaseMetaTileEntity().getFrontFacing() ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void onFacingChange() {
        if (gridProxy != null) gridProxy.setValidSides(EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
    }

    @Override
    public boolean isPowered() {
        return getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return templateType != TemplateType.NONE && getProxy().isActive();
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (configTransactionActive || !isActive() || !getBaseMetaTileEntity().isAllowedToWork()) return false;
        if (!(table instanceof MEInventoryCrafting meInventory)) return false;
        VirtualPattern pattern = resolvePattern(details);
        if (!isPatternEffectivelyEnabled(pattern)) return false;
        CircuitPatternSlot circuitSlot = pattern == null ? null
            : circuitSlot(
                pattern.getKey()
                    .getTemplateType());
        if (hasCircuitSelector() && circuitSlot == null) return false;
        if (!patternMode.canAccept()) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }
        IAEStack<?> expected = pattern.getDetails()
            .getAEInputs()[0];
        List<IAEStack<?>> actualStacks = new ArrayList<IAEStack<?>>();
        long totalAmount = 0;
        for (int index = 0; index < meInventory.getSizeInventory(); index++) {
            IAEStack<?> candidate = meInventory.getAEStackInSlot(index);
            if (candidate == null) continue;
            long amount = candidate.getStackSize();
            if (!isSupportedInput(candidate) || !hasValidBackingStack(candidate)
                || amount <= 0
                || !matchesPatternInput(pattern, candidate)
                || Long.MAX_VALUE - totalAmount < amount) {
                scheduledReason = ScheduledReason.UNSUPPORTED_STACK;
                return false;
            }
            actualStacks.add(candidate);
            totalAmount += amount;
        }
        long expectedAmount = expected == null ? 0 : expected.getStackSize();
        if (actualStacks.isEmpty() || expectedAmount <= 0 || totalAmount % expectedAmount != 0) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }
        SharedItemInputPool targetItemPool = circuitSlot == null ? itemPool : circuitSlot.pool;
        SharedItemInputPool nextItems = null;
        SharedFluidInputPool nextFluids = null;
        if (templateType.getInputKind() == InputKind.INGOT) {
            nextItems = new SharedItemInputPool();
            nextItems.copyFrom(targetItemPool);
        } else {
            nextFluids = new SharedFluidInputPool();
            nextFluids.copyFrom(fluidPool);
        }
        for (IAEStack<?> actual : actualStacks) {
            boolean inserted;
            if (actual instanceof IAEItemStack item) {
                inserted = nextItems != null && nextItems.insert(item.getItemStack(), actual.getStackSize());
            } else if (actual instanceof IAEFluidStack fluid) {
                inserted = nextFluids != null && nextFluids.insert(fluid.getFluidStack(), actual.getStackSize());
            } else {
                inserted = false;
            }
            if (!inserted) {
                scheduledReason = ScheduledReason.SOMETHING_STUCK;
                return false;
            }
        }
        if (nextItems != null) targetItemPool.copyFrom(nextItems);
        if (nextFluids != null) fluidPool.copyFrom(nextFluids);
        patternMode.accepted();
        justHadNewItems = true;
        scheduledReason = ScheduledReason.UNDEFINED;
        markDirty();
        return true;
    }

    private boolean isSupportedInput(IAEStack<?> stack) {
        return templateType.getInputKind() == InputKind.INGOT ? stack instanceof IAEItemStack
            : stack instanceof IAEFluidStack;
    }

    private static boolean hasValidBackingStack(IAEStack<?> stack) {
        if (stack instanceof IAEItemStack item) return item.getItemStack() != null;
        if (stack instanceof IAEFluidStack fluid) {
            FluidStack fluidStack = fluid.getFluidStack();
            return fluidStack != null && fluidStack.getFluid() != null;
        }
        return false;
    }

    private boolean matchesPatternInput(VirtualPattern pattern, IAEStack<?> actual) {
        if (pattern == null || actual == null) return false;
        if (pattern.getKey()
            .getInput()
            .getKind() == InputKind.INGOT) {
            World world = getBaseMetaTileEntity() == null ? null : getBaseMetaTileEntity().getWorld();
            return pattern.getDetails()
                .isValidItemForSlot(0, actual, world);
        }
        IAEStack<?> expected = pattern.getDetails()
            .getAEInputs()[0];
        return expected != null && actual.isFluid() && expected.isSameType(actual);
    }

    @Nullable
    private VirtualPattern resolvePattern(ICraftingPatternDetails details) {
        if (details instanceof VirtualPatternDetails virtual) {
            return findPattern(virtual.getKey());
        }
        if (details == null) return null;
        PatternKey key = VirtualPatternDetails.readPatternKey(details.getPattern());
        return findPattern(key);
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        if (!isActive() || configTransactionActive) return;
        if (!patternMode.isPatternModeAvailable()) return;
        int machineTier = getCurrentMachineTier();
        for (VirtualPattern pattern : getAvailablePatterns()) {
            if (isPatternEffectivelyEnabled(pattern, machineTier)) helper.addCraftingOption(this, pattern.getDetails());
        }
    }

    @Override
    public boolean isBusy() {
        return configTransactionActive || patternMode.busy();
    }

    @Override
    public ScheduledReason getScheduledReason() {
        return scheduledReason;
    }

    @Override
    public ItemStack getCrafterIcon() {
        return getTemplateMachineStack();
    }

    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        if (!patternMode.allows()) return Collections.<IDualInputInventory>emptyList()
            .iterator();
        if (hasCircuitSelector()) {
            List<IDualInputInventory> inventories = new ArrayList<IDualInputInventory>(circuitSlots.size());
            for (CircuitPatternSlot slot : circuitSlots) inventories.add(slot.inventory);
            return inventories.iterator();
        }
        IDualInputInventory pool = templateType.getInputKind() == InputKind.FLUID ? fluidPool : itemPool;
        return Collections.singleton(pool)
            .iterator();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        if (!patternMode.allows()) return Optional.empty();
        if (hasCircuitSelector()) {
            for (CircuitPatternSlot slot : circuitSlots) {
                if (!slot.inventory.isEmpty()) return Optional.of(slot.inventory);
            }
            return Optional.empty();
        }
        IDualInputInventory pool = templateType.getInputKind() == InputKind.FLUID ? fluidPool : itemPool;
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool);
    }

    @Override
    public boolean supportsFluids() {
        return templateType.getInputKind() == InputKind.FLUID;
    }

    @Override
    public ItemStack[] getSharedItems() {
        if (!patternMode.allows()) return new ItemStack[0];
        if (hasCircuitSelector()) return new ItemStack[0];
        ItemStack template = templateType.getTemplateStack();
        return template == null ? new ItemStack[0] : new ItemStack[] { template };
    }

    public GTDualInputPattern getPatternInputs() {
        GTDualInputPattern pattern = new GTDualInputPattern();
        if (!patternMode.allows()) {
            pattern.inputItems = new ItemStack[0];
            pattern.inputFluid = new FluidStack[0];
            return pattern;
        }
        if (hasCircuitSelector()) {
            pattern.inputItems = new ItemStack[0];
            pattern.inputFluid = new FluidStack[0];
            return pattern;
        }
        pattern.inputItems = templateType.getInputKind() == InputKind.INGOT ? getSharedItems() : new ItemStack[0];
        pattern.inputFluid = templateType.getInputKind() == InputKind.FLUID ? fluidPool.getFluidInputs()
            : new FluidStack[0];
        return pattern;
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public boolean hasCircuitSelector() {
        return defaultTemplateType.isCircuitMode();
    }

    public TemplateType[] getSelectableCircuitModes() {
        return defaultTemplateType.getSelectableModes();
    }

    public AutoTemplateModeBuffer getPatternMode() {
        return patternMode;
    }

    public boolean hasPatternModeInputs() {
        if (!itemPool.isEmpty() || !fluidPool.isEmpty()) return true;
        for (CircuitPatternSlot slot : circuitSlots) {
            if (!slot.pool.isEmpty()) return true;
        }
        return false;
    }

    public String getPatternModeRecipeMapId() {
        var recipeMap = templateType.getRecipeMap();
        return recipeMap == null ? "" : recipeMap.unlocalizedName;
    }

    public List<MTEMultiBlockBase> getPatternModeControllers() {
        Set<MTEMultiBlockBase> controllers = new LinkedHashSet<>();
        for (IHatchWatcher watcher : watchers) {
            if (watcher instanceof MTEMultiBlockBase controller && controller.mMachine
                && SuperPatternModeBuffer.isLoaded(controller)) controllers.add(controller);
        }
        // A mirror is a remote inventory view. It must not turn an already installed host into a
        // cross-controller provider merely by being linked from another formed machine.
        if (!controllers.isEmpty()) return new ArrayList<>(controllers);
        for (MTEHatchAutoTemplateInputProxy proxy : getProxyHatches()) {
            if (SuperPatternModeBuffer.isLoaded(proxy)) proxy.collectPatternModeControllers(controllers);
        }
        return new ArrayList<>(controllers);
    }

    public void addProxyHatch(MTEHatchAutoTemplateInputProxy proxy) {
        if (proxy != null && !proxyHatches.contains(proxy)) proxyHatches.add(proxy);
        onProxyControllersChanged();
    }

    public void removeProxyHatch(MTEHatchAutoTemplateInputProxy proxy) {
        proxyHatches.remove(proxy);
        onProxyControllersChanged();
    }

    public List<MTEHatchAutoTemplateInputProxy> getProxyHatches() {
        for (MTEHatchAutoTemplateInputProxy proxy : new ArrayList<>(proxyHatches)) {
            if (proxy == null || proxy.getBaseMetaTileEntity() == null || proxy.getMaster() != this) {
                proxyHatches.remove(proxy);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(proxyHatches));
    }

    public void onProxyControllersChanged() {
        patternMode.invalidateInspection();
        lastMachineTier = Integer.MIN_VALUE;
        needPatternSync = true;
    }

    public Set<PatternKey> getEnabledPatterns() {
        return Collections.unmodifiableSet(new LinkedHashSet<PatternKey>(enabledPatterns));
    }

    public boolean isPatternUserEnabled(PatternKey key) {
        return key != null && enabledPatterns.contains(key);
    }

    public boolean isPatternModeAvailable() {
        return patternMode.isPatternModeAvailable();
    }

    public void onPatternModeInspectionChanged() {
        needPatternSync = true;
    }

    public int getEffectivePatternCount() {
        return getEffectivePatternCount(null);
    }

    public int getEffectivePatternCount(@Nullable TemplateType type) {
        if (!patternMode.isPatternModeAvailable()) return 0;
        int machineTier = getCurrentMachineTier();
        int count = 0;
        List<VirtualPattern> patterns = type == null ? getAvailablePatterns() : getAvailablePatterns(type);
        for (VirtualPattern pattern : patterns) {
            if (isPatternEffectivelyEnabled(pattern, machineTier)) count++;
        }
        return count;
    }

    /**
     * The attached controller owns the actual voltage limit. A formed controller that cannot report
     * a positive input tier is treated as a special machine without a voltage limit; an unformed hatch
     * still exposes no recipes.
     */
    public int getCurrentMachineTier() {
        long tier = -1;
        for (MTEMultiBlockBase controller : getPatternModeControllers()) {
            long controllerTier = controller.getInputVoltageTier();
            if (controllerTier <= 0) return SPECIAL_MODE_TIER;
            tier = Math.max(tier, controllerTier);
        }
        return (int) Math.min(Integer.MAX_VALUE, tier);
    }

    public boolean isPatternVoltageAvailable(VirtualPattern pattern) {
        return pattern != null && pattern.getRecipeTier() <= getCurrentMachineTier();
    }

    public boolean isPatternEffectivelyEnabled(VirtualPattern pattern) {
        return patternMode.isPatternModeAvailable() && isPatternEffectivelyEnabled(pattern, getCurrentMachineTier());
    }

    private boolean isPatternEffectivelyEnabled(VirtualPattern pattern, int machineTier) {
        return pattern != null && enabledPatterns.contains(pattern.getKey())
            && isTierUserEnabled(
                pattern.getKey()
                    .getTemplateType(),
                pattern.getRecipeTier())
            && pattern.getRecipeTier() <= machineTier;
    }

    public boolean isTierUserEnabled(TemplateType type, int tier) {
        if (tier < 0 || tier >= Integer.SIZE || !isPatternTypeAllowed(type)) return false;
        CircuitPatternSlot slot = circuitSlot(type);
        int mask = slot == null ? disabledTierMask : slot.disabledTierMask;
        return (mask & 1 << tier) == 0;
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        super.addWatcher(watcher);
        patternMode.invalidateInspection();
        lastMachineTier = Integer.MIN_VALUE;
        needPatternSync = true;
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        super.removeWatcher(watcher);
        patternMode.invalidateInspection();
        lastMachineTier = Integer.MIN_VALUE;
        needPatternSync = true;
    }

    /** Applies one user toggle transaction. All buffered materials are refunded before state changes. */
    public boolean setPatternEnabled(PatternKey key, boolean enabled) {
        if (key == null || findPattern(key) == null) return false;
        boolean old = enabledPatterns.contains(key);
        if (old == enabled) return true;
        configTransactionActive = true;
        try {
            CircuitPatternSlot slot = circuitSlot(key.getTemplateType());
            if (slot == null ? !refundSharedPool() : !refundItemPool(slot.pool)) return false;
            if (enabled) enabledPatterns.add(key);
            else enabledPatterns.remove(key);
            enabledPatternsInitialized = true;
            markDirty();
            needPatternSync = true;
            return true;
        } finally {
            configTransactionActive = false;
        }
    }

    /** Applies a whole voltage-tier toggle without changing any per-pattern preference. */
    public boolean setTierEnabled(TemplateType type, int tier, boolean enabled) {
        if (tier < 0 || tier >= Integer.SIZE || !isPatternTypeAllowed(type)) return false;
        boolean present = false;
        for (VirtualPattern pattern : getAvailablePatterns(type)) {
            if (pattern.getRecipeTier() == tier) {
                present = true;
                break;
            }
        }
        if (!present || isTierUserEnabled(type, tier) == enabled) return present;
        configTransactionActive = true;
        try {
            CircuitPatternSlot slot = circuitSlot(type);
            if (slot == null ? !refundSharedPool() : !refundItemPool(slot.pool)) return false;
            if (slot == null) {
                if (enabled) disabledTierMask &= ~(1 << tier);
                else disabledTierMask |= 1 << tier;
            } else if (enabled) slot.disabledTierMask &= ~(1 << tier);
            else slot.disabledTierMask |= 1 << tier;
            markDirty();
            needPatternSync = true;
            return true;
        } finally {
            configTransactionActive = false;
        }
    }

    /** Applies one setting to every pattern and clears whole-tier overrides. */
    public boolean setAllPatternsEnabled(TemplateType type, boolean enabled) {
        if (!isPatternTypeAllowed(type)) return false;
        Set<PatternKey> available = availablePatternKeys(type);
        if (available.isEmpty()) return false;
        Set<PatternKey> nextEnabled = enabled ? available : Collections.emptySet();
        Set<PatternKey> currentEnabled = new LinkedHashSet<PatternKey>();
        for (PatternKey key : enabledPatterns) {
            if (key.getTemplateType() == type) currentEnabled.add(key);
        }
        CircuitPatternSlot slot = circuitSlot(type);
        int currentTierMask = slot == null ? disabledTierMask : slot.disabledTierMask;
        if (currentEnabled.equals(nextEnabled) && currentTierMask == 0) return true;
        configTransactionActive = true;
        try {
            if (slot == null ? !refundSharedPool() : !refundItemPool(slot.pool)) return false;
            enabledPatterns.removeIf(key -> key.getTemplateType() == type);
            enabledPatterns.addAll(nextEnabled);
            enabledPatternsInitialized = true;
            if (slot == null) disabledTierMask = 0;
            else slot.disabledTierMask = 0;
            markDirty();
            needPatternSync = true;
            return true;
        } finally {
            configTransactionActive = false;
        }
    }

    /** Manually returns the shared consumable pool without changing template or pattern settings. */
    public boolean refundSharedPoolToME() {
        if (getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide() || configTransactionActive)
            return false;
        configTransactionActive = true;
        try {
            return refundSharedPool();
        } finally {
            configTransactionActive = false;
        }
    }

    /** Refunds all consumables, then replaces the fixed template and resets all template-specific settings. */
    public boolean bindTemplate(TemplateType type) {
        if (getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide()
            || type == null
            || hasCircuitSelector()
            || !type.isPhysicalTemplate()
            || !type.isConfigured()) {
            return false;
        }
        TemplateRecipeCatalog nextCatalog = AutoTemplateRecipeRegistry.get(type);
        if (nextCatalog == null || nextCatalog.getPatterns()
            .isEmpty()) return false;
        return replaceTemplate(type, nextCatalog);
    }

    private boolean replaceTemplate(TemplateType type, TemplateRecipeCatalog nextCatalog) {
        Set<PatternKey> nextEnabledPatterns = new LinkedHashSet<PatternKey>(
            nextCatalog.getByKey()
                .keySet());
        configTransactionActive = true;
        try {
            if (!refundSharedPool()) return false;
            templateType = type;
            enabledPatterns.clear();
            enabledPatterns.addAll(nextEnabledPatterns);
            enabledPatternsInitialized = true;
            disabledTierMask = 0;
            scheduledReason = ScheduledReason.UNDEFINED;
            justHadNewItems = false;
            lastMachineTier = Integer.MIN_VALUE;
            needPatternSync = true;
            markDirty();
            for (IHatchWatcher watcher : watchers) watcher.scheduleRecipeCheckImmediate();
            return true;
        } finally {
            configTransactionActive = false;
        }
    }

    private boolean isTemplateTypeAllowed(TemplateType type) {
        if (type == null) return false;
        if (hasCircuitSelector()) return type.getFamily() == defaultTemplateType.getFamily();
        return type == TemplateType.NONE || type.isPhysicalTemplate();
    }

    private boolean isPatternTypeAllowed(TemplateType type) {
        return hasCircuitSelector() ? isTemplateTypeAllowed(type) : type == templateType;
    }

    @Nullable
    private CircuitPatternSlot circuitSlot(TemplateType mode) {
        for (CircuitPatternSlot slot : circuitSlots) {
            if (slot.mode == mode) return slot;
        }
        return null;
    }

    private boolean refundSharedPool() {
        boolean refunded = refundItemPool(itemPool);
        for (CircuitPatternSlot slot : circuitSlots) refunded &= refundItemPool(slot.pool);
        refunded &= refundFluidPool();
        return refunded;
    }

    private boolean refundItemPool(SharedItemInputPool pool) {
        if (pool.isEmpty()) return true;
        try {
            BaseActionSource source = new MachineSource((IActionHost) getBaseMetaTileEntity());
            IMEMonitor<IAEItemStack> monitor = getProxy().getStorage()
                .getItemInventory();
            for (LongItemStore.Snapshot stored : pool.longSnapshot()) {
                ItemStack identity = stored.getIdentity();
                if (stored.getAmount() <= 0) continue;
                IAEItemStack request = AEApi.instance()
                    .storage()
                    .createItemStack(identity);
                request.setStackSize(stored.getAmount());
                IAEItemStack rest = Platform.poweredInsert(getProxy().getEnergy(), monitor, request, source);
                long remaining = rest == null ? 0 : Math.max(0, rest.getStackSize());
                long inserted = stored.getAmount() - remaining;
                if (inserted > 0) pool.consumeLongItem(identity, inserted);
            }
            return pool.isEmpty();
        } catch (GridAccessException exception) {
            TenkeiTech.LOG
                .warn("Cannot refund auto-template input pool because its AE network is unavailable", exception);
            return false;
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Unexpected failure while refunding the auto-template input pool", exception);
            return false;
        } finally {
            markDirty();
        }
    }

    private boolean refundFluidPool() {
        if (fluidPool.isEmpty()) return true;
        try {
            BaseActionSource source = new MachineSource((IActionHost) getBaseMetaTileEntity());
            IMEMonitor<IAEFluidStack> monitor = getProxy().getStorage()
                .getFluidInventory();
            for (LongFluidStore.Snapshot stored : fluidPool.longSnapshot()) {
                FluidStack identity = stored.getIdentity();
                if (stored.getAmount() <= 0) continue;
                IAEFluidStack request = AEApi.instance()
                    .storage()
                    .createFluidStack(identity);
                request.setStackSize(stored.getAmount());
                IAEFluidStack rest = Platform.poweredInsert(getProxy().getEnergy(), monitor, request, source);
                long remaining = rest == null ? 0 : Math.max(0, rest.getStackSize());
                long inserted = stored.getAmount() - remaining;
                if (inserted > 0) fluidPool.consumeLongFluid(identity, inserted);
            }
            return fluidPool.isEmpty();
        } catch (GridAccessException exception) {
            TenkeiTech.LOG
                .warn("Cannot refund auto-template fluid pool because its AE network is unavailable", exception);
            return false;
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Unexpected failure while refunding the auto-template fluid pool", exception);
            return false;
        } finally {
            markDirty();
        }
    }

    private boolean postMEPatternChange() {
        if (!getProxy().isActive()) return false;
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCraftingPatternChange(this, getProxy().getNode()));
            return true;
        } catch (GridAccessException exception) {
            return false;
        }
    }

    @Override
    public void onBlockDestroyed() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide() && !refundSharedPool()) {
            TenkeiTech.LOG
                .warn("Auto-template input pool could not be refunded during machine destruction at {}", getLocation());
            dropRemainingPoolContents();
        }
        super.onBlockDestroyed();
    }

    private void dropRemainingPoolContents() {
        dropRemainingItemPool(itemPool);
        for (CircuitPatternSlot slot : circuitSlots) dropRemainingItemPool(slot.pool);
        for (LongFluidStore.Snapshot stored : fluidPool.longSnapshot()) {
            FluidStack identity = stored.getIdentity();
            long remaining = stored.getAmount();
            while (remaining > 0) {
                FluidStack dropped = identity.copy();
                dropped.amount = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack packet = com.glodblock.github.common.item.ItemFluidPacket.newStack(dropped);
                if (packet == null) {
                    TenkeiTech.LOG.error(
                        "Unable to create a fluid refund packet for {} mB of {}",
                        dropped.amount,
                        dropped.getFluid());
                    break;
                }
                if (!dropItemStack(packet)) {
                    TenkeiTech.LOG.error(
                        "Unable to spawn a fluid refund packet for {} mB of {}",
                        dropped.amount,
                        dropped.getFluid());
                    break;
                }
                fluidPool.consumeLongFluid(identity, dropped.amount);
                remaining -= dropped.amount;
            }
        }
    }

    private void dropRemainingItemPool(SharedItemInputPool pool) {
        for (LongItemStore.Snapshot stored : pool.longSnapshot()) {
            ItemStack identity = stored.getIdentity();
            long remaining = stored.getAmount();
            while (remaining > 0) {
                ItemStack dropped = identity.copy();
                int droppedAmount = (int) Math.min(Integer.MAX_VALUE, remaining);
                dropped.stackSize = droppedAmount;
                if (!dropItemStack(dropped)) {
                    TenkeiTech.LOG.error(
                        "Unable to spawn an item refund stack during auto-template input destruction: {}",
                        dropped);
                    break;
                }
                pool.consumeLongItem(identity, droppedAmount);
                remaining -= droppedAmount;
            }
        }
    }

    private boolean dropItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || getBaseMetaTileEntity() == null) return false;
        World world = getBaseMetaTileEntity().getWorld();
        if (world == null || world.isRemote) return false;
        EntityItem entity = new EntityItem(
            world,
            getBaseMetaTileEntity().getXCoord() + 0.5D,
            getBaseMetaTileEntity().getYCoord() + 0.5D,
            getBaseMetaTileEntity().getZCoord() + 0.5D,
            stack.copy());
        if (!world.spawnEntityInWorld(entity)) return false;
        stack.stackSize = 0;
        return true;
    }

    @Override
    public String[] getDescription() {
        String key = switch (defaultTemplateType.getFamily()) {
            case WIREMILL -> "tenkeitech.machine.wiremill_template_input.desc";
            case BENDER -> "tenkeitech.machine.bender_template_input.desc";
            default -> "tenkeitech.machine.auto_template_input.desc";
        };
        return GTSplit.splitLocalizedFormatted(key, TIER_COLORS[mTier] + VN[mTier]);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        // GregTech can dispatch a stale interaction while the base tile is being invalidated.
        // Consume it without entering the GUI factory, which requires both objects to be valid.
        if (baseMetaTileEntity == null || player == null) return true;
        try {
            // The callback argument can outlive the meta tile's back-reference during unload/replacement.
            if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity() != baseMetaTileEntity) return true;

            ItemStack held = player.getHeldItem();
            TemplateType heldTemplate;
            try {
                heldTemplate = TemplateType.fromTemplateStack(held);
            } catch (RuntimeException exception) {
                TenkeiTech.LOG
                    .warn("Ignoring invalid template stack during auto-template input right click", exception);
                heldTemplate = TemplateType.NONE;
            }
            if (!hasCircuitSelector() && player.isSneaking() && heldTemplate != TemplateType.NONE) {
                if (baseMetaTileEntity.isServerSide()) {
                    boolean bound;
                    try {
                        bound = bindTemplate(heldTemplate);
                    } catch (RuntimeException exception) {
                        TenkeiTech.LOG.warn("Ignoring failed auto-template input rebind during right click", exception);
                        bound = false;
                    }
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            bound ? "tenkeitech.machine.auto_template_input.bind_success"
                                : "tenkeitech.machine.auto_template_input.bind_failed",
                            templateDisplayName(heldTemplate)));
                }
                return true;
            }

            if (!baseMetaTileEntity.canAccessData()) return true;
            openGui(player);
        } catch (RuntimeException exception) {
            // GUI creation crosses GT/ModularUI callbacks and may observe a tile during invalidation.
            // Keep that transient failure local instead of letting GregTech log a right-click NPE.
            TenkeiTech.LOG
                .warn("Ignoring auto-template input right click while tile context is unavailable", exception);
        }
        return true;
    }

    @Override
    public void onLeftclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        ItemStack dataStick = player.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return;
        saveToDataStick(player, dataStick);
    }

    public void saveToDataStick(EntityPlayer player, ItemStack dataStick) {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || dataStick == null) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", LINK_DATA_TYPE);
        tag.setInteger("x", baseMetaTileEntity.getXCoord());
        tag.setInteger("y", baseMetaTileEntity.getYCoord());
        tag.setInteger("z", baseMetaTileEntity.getZCoord());
        tag.setInteger("dim", baseMetaTileEntity.getWorld().provider.dimensionId);
        dataStick.stackTagCompound = tag;
        dataStick.setStackDisplayName(
            StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.auto_template_input_proxy.data_stick_name",
                baseMetaTileEntity.getXCoord(),
                baseMetaTileEntity.getYCoord(),
                baseMetaTileEntity.getZCoord()));
        player.addChatMessage(new ChatComponentTranslation("tenkeitech.machine.proxy.data_saved"));
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
    public String getInventoryName() {
        return StatCollector.translateToLocal("gt.blockmachines." + mName + ".name");
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        TemplateType type = stack == null || !stack.hasTagCompound() ? TemplateType.NONE
            : TemplateType.fromId(
                stack.getTagCompound()
                    .getInteger(NBT_TEMPLATE_TYPE));
        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.auto_template_input.template",
                templateDisplayName(type)));
        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "tenkeitech.machine.auto_template_input.input",
                StatCollector.translateToLocal(
                    "tenkeitech.autotemplate.input." + type.getInputKind()
                        .name()
                        .toLowerCase())));
    }

    public String getName() {
        return templateType == TemplateType.NONE ? mName : mName + " [" + templateDisplayName(templateType) + "]";
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(
            getBaseMetaTileEntity().getWorld(),
            getBaseMetaTileEntity().getXCoord(),
            getBaseMetaTileEntity().getYCoord(),
            getBaseMetaTileEntity().getZCoord());
    }

    public IAEStackType<?>[] getSupportedStackTypes() {
        return supportsFluids()
            ? new IAEStackType<?>[] { appeng.util.item.AEItemStackType.ITEM_STACK_TYPE,
                appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE }
            : new IAEStackType<?>[] { appeng.util.item.AEItemStackType.ITEM_STACK_TYPE };
    }

    public ItemStack getSelfRep() {
        return getTemplateMachineStack();
    }

    private ItemStack getTemplateMachineStack() {
        ItemStack stack = getStackForm(1);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();
        tag.setInteger(NBT_TEMPLATE_TYPE, templateType.getStableId());
        stack.setTagCompound(tag);
        return stack;
    }

    private static String templateDisplayName(TemplateType type) {
        if (type == null || type == TemplateType.NONE) {
            return StatCollector.translateToLocal("tenkeitech.gui.auto_template.invalid");
        }
        ItemStack template = type.getTemplateStack();
        return template == null ? type.name() : template.getDisplayName();
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** Returns the fixed-template UI; the template type itself is never editable here. */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEAutoTemplateInputGui(this, data.getPlayer()).build(data, syncManager, uiSettings);
    }

    @Override
    public void securityBreak() {}

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        TemplateType displayedType = TemplateType.fromId(
            accessor.getNBTData()
                .getInteger(NBT_TEMPLATE_TYPE));
        if (displayedType.isCircuitMode()) {
            currenttip.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.machine.auto_template_input.circuit_slots",
                    displayedType.getSelectableModes().length));
        } else {
            currenttip.add(
                StatCollector.translateToLocalFormatted(
                    "tenkeitech.machine.auto_template_input.template",
                    templateDisplayName(displayedType)));
        }
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey(WAILA_INVENTORY, Constants.NBT.TAG_LIST)) {
            NBTTagList inventory = tag.getTagList(WAILA_INVENTORY, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < inventory.tagCount(); index++) {
                NBTTagCompound entry = inventory.getCompoundTagAt(index);
                currenttip.add(
                    entry.getString(WAILA_NAME) + ": "
                        + EnumChatFormatting.GOLD
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(entry.getLong(WAILA_AMOUNT))
                        + EnumChatFormatting.RESET);
            }
        }
    }

    @Override
    public void getWailaNBTData(net.minecraft.entity.player.EntityPlayerMP player,
        net.minecraft.tileentity.TileEntity tile, NBTTagCompound tag, World world, int x, int y, int z) {
        tag.setInteger(NBT_TEMPLATE_TYPE, templateType.getStableId());
        Map<String, Long> amountsByName = new HashMap<String, Long>();
        for (LongItemStore.Snapshot stored : itemPool.longSnapshot()) {
            amountsByName.merge(
                stored.getIdentity()
                    .getDisplayName(),
                stored.getAmount(),
                MTEHatchAutoTemplateInputME::saturatedAdd);
        }
        for (CircuitPatternSlot slot : circuitSlots) {
            for (LongItemStore.Snapshot stored : slot.pool.longSnapshot()) {
                amountsByName.merge(
                    stored.getIdentity()
                        .getDisplayName(),
                    stored.getAmount(),
                    MTEHatchAutoTemplateInputME::saturatedAdd);
            }
        }
        for (LongFluidStore.Snapshot stored : fluidPool.longSnapshot()) {
            amountsByName.merge(
                stored.getIdentity()
                    .getLocalizedName(),
                stored.getAmount(),
                MTEHatchAutoTemplateInputME::saturatedAdd);
        }
        NBTTagList inventory = new NBTTagList();
        for (Map.Entry<String, Long> amount : amountsByName.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(WAILA_NAME, amount.getKey());
            entry.setLong(WAILA_AMOUNT, amount.getValue());
            inventory.appendTag(entry);
        }
        tag.setTag(WAILA_INVENTORY, inventory);
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
    }

    private static final class CircuitPatternSlot {

        private final TemplateType mode;
        private final SharedItemInputPool pool = new SharedItemInputPool();
        private final CircuitInputInventory inventory;
        private int disabledTierMask;

        private CircuitPatternSlot(TemplateType mode) {
            this.mode = mode;
            this.inventory = new CircuitInputInventory(pool, mode);
        }
    }

    private static final class CircuitInputInventory implements IDualInputInventory, ILongItemInputInventory {

        private final SharedItemInputPool pool;
        private final TemplateType mode;

        private CircuitInputInventory(SharedItemInputPool pool, TemplateType mode) {
            this.pool = pool;
            this.mode = mode;
        }

        @Override
        public boolean isEmpty() {
            return pool.isEmpty();
        }

        @Override
        public ItemStack[] getItemInputs() {
            ItemStack[] backing = pool.getItemInputs();
            ItemStack circuit = mode.getTemplateStack();
            if (circuit == null) return backing;
            ItemStack[] combined = new ItemStack[backing.length + 1];
            System.arraycopy(backing, 0, combined, 0, backing.length);
            combined[backing.length] = circuit;
            return combined;
        }

        @Override
        public FluidStack[] getFluidInputs() {
            return new FluidStack[0];
        }

        @Override
        public List<LongItemStore.Snapshot> longItemSnapshot() {
            return pool.longItemSnapshot();
        }

        @Override
        public long getLongItemAmount(ItemStack identity) {
            return pool.getLongItemAmount(identity);
        }

        @Override
        public void consumeLongItem(ItemStack identity, long amount) {
            pool.consumeLongItem(identity, amount);
        }
    }
}
