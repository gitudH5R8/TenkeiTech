/*
 * SPDX-FileCopyrightText: GT5-Unofficial contributors
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: LGPL-3.0-only
 * Adapted from GT5-Unofficial's MTEHatchCraftingInputME 5.09.54.105.
 * Interface suffix components updated for GT5-Unofficial 5.09.54.133.
 */
package com.tenkeitech.machine;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;
import static gregtech.api.enums.GTValues.TIER_COLORS;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_CRAFTING_INPUT_BUFFER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_CRAFTING_INPUT_BUS;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotGroup;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.tenkeitech.ae.TransformedPatternDetails;
import com.tenkeitech.gui.MTESuperCraftingInputMEGui;
import com.tenkeitech.integration.wildcardpattern.WildcardPatternCircuitData;
import com.tenkeitech.integration.wildcardpattern.WildcardPatternItemBridge;
import com.tenkeitech.inventory.ILongFluidInputInventory;
import com.tenkeitech.inventory.ILongItemInputInventory;
import com.tenkeitech.inventory.LongFluidStore;
import com.tenkeitech.inventory.LongItemStore;
import com.tenkeitech.item.ItemPatternBufferTransferTool;
import com.tenkeitech.item.ItemPatternBufferTransferTool.Mode;
import com.tenkeitech.item.ItemPatternBufferTransferTool.TransferResult;
import com.tenkeitech.item.ItemPatternCatalystMarker;
import com.tenkeitech.patternmode.PatternModeController;
import com.tenkeitech.patternmode.PatternRecipeMapData;
import com.tenkeitech.patternmode.SuperPatternModeBuffer;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.interfaces.IInterfaceNameProvider;
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
import appeng.api.util.IInterfaceViewable;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.helpers.ICustomNameObject;
import appeng.items.misc.ItemEncodedPattern;
import appeng.items.tools.quartz.ToolQuartzCuttingKnife;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;
import cpw.mods.fml.common.Loader;
import gregtech.GTLoggers;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IMEConnectable;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import gregtech.api.util.extensions.ArrayExt;
import gregtech.common.config.Gregtech;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputHatchWithPattern;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.crossmod.ae2.ChatComponentNonConsumedItemsSuffix;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@IMetaTileEntity.SkipGenerateDescription
public class MTESuperCraftingInputME extends MTEHatchInputBus implements IPowerChannelState, ICraftingProvider,
    IGridProxyable, IDualInputHatchWithPattern, ICustomNameObject, IInterfaceViewable, IMEConnectable {

    /**
     * One logical input selected for Eye of Harmony compatibility. The arrays are copies, but their non-virtual stack
     * entries are the machine-owned backing objects so the target machine can consume them in place.
     */
    public static final class EyeOfHarmonyInputView {

        private final ItemStack[] items;
        private final FluidStack[] fluids;

        private EyeOfHarmonyInputView(ItemStack[] items, FluidStack[] fluids) {
            this.items = items;
            this.fluids = fluids;
        }

        public ItemStack[] getItems() {
            return items;
        }

        public FluidStack[] getFluids() {
            return fluids;
        }
    }

    // Each pattern slot in the crafting input hatch has its own internal inventory
    public static class PatternSlot<P extends IMetaTileEntity & IDualInputHatch>
        implements IDualInputInventoryWithPattern {

        private static final String NBT_ACTIVE_EXPANDED_PATTERN_ID = "tenkeitechActiveExpandedPatternId";

        private static final class PatternDetailsEntry {

            private final String id;
            private final ICraftingPatternDetails raw;
            private final ICraftingPatternDetails effective;
            private final List<ItemStack> automaticItemCatalysts;

            private PatternDetailsEntry(String id, ICraftingPatternDetails raw) {
                this(id, raw, new TransformedPatternDetails(raw));
            }

            private PatternDetailsEntry(String id, ICraftingPatternDetails raw, ICraftingPatternDetails effective) {
                this.id = id;
                this.raw = raw;
                this.effective = effective;
                this.automaticItemCatalysts = readAutomaticItemCatalysts(raw);
            }
        }

        protected final P parentMTE;
        protected final int fixedIndex;
        protected final ItemStack pattern;
        private final String recipeMapId;
        @Nullable
        protected final ICraftingPatternDetails rawPatternDetails;
        @Nullable
        protected final ICraftingPatternDetails effectiveDetails;
        protected final GTUtility.ItemId patternItemId;
        private final boolean expandedPattern;
        private final List<PatternDetailsEntry> registrationDetails;
        @Nullable
        private PatternDetailsEntry activeExpandedDetails;

        protected final LongItemStore itemInventory;
        protected final LongFluidStore fluidInventory;
        protected final PatternAugmentData augmentData;
        private final IDualInputInventoryWithPattern processingInventory;

        public PatternSlot(ItemStack pattern, P parent) {
            this(pattern, null, parent, new PatternAugmentData(), -1);
        }

        public PatternSlot(ItemStack pattern, NBTTagCompound nbt, P parent) {
            this(pattern, nbt, parent, new PatternAugmentData(), -1);
        }

        private PatternSlot(ItemStack pattern, NBTTagCompound nbt, P parent, PatternAugmentData augmentData,
            int fixedIndex) {
            this.pattern = pattern;
            this.parentMTE = parent;
            this.fixedIndex = fixedIndex;
            if (!(pattern.getItem() instanceof ICraftingPatternItem patternItem)) {
                throw new IllegalArgumentException("Pattern slot item does not implement ICraftingPatternItem");
            }
            this.recipeMapId = PatternRecipeMapData.read(pattern);
            this.rawPatternDetails = patternItem.getPatternForItem(
                pattern,
                parent.getBaseMetaTileEntity()
                    .getWorld());
            this.augmentData = augmentData;
            this.effectiveDetails = rawPatternDetails == null ? null : new TransformedPatternDetails(rawPatternDetails);
            this.expandedPattern = patternItem instanceof WildcardPatternItemBridge;
            this.registrationDetails = createRegistrationDetails(
                patternItem,
                pattern,
                parent.getBaseMetaTileEntity()
                    .getWorld(),
                rawPatternDetails,
                effectiveDetails);
            this.itemInventory = new LongItemStore();
            this.fluidInventory = new LongFluidStore();
            this.patternItemId = GTUtility.ItemId.create(pattern);
            this.processingInventory = new SharedConsumableProcessingInventory();

            if (nbt == null) return;
            NBTTagList inv = nbt.getTagList("inventory", Constants.NBT.TAG_COMPOUND);
            itemInventory.readFromNBT(inv);
            fluidInventory.readFromNBT(nbt.getTagList("fluidInventory", Constants.NBT.TAG_COMPOUND));
            restoreExpandedPattern(nbt.getString(NBT_ACTIVE_EXPANDED_PATTERN_ID));
        }

        private PatternSlot(PatternSlot<P> source) {
            this(source, source.augmentData);
        }

        private PatternSlot(PatternSlot<P> source, PatternAugmentData augmentData) {
            this.parentMTE = source.parentMTE;
            this.fixedIndex = source.fixedIndex;
            this.pattern = source.pattern;
            this.recipeMapId = source.recipeMapId;
            this.rawPatternDetails = source.rawPatternDetails;
            this.patternItemId = source.patternItemId;
            this.itemInventory = new LongItemStore();
            this.fluidInventory = new LongFluidStore();
            this.augmentData = augmentData;
            this.effectiveDetails = rawPatternDetails == null ? null : new TransformedPatternDetails(rawPatternDetails);
            this.expandedPattern = source.expandedPattern;
            if (expandedPattern) {
                List<PatternDetailsEntry> copiedDetails = new ArrayList<>(source.registrationDetails.size());
                for (PatternDetailsEntry entry : source.registrationDetails) {
                    copiedDetails.add(new PatternDetailsEntry(entry.id, entry.raw));
                }
                this.registrationDetails = Collections.unmodifiableList(copiedDetails);
            } else {
                this.registrationDetails = rawPatternDetails == null ? Collections.emptyList()
                    : Collections.singletonList(new PatternDetailsEntry("", rawPatternDetails, effectiveDetails));
            }
            this.processingInventory = new SharedConsumableProcessingInventory();
            restoreActiveExpandedPatternId(source.getActiveExpandedPatternId());
        }

        private static List<PatternDetailsEntry> createRegistrationDetails(ICraftingPatternItem patternItem,
            ItemStack pattern, World world, @Nullable ICraftingPatternDetails standardDetails,
            @Nullable ICraftingPatternDetails standardEffectiveDetails) {
            if (!(patternItem instanceof WildcardPatternItemBridge bridge)) {
                return standardDetails == null ? Collections.emptyList()
                    : Collections.singletonList(new PatternDetailsEntry("", standardDetails, standardEffectiveDetails));
            }

            List<ICraftingPatternDetails> expanded = bridge.tenkeitech$getExpandedPatternDetails(pattern, world);
            if (expanded == null || expanded.isEmpty()) return Collections.emptyList();
            List<PatternDetailsEntry> entries = new ArrayList<>(expanded.size());
            Set<String> ids = new LinkedHashSet<>();
            for (ICraftingPatternDetails details : expanded) {
                if (details == null) continue;
                String id = bridge.tenkeitech$getExpandedPatternId(details);
                if (id == null || id.isEmpty()) {
                    throw new IllegalArgumentException("Expanded wildcard pattern has no stable generated ID");
                }
                if (!ids.add(id)) {
                    throw new IllegalArgumentException(
                        "Expanded wildcard pattern contains duplicate generated ID " + id);
                }
                entries.add(new PatternDetailsEntry(id, details));
            }
            return Collections.unmodifiableList(entries);
        }

        public PatternSlot<P> copyForConfiguration() {
            if (!isEmpty()) throw new IllegalStateException("Consumable inputs must be empty before replacing a slot");
            return new PatternSlot<>(this);
        }

        private PatternSlot<P> copyForConfiguration(PatternAugmentData augmentData) {
            if (!isEmpty()) throw new IllegalStateException("Consumable inputs must be empty before replacing a slot");
            return new PatternSlot<>(this, augmentData);
        }

        public boolean hasChanged(ItemStack newPattern, World world) {
            if (newPattern == null || rawPatternDetails == null
                || !(newPattern.getItem() instanceof ICraftingPatternItem patternItem)) {
                return true;
            }
            if (expandedPattern || patternItem instanceof WildcardPatternItemBridge) {
                return !recipeMapId.equals(PatternRecipeMapData.read(newPattern))
                    || !ItemStack.areItemStacksEqual(pattern, newPattern);
            }
            try {
                ICraftingPatternDetails newDetails = patternItem.getPatternForItem(newPattern, world);
                return !recipeMapId.equals(PatternRecipeMapData.read(newPattern))
                    || !ItemStack.areItemStacksEqual(pattern, newPattern) && !rawPatternDetails.equals(newDetails);
            } catch (RuntimeException exception) {
                return true;
            }
        }

        public void updateSlotItems() {
            itemInventory.observeBackingChanges();
        }

        public void updateSlotFluids() {
            fluidInventory.observeBackingChanges();
        }

        public boolean isItemEmpty() {
            updateSlotItems();
            return itemInventory.isEmpty();
        }

        public boolean isFluidEmpty() {
            return fluidInventory.isEmpty();
        }

        @Override
        public boolean isEmpty() {
            return isItemEmpty() && isFluidEmpty();
        }

        public IDualInputInventoryWithPattern getProcessingInventory() {
            return processingInventory;
        }

        private final class SharedConsumableProcessingInventory
            implements IDualInputInventoryWithPattern, ILongFluidInputInventory, ILongItemInputInventory {

            @Override
            public boolean isEmpty() {
                if (!PatternSlot.this.isEmpty()) return false;
                return !sharedConsumablesSupplyPattern();
            }

            private boolean sharedConsumablesSupplyPattern() {
                if (expandedPattern || effectiveDetails == null
                    || effectiveDetails.isCraftable()
                    || !(parentMTE instanceof MTESuperCraftingInputME machine)) return false;
                IAEStack<?>[] inputs = effectiveDetails.getCondensedAEInputs();
                if (inputs == null || inputs.length == 0) return false;
                boolean sawInput = false;
                for (IAEStack<?> input : inputs) {
                    if (input == null || input.getStackSize() <= 0) continue;
                    sawInput = true;
                    if (input instanceof IAEItemStack itemInput) {
                        if (machine.getSharedConsumableAmount(itemInput.getItemStack()) < input.getStackSize()) {
                            return false;
                        }
                    } else if (input instanceof IAEFluidStack fluidInput) {
                        if (machine.getSharedConsumableAmount(fluidInput.getFluidStack()) < input.getStackSize()) {
                            return false;
                        }
                    } else return false;
                }
                return sawInput;
            }

            @Override
            public ItemStack[] getItemInputs() {
                return PatternSlot.this.getItemInputs(this);
            }

            @Override
            public FluidStack[] getFluidInputs() {
                return PatternSlot.this.getFluidInputs();
            }

            @Override
            public boolean ownsAllFluidInputs(GTRecipe recipe) {
                if (recipe == null || recipe.mFluidInputs == null || recipe.mFluidInputs.length == 0) return false;
                for (FluidStack input : recipe.mFluidInputs) {
                    if (input != null && input.amount > 0 && getLongFluidAmount(input) <= 0) return false;
                }
                return true;
            }

            @Override
            public long getLongFluidAmount(FluidStack identity) {
                long amount = fluidInventory.getAmount(identity);
                if (parentMTE instanceof MTESuperCraftingInputME machine) {
                    long shared = machine.sharedConsumableFluidStock.getLongFluidAmount(identity);
                    amount = amount > Long.MAX_VALUE - shared ? Long.MAX_VALUE : amount + shared;
                }
                return amount;
            }

            @Override
            public void consumeLongFluid(FluidStack identity, long amount) {
                long consumed = fluidInventory.consumeUpTo(identity, amount);
                long remaining = amount - consumed;
                if (remaining <= 0) return;
                if (!(parentMTE instanceof MTESuperCraftingInputME machine)) {
                    throw new IllegalStateException("Insufficient pattern fluid input");
                }
                machine.sharedConsumableFluidStock.consumeLongFluid(identity, remaining);
            }

            @Override
            public List<LongItemStore.Snapshot> longItemSnapshot() {
                List<LongItemStore.Snapshot> result = new ArrayList<>(itemInventory.longSnapshot());
                if (parentMTE instanceof MTESuperCraftingInputME machine) {
                    result.addAll(machine.sharedConsumableStock.longItemSnapshot());
                }
                return result;
            }

            @Override
            public long getLongItemAmount(ItemStack identity) {
                long amount = itemInventory.getAmount(identity);
                if (parentMTE instanceof MTESuperCraftingInputME machine) {
                    long shared = machine.sharedConsumableStock.getLongItemAmount(identity);
                    amount = amount > Long.MAX_VALUE - shared ? Long.MAX_VALUE : amount + shared;
                }
                return amount;
            }

            @Override
            public void consumeLongItem(ItemStack identity, long amount) {
                long consumed = Math.min(amount, itemInventory.getAmount(identity));
                if (consumed > 0) itemInventory.consume(identity, consumed);
                long remaining = amount - consumed;
                if (remaining <= 0) return;
                if (!(parentMTE instanceof MTESuperCraftingInputME machine)) {
                    throw new IllegalStateException("Insufficient pattern item input");
                }
                machine.sharedConsumableStock.consumeLongItem(identity, remaining);
            }

            @Override
            public GTDualInputPattern getPatternInputs() {
                return PatternSlot.this.getPatternInputs();
            }
        }

        public String getRecipeMapId() {
            return recipeMapId;
        }

        private boolean allowedForProcessing() {
            if (expandedPattern && !isEmpty() && activeExpandedDetails == null) return false;
            return !(parentMTE instanceof MTESuperCraftingInputME host) || host.getPatternMode()
                .allows(this);
        }

        public boolean isExpandedPattern() {
            return expandedPattern;
        }

        public PatternAugmentData getAugmentData() {
            return augmentData.copy();
        }

        public int getFixedIndex() {
            return fixedIndex;
        }

        public List<ItemStack> getMissingAutomaticItemCatalysts(ICraftingPatternDetails details) {
            PatternDetailsEntry entry = findRegistrationDetails(details);
            if (entry == null || !canAcceptPattern(entry)) return Collections.emptyList();
            return entry.automaticItemCatalysts.stream()
                .filter(catalyst -> !hasInstalledItemCatalyst(catalyst))
                .map(ItemStack::copy)
                .collect(Collectors.toList());
        }

        private boolean hasInstalledItemCatalyst(ItemStack requirement) {
            for (int index = 0; index < PatternAugmentData.CATALYST_SLOT_COUNT; index++) {
                if (ItemPatternCatalystMarker.isSameCatalyst(requirement, augmentData.getItemCatalyst(index))) {
                    return true;
                }
            }
            return false;
        }

        private static List<ItemStack> readAutomaticItemCatalysts(@Nullable ICraftingPatternDetails details) {
            if (details == null || details.isCraftable()) return Collections.emptyList();
            List<ItemStack> catalysts = new ArrayList<>();
            for (IAEStack<?> input : details.getAEInputs()) {
                if (!(input instanceof IAEItemStack itemInput)) continue;
                ItemStack marker = itemInput.getItemStack();
                if (!ItemPatternCatalystMarker.isMarker(marker)) continue;
                ItemStack catalyst = ItemPatternCatalystMarker.getCatalyst(marker);
                if (catalyst == null) throw new IllegalArgumentException("Pattern contains an invalid catalyst marker");
                if (catalysts.stream()
                    .anyMatch(existing -> ItemPatternCatalystMarker.isSameCatalyst(existing, catalyst))) {
                    continue;
                }
                if (catalysts.size() >= PatternAugmentData.CATALYST_SLOT_COUNT) {
                    throw new IllegalArgumentException("Pattern contains more automatic catalysts than catalyst slots");
                }
                catalysts.add(catalyst);
            }
            return Collections.unmodifiableList(catalysts);
        }

        public List<ICraftingPatternDetails> getRegistrationDetails() {
            return registrationDetails.stream()
                .map(entry -> entry.effective)
                .collect(Collectors.toList());
        }

        private List<ICraftingPatternDetails> getRawRegistrationDetails() {
            return registrationDetails.stream()
                .map(entry -> entry.raw)
                .collect(Collectors.toList());
        }

        private boolean hasRegistrationDetailsIdentity(ICraftingPatternDetails details) {
            ICraftingPatternDetails raw = unwrap(details);
            for (PatternDetailsEntry entry : registrationDetails) {
                if (entry.effective == details || entry.raw == raw) return true;
            }
            return false;
        }

        @Nullable
        private PatternDetailsEntry findRegistrationDetails(ICraftingPatternDetails details) {
            ICraftingPatternDetails raw = unwrap(details);
            for (PatternDetailsEntry entry : registrationDetails) {
                if (entry.effective == details || entry.raw == raw || entry.raw.equals(raw)) return entry;
            }
            return null;
        }

        private static ICraftingPatternDetails unwrap(ICraftingPatternDetails details) {
            return details instanceof TransformedPatternDetails transformed ? transformed.getRawDetails() : details;
        }

        public boolean activatePattern(ICraftingPatternDetails details) {
            PatternDetailsEntry requested = findRegistrationDetails(details);
            if (requested == null || !canAcceptPattern(requested)) return false;
            if (!expandedPattern) return true;
            activeExpandedDetails = requested;
            return true;
        }

        private boolean canAcceptPattern(ICraftingPatternDetails details) {
            PatternDetailsEntry requested = findRegistrationDetails(details);
            return requested != null && canAcceptPattern(requested);
        }

        private boolean canAcceptPattern(PatternDetailsEntry requested) {
            return !expandedPattern || isEmpty()
                || activeExpandedDetails != null && activeExpandedDetails.id.equals(requested.id);
        }

        @Nullable
        public String getActiveExpandedPatternId() {
            return activeExpandedDetails == null ? null : activeExpandedDetails.id;
        }

        public void restoreActiveExpandedPatternId(@Nullable String id) {
            activeExpandedDetails = findRegistrationDetails(id);
        }

        @Nullable
        private PatternDetailsEntry findRegistrationDetails(@Nullable String id) {
            if (id == null || id.isEmpty()) return null;
            for (PatternDetailsEntry entry : registrationDetails) if (entry.id.equals(id)) return entry;
            return null;
        }

        private void restoreExpandedPattern(String savedId) {
            if (!expandedPattern || isEmpty()) return;
            activeExpandedDetails = findRegistrationDetails(savedId);
            if (activeExpandedDetails == null) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Missing or invalid active wildcard recipe ID for TenkeiTech super pattern buffer slot {}; processing is blocked until its inputs are refunded",
                    fixedIndex);
            }
        }

        @Override
        public ItemStack[] getItemInputs() {
            return getItemInputs(null);
        }

        private ItemStack[] getItemInputs(@Nullable ILongItemInputInventory owner) {
            if (!allowedForProcessing()) return GTValues.emptyItemStackArray;
            ItemStack[] inputs = itemInventory.getItemInputs(owner);
            for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
                ItemStack catalyst = augmentData.getItemCatalyst(i);
                if (catalyst == null) continue;
                catalyst.stackSize = 0;
                inputs = ArrayUtils.add(inputs, catalyst);
            }
            ItemStack virtualCircuit = getVirtualCircuit();
            if (virtualCircuit != null) {
                virtualCircuit.stackSize = 0;
                inputs = ArrayUtils.add(inputs, virtualCircuit);
            }
            return inputs.length == 0 ? GTValues.emptyItemStackArray : inputs;
        }

        @Override
        public FluidStack[] getFluidInputs() {
            if (!allowedForProcessing()) return GTValues.emptyFluidStackArray;
            FluidStack[] inputs = fluidInventory.getFluidInputs((ILongFluidInputInventory) processingInventory);
            for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
                FluidStack catalyst = augmentData.getFluidCatalyst(i);
                if (catalyst == null) continue;
                catalyst.amount = 0;
                inputs = ArrayUtils.add(inputs, catalyst);
            }
            if (parentMTE instanceof MTESuperCraftingInputME machine) {
                FluidCatalystData sharedCatalysts = machine.getSharedFluidCatalysts();
                for (int i = 0; i < FluidCatalystData.TANK_COUNT; i++) {
                    FluidStack catalyst = sharedCatalysts.get(i);
                    if (catalyst == null) continue;
                    catalyst.amount = 0;
                    inputs = ArrayUtils.add(inputs, catalyst);
                }
                inputs = ArrayUtils.addAll(inputs, machine.getSharedConsumableFluids());
            }
            return inputs.length == 0 ? GTValues.emptyFluidStackArray : inputs;
        }

        @Nullable
        public ICraftingPatternDetails getPatternDetails() {
            return expandedPattern && !isEmpty()
                ? activeExpandedDetails == null ? null : activeExpandedDetails.effective
                : effectiveDetails;
        }

        @Nullable
        public ICraftingPatternDetails getRawPatternDetails() {
            return rawPatternDetails;
        }

        @Override
        public GTDualInputPattern getPatternInputs() {
            GTDualInputPattern dualInputs = new GTDualInputPattern();
            ICraftingPatternDetails processingDetails = getPatternDetails();
            if (expandedPattern && !isEmpty() && processingDetails == null) {
                dualInputs.inputItems = GTValues.emptyItemStackArray;
                dualInputs.inputFluid = GTValues.emptyFluidStackArray;
                return dualInputs;
            }

            ItemStack[] inputItems = Arrays.stream(this.parentMTE.getSharedItems())
                .filter(stack -> !GTUtility.isAnyIntegratedCircuit(stack))
                .toArray(ItemStack[]::new);
            FluidStack[] inputFluids = parentMTE instanceof MTESuperCraftingInputME machine
                ? machine.getSharedConsumableFluids()
                : GTValues.emptyFluidStackArray;

            if (processingDetails != null) {
                for (IAEStack<?> singleInput : processingDetails.getAEInputs()) {
                    if (singleInput instanceof IAEItemStack ais) {
                        inputItems = ArrayUtils.addAll(inputItems, ais.getItemStack());
                    } else if (singleInput instanceof IAEFluidStack ifs) {
                        inputFluids = ArrayUtils.addAll(inputFluids, ifs.getFluidStack());
                    }
                }
            }
            for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
                ItemStack catalyst = augmentData.getItemCatalyst(i);
                if (catalyst != null) {
                    catalyst.stackSize = 0;
                    inputItems = ArrayUtils.add(inputItems, catalyst);
                }
                FluidStack fluidCatalyst = augmentData.getFluidCatalyst(i);
                if (fluidCatalyst != null) {
                    fluidCatalyst.amount = 0;
                    inputFluids = ArrayUtils.add(inputFluids, fluidCatalyst);
                }
            }
            if (parentMTE instanceof MTESuperCraftingInputME machine) {
                FluidCatalystData sharedCatalysts = machine.getSharedFluidCatalysts();
                for (int i = 0; i < FluidCatalystData.TANK_COUNT; i++) {
                    FluidStack catalyst = sharedCatalysts.get(i);
                    if (catalyst != null) {
                        catalyst.amount = 0;
                        inputFluids = ArrayUtils.add(inputFluids, catalyst);
                    }
                }
            }
            ItemStack virtualCircuit = getVirtualCircuit();
            if (virtualCircuit != null) {
                virtualCircuit.stackSize = 0;
                inputItems = ArrayUtils.add(inputItems, virtualCircuit);
            }

            dualInputs.inputItems = inputItems;
            dualInputs.inputFluid = inputFluids;
            return dualInputs;
        }

        @Nullable
        public ItemStack getVirtualCircuit() {
            ItemStack storedCircuit = getStoredPatternCircuit();
            if (storedCircuit != null) return storedCircuit;
            if (expandedPattern && !isEmpty() && activeExpandedDetails != null) {
                ItemStack generatedCircuit = findCircuit(activeExpandedDetails.raw);
                if (generatedCircuit != null) return generatedCircuit;
            }
            for (ItemStack shared : parentMTE.getSharedItems()) {
                if (GTUtility.isAnyIntegratedCircuit(shared)) return shared.copy();
            }
            return null;
        }

        @Nullable
        public ItemStack getStoredPatternCircuit() {
            return expandedPattern ? WildcardPatternCircuitData.read(pattern) : findCircuit(rawPatternDetails);
        }

        @Nullable
        private static ItemStack findCircuit(@Nullable ICraftingPatternDetails details) {
            if (details == null || details.isCraftable()) return null;
            for (IAEStack<?> stack : details.getAEInputs()) {
                if (stack instanceof IAEItemStack itemStack) {
                    ItemStack candidate = itemStack.getItemStack();
                    if (GTUtility.isAnyIntegratedCircuit(candidate)) return candidate.copy();
                }
            }
            return null;
        }

        /**
         * Try to refund the items and fluids back.
         * <p>
         * Push all the items and fluids back to the AE network first. If shouldDrop is true, the remaining are dropped
         * to the world (the fluids are dropped as AE2FC fluid drop). Otherwise, they are still left in the inventory.
         */
        public boolean refund(AENetworkProxy proxy, BaseActionSource src, boolean shouldDrop)
            throws GridAccessException {
            if (!isItemEmpty()) {
                IMEMonitor<IAEItemStack> sg = proxy.getStorage()
                    .getItemInventory();
                for (LongItemStore.Snapshot stored : itemInventory.longSnapshot()) {
                    ItemStack identity = stored.getIdentity();
                    if (stored.getAmount() <= 0) continue;
                    IAEItemStack request = AEApi.instance()
                        .storage()
                        .createItemStack(identity);
                    request.setStackSize(stored.getAmount());
                    IAEItemStack rest = Platform.poweredInsert(proxy.getEnergy(), sg, request, src);
                    long remaining = rest == null ? 0 : Math.max(0, rest.getStackSize());
                    long inserted = stored.getAmount() - remaining;
                    if (inserted > 0) itemInventory.consume(identity, inserted);

                    if (Gregtech.machines.allowCribDropItems && shouldDrop && remaining > 0) {
                        long dropped = dropItemAmount(identity, remaining);
                        if (dropped > 0) itemInventory.consume(identity, dropped);
                    }
                }
            }
            if (!isFluidEmpty()) {
                IMEMonitor<IAEFluidStack> fsg = proxy.getStorage()
                    .getFluidInventory();
                for (LongFluidStore.Snapshot stored : fluidInventory.longSnapshot()) {
                    FluidStack fluidStack = stored.getIdentity();
                    IAEFluidStack request = AEApi.instance()
                        .storage()
                        .createFluidStack(fluidStack);
                    request.setStackSize(stored.getAmount());
                    IAEFluidStack rest = Platform.poweredInsert(proxy.getEnergy(), fsg, request, src);
                    long remaining = rest == null ? 0 : Math.max(0, rest.getStackSize());
                    long inserted = stored.getAmount() - remaining;
                    if (inserted > 0) fluidInventory.consume(fluidStack, inserted);

                    if (Gregtech.machines.allowCribDropItems && shouldDrop && remaining > 0) {
                        long dropped = dropFluidAmount(fluidStack, remaining);
                        if (dropped > 0) fluidInventory.consume(fluidStack, dropped);
                    }
                }
            }

            // Delete zero size stacks
            return this.isEmpty();
        }

        private void dropItem(ItemStack stack) {
            World world = parentMTE.getBaseMetaTileEntity()
                .getWorld();
            EntityItem entityItem = new EntityItem(
                world,
                parentMTE.getBaseMetaTileEntity()
                    .getXCoord() + XSTR_INSTANCE.nextFloat() * 0.8F
                    + 0.1F,
                parentMTE.getBaseMetaTileEntity()
                    .getYCoord() + XSTR_INSTANCE.nextFloat() * 0.8F
                    + 0.1F,
                parentMTE.getBaseMetaTileEntity()
                    .getZCoord() + XSTR_INSTANCE.nextFloat() * 0.8F
                    + 0.1F,
                GTUtility.copy(stack));
            entityItem.motionX = XSTR_INSTANCE.nextGaussian() * 0.05;
            entityItem.motionY = XSTR_INSTANCE.nextGaussian() * 0.25;
            entityItem.motionZ = XSTR_INSTANCE.nextGaussian() * 0.05;
            world.spawnEntityInWorld(entityItem);
        }

        private long dropItemAmount(ItemStack identity, long amount) {
            long remaining = amount;
            while (remaining > 0) {
                ItemStack dropped = identity.copy();
                dropped.stackSize = (int) Math.min(Integer.MAX_VALUE, remaining);
                dropItem(dropped);
                remaining -= dropped.stackSize;
            }
            return amount - remaining;
        }

        private boolean dropFluid(FluidStack stack) {
            ItemStack fluidPacket = ItemFluidPacket.newStack(stack);
            if (fluidPacket == null) return false;
            dropItem(fluidPacket);
            return true;
        }

        private long dropFluidAmount(FluidStack identity, long amount) {
            long remaining = amount;
            while (remaining > 0) {
                FluidStack dropped = identity.copy();
                dropped.amount = (int) Math.min(Integer.MAX_VALUE, remaining);
                if (!dropFluid(dropped)) break;
                remaining -= dropped.amount;
            }
            return amount - remaining;
        }

        public void dropRemainingContents() {
            if (!Gregtech.machines.allowCribDropItems) return;
            for (LongItemStore.Snapshot stored : itemInventory.longSnapshot()) {
                long dropped = dropItemAmount(stored.getIdentity(), stored.getAmount());
                if (dropped > 0) itemInventory.consume(stored.getIdentity(), dropped);
            }
            for (LongFluidStore.Snapshot stored : fluidInventory.longSnapshot()) {
                FluidStack identity = stored.getIdentity();
                long dropped = dropFluidAmount(identity, stored.getAmount());
                if (dropped > 0) fluidInventory.consume(identity, dropped);
            }
            updateSlotItems();
            updateSlotFluids();
        }

        public boolean insertItemsAndFluids(MEInventoryCrafting inventoryCrafting) {
            LongItemStore nextItems = new LongItemStore();
            nextItems.copyFrom(itemInventory);
            LongFluidStore nextFluids = new LongFluidStore();
            nextFluids.copyFrom(fluidInventory);
            for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
                final IAEStack<?> aes = inventoryCrafting.getAEStackInSlot(i);
                if (aes instanceof IAEFluidStack ifs) {
                    long amount = ifs.getStackSize();
                    if (amount > 0 && !nextFluids.insert(ifs.getFluidStack(), amount)) return false;
                } else if (aes instanceof IAEItemStack ais) {
                    long amount = ais.getStackSize();
                    if (amount > 0 && !nextItems.insert(ais.getItemStack(), amount)) return false;
                }
            }
            itemInventory.copyFrom(nextItems);
            fluidInventory.copyFrom(nextFluids);
            return true;
        }

        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            nbt.setTag("pattern", pattern.writeToNBT(new NBTTagCompound()));

            nbt.setTag("inventory", itemInventory.writeToNBT());

            nbt.setTag("fluidInventory", fluidInventory.writeToNBT());

            if (expandedPattern && !isEmpty() && activeExpandedDetails != null) {
                nbt.setString(NBT_ACTIVE_EXPANDED_PATTERN_ID, activeExpandedDetails.id);
            } else {
                nbt.removeTag(NBT_ACTIVE_EXPANDED_PATTERN_ID);
            }

            return nbt;
        }
    }

    // mInventory is used for storing patterns, circuit and manual slot (typically NC items)
    public static final int PATTERN_COLUMNS = 9;
    public static final int PATTERN_ROWS = 18;
    public static final int PAGE_ROWS = 6;
    public static final int PAGE_COUNT = 3;
    public static final int PATTERNS_PER_PAGE = PATTERN_COLUMNS * PAGE_ROWS;
    public static final int MAX_PATTERN_COUNT = PATTERN_COLUMNS * PATTERN_ROWS;
    private static final int PHASE_2_SCHEMA = 6;
    private static final Pattern GTNL_NAME_PREFIX = Pattern
        .compile("(?:gt_circuit_[0-9]+_)?(?:extra_start_.+?_extra_end_)?(?:extra_item_start_.+?extra_item_end_)?");
    public static final int MIN_PROGRAMMED_CIRCUIT = 1;
    public static final int MAX_PROGRAMMED_CIRCUIT = 24;
    private static final String NBT_PHASE_2_SCHEMA = "tenkeitechPhase2Schema";
    private static final String NBT_PATTERN_AUGMENTS = "patternAugments";
    private static final String NBT_SHARED_FLUID_CATALYSTS = "sharedFluidCatalysts";
    private static final String NBT_EMBEDDED_CIRCUIT_CONFIG = "embeddedCircuitConfig";
    private static final String NBT_SKIP_EXISTING_CIRCUIT_PATTERNS = "skipExistingCircuitPatterns";
    private static final String NBT_SHARED_CONSUMABLE_STOCK = "sharedConsumableStock";
    private static final String NBT_SHARED_CONSUMABLE_ITEMS = "items";
    private static final String NBT_SHARED_CONSUMABLE_FLUIDS = "fluids";
    private static final String NBT_SHARED_CONSUMABLE_REFILL_INTERVAL = "sharedConsumableRefillInterval";
    private static final String NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE = "sharedConsumableRefillAfterRecipe";
    private static final String NBT_SHARED_CONSUMABLE_REFILL_REMAINING = "sharedConsumableRefillRemaining";
    private static final String NBT_TRANSFER_PATTERNS = "patterns";
    private static final String NBT_TRANSFER_PATTERN = "pattern";
    private static final String NBT_TRANSFER_SLOT = "slot";
    private static final String NBT_TRANSFER_AUGMENTS = "augments";
    private static final String NBT_TRANSFER_AUGMENT = "augment";
    private static final String NBT_TRANSFER_HAS_CUSTOM_NAME = "hasCustomName";
    private static final String NBT_TRANSFER_CUSTOM_NAME = "customName";
    private static final String NBT_TRANSFER_HAS_SHARED_CIRCUIT = "hasSharedCircuit";
    private static final String NBT_TRANSFER_SHARED_CIRCUIT = "sharedCircuit";
    private static final String NBT_TRANSFER_SHOW_PATTERN = "showPattern";
    private static final String NBT_TRANSFER_ADDITIONAL_CONNECTION = "additionalConnection";
    private static final int SLOT_MANUAL_SIZE = 9;
    private static final int MAX_INV_COUNT = MAX_PATTERN_COUNT + SLOT_MANUAL_SIZE + 1;
    private static final int SLOT_CIRCUIT = MAX_PATTERN_COUNT;
    public static final int SLOT_MANUAL_START = SLOT_CIRCUIT + 1;
    private static final int MANUAL_SLOT_WINDOW = 10;
    private BaseActionSource requestSource = null;
    private @Nullable AENetworkProxy gridProxy = null;
    private static final ITexture SUPER_PATTERN_BUFFER_CASING = TextureFactory
        .of(Textures.BlockIcons.NEUTRONIUM_ACTIVE_CASING);

    private final List<MTESuperCraftingInputProxy> proxyHatches = new ArrayList<>();
    private final SuperPatternModeBuffer patternMode = new SuperPatternModeBuffer(this);
    private long patternModeControllersTick = Long.MIN_VALUE;
    private List<MTEMultiBlockBase> patternModeControllers = Collections.emptyList();

    // holds all internal inventories
    @SuppressWarnings("unchecked") // Java doesn't allow to create an array of a generic type.
    private final PatternSlot<MTESuperCraftingInputME>[] internalInventory = new PatternSlot[MAX_PATTERN_COUNT];
    private final PatternAugmentData[] patternAugments = createPatternAugments();

    // a hash map for faster lookup of pattern slots, not necessarily all valid.
    private final Map<ICraftingPatternDetails, List<PatternSlot<MTESuperCraftingInputME>>> patternDetailsPatternSlotMap = new HashMap<>(
        MAX_PATTERN_COUNT);

    private FluidCatalystData sharedFluidCatalysts = new FluidCatalystData();
    private final SharedConsumableItemStock sharedConsumableStock = new SharedConsumableItemStock();
    private final SharedConsumableFluidStock sharedConsumableFluidStock = new SharedConsumableFluidStock();
    private final IDualInputInventoryWithPattern sharedConsumableInventory = new SharedConsumableInventory();
    private int sharedConsumableRefillIntervalSeconds = 5;
    private boolean sharedConsumableRefillOnRecipeStart;
    private long nextSharedConsumableRefillTick = Long.MIN_VALUE;
    private long loadedSharedConsumableRefillDelay = -1;
    private boolean needPatternSync = true;
    private boolean justHadNewItems = false;
    private long lastStalePatternLogTick = -200;
    private long lastAutomaticCatalystLogTick = -200;

    private String customName = null;
    private final boolean supportFluids;
    private boolean additionalConnection = false;
    // 自动倍增样板不需要优化。
    public final boolean disablePatternOptimization = true;
    public boolean showPattern = true;
    private int embeddedCircuitConfig = MIN_PROGRAMMED_CIRCUIT;
    private boolean skipExistingCircuitPatterns = true;

    private ScheduledReason scheduledReason = ScheduledReason.UNDEFINED;

    private final class SharedConsumableInventory
        implements IDualInputInventoryWithPattern, ILongFluidInputInventory, ILongItemInputInventory {

        @Override
        public boolean isEmpty() {
            return !sharedConsumableStock.hasConsumableInputs() && !sharedConsumableFluidStock.hasConsumableInputs();
        }

        @Override
        public ItemStack[] getItemInputs() {
            return GTValues.emptyItemStackArray;
        }

        @Override
        public FluidStack[] getFluidInputs() {
            return getSharedConsumableFluids();
        }

        @Override
        public boolean ownsAllFluidInputs(GTRecipe recipe) {
            return sharedConsumableFluidStock.ownsAllFluidInputs(recipe);
        }

        @Override
        public long getLongFluidAmount(FluidStack identity) {
            return sharedConsumableFluidStock.getLongFluidAmount(identity);
        }

        @Override
        public void consumeLongFluid(FluidStack identity, long amount) {
            sharedConsumableFluidStock.consumeLongFluid(identity, amount);
        }

        @Override
        public List<LongItemStore.Snapshot> longItemSnapshot() {
            return sharedConsumableStock.longItemSnapshot();
        }

        @Override
        public long getLongItemAmount(ItemStack identity) {
            return sharedConsumableStock.getLongItemAmount(identity);
        }

        @Override
        public void consumeLongItem(ItemStack identity, long amount) {
            sharedConsumableStock.consumeLongItem(identity, amount);
        }

        @Override
        public GTDualInputPattern getPatternInputs() {
            GTDualInputPattern inputs = new GTDualInputPattern();
            inputs.inputItems = GTValues.emptyItemStackArray;
            inputs.inputFluid = GTValues.emptyFluidStackArray;
            return inputs;
        }

        @Override
        public boolean shouldBeCached() {
            return false;
        }
    }

    private void addPatternMapping(PatternSlot<MTESuperCraftingInputME> slot) {
        patternMode.invalidate();
        for (ICraftingPatternDetails details : slot.getRawRegistrationDetails()) {
            patternDetailsPatternSlotMap.computeIfAbsent(details, ignored -> new ArrayList<>())
                .add(slot);
        }
    }

    private void removePatternMapping(PatternSlot<MTESuperCraftingInputME> slot) {
        patternMode.invalidate();
        for (ICraftingPatternDetails details : slot.getRawRegistrationDetails()) {
            List<PatternSlot<MTESuperCraftingInputME>> slots = patternDetailsPatternSlotMap.get(details);
            if (slots == null) continue;
            slots.remove(slot);
            if (slots.isEmpty()) patternDetailsPatternSlotMap.remove(details);
        }
    }

    @Nullable
    private PatternSlot<MTESuperCraftingInputME> findPatternSlot(ICraftingPatternDetails details) {
        ICraftingPatternDetails lookup = details instanceof TransformedPatternDetails transformed
            ? transformed.getRawDetails()
            : details;
        List<PatternSlot<MTESuperCraftingInputME>> slots = patternDetailsPatternSlotMap.get(lookup);
        if (slots == null || slots.isEmpty()) return null;

        // AE normally returns the same details instance that was published. Preserve identity so duplicate encoded
        // patterns keep independent input buffers; equality fallback remains deterministic after a cache rebuild.
        for (PatternSlot<MTESuperCraftingInputME> slot : slots) {
            if (slot.hasRegistrationDetailsIdentity(details) && slot.canAcceptPattern(details)) return slot;
        }
        for (PatternSlot<MTESuperCraftingInputME> slot : slots) if (slot.canAcceptPattern(details)) return slot;
        return null;
    }

    @Nullable
    private PatternSlot<MTESuperCraftingInputME> createPatternSlotSafely(ItemStack pattern, NBTTagCompound nbt,
        int index) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem)
            || pattern.getItem() instanceof com.tenkeitech.item.ItemResonatingPattern) return null;
        try {
            return new PatternSlot<>(pattern, nbt, this, patternAugments[index], index);
        } catch (RuntimeException exception) {
            GTLoggers.GT_FML_LOGGER.warn(
                "Ignoring invalid TenkeiTech super pattern buffer entry in slot {}: {}",
                index,
                pattern,
                exception);
            return null;
        }
    }

    private static PatternAugmentData[] createPatternAugments() {
        PatternAugmentData[] augments = new PatternAugmentData[MAX_PATTERN_COUNT];
        Arrays.setAll(augments, ignored -> new PatternAugmentData());
        return augments;
    }

    public MTESuperCraftingInputME(int aID, String aName, String aNameRegional, boolean supportFluids) {
        super(aID, aName, aNameRegional, supportFluids ? 10 : 6, MAX_INV_COUNT, null);
        disableSort = true;
        this.supportFluids = supportFluids;
    }

    public MTESuperCraftingInputME(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures,
        boolean supportFluids) {
        super(aName, aTier, MAX_INV_COUNT, aDescription, aTextures);
        this.supportFluids = supportFluids;
        disableSort = true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESuperCraftingInputME(mName, mTier, mDescriptionArray, mTextures, supportFluids);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return getTexturesInactive(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { SUPER_PATTERN_BUFFER_CASING,
            TextureFactory.of(supportFluids ? OVERLAY_ME_CRAFTING_INPUT_BUFFER : OVERLAY_ME_CRAFTING_INPUT_BUS) };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture controllerCasing = super.getCasingTexture();
        if (controllerCasing == null)
            return super.getTexture(baseMetaTileEntity, side, facing, colorIndex, active, redstoneLevel);
        if (side != facing) return new ITexture[] { controllerCasing };
        ITexture overlay = TextureFactory
            .of(supportFluids ? OVERLAY_ME_CRAFTING_INPUT_BUFFER : OVERLAY_ME_CRAFTING_INPUT_BUS);
        return new ITexture[] { controllerCasing, overlay };
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        super.onPostTick(aBaseMetaTileEntity, aTimer);

        if (getBaseMetaTileEntity().isServerSide()) {
            boolean sharedConsumableChanged = sharedConsumableStock.observeBackingChanges();
            sharedConsumableChanged |= sharedConsumableFluidStock.observeBackingChanges();
            if (sharedConsumableChanged) markDirty();
            tickSharedConsumableRefill(aTimer);
            patternMode.tick();
            if (needPatternSync && aTimer % 10 == 0) {
                needPatternSync = !postMEPatternChange();
            }
            if (aTimer % 20 == 0) {
                getBaseMetaTileEntity().setActive(isActive());
            }
            if (justHadNewItems) {
                for (IHatchWatcher watcher : watchers) {
                    watcher.scheduleRecipeCheckImmediate();
                }
                for (MTESuperCraftingInputProxy slave : proxyHatches) {
                    slave.onParentInvChange();
                }
                justHadNewItems = false;
            }
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public void onColorChangeServer(byte aColor) {
        updateAE2ProxyColor();
    }

    public SuperPatternModeBuffer getPatternMode() {
        return patternMode;
    }

    public List<PatternSlot<MTESuperCraftingInputME>> getPatternModeSlots() {
        return Arrays.stream(internalInventory)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public void invalidatePatternModeControllers() {
        patternModeControllersTick = Long.MIN_VALUE;
    }

    /** Machine identities for client-side NEI category lookup; no recipe or mode inspection. */
    public List<Integer> getUploadMachineIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (MTEMultiBlockBase controller : getAllAssociatedPatternModeControllers()) {
            ids.add(
                controller.getBaseMetaTileEntity()
                    .getMetaTileID());
        }
        return new ArrayList<>(ids);
    }

    public List<MTEMultiBlockBase> getPatternModeControllers() {
        long tick = getBaseMetaTileEntity().getWorld()
            .getTotalWorldTime();
        if (patternModeControllersTick == tick) return patternModeControllers;

        Set<MTEMultiBlockBase> controllers = collectDirectPatternModeControllers(true);
        if (controllers.isEmpty()) controllers = collectProxyPatternModeControllers(true);
        patternModeControllers = new ArrayList<>(controllers);
        patternModeControllersTick = tick;
        return patternModeControllers;
    }

    private List<MTEMultiBlockBase> getAllAssociatedPatternModeControllers() {
        Set<MTEMultiBlockBase> controllers = collectDirectPatternModeControllers(false);
        controllers.addAll(collectProxyPatternModeControllers(false));
        return new ArrayList<>(controllers);
    }

    private Set<MTEMultiBlockBase> collectDirectPatternModeControllers(boolean requireFormed) {
        Set<MTEMultiBlockBase> controllers = new LinkedHashSet<>();
        for (IHatchWatcher watcher : watchers) {
            if (watcher instanceof MTEMultiBlockBase controller
                && isAvailablePatternModeController(controller, requireFormed)) controllers.add(controller);
        }
        return controllers;
    }

    private Set<MTEMultiBlockBase> collectProxyPatternModeControllers(boolean requireFormed) {
        Set<MTEMultiBlockBase> controllers = new LinkedHashSet<>();
        for (MTESuperCraftingInputProxy proxy : getProxyHatches()) {
            controllers.addAll(collectProxyPatternModeControllers(proxy, requireFormed));
        }
        return controllers;
    }

    private Set<MTEMultiBlockBase> collectProxyPatternModeControllers(MTESuperCraftingInputProxy proxy,
        boolean requireFormed) {
        Set<MTEMultiBlockBase> controllers = new LinkedHashSet<>();
        if (SuperPatternModeBuffer.isLoaded(proxy)) proxy.collectPatternModeControllers(controllers);
        controllers.removeIf(controller -> !isAvailablePatternModeController(controller, requireFormed));
        return controllers;
    }

    private static boolean isAvailablePatternModeController(MTEMultiBlockBase controller, boolean requireFormed) {
        return controller != null && (!requireFormed || controller.mMachine)
            && SuperPatternModeBuffer.isLoaded(controller);
    }

    public void addProxyHatch(MTESuperCraftingInputProxy proxy) {
        invalidatePatternModeControllers();
        if (!proxyHatches.contains(proxy)) proxyHatches.add(proxy);
    }

    public void removeProxyHatch(MTESuperCraftingInputProxy proxy) {
        invalidatePatternModeControllers();
        proxyHatches.remove(proxy);
    }

    public void scheduleProxyRecipeChecks() {
        for (MTESuperCraftingInputProxy proxy : getProxyHatches()) {
            if (SuperPatternModeBuffer.isLoaded(proxy)) proxy.onParentInvChange();
        }
    }

    public List<MTESuperCraftingInputProxy> getProxyHatches() {
        validateProxyHatchList();
        return Collections.unmodifiableList(proxyHatches);
    }

    private long lastProxyHatchValidationTime = -1;

    private void validateProxyHatchList() {
        long currentTime = getBaseMetaTileEntity().getTimer();
        if (currentTime != lastProxyHatchValidationTime) {
            proxyHatches
                .removeIf(hatch -> hatch == null || hatch.getBaseMetaTileEntity() == null || hatch.getMaster() != this);
            lastProxyHatchValidationTime = currentTime;
        }
    }

    public void updateAE2ProxyColor() {
        AENetworkProxy proxy = getProxy();
        byte color = this.getColor();
        if (color == -1) {
            proxy.setColor(AEColor.Transparent);
        } else {
            proxy.setColor(AEColor.values()[Dyes.transformDyeIndex(color)]);
        }
        if (proxy.getNode() != null) {
            proxy.getNode()
                .updateState();
        }
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return isOutputFacing(forgeDirection) ? AECableType.SMART : AECableType.NONE;
    }

    private void updateValidGridProxySides() {
        if (additionalConnection) {
            getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
        } else {
            getProxy().setValidSides(EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
        }
    }

    @Override
    public void onFacingChange() {
        updateValidGridProxySides();
    }

    @Override
    public void securityBreak() {}

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (aPlayer.isSneaking()) {
            IGregTechTileEntity te = getBaseMetaTileEntity();
            aPlayer.openGui(
                AppEng.instance(),
                GuiBridge.GUI_RENAMER.ordinal() << 5 | (side.ordinal()),
                te.getWorld(),
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
            return true;
        }

        additionalConnection = !additionalConnection;
        updateValidGridProxySides();
        aPlayer.addChatComponentMessage(
            new ChatComponentTranslation("GT5U.hatch.additionalConnection." + additionalConnection));
        return true;
    }

    @Override
    public boolean connectsToAllSides() {
        return additionalConnection;
    }

    @Override
    public void setConnectsToAllSides(boolean connects) {
        additionalConnection = connects;
        updateValidGridProxySides();
    }

    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            gridProxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            updateValidGridProxySides();
        }

        return this.gridProxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(
            getBaseMetaTileEntity().getWorld(),
            getBaseMetaTileEntity().getXCoord(),
            getBaseMetaTileEntity().getYCoord(),
            getBaseMetaTileEntity().getZCoord());
    }

    @Override
    public int rows() {
        return PATTERN_ROWS;
    }

    @Override
    public int rowSize() {
        return 9;
    }

    @Override
    public int numSlots() {
        return MAX_PATTERN_COUNT;
    }

    @Override
    public IInventory getPatterns() {
        return this;
    }

    @Override
    public String getName() {
        if (hasCustomName()) {
            return getCustomName();
        }
        StringBuilder name = new StringBuilder();
        if (getCrafterIcon() != null) {
            name.append(getCrafterIcon().getDisplayName());
        } else {
            name.append(getLocalName());
        }

        IChatComponent suffix = getNameSuffix();
        if (suffix != null) name.append(suffix.getUnformattedText());
        return name.toString();
    }

    @Override
    public String getRawName() {
        if (hasCustomName()) {
            return getCustomName();
        }

        if (getCrafterIcon() != null) {
            return getCrafterIcon().getUnlocalizedName();
        } else {
            return getLocalName();
        }
    }

    @Override
    public IChatComponent getNameSuffix() {
        if (hasCustomName()) return null;

        IGregTechTileEntity base = getBaseMetaTileEntity();
        IChatComponent suffix = base instanceof IInterfaceNameProvider provider ? provider.getInterfaceNameSuffix()
            : null;

        List<ItemStack> manualSlots = new ArrayList<>();
        for (int i = SLOT_MANUAL_START; i < SLOT_MANUAL_START + SLOT_MANUAL_SIZE; i++) {
            if (mInventory[i] != null) {
                manualSlots.add(mInventory[i]);
            }
        }
        if (!manualSlots.isEmpty()) {
            IChatComponent manualSuffix = new ChatComponentNonConsumedItemsSuffix(manualSlots);
            suffix = suffix == null ? manualSuffix
                : suffix.createCopy()
                    .appendSibling(manualSuffix);
        }

        return suffix;
    }

    @Override
    public TileEntity getTileEntity() {
        return (TileEntity) getBaseMetaTileEntity();
    }

    @Override
    public boolean shouldDisplay() {
        return showPattern;
    }

    @Override
    public boolean allowsPatternOptimization() {
        return false;
    }

    @Override
    public IAEStackType<?>[] getSupportedStackTypes() {
        if (supportFluids) {
            return new IAEStackType<?>[] { ITEM_STACK_TYPE, FLUID_STACK_TYPE };
        }
        return new IAEStackType<?>[] { ITEM_STACK_TYPE };
    }

    @Override
    public ItemStack getSelfRep() {
        return this.getStackForm(1);
    }

    @Override
    public void gridChanged() {
        needPatternSync = true;
    }

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);

        aNBT.setInteger(NBT_PHASE_2_SCHEMA, PHASE_2_SCHEMA);

        // save internalInventory
        NBTTagList internalInventoryNBT = new NBTTagList();
        for (int i = 0; i < internalInventory.length; i++) {
            if (internalInventory[i] != null) {
                NBTTagCompound internalInventorySlotNBT = new NBTTagCompound();
                internalInventorySlotNBT.setInteger("patternSlot", i);
                internalInventorySlotNBT
                    .setTag("patternSlotNBT", internalInventory[i].writeToNBT(new NBTTagCompound()));
                internalInventoryNBT.appendTag(internalInventorySlotNBT);
            }
        }
        aNBT.setTag("internalInventory", internalInventoryNBT);

        NBTTagList patternAugmentNBT = new NBTTagList();
        for (int i = 0; i < patternAugments.length; i++) {
            if (patternAugments[i].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("patternSlot", i);
            entry.setTag("augment", patternAugments[i].writeToNBT(new NBTTagCompound()));
            patternAugmentNBT.appendTag(entry);
        }
        if (patternAugmentNBT.tagCount() > 0) aNBT.setTag(NBT_PATTERN_AUGMENTS, patternAugmentNBT);

        if (!sharedFluidCatalysts.isEmpty()) {
            aNBT.setTag(NBT_SHARED_FLUID_CATALYSTS, sharedFluidCatalysts.writeToNBT());
        }
        if (customName != null) aNBT.setString("customName", customName);
        aNBT.setBoolean("additionalConnection", additionalConnection);
        aNBT.setBoolean("disablePatternOptimization", disablePatternOptimization);
        aNBT.setBoolean("showPattern", showPattern);
        aNBT.setInteger(NBT_EMBEDDED_CIRCUIT_CONFIG, embeddedCircuitConfig);
        aNBT.setBoolean(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS, skipExistingCircuitPatterns);
        aNBT.setTag(NBT_SHARED_CONSUMABLE_STOCK, writeSharedConsumableStock(true));
        aNBT.setInteger(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL, sharedConsumableRefillIntervalSeconds);
        aNBT.setBoolean(NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE, sharedConsumableRefillOnRecipeStart);
        long timer = getBaseMetaTileEntity() == null ? 0 : getBaseMetaTileEntity().getTimer();
        long remaining = nextSharedConsumableRefillTick == Long.MIN_VALUE ? getSharedConsumableRefillIntervalTicks()
            : Math.max(0, nextSharedConsumableRefillTick - timer);
        aNBT.setLong(NBT_SHARED_CONSUMABLE_REFILL_REMAINING, remaining);
        getProxy().writeToNBT(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        int phase2Schema = aNBT.getInteger(NBT_PHASE_2_SCHEMA);
        if (phase2Schema != PHASE_2_SCHEMA) {
            GTLoggers.GT_FML_LOGGER.warn(
                "Ignoring unsupported TenkeiTech super pattern buffer schema {}; expected {}",
                phase2Schema,
                PHASE_2_SCHEMA);
            aNBT = new NBTTagCompound();
        }
        sharedFluidCatalysts = FluidCatalystData.readFromNBT(aNBT, NBT_SHARED_FLUID_CATALYSTS);
        PatternAugmentData emptyAugment = new PatternAugmentData();
        for (PatternAugmentData patternAugment : patternAugments) patternAugment.copyFrom(emptyAugment);
        boolean[] loadedAugments = new boolean[MAX_PATTERN_COUNT];
        NBTTagList patternAugmentNBT = aNBT.getTagList(NBT_PATTERN_AUGMENTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < patternAugmentNBT.tagCount(); i++) {
            NBTTagCompound entry = patternAugmentNBT.getCompoundTagAt(i);
            int patternSlot = entry.getInteger("patternSlot");
            if (patternSlot < 0 || patternSlot >= MAX_PATTERN_COUNT) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Ignoring out-of-range TenkeiTech catalyst slot {} (valid range: 0-{})",
                    patternSlot,
                    MAX_PATTERN_COUNT - 1);
                continue;
            }
            if (loadedAugments[patternSlot]) {
                GTLoggers.GT_FML_LOGGER
                    .warn("Ignoring duplicate TenkeiTech catalyst NBT entry for slot {}", patternSlot);
                continue;
            }
            patternAugments[patternSlot]
                .copyFrom(PatternAugmentData.readFromNBT(entry.getCompoundTag("augment"), patternSlot));
            loadedAugments[patternSlot] = true;
        }
        // load internalInventory
        Arrays.fill(internalInventory, null);
        NBTTagList internalInventoryNBT = aNBT.getTagList("internalInventory", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < internalInventoryNBT.tagCount(); i++) {
            NBTTagCompound internalInventorySlotNBT = internalInventoryNBT.getCompoundTagAt(i);
            int patternSlot = internalInventorySlotNBT.getInteger("patternSlot");
            if (patternSlot < 0 || patternSlot >= MAX_PATTERN_COUNT) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Ignoring out-of-range TenkeiTech super pattern buffer slot {} (valid range: 0-{})",
                    patternSlot,
                    MAX_PATTERN_COUNT - 1);
                continue;
            }
            if (internalInventory[patternSlot] != null) {
                GTLoggers.GT_FML_LOGGER
                    .warn("Ignoring duplicate TenkeiTech super pattern buffer NBT entry for slot {}", patternSlot);
                continue;
            }
            NBTTagCompound patternSlotNBT = internalInventorySlotNBT.getCompoundTag("patternSlotNBT");
            ItemStack pattern = ItemStack.loadItemStackFromNBT(patternSlotNBT.getCompoundTag("pattern"));
            PatternSlot<MTESuperCraftingInputME> loadedSlot = createPatternSlotSafely(
                pattern,
                patternSlotNBT,
                patternSlot);
            if (loadedSlot != null) {
                internalInventory[patternSlot] = loadedSlot;
            } else {
                GTLoggers.GT_FML_LOGGER.warn(
                    "An error occurred while loading contents of ME Crafting Input Bus. This pattern has been voided: {}",
                    patternSlotNBT);
            }
        }

        // reconstruct patternDetailsPatternSlotMap
        patternDetailsPatternSlotMap.clear();
        for (PatternSlot<MTESuperCraftingInputME> patternSlot : internalInventory) {
            if (patternSlot != null) addPatternMapping(patternSlot);
        }

        if (aNBT.hasKey("customName")) customName = aNBT.getString("customName");
        additionalConnection = aNBT.getBoolean("additionalConnection");
        if (aNBT.hasKey("showPattern")) showPattern = aNBT.getBoolean("showPattern");
        embeddedCircuitConfig = aNBT.hasKey(NBT_EMBEDDED_CIRCUIT_CONFIG)
            ? clampProgrammedCircuit(aNBT.getInteger(NBT_EMBEDDED_CIRCUIT_CONFIG))
            : MIN_PROGRAMMED_CIRCUIT;
        skipExistingCircuitPatterns = !aNBT.hasKey(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS)
            || aNBT.getBoolean(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS);
        sharedConsumableStock.clear();
        sharedConsumableFluidStock.clear();
        if (aNBT.hasKey(NBT_SHARED_CONSUMABLE_STOCK)) {
            if (!aNBT.hasKey(NBT_SHARED_CONSUMABLE_STOCK, Constants.NBT.TAG_COMPOUND)
                || !readSharedConsumableStock(aNBT.getCompoundTag(NBT_SHARED_CONSUMABLE_STOCK), true, false)) {
                GTLoggers.GT_FML_LOGGER.warn("Loaded invalid TenkeiTech shared consumable stock data");
            }
        }
        sharedConsumableRefillIntervalSeconds = aNBT
            .hasKey(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL, Constants.NBT.TAG_INT)
                ? Math.max(0, aNBT.getInteger(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL))
                : 5;
        sharedConsumableRefillOnRecipeStart = aNBT.getBoolean(NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE);
        loadedSharedConsumableRefillDelay = aNBT.hasKey(NBT_SHARED_CONSUMABLE_REFILL_REMAINING, Constants.NBT.TAG_LONG)
            ? Math.max(0, aNBT.getLong(NBT_SHARED_CONSUMABLE_REFILL_REMAINING))
            : getSharedConsumableRefillIntervalTicks();
        nextSharedConsumableRefillTick = Long.MIN_VALUE;

        if (aNBT.hasKey("proxy")) getProxy().readFromNBT(aNBT);
        updateAE2ProxyColor();

        // Sync inventories to ensure that the real inventory matches what AE2 is seeing.
        for (int i = 0; i < MAX_PATTERN_COUNT; i++) {
            if (internalInventory[i] == null) continue;
            mInventory[i] = internalInventory[i].pattern;
        }
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    private String describePattern(ICraftingPatternDetails patternDetails) {
        return Arrays.stream(patternDetails.getCondensedOutputs())
            .map(
                aeItemStack -> aeItemStack.getItem()
                    .getItemStackDisplayName(aeItemStack.getItemStack()))
            .collect(Collectors.joining(", "));
    }

    @Override
    public String[] getInfoData() {
        List<String> ret = new ArrayList<>();
        ret.add(
            (getProxy() != null && getProxy().isActive()) ? "GT5U.infodata.hatch.crafting_input_me.bus.online"
                : IGregTechDeviceInformation
                    .encode("GT5U.infodata.hatch.crafting_input_me.bus.offline", getAEDiagnostics()));
        ret.add("GT5U.infodata.hatch.crafting_input_me.show_pattern." + (showPattern ? "enable" : "disabled"));
        ret.add(IGregTechDeviceInformation.encode("tenkeitech.patternmode.status", patternMode.status()));
        ret.add("GT5U.infodata.hatch.internal_inventory");
        int i = 0;
        for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
            if (slot == null) continue;
            if (slot.getPatternDetails() == null) continue;
            IWideReadableNumberConverter nc = ReadableNumberConverter.INSTANCE;

            i += 1;
            ret.add(
                IGregTechDeviceInformation.encode(
                    "GT5U.infodata.hatch.internal_inventory.slot",
                    i,
                    EnumChatFormatting.BLUE + describePattern(slot.getPatternDetails()) + EnumChatFormatting.RESET));
            for (LongItemStore.Snapshot stored : slot.itemInventory.longSnapshot()) {
                ItemStack item = stored.getIdentity();
                long amount = stored.getAmount();
                ret.add(
                    item.getItem()
                        .getItemStackDisplayName(item) + ": "
                        + EnumChatFormatting.GOLD
                        + nc.toWideReadableForm(amount)
                        + EnumChatFormatting.RESET);
            }
            for (LongFluidStore.Snapshot stored : slot.fluidInventory.longSnapshot()) {
                FluidStack fluid = stored.getIdentity();
                long amount = stored.getAmount();
                ret.add(
                    fluid.getLocalizedName() + ": "
                        + EnumChatFormatting.AQUA
                        + nc.toWideReadableForm(amount)
                        + EnumChatFormatting.RESET);
            }
        }
        return ret.toArray(new String[0]);
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
    public int getCircuitSlot() {
        return SLOT_CIRCUIT;
    }

    @Override
    public int getCircuitSlotX() {
        return 170;
    }

    @Override
    public int getCircuitSlotY() {
        return 64;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return true;
    }

    @Override
    public int getGUIWidth() {
        return 176 + 16;
    }

    @Override
    public int getGUIHeight() {
        return 166;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTESuperCraftingInputMEGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.@NotNull Builder builder, UIBuildContext buildContext) {
        buildContext.addSyncedWindow(MANUAL_SLOT_WINDOW, this::createSlotManualWindow);
        builder.widget(
            SlotGroup.ofItemHandler(inventoryHandler, 9)
                .startFromSlot(0)
                .endAtSlot(MAX_PATTERN_COUNT - 1)
                .phantom(false)
                .background(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_PATTERN_ME)
                .widgetCreator(slot -> new SlotWidget(slot) {

                    @Override
                    protected ItemStack getItemStackForRendering(Slot slotIn) {
                        ItemStack stack = slot.getStack();
                        if (stack == null || !(stack.getItem() instanceof ItemEncodedPattern patternItem)) {
                            return stack;
                        }
                        ItemStack output = patternItem.getOutput(stack);
                        return output != null ? output : stack;
                    }
                }.setFilter(itemStack -> itemStack.getItem() instanceof ICraftingPatternItem)
                    .setChangeListener(() -> onPatternChange(slot.getSlotIndex(), slot.getStack())))
                .build()
                .setPos(7, 9))
            .widget(new ButtonWidget().setOnClick((clickData, widget) -> {
                if (clickData.mouseButton == 0) {
                    widget.getContext()
                        .openSyncedWindow(MANUAL_SLOT_WINDOW);
                }
            })
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_PLUS_LARGE)
                .addTooltip(
                    StatCollector.translateToLocal("GT5U.gui.tooltip.hatch.crafting_input_me.place_manual_items"))
                .setSize(16, 16)
                .setPos(170, 46))
            .widget(new ButtonWidget().setOnClick((clickData, widget) -> {
                if (clickData.mouseButton == 0) {
                    refundAll(false);
                }
            })
                .setPlayClickSound(true)
                .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_EXPORT)
                .addTooltip(StatCollector.translateToLocal("GT5U.gui.tooltip.hatch.crafting_input_me.export"))
                .setSize(16, 16)
                .setPos(170, 28))
            .widget(
                new ButtonWidget()
                    .setBackground(
                        GTUITextures.BUTTON_STANDARD_DISABLED,
                        GTUITextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE,
                        GTUITextures.OVERLAY_BUTTON_FORBIDDEN)
                    .addTooltip(
                        "Pattern Optimization:\n§7Disabled\n§7"
                            + StatCollector.translateToLocal("tenkeitech.gui.pattern_optimization.disabled_reason"))
                    .setPos(170, 10)
                    .setSize(16, 16))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> showPattern = !showPattern)
                    .setPlayClickSoundResource(
                        () -> showPattern ? SoundResource.GUI_BUTTON_UP.resourceLocation
                            : SoundResource.GUI_BUTTON_DOWN.resourceLocation)
                    .setBackground(() -> {
                        if (showPattern) {
                            return new IDrawable[] { GTUITextures.BUTTON_STANDARD_PRESSED,
                                GTUITextures.OVERLAY_BUTTON_WHITELIST };
                        } else {
                            return new IDrawable[] { GTUITextures.BUTTON_STANDARD,
                                GTUITextures.OVERLAY_BUTTON_BLACKLIST };
                        }
                    })
                    .attachSyncer(
                        new FakeSyncWidget.BooleanSyncer(() -> showPattern, val -> showPattern = val),
                        builder)
                    .dynamicTooltip(
                        () -> Collections.singletonList(
                            StatCollector.translateToLocal(
                                "GT5U.infodata.hatch.crafting_input_me.show_pattern."
                                    + (showPattern ? "enable" : "disabled"))))
                    .setTooltipShowUpDelay(TOOLTIP_DELAY)
                    .setUpdateTooltipEveryTick(true)
                    .setPos(194, 28)
                    .setSize(16, 16));
    }

    @Override
    public void updateSlots() {
        for (int slotId = SLOT_MANUAL_START; slotId < SLOT_MANUAL_START + SLOT_MANUAL_SIZE; ++slotId) {
            if (mInventory[slotId] != null && mInventory[slotId].stackSize <= 0) mInventory[slotId] = null;
        }
    }

    private BaseActionSource getRequest() {
        if (requestSource == null) requestSource = new MachineSource((IActionHost) getBaseMetaTileEntity());
        return requestSource;
    }

    private NBTTagCompound writeSharedConsumableStock(boolean includeStored) {
        NBTTagCompound stock = createEmptySharedConsumableStock();
        stock.setTag(NBT_SHARED_CONSUMABLE_ITEMS, sharedConsumableStock.writeToNBT(includeStored));
        stock.setTag(NBT_SHARED_CONSUMABLE_FLUIDS, sharedConsumableFluidStock.writeToNBT(includeStored));
        return stock;
    }

    private static NBTTagCompound createEmptySharedConsumableStock() {
        NBTTagCompound stock = new NBTTagCompound();
        stock.setTag(NBT_SHARED_CONSUMABLE_ITEMS, new SharedConsumableItemStock().writeToNBT(false));
        stock.setTag(NBT_SHARED_CONSUMABLE_FLUIDS, new SharedConsumableFluidStock().writeToNBT(false));
        return stock;
    }

    private boolean readSharedConsumableStock(NBTTagCompound stock, boolean includeStored, boolean strict) {
        boolean hasItems = stock != null && stock.hasKey(NBT_SHARED_CONSUMABLE_ITEMS, Constants.NBT.TAG_COMPOUND);
        boolean hasFluids = stock != null && stock.hasKey(NBT_SHARED_CONSUMABLE_FLUIDS, Constants.NBT.TAG_COMPOUND);
        if (strict && (!hasItems || !hasFluids)) return false;

        SharedConsumableItemStock nextItems = new SharedConsumableItemStock();
        SharedConsumableFluidStock nextFluids = new SharedConsumableFluidStock();
        boolean valid = hasItems
            && nextItems.readFromNBT(stock.getCompoundTag(NBT_SHARED_CONSUMABLE_ITEMS), includeStored, strict);
        valid &= hasFluids
            && nextFluids.readFromNBT(stock.getCompoundTag(NBT_SHARED_CONSUMABLE_FLUIDS), includeStored, strict);
        if (strict && !valid) return false;

        sharedConsumableStock.clear();
        sharedConsumableFluidStock.clear();
        sharedConsumableStock.readFromNBT(nextItems.writeToNBT(includeStored), includeStored, true);
        sharedConsumableFluidStock.readFromNBT(nextFluids.writeToNBT(includeStored), includeStored, true);
        return valid;
    }

    public ItemStack getSharedConsumableConfig(int slot) {
        return sharedConsumableStock.getConfig(slot);
    }

    public ItemStack getSharedConsumableStored(int slot) {
        return sharedConsumableStock.getStoredCopy(slot);
    }

    public long getSharedConsumableTarget(int slot) {
        return sharedConsumableStock.getTarget(slot);
    }

    public long getSharedConsumableStoredAmount(int slot) {
        return sharedConsumableStock.getStoredAmount(slot);
    }

    public boolean isValidSharedConsumableConfig(int slot, ItemStack stack) {
        return slot >= 0 && slot < SharedConsumableItemStock.SLOT_COUNT
            && stack != null
            && stack.getItem() != null
            && stack.stackSize > 0
            && !GTUtility.isAnyIntegratedCircuit(stack)
            && !sharedConsumableStock.containsConfig(stack, slot);
    }

    /** Changes one ghost configuration after returning only the real stock owned by that configuration slot. */
    public boolean setSharedConsumableConfig(int slot, @Nullable ItemStack config) {
        if (!isServerMachine() || slot < 0 || slot >= SharedConsumableItemStock.SLOT_COUNT) return false;
        ItemStack current = sharedConsumableStock.getConfig(slot);
        ItemStack normalized = config == null || config.getItem() == null || config.stackSize <= 0 ? null
            : config.copy();
        if (normalized != null) normalized.stackSize = 1;
        if (ItemStack.areItemStacksEqual(current, normalized)) return true;
        if (normalized != null && !isValidSharedConsumableConfig(slot, normalized)) return false;
        long storedAmount = sharedConsumableStock.getStoredAmount(slot);
        if (storedAmount > 0 && !refundSharedConsumableSlotAmount(slot, storedAmount)) return false;
        sharedConsumableStock.setConfig(slot, normalized);
        invalidateSharedConsumableMatchingContext();
        resetSharedConsumableRefillTimer();
        return true;
    }

    /** Low targets return the surplus first; a partial AE insertion leaves the previous target in force. */
    public boolean setSharedConsumableTarget(int slot, long target) {
        if (!isServerMachine() || slot < 0
            || slot >= SharedConsumableItemStock.SLOT_COUNT
            || target <= 0
            || sharedConsumableStock.getConfig(slot) == null) return false;
        long currentTarget = sharedConsumableStock.getTarget(slot);
        if (currentTarget == target) return true;
        long storedAmount = sharedConsumableStock.getStoredAmount(slot);
        if (storedAmount > target && !refundSharedConsumableSlotAmount(slot, storedAmount - target)) return false;
        sharedConsumableStock.setTarget(slot, target);
        resetSharedConsumableRefillTimer();
        markDirty();
        return true;
    }

    public FluidStack getSharedConsumableFluidConfig(int slot) {
        return sharedConsumableFluidStock.getConfig(slot);
    }

    public FluidStack getSharedConsumableStoredFluid(int slot) {
        return sharedConsumableFluidStock.getStoredCopy(slot);
    }

    public long getSharedConsumableFluidTarget(int slot) {
        return sharedConsumableFluidStock.getTarget(slot);
    }

    public long getSharedConsumableStoredFluidAmount(int slot) {
        return sharedConsumableFluidStock.getStoredAmount(slot);
    }

    public boolean isValidSharedConsumableFluidConfig(int slot, FluidStack stack) {
        return slot >= 0 && slot < SharedConsumableFluidStock.SLOT_COUNT
            && stack != null
            && stack.getFluid() != null
            && stack.amount > 0
            && !sharedConsumableFluidStock.containsConfig(stack, slot);
    }

    /** Changes one fluid identity after returning only the real stock owned by that configuration slot. */
    public boolean setSharedConsumableFluidConfig(int slot, @Nullable FluidStack config) {
        if (!isServerMachine() || slot < 0 || slot >= SharedConsumableFluidStock.SLOT_COUNT) return false;
        FluidStack current = sharedConsumableFluidStock.getConfig(slot);
        FluidStack normalized = config == null || config.getFluid() == null || config.amount <= 0 ? null
            : config.copy();
        if (normalized != null) normalized.amount = 1;
        if ((current == null && normalized == null)
            || (current != null && normalized != null && GTUtility.areFluidsEqual(current, normalized))) return true;
        if (normalized != null && !isValidSharedConsumableFluidConfig(slot, normalized)) return false;
        long storedAmount = sharedConsumableFluidStock.getStoredAmount(slot);
        if (storedAmount > 0 && !refundSharedConsumableFluidSlotAmount(slot, storedAmount)) return false;
        sharedConsumableFluidStock.setConfig(slot, normalized);
        invalidateSharedConsumableMatchingContext();
        resetSharedConsumableRefillTimer();
        return true;
    }

    public boolean setSharedConsumableFluidTarget(int slot, long target) {
        if (!isServerMachine() || slot < 0
            || slot >= SharedConsumableFluidStock.SLOT_COUNT
            || target <= 0
            || sharedConsumableFluidStock.getConfig(slot) == null) return false;
        long currentTarget = sharedConsumableFluidStock.getTarget(slot);
        if (currentTarget == target) return true;
        long storedAmount = sharedConsumableFluidStock.getStoredAmount(slot);
        if (storedAmount > target && !refundSharedConsumableFluidSlotAmount(slot, storedAmount - target)) return false;
        sharedConsumableFluidStock.setTarget(slot, target);
        resetSharedConsumableRefillTimer();
        markDirty();
        return true;
    }

    public int getSharedConsumableRefillIntervalSeconds() {
        return sharedConsumableRefillIntervalSeconds;
    }

    public void setSharedConsumableRefillIntervalSeconds(int seconds) {
        int normalized = Math.max(0, seconds);
        if (sharedConsumableRefillIntervalSeconds == normalized) return;
        sharedConsumableRefillIntervalSeconds = normalized;
        resetSharedConsumableRefillTimer();
        markDirty();
    }

    public boolean isSharedConsumableRefillOnRecipeStart() {
        return sharedConsumableRefillOnRecipeStart;
    }

    public void setSharedConsumableRefillOnRecipeStart(boolean enabled) {
        if (sharedConsumableRefillOnRecipeStart == enabled) return;
        sharedConsumableRefillOnRecipeStart = enabled;
        markDirty();
    }

    private long getSharedConsumableRefillIntervalTicks() {
        return (long) sharedConsumableRefillIntervalSeconds * 20L;
    }

    public int getSharedConsumableRefillRemainingSeconds() {
        long interval = getSharedConsumableRefillIntervalTicks();
        if (interval <= 0) return -1;
        long remaining;
        if (nextSharedConsumableRefillTick == Long.MIN_VALUE) {
            remaining = loadedSharedConsumableRefillDelay >= 0 ? Math.min(loadedSharedConsumableRefillDelay, interval)
                : interval;
        } else {
            IGregTechTileEntity tile = getBaseMetaTileEntity();
            remaining = tile == null ? interval : Math.max(0, nextSharedConsumableRefillTick - tile.getTimer());
        }
        return (int) Math.min(Integer.MAX_VALUE, (remaining + 19L) / 20L);
    }

    private void tickSharedConsumableRefill(long timer) {
        long interval = getSharedConsumableRefillIntervalTicks();
        if (interval <= 0) {
            nextSharedConsumableRefillTick = Long.MIN_VALUE;
            loadedSharedConsumableRefillDelay = -1;
            return;
        }
        if (nextSharedConsumableRefillTick == Long.MIN_VALUE) {
            long delay = loadedSharedConsumableRefillDelay >= 0 ? loadedSharedConsumableRefillDelay : interval;
            nextSharedConsumableRefillTick = timer + Math.min(delay, interval);
            loadedSharedConsumableRefillDelay = -1;
        }
        if (timer < nextSharedConsumableRefillTick) return;
        refillSharedConsumables();
        nextSharedConsumableRefillTick = timer + interval;
    }

    private void resetSharedConsumableRefillTimer() {
        long interval = getSharedConsumableRefillIntervalTicks();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        nextSharedConsumableRefillTick = interval <= 0 || tile == null ? Long.MIN_VALUE : tile.getTimer() + interval;
        loadedSharedConsumableRefillDelay = -1;
    }

    /** Pulls actual, powered item and fluid stacks from AE until every configured slot reaches its target. */
    private void refillSharedConsumables() {
        if (!isServerMachine() || !isActive()
            || (!sharedConsumableStock.hasConfiguration() && !sharedConsumableFluidStock.hasConfiguration())) return;
        boolean changed = false;
        try {
            IMEMonitor<IAEItemStack> itemStorage = getProxy().getStorage()
                .getItemInventory();
            for (int slot = 0; slot < SharedConsumableItemStock.SLOT_COUNT; slot++) {
                ItemStack config = sharedConsumableStock.getConfig(slot);
                long target = sharedConsumableStock.getTarget(slot);
                long deficit = target - sharedConsumableStock.getStoredAmount(slot);
                if (config == null || deficit <= 0) continue;
                IAEItemStack request = AEApi.instance()
                    .storage()
                    .createItemStack(config);
                request.setStackSize(deficit);
                IAEItemStack extracted = Platform
                    .poweredExtraction(getProxy().getEnergy(), itemStorage, request, getRequest());
                if (extracted == null || extracted.getStackSize() <= 0) continue;
                if (!sharedConsumableStock.addExtracted(slot, config, extracted.getStackSize())) {
                    Platform.poweredInsert(getProxy().getEnergy(), itemStorage, extracted, getRequest());
                    continue;
                }
                changed = true;
            }

            IMEMonitor<IAEFluidStack> fluidStorage = getProxy().getStorage()
                .getFluidInventory();
            for (int slot = 0; slot < SharedConsumableFluidStock.SLOT_COUNT; slot++) {
                FluidStack config = sharedConsumableFluidStock.getConfig(slot);
                long target = sharedConsumableFluidStock.getTarget(slot);
                long deficit = target - sharedConsumableFluidStock.getStoredAmount(slot);
                if (config == null || deficit <= 0) continue;
                IAEFluidStack request = AEApi.instance()
                    .storage()
                    .createFluidStack(config);
                request.setStackSize(deficit);
                IAEFluidStack extracted = Platform
                    .poweredExtraction(getProxy().getEnergy(), fluidStorage, request, getRequest());
                if (extracted == null || extracted.getStackSize() <= 0) continue;
                if (!sharedConsumableFluidStock.addExtracted(slot, config, extracted.getStackSize())) {
                    Platform.poweredInsert(getProxy().getEnergy(), fluidStorage, extracted, getRequest());
                    continue;
                }
                changed = true;
            }
        } catch (GridAccessException ignored) {
            // The next configured refill attempt will retry after the grid becomes available.
        } catch (RuntimeException exception) {
            GTLoggers.GT_FML_LOGGER.warn("Could not refill TenkeiTech shared consumable stock", exception);
        } finally {
            if (changed) {
                sharedConsumableStock.acknowledgeBackingAmounts();
                sharedConsumableFluidStock.acknowledgeBackingAmounts();
                justHadNewItems = true;
                markDirty();
            }
        }
    }

    private boolean needsSharedConsumableRefill() {
        for (int slot = 0; slot < SharedConsumableItemStock.SLOT_COUNT; slot++) {
            if (sharedConsumableStock.getConfig(slot) != null
                && sharedConsumableStock.getStoredAmount(slot) < sharedConsumableStock.getTarget(slot)) return true;
        }
        for (int slot = 0; slot < SharedConsumableFluidStock.SLOT_COUNT; slot++) {
            if (sharedConsumableFluidStock.getConfig(slot) != null
                && sharedConsumableFluidStock.getStoredAmount(slot) < sharedConsumableFluidStock.getTarget(slot)) {
                return true;
            }
        }
        return false;
    }

    public void onControllerRecipeStarted() {
        if (!sharedConsumableRefillOnRecipeStart || !needsSharedConsumableRefill()) return;
        refillSharedConsumables();
        resetSharedConsumableRefillTimer();
    }

    /** Stores a reusable, compact copy of the encoded patterns and custom name. Catalysts are deliberately excluded. */
    public TransferResult capturePatternCopy(ItemStack tool) {
        if (!isServerMachine()) return TransferResult.INVALID_DATA;
        NBTTagCompound payload = new NBTTagCompound();
        int patternCount = writeTransferPatterns(payload, false);
        writeTransferCustomName(payload);
        if (patternCount == 0 && customName == null) return TransferResult.SOURCE_EMPTY;
        if (!ItemPatternBufferTransferTool.storePayload(tool, Mode.COPY, MAX_PATTERN_COUNT, patternCount, payload)) {
            return TransferResult.DATA_TOO_LARGE;
        }
        return TransferResult.COPY_STORED;
    }

    /**
     * Adds copied patterns to empty slots after validating the entire operation and reserving the required blank
     * patterns. Existing target catalyst configuration is not modified.
     */
    public TransferResult pastePatternCopy(ItemStack tool, EntityPlayer player) {
        if (!isServerMachine()) return TransferResult.INVALID_DATA;
        if (ItemPatternBufferTransferTool.hasAnyStoredData(tool)
            && !ItemPatternBufferTransferTool.isStoredDataWithinLimit(tool, Mode.COPY)) {
            return TransferResult.INVALID_DATA;
        }
        NBTTagCompound payload = ItemPatternBufferTransferTool.getStoredPayload(tool, Mode.COPY);
        if (payload == null) {
            return ItemPatternBufferTransferTool.hasAnyStoredData(tool) ? TransferResult.INVALID_DATA
                : TransferResult.NO_STORED_DATA;
        }
        int storedCount = ItemPatternBufferTransferTool.getStoredPatternCount(tool, Mode.COPY);
        if (ItemPatternBufferTransferTool.getStoredSourceCapacity(tool, Mode.COPY) != MAX_PATTERN_COUNT
            || storedCount < 0
            || storedCount > MAX_PATTERN_COUNT
            || !hasValidTransferredCustomName(payload)
            || !hasCompoundList(payload, NBT_TRANSFER_PATTERNS)) {
            return TransferResult.INVALID_DATA;
        }

        NBTTagList patternList = payload.getTagList(NBT_TRANSFER_PATTERNS, Constants.NBT.TAG_COMPOUND);
        if (patternList.tagCount() != storedCount) return TransferResult.INVALID_DATA;
        ItemStack[] patterns = new ItemStack[storedCount];
        for (int i = 0; i < patternList.tagCount(); i++) {
            NBTTagCompound entry = patternList.getCompoundTagAt(i);
            if (!entry.hasKey(NBT_TRANSFER_PATTERN, Constants.NBT.TAG_COMPOUND)) {
                return TransferResult.INVALID_DATA;
            }
            ItemStack pattern = GTUtility.loadItem(entry.getCompoundTag(NBT_TRANSFER_PATTERN));
            if (!isValidTransferredPattern(pattern)) return TransferResult.INVALID_DATA;
            pattern.stackSize = 1;
            patterns[i] = pattern;
        }

        int[] destinationSlots = new int[storedCount];
        int destinationCount = 0;
        for (int slot = 0; slot < MAX_PATTERN_COUNT && destinationCount < storedCount; slot++) {
            if (internalInventory[slot] == null && mInventory[slot] == null) {
                destinationSlots[destinationCount++] = slot;
            }
        }
        if (destinationCount != storedCount) return TransferResult.TARGET_CAPACITY;

        @SuppressWarnings("unchecked")
        PatternSlot<MTESuperCraftingInputME>[] prepared = new PatternSlot[storedCount];
        for (int i = 0; i < storedCount; i++) {
            prepared[i] = createPatternSlotSafely(patterns[i], null, destinationSlots[i]);
            if (prepared[i] == null) return TransferResult.INVALID_DATA;
        }
        if (!hasBlankPatterns(player, storedCount)) return TransferResult.MISSING_BLANK_PATTERNS;

        consumeBlankPatterns(player, storedCount);
        for (int i = 0; i < storedCount; i++) {
            int slot = destinationSlots[i];
            internalInventory[slot] = prepared[i];
            mInventory[slot] = prepared[i].pattern;
            addPatternMapping(prepared[i]);
        }
        readTransferCustomName(payload);
        markMatchingContextChanged();
        return TransferResult.COPY_PASTED;
    }

    /**
     * Moves patterns, catalysts, the shared circuit, and internal settings into a one-use tool payload. Consumable
     * inputs are returned before the source is changed, and an occupied cut tool is never overwritten.
     */
    public TransferResult capturePatternCut(ItemStack tool) {
        if (!isServerMachine()) return TransferResult.INVALID_DATA;
        if (ItemPatternBufferTransferTool.hasAnyStoredData(tool)) return TransferResult.CUT_TOOL_NOT_EMPTY;
        if (!isValidCutSourceState()) return TransferResult.INVALID_DATA;

        NBTTagCompound payload = new NBTTagCompound();
        int patternCount = writeTransferPatterns(payload, true);
        writeTransferCustomName(payload);

        NBTTagList augmentList = new NBTTagList();
        boolean hasAugments = false;
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            if (patternAugments[slot].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(NBT_TRANSFER_SLOT, slot);
            entry.setTag(NBT_TRANSFER_AUGMENT, patternAugments[slot].writeToNBT(new NBTTagCompound()));
            augmentList.appendTag(entry);
            hasAugments = true;
        }
        payload.setTag(NBT_TRANSFER_AUGMENTS, augmentList);
        payload.setTag(NBT_SHARED_FLUID_CATALYSTS, sharedFluidCatalysts.writeToNBT());

        ItemStack sharedCircuit = mInventory[SLOT_CIRCUIT];
        boolean hasSharedCircuit = isValidTransferredStack(sharedCircuit);
        payload.setBoolean(NBT_TRANSFER_HAS_SHARED_CIRCUIT, hasSharedCircuit);
        if (hasSharedCircuit) {
            ItemStack storedCircuit = sharedCircuit.copy();
            storedCircuit.stackSize = 1;
            payload.setTag(NBT_TRANSFER_SHARED_CIRCUIT, GTUtility.saveItem(storedCircuit));
        }

        payload.setBoolean(NBT_TRANSFER_SHOW_PATTERN, showPattern);
        payload.setBoolean(NBT_TRANSFER_ADDITIONAL_CONNECTION, additionalConnection);
        payload.setInteger(NBT_EMBEDDED_CIRCUIT_CONFIG, embeddedCircuitConfig);
        payload.setBoolean(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS, skipExistingCircuitPatterns);
        payload.setTag(NBT_SHARED_CONSUMABLE_STOCK, writeSharedConsumableStock(false));
        payload.setInteger(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL, sharedConsumableRefillIntervalSeconds);
        payload.setBoolean(NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE, sharedConsumableRefillOnRecipeStart);

        boolean hasConfiguration = patternCount > 0 || customName != null
            || hasAugments
            || !sharedFluidCatalysts.isEmpty()
            || sharedConsumableStock.hasConfiguration()
            || sharedConsumableFluidStock.hasConfiguration()
            || hasSharedCircuit
            || !showPattern
            || additionalConnection
            || embeddedCircuitConfig != MIN_PROGRAMMED_CIRCUIT
            || !skipExistingCircuitPatterns
            || sharedConsumableRefillIntervalSeconds != 5
            || sharedConsumableRefillOnRecipeStart;
        if (!hasConfiguration) return TransferResult.SOURCE_EMPTY;
        if (!ItemPatternBufferTransferTool.canStorePayload(Mode.CUT, MAX_PATTERN_COUNT, patternCount, payload)) {
            return TransferResult.DATA_TOO_LARGE;
        }
        if (!refundAllConsumableInputsForConfiguration()) return TransferResult.REFUND_FAILED;
        if (!ItemPatternBufferTransferTool.storePayload(tool, Mode.CUT, MAX_PATTERN_COUNT, patternCount, payload)) {
            return TransferResult.DATA_TOO_LARGE;
        }

        clearTransferredConfiguration();
        return TransferResult.CUT_STORED;
    }

    /** Installs a cut payload only into a target whose physical configuration slots are entirely empty. */
    public TransferResult pastePatternCut(ItemStack tool) {
        if (!isServerMachine()) return TransferResult.INVALID_DATA;
        if (ItemPatternBufferTransferTool.hasAnyStoredData(tool)
            && !ItemPatternBufferTransferTool.isStoredDataWithinLimit(tool, Mode.CUT)) {
            return TransferResult.INVALID_DATA;
        }
        NBTTagCompound payload = ItemPatternBufferTransferTool.getStoredPayload(tool, Mode.CUT);
        if (payload == null) {
            return ItemPatternBufferTransferTool.hasAnyStoredData(tool) ? TransferResult.INVALID_DATA
                : TransferResult.NO_STORED_DATA;
        }
        int storedCount = ItemPatternBufferTransferTool.getStoredPatternCount(tool, Mode.CUT);
        if (ItemPatternBufferTransferTool.getStoredSourceCapacity(tool, Mode.CUT) != MAX_PATTERN_COUNT
            || storedCount < 0
            || storedCount > MAX_PATTERN_COUNT) {
            return TransferResult.INVALID_DATA;
        }
        CutTransferState state = readCutTransferState(payload, storedCount);
        if (state == null) return TransferResult.INVALID_DATA;
        if (!isCutTransferTargetEmpty()) return TransferResult.TARGET_NOT_EMPTY;

        @SuppressWarnings("unchecked")
        PatternSlot<MTESuperCraftingInputME>[] prepared = new PatternSlot[MAX_PATTERN_COUNT];
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            ItemStack pattern = state.patterns[slot];
            if (pattern == null) continue;
            prepared[slot] = createPatternSlotSafely(pattern, null, slot);
            if (prepared[slot] == null) return TransferResult.INVALID_DATA;
        }

        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            patternAugments[slot].copyFrom(state.augments[slot]);
        }
        sharedFluidCatalysts = state.sharedFluidCatalysts.copy();
        mInventory[SLOT_CIRCUIT] = state.sharedCircuit == null ? null : state.sharedCircuit.copy();
        customName = state.hasCustomName ? state.customName : null;
        showPattern = state.showPattern;
        additionalConnection = state.additionalConnection;
        embeddedCircuitConfig = state.embeddedCircuitConfig;
        skipExistingCircuitPatterns = state.skipExistingCircuitPatterns;
        readSharedConsumableStock(state.sharedConsumableStock, false, true);
        sharedConsumableRefillIntervalSeconds = state.sharedConsumableRefillIntervalSeconds;
        sharedConsumableRefillOnRecipeStart = state.sharedConsumableRefillOnRecipeStart;
        resetSharedConsumableRefillTimer();

        patternDetailsPatternSlotMap.clear();
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            if (prepared[slot] == null) continue;
            internalInventory[slot] = prepared[slot];
            mInventory[slot] = prepared[slot].pattern;
            addPatternMapping(prepared[slot]);
        }
        updateValidGridProxySides();
        markMatchingContextChanged();
        return TransferResult.CUT_PASTED;
    }

    private int writeTransferPatterns(NBTTagCompound payload, boolean includeSlots) {
        NBTTagList patterns = new NBTTagList();
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            PatternSlot<MTESuperCraftingInputME> patternSlot = internalInventory[slot];
            if (patternSlot == null || !isValidTransferredPattern(patternSlot.pattern)) continue;
            NBTTagCompound entry = new NBTTagCompound();
            if (includeSlots) entry.setInteger(NBT_TRANSFER_SLOT, slot);
            ItemStack storedPattern = patternSlot.pattern.copy();
            storedPattern.stackSize = 1;
            entry.setTag(NBT_TRANSFER_PATTERN, GTUtility.saveItem(storedPattern));
            patterns.appendTag(entry);
        }
        payload.setTag(NBT_TRANSFER_PATTERNS, patterns);
        return patterns.tagCount();
    }

    private void writeTransferCustomName(NBTTagCompound payload) {
        payload.setBoolean(NBT_TRANSFER_HAS_CUSTOM_NAME, customName != null);
        if (customName != null) payload.setString(NBT_TRANSFER_CUSTOM_NAME, customName);
    }

    private static boolean hasValidTransferredCustomName(NBTTagCompound payload) {
        if (!payload.hasKey(NBT_TRANSFER_HAS_CUSTOM_NAME, Constants.NBT.TAG_BYTE)) return false;
        return payload.getBoolean(NBT_TRANSFER_HAS_CUSTOM_NAME)
            ? payload.hasKey(NBT_TRANSFER_CUSTOM_NAME, Constants.NBT.TAG_STRING)
            : !payload.hasKey(NBT_TRANSFER_CUSTOM_NAME);
    }

    private void readTransferCustomName(NBTTagCompound payload) {
        customName = payload.getBoolean(NBT_TRANSFER_HAS_CUSTOM_NAME) ? payload.getString(NBT_TRANSFER_CUSTOM_NAME)
            : null;
    }

    private void clearTransferredConfiguration() {
        Arrays.fill(internalInventory, null);
        Arrays.fill(mInventory, 0, MAX_PATTERN_COUNT, null);
        patternDetailsPatternSlotMap.clear();
        PatternAugmentData emptyAugment = new PatternAugmentData();
        for (PatternAugmentData patternAugment : patternAugments) patternAugment.copyFrom(emptyAugment);
        sharedFluidCatalysts = new FluidCatalystData();
        mInventory[SLOT_CIRCUIT] = null;
        customName = null;
        showPattern = true;
        additionalConnection = false;
        embeddedCircuitConfig = MIN_PROGRAMMED_CIRCUIT;
        skipExistingCircuitPatterns = true;
        sharedConsumableStock.clear();
        sharedConsumableFluidStock.clear();
        sharedConsumableRefillIntervalSeconds = 5;
        sharedConsumableRefillOnRecipeStart = false;
        resetSharedConsumableRefillTimer();
        updateValidGridProxySides();
        markMatchingContextChanged();
    }

    private boolean isCutTransferTargetEmpty() {
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            if (internalInventory[slot] != null || mInventory[slot] != null || !patternAugments[slot].isEmpty()) {
                return false;
            }
        }
        return sharedFluidCatalysts.isEmpty() && mInventory[SLOT_CIRCUIT] == null
            && sharedConsumableStock.isCompletelyEmpty()
            && sharedConsumableFluidStock.isCompletelyEmpty();
    }

    @Nullable
    private CutTransferState readCutTransferState(NBTTagCompound payload, int storedCount) {
        if (!hasValidTransferredCustomName(payload) || !hasCompoundList(payload, NBT_TRANSFER_PATTERNS)
            || !hasCompoundList(payload, NBT_TRANSFER_AUGMENTS)
            || !hasCompoundList(payload, NBT_SHARED_FLUID_CATALYSTS)
            || !payload.hasKey(NBT_TRANSFER_HAS_SHARED_CIRCUIT, Constants.NBT.TAG_BYTE)
            || !payload.hasKey(NBT_TRANSFER_SHOW_PATTERN, Constants.NBT.TAG_BYTE)
            || !payload.hasKey(NBT_TRANSFER_ADDITIONAL_CONNECTION, Constants.NBT.TAG_BYTE)
            || !payload.hasKey(NBT_EMBEDDED_CIRCUIT_CONFIG, Constants.NBT.TAG_INT)
            || !payload.hasKey(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS, Constants.NBT.TAG_BYTE)
            || !payload.hasKey(NBT_SHARED_CONSUMABLE_STOCK, Constants.NBT.TAG_COMPOUND)
            || !payload.hasKey(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL, Constants.NBT.TAG_INT)
            || !payload.hasKey(NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE, Constants.NBT.TAG_BYTE)) {
            return null;
        }

        CutTransferState state = new CutTransferState();
        state.hasCustomName = payload.getBoolean(NBT_TRANSFER_HAS_CUSTOM_NAME);
        state.customName = state.hasCustomName ? payload.getString(NBT_TRANSFER_CUSTOM_NAME) : null;
        state.showPattern = payload.getBoolean(NBT_TRANSFER_SHOW_PATTERN);
        state.additionalConnection = payload.getBoolean(NBT_TRANSFER_ADDITIONAL_CONNECTION);
        state.embeddedCircuitConfig = payload.getInteger(NBT_EMBEDDED_CIRCUIT_CONFIG);
        state.skipExistingCircuitPatterns = payload.getBoolean(NBT_SKIP_EXISTING_CIRCUIT_PATTERNS);
        if (state.embeddedCircuitConfig < MIN_PROGRAMMED_CIRCUIT
            || state.embeddedCircuitConfig > MAX_PROGRAMMED_CIRCUIT) {
            return null;
        }

        NBTTagCompound stock = payload.getCompoundTag(NBT_SHARED_CONSUMABLE_STOCK);
        if (!stock.hasKey(NBT_SHARED_CONSUMABLE_ITEMS, Constants.NBT.TAG_COMPOUND)
            || !stock.hasKey(NBT_SHARED_CONSUMABLE_FLUIDS, Constants.NBT.TAG_COMPOUND)) return null;
        SharedConsumableItemStock items = new SharedConsumableItemStock();
        SharedConsumableFluidStock fluids = new SharedConsumableFluidStock();
        if (!items.readFromNBT(stock.getCompoundTag(NBT_SHARED_CONSUMABLE_ITEMS), false, true)
            || !fluids.readFromNBT(stock.getCompoundTag(NBT_SHARED_CONSUMABLE_FLUIDS), false, true)) return null;
        state.sharedConsumableStock = new NBTTagCompound();
        state.sharedConsumableStock.setTag(NBT_SHARED_CONSUMABLE_ITEMS, items.writeToNBT(false));
        state.sharedConsumableStock.setTag(NBT_SHARED_CONSUMABLE_FLUIDS, fluids.writeToNBT(false));
        state.sharedConsumableRefillIntervalSeconds = payload.getInteger(NBT_SHARED_CONSUMABLE_REFILL_INTERVAL);
        state.sharedConsumableRefillOnRecipeStart = payload.getBoolean(NBT_SHARED_CONSUMABLE_REFILL_AFTER_RECIPE);
        if (state.sharedConsumableRefillIntervalSeconds < 0) return null;

        NBTTagList patternList = payload.getTagList(NBT_TRANSFER_PATTERNS, Constants.NBT.TAG_COMPOUND);
        if (patternList.tagCount() != storedCount) return null;
        boolean[] occupiedPatterns = new boolean[MAX_PATTERN_COUNT];
        for (int i = 0; i < patternList.tagCount(); i++) {
            NBTTagCompound entry = patternList.getCompoundTagAt(i);
            if (!entry.hasKey(NBT_TRANSFER_SLOT, Constants.NBT.TAG_INT)
                || !entry.hasKey(NBT_TRANSFER_PATTERN, Constants.NBT.TAG_COMPOUND)) {
                return null;
            }
            int slot = entry.getInteger(NBT_TRANSFER_SLOT);
            if (slot < 0 || slot >= MAX_PATTERN_COUNT || occupiedPatterns[slot]) return null;
            ItemStack pattern = GTUtility.loadItem(entry.getCompoundTag(NBT_TRANSFER_PATTERN));
            if (!isValidTransferredPattern(pattern)) return null;
            pattern.stackSize = 1;
            state.patterns[slot] = pattern;
            occupiedPatterns[slot] = true;
        }

        NBTTagList augmentList = payload.getTagList(NBT_TRANSFER_AUGMENTS, Constants.NBT.TAG_COMPOUND);
        boolean[] occupiedAugments = new boolean[MAX_PATTERN_COUNT];
        for (int i = 0; i < augmentList.tagCount(); i++) {
            NBTTagCompound entry = augmentList.getCompoundTagAt(i);
            if (!entry.hasKey(NBT_TRANSFER_SLOT, Constants.NBT.TAG_INT)
                || !entry.hasKey(NBT_TRANSFER_AUGMENT, Constants.NBT.TAG_COMPOUND)) {
                return null;
            }
            int slot = entry.getInteger(NBT_TRANSFER_SLOT);
            NBTTagCompound augmentTag = entry.getCompoundTag(NBT_TRANSFER_AUGMENT);
            if (slot < 0 || slot >= MAX_PATTERN_COUNT
                || occupiedAugments[slot]
                || !isValidTransferredAugment(augmentTag)) {
                return null;
            }
            PatternAugmentData augment = PatternAugmentData.readFromNBT(augmentTag, slot);
            if (augment.isEmpty()) return null;
            state.augments[slot] = augment;
            occupiedAugments[slot] = true;
        }

        NBTTagList sharedFluidList = payload.getTagList(NBT_SHARED_FLUID_CATALYSTS, Constants.NBT.TAG_COMPOUND);
        if (!isValidTransferredFluidCatalysts(sharedFluidList)) return null;
        state.sharedFluidCatalysts = FluidCatalystData.readFromNBT(sharedFluidList);

        if (payload.getBoolean(NBT_TRANSFER_HAS_SHARED_CIRCUIT)) {
            if (!payload.hasKey(NBT_TRANSFER_SHARED_CIRCUIT, Constants.NBT.TAG_COMPOUND)) return null;
            ItemStack circuit = GTUtility.loadItem(payload.getCompoundTag(NBT_TRANSFER_SHARED_CIRCUIT));
            if (!isValidTransferredStack(circuit) || circuit.stackSize != 1
                || !isItemValidForSlot(SLOT_CIRCUIT, circuit)) {
                return null;
            }
            state.sharedCircuit = circuit;
        } else if (payload.hasKey(NBT_TRANSFER_SHARED_CIRCUIT)) {
            return null;
        }
        return state;
    }

    private static boolean isValidTransferredAugment(NBTTagCompound augment) {
        if ((augment.hasKey("itemCatalysts") && !hasCompoundList(augment, "itemCatalysts"))
            || (augment.hasKey("fluidCatalysts") && !hasCompoundList(augment, "fluidCatalysts"))) {
            return false;
        }
        return isValidTransferredItemCatalysts(augment.getTagList("itemCatalysts", Constants.NBT.TAG_COMPOUND))
            && isValidTransferredFluidCatalysts(augment.getTagList("fluidCatalysts", Constants.NBT.TAG_COMPOUND));
    }

    private static boolean isValidTransferredItemCatalysts(NBTTagList list) {
        boolean[] occupied = new boolean[PatternAugmentData.CATALYST_SLOT_COUNT];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey("slot", Constants.NBT.TAG_INT) || !entry.hasKey("stack", Constants.NBT.TAG_COMPOUND)) {
                return false;
            }
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= occupied.length || occupied[slot]) return false;
            if (!isValidTransferredStack(GTUtility.loadItem(entry.getCompoundTag("stack")))) return false;
            occupied[slot] = true;
        }
        return true;
    }

    private static boolean isValidTransferredFluidCatalysts(NBTTagList list) {
        boolean[] occupied = new boolean[FluidCatalystData.TANK_COUNT];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey("tank", Constants.NBT.TAG_INT) || !entry.hasKey("fluid", Constants.NBT.TAG_COMPOUND)) {
                return false;
            }
            int tank = entry.getInteger("tank");
            if (tank < 0 || tank >= occupied.length || occupied[tank]) return false;
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("fluid"));
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) return false;
            occupied[tank] = true;
        }
        return true;
    }

    private static boolean isValidTransferredPattern(@Nullable ItemStack pattern) {
        return isValidTransferredStack(pattern) && pattern.getItem() instanceof ICraftingPatternItem;
    }

    private static boolean isValidTransferredStack(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.stackSize > 0;
    }

    private static boolean hasCompoundList(NBTTagCompound nbt, String key) {
        if (!nbt.hasKey(key, Constants.NBT.TAG_LIST)) return false;
        int elementType = ((NBTTagList) nbt.getTag(key)).func_150303_d();
        return elementType == Constants.NBT.TAG_END || elementType == Constants.NBT.TAG_COMPOUND;
    }

    private boolean isValidCutSourceState() {
        for (int slot = 0; slot < MAX_PATTERN_COUNT; slot++) {
            PatternSlot<MTESuperCraftingInputME> patternSlot = internalInventory[slot];
            ItemStack inventoryPattern = mInventory[slot];
            if ((patternSlot == null) != (inventoryPattern == null)) return false;
            if (patternSlot == null) continue;
            if (!isValidTransferredPattern(patternSlot.pattern) || patternSlot.pattern.stackSize != 1
                || !ItemStack.areItemStacksEqual(patternSlot.pattern, inventoryPattern)) {
                return false;
            }
        }
        ItemStack sharedCircuit = mInventory[SLOT_CIRCUIT];
        return sharedCircuit == null || (isValidTransferredStack(sharedCircuit) && sharedCircuit.stackSize == 1
            && isItemValidForSlot(SLOT_CIRCUIT, sharedCircuit));
    }

    private static boolean hasBlankPatterns(EntityPlayer player, int required) {
        if (required == 0 || player.capabilities.isCreativeMode) return true;
        int found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack == null || !AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .isSameAs(stack)) {
                continue;
            }
            found += stack.stackSize;
            if (found >= required) return true;
        }
        return false;
    }

    private static void consumeBlankPatterns(EntityPlayer player, int required) {
        if (required == 0 || player.capabilities.isCreativeMode) return;
        int remaining = required;
        for (int slot = 0; slot < player.inventory.mainInventory.length && remaining > 0; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            if (stack == null || !AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .isSameAs(stack)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.stackSize);
            stack.stackSize -= consumed;
            remaining -= consumed;
            if (stack.stackSize <= 0) player.inventory.mainInventory[slot] = null;
        }
        player.inventory.markDirty();
    }

    private boolean isServerMachine() {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        return baseMetaTileEntity != null && baseMetaTileEntity.isServerSide();
    }

    private static final class CutTransferState {

        private final ItemStack[] patterns = new ItemStack[MAX_PATTERN_COUNT];
        private final PatternAugmentData[] augments = createPatternAugments();
        private FluidCatalystData sharedFluidCatalysts = new FluidCatalystData();
        private ItemStack sharedCircuit;
        private boolean hasCustomName;
        private String customName;
        private boolean showPattern;
        private boolean additionalConnection;
        private int embeddedCircuitConfig;
        private boolean skipExistingCircuitPatterns;
        private NBTTagCompound sharedConsumableStock = createEmptySharedConsumableStock();
        private int sharedConsumableRefillIntervalSeconds = 5;
        private boolean sharedConsumableRefillOnRecipeStart;
    }

    @Nullable
    public PatternAugmentData getPatternAugmentData(int index) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return null;
        return patternAugments[index].copy();
    }

    public boolean preparePatternCatalystConfiguration(int index) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return false;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return false;
        PatternSlot<MTESuperCraftingInputME> slot = internalInventory[index];
        if (slot == null || slot.isEmpty()) return true;
        try {
            return slot.refund(getProxy(), getRequest(), false);
        } catch (GridAccessException exception) {
            return false;
        }
    }

    public FluidCatalystData getSharedFluidCatalysts() {
        return sharedFluidCatalysts.copy();
    }

    public String getPatternOutputName(int index) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return "-";
        PatternSlot<MTESuperCraftingInputME> slot = internalInventory[index];
        ICraftingPatternDetails details = slot == null ? null : slot.getPatternDetails();
        if (details == null) return "-";
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        return outputs.length == 0 || outputs[0] == null ? "-" : outputs[0].getDisplayName();
    }

    @Nullable
    public ItemStack getPatternVirtualCircuitStack(int index) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return null;
        PatternSlot<MTESuperCraftingInputME> slot = internalInventory[index];
        ItemStack circuit = slot == null ? null : slot.getStoredPatternCircuit();
        if (circuit == null) return null;
        circuit.stackSize = 1;
        return circuit;
    }

    /** Returns {@code -1} for no pattern-owned circuit, otherwise the GT configuration in the range 1..24. */
    public int getPatternVirtualCircuitConfig(int index) {
        ItemStack circuit = getPatternVirtualCircuitStack(index);
        if (circuit == null) return -1;
        int config = circuit.getItemDamage();
        return config >= MIN_PROGRAMMED_CIRCUIT && config <= MAX_PROGRAMMED_CIRCUIT ? config : -1;
    }

    public boolean setPatternVirtualCircuitConfig(int index, int config) {
        if (config == -1) return setPatternVirtualCircuit(index, null);
        if (config < MIN_PROGRAMMED_CIRCUIT || config > MAX_PROGRAMMED_CIRCUIT) return false;
        return setPatternVirtualCircuit(index, GTUtility.getIntegratedCircuit(config));
    }

    public boolean setPatternVirtualCircuit(int index, @Nullable ItemStack circuit) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return false;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return false;
        if (circuit != null && !GTUtility.isAnyIntegratedCircuit(circuit)) return false;

        PatternSlot<MTESuperCraftingInputME> original = internalInventory[index];
        if (original == null || original.rawPatternDetails == null
            || !original.isExpandedPattern() && original.rawPatternDetails.isCraftable()) {
            return false;
        }

        ItemStack normalizedCircuit = circuit == null ? null : circuit.copy();
        if (normalizedCircuit != null) normalizedCircuit.stackSize = 1;
        ItemStack rewrittenPattern = original.isExpandedPattern()
            ? WildcardPatternCircuitData.write(original.pattern, normalizedCircuit)
            : rewriteEncodedProcessingPatternCircuit(original.pattern, normalizedCircuit, true);
        if (rewrittenPattern == null) return normalizedCircuit == null;
        if (ItemStack.areItemStacksEqual(original.pattern, rewrittenPattern)) return true;

        PatternSlot<MTESuperCraftingInputME> replacement = createPatternSlotSafely(rewrittenPattern, null, index);
        if (replacement == null) return false;
        try {
            if (!original.isEmpty() && !original.refund(getProxy(), getRequest(), false)) return false;
        } catch (GridAccessException exception) {
            return false;
        }
        replacePatternSlot(index, original, replacement);
        return true;
    }

    public int getEmbeddedCircuitConfig() {
        return embeddedCircuitConfig;
    }

    public void setEmbeddedCircuitConfig(int config) {
        embeddedCircuitConfig = clampProgrammedCircuit(config);
        TileEntity tile = getTileEntity();
        if (tile != null) tile.markDirty();
    }

    public boolean shouldSkipExistingCircuitPatterns() {
        return skipExistingCircuitPatterns;
    }

    public void setSkipExistingCircuitPatterns(boolean skipExisting) {
        skipExistingCircuitPatterns = skipExisting;
        TileEntity tile = getTileEntity();
        if (tile != null) tile.markDirty();
    }

    /** Writes the configured integrated circuit into every eligible encoded processing pattern. */
    public void applyConfiguredCircuitToPatterns() {
        rewritePatternCircuits(embeddedCircuitConfig, !skipExistingCircuitPatterns);
    }

    /** Removes all integrated circuits from every eligible encoded processing pattern. */
    public void clearCircuitsFromPatterns() {
        rewritePatternCircuits(null, true);
    }

    private int rewritePatternCircuits(@Nullable Integer circuitConfig, boolean replaceExisting) {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return 0;

        @SuppressWarnings("unchecked")
        PatternSlot<MTESuperCraftingInputME>[] replacements = new PatternSlot[MAX_PATTERN_COUNT];
        int replacementCount = 0;
        for (int index = 0; index < MAX_PATTERN_COUNT; index++) {
            PatternSlot<MTESuperCraftingInputME> original = internalInventory[index];
            if (original == null || original.isExpandedPattern()
                || original.rawPatternDetails == null
                || original.rawPatternDetails.isCraftable()) {
                continue;
            }
            ItemStack rewrittenPattern = rewriteEncodedProcessingPatternCircuit(
                original.pattern,
                circuitConfig,
                replaceExisting);
            if (rewrittenPattern == null) continue;
            PatternSlot<MTESuperCraftingInputME> replacement = createPatternSlotSafely(rewrittenPattern, null, index);
            if (replacement == null) continue;
            replacements[index] = replacement;
            replacementCount++;
        }
        if (replacementCount == 0) return 0;

        try {
            for (int index = 0; index < MAX_PATTERN_COUNT; index++) {
                PatternSlot<MTESuperCraftingInputME> original = internalInventory[index];
                if (replacements[index] != null && original != null
                    && !original.isEmpty()
                    && !original.refund(getProxy(), getRequest(), false)) {
                    GTLoggers.GT_FML_LOGGER.warn(
                        "Aborting bulk circuit rewrite because inputs in super pattern buffer slot {} could not be refunded",
                        index);
                    return 0;
                }
            }
        } catch (GridAccessException exception) {
            GTLoggers.GT_FML_LOGGER
                .warn("Aborting bulk circuit rewrite while the super pattern buffer AE network is unavailable");
            return 0;
        }

        for (int index = 0; index < MAX_PATTERN_COUNT; index++) {
            PatternSlot<MTESuperCraftingInputME> replacement = replacements[index];
            if (replacement == null) continue;
            PatternSlot<MTESuperCraftingInputME> original = internalInventory[index];
            removePatternMapping(original);
            internalInventory[index] = replacement;
            mInventory[index] = replacement.pattern;
            addPatternMapping(replacement);
        }
        markMatchingContextChanged();
        return replacementCount;
    }

    @Nullable
    private static ItemStack rewriteEncodedProcessingPatternCircuit(ItemStack pattern, @Nullable Integer circuitConfig,
        boolean replaceExisting) {
        ItemStack circuit = circuitConfig == null ? null
            : GTUtility.getIntegratedCircuit(clampProgrammedCircuit(circuitConfig));
        return rewriteEncodedProcessingPatternCircuit(pattern, circuit, replaceExisting);
    }

    @Nullable
    private static ItemStack rewriteEncodedProcessingPatternCircuit(ItemStack pattern, @Nullable ItemStack circuit,
        boolean replaceExisting) {
        if (pattern == null || !(pattern.getItem() instanceof ItemEncodedPattern) || pattern.getTagCompound() == null) {
            return null;
        }
        NBTTagCompound patternTag = pattern.getTagCompound();
        if (patternTag.getBoolean("crafting")) return null;

        NBTTagList originalInputs = patternTag.getTagList("in", Constants.NBT.TAG_COMPOUND);
        NBTTagList rewrittenInputs = new NBTTagList();
        if (circuit != null) {
            circuit = circuit.copy();
            circuit.stackSize = 1;
            IAEItemStack aeCircuit = AEApi.instance()
                .storage()
                .createItemStack(circuit);
            if (aeCircuit == null) return null;
            aeCircuit.setStackSize(1);
            NBTTagCompound circuitTag = new NBTTagCompound();
            // AE2 encoded patterns use Cnt as the authoritative amount; their vanilla Count field remains zero.
            aeCircuit.writeToNBT(circuitTag);
            rewrittenInputs.appendTag(circuitTag);
        }

        boolean foundCircuit = false;
        for (int inputIndex = 0; inputIndex < originalInputs.tagCount(); inputIndex++) {
            NBTTagCompound inputTag = originalInputs.getCompoundTagAt(inputIndex);
            ItemStack input = Platform.loadItemStackFromNBT(inputTag);
            if (GTUtility.isAnyIntegratedCircuit(input)) {
                foundCircuit = true;
                if (circuit != null && !replaceExisting && getEncodedPatternInputAmount(inputTag) > 0) {
                    return null;
                }
                continue;
            }
            rewrittenInputs.appendTag(inputTag.copy());
        }
        if (circuit == null && !foundCircuit) return null;

        ItemStack rewrittenPattern = pattern.copy();
        rewrittenPattern.getTagCompound()
            .setTag("in", rewrittenInputs);
        return rewrittenPattern;
    }

    private static long getEncodedPatternInputAmount(NBTTagCompound inputTag) {
        return inputTag.hasKey("Cnt") ? inputTag.getLong("Cnt") : inputTag.getInteger("Count");
    }

    private static int clampProgrammedCircuit(int config) {
        return Math.max(MIN_PROGRAMMED_CIRCUIT, Math.min(MAX_PROGRAMMED_CIRCUIT, config));
    }

    /**
     * Installs an authoritative fixed-slot catalyst state. An installed PatternSlot is replaced so GT5U's
     * WeakHashMap recipe cache cannot retain the old matching context.
     */
    public boolean applyPatternAugmentData(int index, PatternAugmentData newData) {
        if (index < 0 || index >= MAX_PATTERN_COUNT || newData == null) return false;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return false;
        PatternSlot<MTESuperCraftingInputME> original = internalInventory[index];
        try {
            if (original != null && !original.isEmpty() && !original.refund(getProxy(), getRequest(), false)) {
                return false;
            }
        } catch (GridAccessException exception) {
            return false;
        }

        patternAugments[index].copyFrom(newData);
        if (original != null) {
            PatternSlot<MTESuperCraftingInputME> replacement = original.copyForConfiguration();
            replacePatternSlot(index, original, replacement);
        } else {
            markMatchingContextChanged();
        }
        return true;
    }

    private AutomaticCatalystInstallResult installAutomaticItemCatalysts(PatternSlot<MTESuperCraftingInputME> target,
        ICraftingPatternDetails details, MEInventoryCrafting craftingTable) {
        List<ItemStack> missing = target.getMissingAutomaticItemCatalysts(details);
        if (missing.isEmpty()) return AutomaticCatalystInstallResult.NOT_REQUIRED;

        PatternAugmentData installed = target.getAugmentData();
        List<Integer> emptySlots = new ArrayList<>();
        for (int index = 0; index < PatternAugmentData.CATALYST_SLOT_COUNT; index++) {
            if (installed.getItemCatalyst(index) == null) emptySlots.add(index);
        }
        if (missing.size() > emptySlots.size()) {
            logAutomaticCatalystProblem(
                "Cannot install automatic catalysts for super pattern buffer slot {}: {} empty item catalyst slots are required",
                target.getFixedIndex(),
                missing.size());
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return AutomaticCatalystInstallResult.FAILED;
        }

        long[] capturedByTableSlot = new long[craftingTable.getSizeInventory()];
        for (ItemStack requirement : missing) {
            boolean captured = false;
            for (int tableSlot = 0; tableSlot < craftingTable.getSizeInventory(); tableSlot++) {
                IAEStack<?> input = craftingTable.getAEStackInSlot(tableSlot);
                if (!(input instanceof IAEItemStack itemInput)
                    || !ItemPatternCatalystMarker.isSameCatalyst(requirement, itemInput.getItemStack())
                    || input.getStackSize() <= capturedByTableSlot[tableSlot]) {
                    continue;
                }
                capturedByTableSlot[tableSlot]++;
                captured = true;
                break;
            }
            if (!captured) {
                logAutomaticCatalystProblem(
                    "AE did not deliver required automatic catalyst {} for super pattern buffer slot {}",
                    requirement.getDisplayName(),
                    target.getFixedIndex());
                scheduledReason = ScheduledReason.NOT_ENOUGH_INGREDIENTS;
                return AutomaticCatalystInstallResult.FAILED;
            }
        }

        try {
            if (!target.isEmpty() && !target.refund(getProxy(), getRequest(), false)) {
                logAutomaticCatalystProblem(
                    "Cannot install automatic catalysts for super pattern buffer slot {} because its consumable inputs could not be refunded",
                    target.getFixedIndex());
                scheduledReason = ScheduledReason.SOMETHING_STUCK;
                return AutomaticCatalystInstallResult.FAILED;
            }
        } catch (GridAccessException exception) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return AutomaticCatalystInstallResult.FAILED;
        }
        if (target.getFixedIndex() < 0 || internalInventory[target.getFixedIndex()] != target) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            needPatternSync = true;
            return AutomaticCatalystInstallResult.FAILED;
        }

        for (int index = 0; index < missing.size(); index++) {
            installed.setItemCatalyst(emptySlots.get(index), missing.get(index));
        }
        PatternSlot<MTESuperCraftingInputME> replacement = target.copyForConfiguration(installed);
        if (!insertCraftingTableExcluding(replacement, craftingTable, capturedByTableSlot)) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return AutomaticCatalystInstallResult.FAILED;
        }
        patternAugments[target.getFixedIndex()] = installed;
        replacePatternSlot(target.getFixedIndex(), target, replacement);
        return AutomaticCatalystInstallResult.INSTALLED;
    }

    /**
     * Returns the exact, server-authoritative fixed catalysts still needed by the addressed pattern. The returned
     * stacks are defensive copies and are used only by the active AE crafting CPU to prepare the first dispatch.
     */
    public List<ItemStack> getMissingAutomaticItemCatalysts(ICraftingPatternDetails details) {
        PatternSlot<MTESuperCraftingInputME> target = findPatternSlot(details);
        return target == null ? Collections.emptyList() : target.getMissingAutomaticItemCatalysts(details);
    }

    private static boolean insertCraftingTableExcluding(PatternSlot<MTESuperCraftingInputME> target,
        MEInventoryCrafting craftingTable, long[] excludedAmounts) {
        LongItemStore nextItems = new LongItemStore();
        nextItems.copyFrom(target.itemInventory);
        LongFluidStore nextFluids = new LongFluidStore();
        nextFluids.copyFrom(target.fluidInventory);
        for (int tableSlot = 0; tableSlot < craftingTable.getSizeInventory(); tableSlot++) {
            IAEStack<?> input = craftingTable.getAEStackInSlot(tableSlot);
            if (input == null) continue;
            long remaining = input.getStackSize() - excludedAmounts[tableSlot];
            if (remaining <= 0) continue;
            IAEStack<?> retained = input.copy()
                .setStackSize(remaining);
            if (retained instanceof IAEItemStack itemInput) {
                if (!nextItems.insert(itemInput.getItemStack(), remaining)) return false;
            } else if (retained instanceof IAEFluidStack fluidInput) {
                if (!nextFluids.insert(fluidInput.getFluidStack(), remaining)) return false;
            }
        }
        target.itemInventory.copyFrom(nextItems);
        target.fluidInventory.copyFrom(nextFluids);
        return true;
    }

    private void logAutomaticCatalystProblem(String message, Object... arguments) {
        long currentTick = getBaseMetaTileEntity().getTimer();
        if (currentTick - lastAutomaticCatalystLogTick < 200) return;
        GTLoggers.GT_FML_LOGGER.warn(message, arguments);
        lastAutomaticCatalystLogTick = currentTick;
    }

    private enum AutomaticCatalystInstallResult {
        NOT_REQUIRED,
        INSTALLED,
        FAILED
    }

    /**
     * Installs authoritative shared-fluid state after all consumable slot buffers have been safely returned. Changing
     * shared matching context replaces every active PatternSlot so GTNH's WeakHashMap recipe caches become unreachable.
     */
    public boolean applySharedFluidCatalysts(FluidCatalystData newData) {
        if (newData == null) return false;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return false;
        if (!refundAllConsumableInputsForConfiguration()) return false;

        @SuppressWarnings("unchecked")
        PatternSlot<MTESuperCraftingInputME>[] replacements = new PatternSlot[MAX_PATTERN_COUNT];
        for (int i = 0; i < MAX_PATTERN_COUNT; i++) {
            PatternSlot<MTESuperCraftingInputME> original = internalInventory[i];
            if (original == null) continue;
            replacements[i] = original.copyForConfiguration();
        }

        sharedFluidCatalysts = newData.copy();
        patternDetailsPatternSlotMap.clear();
        for (int i = 0; i < MAX_PATTERN_COUNT; i++) {
            if (internalInventory[i] == null) continue;
            internalInventory[i] = replacements[i];
            addPatternMapping(replacements[i]);
        }
        markMatchingContextChanged();
        return true;
    }

    private boolean refundAllConsumableInputsForConfiguration() {
        try {
            for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
                if (slot != null && !slot.isEmpty() && !slot.refund(getProxy(), getRequest(), false)) return false;
            }
            return refundSharedConsumableStock(false);
        } catch (GridAccessException exception) {
            return false;
        }
    }

    private boolean refundSharedConsumableSlotAmount(int slot, long amount) {
        if (amount <= 0) return true;
        ItemStack identity = sharedConsumableStock.getConfig(slot);
        long storedAmount = sharedConsumableStock.getStoredAmount(slot);
        if (identity == null || storedAmount <= 0) return true;
        long requestedAmount = Math.min(amount, storedAmount);
        try {
            IMEMonitor<IAEItemStack> storage = getProxy().getStorage()
                .getItemInventory();
            IAEItemStack requestStack = AEApi.instance()
                .storage()
                .createItemStack(identity);
            requestStack.setStackSize(requestedAmount);
            IAEItemStack remainder = Platform
                .poweredInsert(getProxy().getEnergy(), storage, requestStack, getRequest());
            long remaining = remainder == null ? 0 : remainder.getStackSize();
            long inserted = requestedAmount - remaining;
            sharedConsumableStock.setStoredAmount(slot, storedAmount - inserted);
            sharedConsumableStock.acknowledgeBackingAmounts();
            markDirty();
            return remaining == 0;
        } catch (GridAccessException | RuntimeException exception) {
            return false;
        }
    }

    private boolean refundSharedConsumableFluidSlotAmount(int slot, long amount) {
        if (amount <= 0) return true;
        FluidStack identity = sharedConsumableFluidStock.getConfig(slot);
        long storedAmount = sharedConsumableFluidStock.getStoredAmount(slot);
        if (identity == null || storedAmount <= 0) return true;
        long requestedAmount = Math.min(amount, storedAmount);
        try {
            IMEMonitor<IAEFluidStack> storage = getProxy().getStorage()
                .getFluidInventory();
            IAEFluidStack requestStack = AEApi.instance()
                .storage()
                .createFluidStack(identity);
            requestStack.setStackSize(requestedAmount);
            IAEFluidStack remainder = Platform
                .poweredInsert(getProxy().getEnergy(), storage, requestStack, getRequest());
            long remaining = remainder == null ? 0 : remainder.getStackSize();
            long inserted = requestedAmount - remaining;
            sharedConsumableFluidStock.setStoredAmount(slot, storedAmount - inserted);
            sharedConsumableFluidStock.acknowledgeBackingAmounts();
            markDirty();
            return remaining == 0;
        } catch (GridAccessException | RuntimeException exception) {
            return false;
        }
    }

    private boolean refundSharedConsumableStock(boolean shouldDrop) {
        boolean complete = true;
        for (int slot = 0; slot < SharedConsumableItemStock.SLOT_COUNT; slot++) {
            long storedAmount = sharedConsumableStock.getStoredAmount(slot);
            if (storedAmount <= 0) continue;
            if (!refundSharedConsumableSlotAmount(slot, storedAmount)) complete = false;
            if (shouldDrop && Gregtech.machines.allowCribDropItems) dropSharedConsumableItemStock(slot);
        }
        for (int index = sharedConsumableStock.getRecoveryCount() - 1; index >= 0; index--) {
            ItemStack identity = sharedConsumableStock.getRecoveryIdentity(index);
            long remaining = refundSharedConsumableStack(identity, sharedConsumableStock.getRecoveryAmount(index));
            if (remaining > 0) complete = false;
            if (shouldDrop && Gregtech.machines.allowCribDropItems) {
                remaining = dropSharedConsumableItemAmount(identity, remaining);
            }
            sharedConsumableStock.setRecoveryAmount(index, remaining);
        }
        for (int slot = 0; slot < SharedConsumableFluidStock.SLOT_COUNT; slot++) {
            long storedAmount = sharedConsumableFluidStock.getStoredAmount(slot);
            if (storedAmount <= 0) continue;
            if (!refundSharedConsumableFluidSlotAmount(slot, storedAmount)) complete = false;
            if (shouldDrop && Gregtech.machines.allowCribDropItems) dropSharedConsumableFluidStock(slot);
        }
        for (int index = sharedConsumableFluidStock.getRecoveryCount() - 1; index >= 0; index--) {
            FluidStack identity = sharedConsumableFluidStock.getRecoveryIdentity(index);
            long remaining = refundSharedConsumableFluidStack(
                identity,
                sharedConsumableFluidStock.getRecoveryAmount(index));
            if (remaining > 0) complete = false;
            if (shouldDrop && Gregtech.machines.allowCribDropItems) {
                remaining = dropSharedConsumableFluidAmount(identity, remaining);
            }
            sharedConsumableFluidStock.setRecoveryAmount(index, remaining);
        }
        sharedConsumableStock.acknowledgeBackingAmounts();
        sharedConsumableFluidStock.acknowledgeBackingAmounts();
        markDirty();
        return complete && sharedConsumableStock.isStoredEmpty() && sharedConsumableFluidStock.isStoredEmpty();
    }

    private void dropSharedConsumableItemStock(int slot) {
        ItemStack identity = sharedConsumableStock.getConfig(slot);
        long remaining = dropSharedConsumableItemAmount(identity, sharedConsumableStock.getStoredAmount(slot));
        sharedConsumableStock.setStoredAmount(slot, remaining);
    }

    private long dropSharedConsumableItemAmount(ItemStack identity, long remaining) {
        while (identity != null && remaining > 0) {
            int amount = (int) Math.min(Integer.MAX_VALUE, remaining);
            ItemStack dropped = identity.copy();
            dropped.stackSize = amount;
            dropMachineItem(dropped);
            remaining -= amount;
        }
        return remaining;
    }

    private void dropSharedConsumableFluidStock(int slot) {
        FluidStack identity = sharedConsumableFluidStock.getConfig(slot);
        long remaining = dropSharedConsumableFluidAmount(identity, sharedConsumableFluidStock.getStoredAmount(slot));
        sharedConsumableFluidStock.setStoredAmount(slot, remaining);
    }

    private long dropSharedConsumableFluidAmount(FluidStack identity, long remaining) {
        while (identity != null && remaining > 0) {
            int amount = (int) Math.min(Integer.MAX_VALUE, remaining);
            FluidStack dropped = identity.copy();
            dropped.amount = amount;
            if (!dropMachineFluid(dropped)) return remaining;
            remaining -= amount;
        }
        return remaining;
    }

    private long refundSharedConsumableStack(ItemStack identity, long amount) {
        if (identity == null || amount <= 0) return 0;
        try {
            IAEItemStack request = AEApi.instance()
                .storage()
                .createItemStack(identity);
            request.setStackSize(amount);
            IAEItemStack remainder = Platform.poweredInsert(
                getProxy().getEnergy(),
                getProxy().getStorage()
                    .getItemInventory(),
                request,
                getRequest());
            return remainder == null ? 0 : remainder.getStackSize();
        } catch (GridAccessException | RuntimeException exception) {
            return amount;
        }
    }

    private long refundSharedConsumableFluidStack(FluidStack identity, long amount) {
        if (identity == null || amount <= 0) return 0;
        try {
            IAEFluidStack request = AEApi.instance()
                .storage()
                .createFluidStack(identity);
            request.setStackSize(amount);
            IAEFluidStack remainder = Platform.poweredInsert(
                getProxy().getEnergy(),
                getProxy().getStorage()
                    .getFluidInventory(),
                request,
                getRequest());
            return remainder == null ? 0 : remainder.getStackSize();
        } catch (GridAccessException | RuntimeException exception) {
            return amount;
        }
    }

    private void replacePatternSlotsForMatchingContext(PatternSlot<MTESuperCraftingInputME>[] replacements) {
        patternDetailsPatternSlotMap.clear();
        for (int index = 0; index < MAX_PATTERN_COUNT; index++) {
            if (replacements[index] == null) continue;
            internalInventory[index] = replacements[index];
            addPatternMapping(replacements[index]);
        }
        markMatchingContextChanged();
    }

    private void invalidateSharedConsumableMatchingContext() {
        for (MTEMultiBlockBase controller : getPatternModeControllers()) {
            invalidatePatternRecipeCache(controller);
        }
        markMatchingContextChanged();
    }

    public void invalidatePatternRecipeCache(MTEMultiBlockBase controller) {
        if (controller == null) return;
        PatternModeController cacheOwner = (PatternModeController) controller;
        for (PatternSlot<MTESuperCraftingInputME> patternSlot : internalInventory) {
            if (patternSlot != null) cacheOwner.tenkeitech$invalidateRecipeCache(patternSlot.getProcessingInventory());
        }
    }

    private void replacePatternSlot(int index, PatternSlot<MTESuperCraftingInputME> original,
        PatternSlot<MTESuperCraftingInputME> replacement) {
        removePatternMapping(original);
        internalInventory[index] = replacement;
        mInventory[index] = replacement.pattern;
        addPatternMapping(replacement);
        markMatchingContextChanged();
    }

    private void markMatchingContextChanged() {
        needPatternSync = true;
        justHadNewItems = true;
        TileEntity tile = getTileEntity();
        if (tile != null) tile.markDirty();
    }

    /**
     * Installs one encoded pattern received from the pattern-terminal upload flow. The replacement is fully decoded
     * before either inventory representation is changed, so an invalid pattern cannot leave {@code mInventory} and
     * {@code internalInventory} out of sync.
     *
     * @return the installed slot index, or {@code -1} when no compatible empty slot is available
     */
    public int tryInstallUploadedPattern(ItemStack encodedPattern) {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()
            || encodedPattern == null
            || !(encodedPattern.getItem() instanceof ICraftingPatternItem)) {
            return -1;
        }

        ItemStack candidate = encodedPattern.copy();
        candidate.stackSize = 1;
        for (int index = 0; index < MAX_PATTERN_COUNT; index++) {
            if (internalInventory[index] != null || mInventory[index] != null
                || !isItemValidForSlot(index, candidate)) {
                continue;
            }

            PatternSlot<MTESuperCraftingInputME> prepared = createPatternSlotSafely(candidate, null, index);
            if (prepared == null) return -1;

            // Recheck immediately before committing so this method stays safe if its callers are ever generalized.
            if (internalInventory[index] != null || mInventory[index] != null) continue;
            internalInventory[index] = prepared;
            mInventory[index] = prepared.pattern;
            addPatternMapping(prepared);
            markMatchingContextChanged();
            return index;
        }
        return -1;
    }

    /** Rolls back a same-tick terminal upload if removing the source pattern unexpectedly fails. */
    public boolean rollbackUploadedPattern(int index, ItemStack encodedPattern) {
        if (index < 0 || index >= MAX_PATTERN_COUNT || encodedPattern == null) return false;
        PatternSlot<MTESuperCraftingInputME> installed = internalInventory[index];
        if (installed == null || !installed.isEmpty()
            || !ItemStack.areItemStacksEqual(installed.pattern, encodedPattern)) {
            return false;
        }

        removePatternMapping(installed);
        internalInventory[index] = null;
        mInventory[index] = null;
        markMatchingContextChanged();
        return true;
    }

    /** Safely removes the exact pattern recorded by the most recent terminal upload. */
    public boolean withdrawUploadedPattern(int index, ItemStack encodedPattern) {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()
            || index < 0
            || index >= MAX_PATTERN_COUNT
            || encodedPattern == null) return false;
        PatternSlot<MTESuperCraftingInputME> installed = internalInventory[index];
        if (installed == null || !ItemStack.areItemStacksEqual(installed.pattern, encodedPattern)) return false;
        if (!preparePatternSlotForEdit(index)) return false;

        installed = internalInventory[index];
        if (installed == null || !ItemStack.areItemStacksEqual(installed.pattern, encodedPattern)) return false;
        removePatternMapping(installed);
        internalInventory[index] = null;
        mInventory[index] = null;
        markMatchingContextChanged();
        return true;
    }

    /**
     * Returns buffered inputs before a pattern can be removed by a GUI or terminal transaction. A failed refund keeps
     * the pattern locked in place, which prevents a post-click rollback from duplicating its encoded pattern item.
     */
    public boolean preparePatternSlotForEdit(int index) {
        if (index < 0 || index >= MAX_PATTERN_COUNT) return false;
        PatternSlot<MTESuperCraftingInputME> slot = internalInventory[index];
        if (slot == null) return true;
        if (slot.isEmpty()) return true;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null || !baseMetaTileEntity.isServerSide()) return true;
        try {
            boolean refunded = slot.refund(getProxy(), getRequest(), false);
            if (!refunded) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Refusing to edit super pattern buffer slot {} because some inputs could not be refunded",
                    index);
            }
            return refunded;
        } catch (GridAccessException exception) {
            GTLoggers.GT_FML_LOGGER
                .warn("Refusing to edit super pattern buffer slot {} while its AE network is unavailable", index);
            return false;
        }
    }

    public void onPatternChange(int index, ItemStack newItem) {
        if (!getBaseMetaTileEntity().isServerSide()) return;
        if (index < 0 || index >= MAX_PATTERN_COUNT) {
            GTLoggers.GT_FML_LOGGER.warn("Ignoring out-of-range TenkeiTech pattern change for slot {}", index);
            return;
        }

        World world = getBaseMetaTileEntity().getWorld();

        // remove old if applicable
        PatternSlot<MTESuperCraftingInputME> originalPattern = internalInventory[index];
        if (originalPattern != null) {
            if (originalPattern.hasChanged(newItem, world)) {
                try {
                    if (!originalPattern.refund(getProxy(), getRequest(), false)) {
                        mInventory[index] = originalPattern.pattern;
                        GTLoggers.GT_FML_LOGGER.warn(
                            "Refusing to replace super pattern buffer slot {} because its inputs could not be refunded",
                            index);
                        return;
                    }
                } catch (GridAccessException ignored) {
                    mInventory[index] = originalPattern.pattern;
                    return;
                }
                removePatternMapping(originalPattern);
                internalInventory[index] = null;
                needPatternSync = true;
            } else {
                if (newItem != null && !ItemStack.areItemStacksEqual(originalPattern.pattern, newItem)) {
                    PatternSlot<MTESuperCraftingInputME> replacement = createPatternSlotSafely(newItem, null, index);
                    if (replacement != null) {
                        replacement.itemInventory.copyFrom(originalPattern.itemInventory);
                        for (LongFluidStore.Snapshot stored : originalPattern.fluidInventory.longSnapshot()) {
                            replacement.fluidInventory.insert(stored.getIdentity(), stored.getAmount());
                        }
                        removePatternMapping(originalPattern);
                        internalInventory[index] = replacement;
                        addPatternMapping(replacement);
                        needPatternSync = true;
                    }
                }
                return; // nothing has changed
            }
        }

        // original does not exist or has changed
        if (newItem == null || !(newItem.getItem() instanceof ICraftingPatternItem)) return;

        PatternSlot<MTESuperCraftingInputME> patternSlot = createPatternSlotSafely(newItem, null, index);
        if (patternSlot == null) return;
        internalInventory[index] = patternSlot;
        addPatternMapping(patternSlot);
        needPatternSync = true;
    }

    @Override
    public ItemStack[] getSharedItems() {
        ItemStack[] sharedItems = new ItemStack[SLOT_MANUAL_SIZE + 1];
        sharedItems[0] = mInventory[SLOT_CIRCUIT];
        System.arraycopy(mInventory, SLOT_MANUAL_START, sharedItems, 1, SLOT_MANUAL_SIZE);
        return ArrayUtils
            .addAll(ArrayExt.withoutNulls(sharedItems, ItemStack[]::new), sharedConsumableStock.getConsumableInputs());
    }

    /**
     * Exposes exactly one logical inventory to Eye of Harmony, followed by the hatch-wide manual and shared inputs.
     * This deliberately does not flatten multiple PatternSlots into one recipe candidate.
     */
    @Nullable
    public EyeOfHarmonyInputView createEyeOfHarmonyInputView() {
        IDualInputInventory selected = getFirstNonEmptyInventory().orElse(null);
        ItemStack[] items = selected == null ? GTValues.emptyItemStackArray : selected.getItemInputs();
        FluidStack[] fluids = selected == null ? GTValues.emptyFluidStackArray : selected.getFluidInputs();
        items = ArrayUtils.addAll(items, getSharedItems());
        if (items.length == 0 && fluids.length == 0) return null;
        return new EyeOfHarmonyInputView(items, fluids);
    }

    /** Cleans backing stacks after Eye of Harmony has consumed them directly and persists the resulting amounts. */
    public void acknowledgeEyeOfHarmonyInputChanges() {
        for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
            if (slot == null) continue;
            slot.updateSlotItems();
            slot.updateSlotFluids();
        }
        updateSlots();
        sharedConsumableStock.observeBackingChanges();
        sharedConsumableFluidStock.observeBackingChanges();
        patternMode.invalidate();
        markDirty();
    }

    private FluidStack[] getSharedConsumableFluids() {
        return sharedConsumableFluidStock.getConsumableInputs();
    }

    private long getSharedConsumableAmount(ItemStack identity) {
        return sharedConsumableStock.getConsumableAmount(identity);
    }

    private long getSharedConsumableAmount(FluidStack identity) {
        return sharedConsumableFluidStock.getConsumableAmount(identity);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        // Preserve GT5U's complete Waila chain. Runtime Mixins that enhance the shared hatch
        // hierarchy can then render their data without TenkeiTech knowing their encoding.
        super.getWailaBody(itemStack, currenttip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        currenttip.add(
            StatCollector.translateToLocal(
                "GT5U.infodata.hatch.crafting_input_me.show_pattern." + (showPattern ? "enable" : "disabled")));
        if (tag.hasKey("patternModeBusy")) {
            currenttip.add(
                StatCollector.translateToLocal(
                    "tenkeitech.waila.super_pattern_buffer.busy."
                        + (tag.getBoolean("patternModeBusy") ? "yes" : "no")));
        }
        if (tag.hasKey("inventory")) {
            NBTTagList inventory = tag.getTagList("inventory", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < inventory.tagCount(); ++i) {
                NBTTagCompound item = inventory.getCompoundTagAt(i);
                String name = item.getString("name");
                long amount = item.getLong("amount");
                currenttip.add(
                    name + ": "
                        + EnumChatFormatting.GOLD
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(amount)
                        + EnumChatFormatting.RESET);
            }
        }
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        tag.setBoolean("showPattern", showPattern);
        tag.setBoolean("patternModeBusy", patternMode.busy());
        NBTTagList inventory = new NBTTagList();
        HashMap<String, Long> nameToAmount = new HashMap<>();
        for (Iterator<PatternSlot<MTESuperCraftingInputME>> it = getPatternModeSlots().iterator(); it.hasNext();) {
            PatternSlot<MTESuperCraftingInputME> i = it.next();
            for (LongItemStore.Snapshot stored : i.itemInventory.longSnapshot()) {
                if (stored.getAmount() > 0) {
                    String name = stored.getIdentity()
                        .getDisplayName();
                    mergeDisplayedAmount(nameToAmount, name, stored.getAmount());
                }
            }
            for (LongFluidStore.Snapshot stored : i.fluidInventory.longSnapshot()) {
                if (stored.getAmount() > 0) {
                    String name = stored.getIdentity()
                        .getLocalizedName();
                    mergeDisplayedAmount(nameToAmount, name, stored.getAmount());
                }
            }
        }
        for (int slot = 0; slot < SharedConsumableItemStock.SLOT_COUNT; slot++) {
            ItemStack item = sharedConsumableStock.getConfig(slot);
            long amount = sharedConsumableStock.getStoredAmount(slot);
            if (item != null && amount > 0) mergeDisplayedAmount(nameToAmount, item.getDisplayName(), amount);
        }
        for (int slot = 0; slot < SharedConsumableFluidStock.SLOT_COUNT; slot++) {
            FluidStack fluid = sharedConsumableFluidStock.getConfig(slot);
            long amount = sharedConsumableFluidStock.getStoredAmount(slot);
            if (fluid != null && amount > 0) mergeDisplayedAmount(nameToAmount, fluid.getLocalizedName(), amount);
        }
        for (Map.Entry<String, Long> entry : nameToAmount.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("name", entry.getKey());
            item.setLong("amount", entry.getValue());
            inventory.appendTag(item);
        }

        tag.setTag("inventory", inventory);
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        completeGtnlWailaName(tag);
    }

    private static void mergeDisplayedAmount(Map<String, Long> amounts, String name, long amount) {
        amounts.merge(name, amount, (left, right) -> left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right);
    }

    private void completeGtnlWailaName(NBTTagCompound tag) {
        if (!Loader.isModLoaded("sciencenotleisure") || hasCustomName()
            || !tag.hasKey("name", Constants.NBT.TAG_STRING)) return;
        ItemStack icon = getCrafterIcon();
        if (icon == null || !icon.hasDisplayName()) return;
        String prefix = icon.getDisplayName();
        if (prefix.isEmpty() || !GTNL_NAME_PREFIX.matcher(prefix)
            .matches()) return;
        if (!tag.getString("name")
            .equals(getName())) return;

        // GTNL replaces the icon's display name with metadata only. Its vanilla-buffer hook adds
        // the machine key, but does not target this sibling class. Leave decoding to GTNL's Waila hook.
        tag.setString("name", prefix + icon.getUnlocalizedName());
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (!isActive()) return;

        for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
            if (slot == null) continue;
            List<ICraftingPatternDetails> details = slot.getRegistrationDetails();
            if (details.isEmpty()) {
                if (!slot.isExpandedPattern()) {
                    GTLoggers.GT_FML_LOGGER.warn(
                        "Found an invalid pattern at {} in dim {}",
                        getBaseMetaTileEntity().getCoords(),
                        getBaseMetaTileEntity().getWorld().provider.dimensionId);
                }
                continue;
            }
            for (ICraftingPatternDetails detail : details) craftingTracker.addCraftingOption(this, detail);
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!isActive()) return false;
        if (!getBaseMetaTileEntity().isAllowedToWork()) return false;
        if (!(table instanceof MEInventoryCrafting meic)) return false;

        for (int i = 0; i < table.getSizeInventory(); ++i) {
            IAEStack<?> stackInSlot = meic.getAEStackInSlot(i);
            if (stackInSlot == null || stackInSlot instanceof IAEItemStack
                || (supportFluids && stackInSlot instanceof IAEFluidStack)) {
                continue;
            }

            scheduledReason = ScheduledReason.UNSUPPORTED_STACK;
            return false;
        }

        PatternSlot<MTESuperCraftingInputME> targetSlot = findPatternSlot(patternDetails);
        if (targetSlot == null) {
            // The pattern was removed or replaced after AE built its crafting-provider cache. Never route into an
            // unrelated slot; fail until the queued pattern-change event rebuilds the cache.
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            long currentTick = getBaseMetaTileEntity().getTimer();
            if (currentTick - lastStalePatternLogTick >= 200) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Rejected a stale AE crafting request for TenkeiTech super pattern buffer at {}",
                    getBaseMetaTileEntity().getCoords());
                lastStalePatternLogTick = currentTick;
            }
            needPatternSync = true;
            return false;
        }

        if (!patternMode.canAccept(targetSlot)) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }

        String previousExpandedPatternId = targetSlot.getActiveExpandedPatternId();
        if (!targetSlot.activatePattern(patternDetails)) {
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }
        if (targetSlot.isExpandedPattern()
            && !Objects.equals(previousExpandedPatternId, targetSlot.getActiveExpandedPatternId())) {
            PatternSlot<MTESuperCraftingInputME> replacement = targetSlot.copyForConfiguration();
            replacePatternSlot(targetSlot.getFixedIndex(), targetSlot, replacement);
            targetSlot = replacement;
        }

        final AutomaticCatalystInstallResult installResult;
        try {
            installResult = installAutomaticItemCatalysts(targetSlot, patternDetails, meic);
        } catch (RuntimeException exception) {
            targetSlot.restoreActiveExpandedPatternId(previousExpandedPatternId);
            throw exception;
        }
        if (installResult == AutomaticCatalystInstallResult.FAILED) {
            targetSlot.restoreActiveExpandedPatternId(previousExpandedPatternId);
            return false;
        }
        if (installResult == AutomaticCatalystInstallResult.INSTALLED) {
            patternMode.accepted(targetSlot);
            markNewInputsAccepted();
            return true;
        }

        final boolean inserted;
        try {
            inserted = targetSlot.insertItemsAndFluids(meic);
        } catch (RuntimeException exception) {
            // Keep the requested identity if a third-party stack throws after a partial insert. This blocks other
            // expanded recipes from mixing into any inputs the slot may already own.
            throw exception;
        }
        if (!inserted) {
            targetSlot.restoreActiveExpandedPatternId(previousExpandedPatternId);
            scheduledReason = ScheduledReason.SOMETHING_STUCK;
            return false;
        }
        patternMode.accepted(targetSlot);
        markNewInputsAccepted();
        return true;
    }

    private void markNewInputsAccepted() {
        justHadNewItems = true;
        TileEntity tile = getTileEntity();
        if (tile != null) tile.markDirty();
    }

    @Override
    public ScheduledReason getScheduledReason() {
        return scheduledReason;
    }

    @Override
    public boolean isBusy() {
        return patternMode.busy();
    }

    @Override
    public Iterator<IDualInputInventoryWithPattern> inventories() {
        return Stream.concat(
            Arrays.stream(internalInventory)
                .filter(Objects::nonNull)
                .filter(patternMode::allows)
                .map(PatternSlot::getProcessingInventory),
            Stream.of(sharedConsumableInventory))
            .iterator();
    }

    public Iterator<IDualInputInventoryWithPattern> inventoriesReversed() {
        return Stream.concat(
            IntStream.range(0, internalInventory.length)
                .map(i -> internalInventory.length - 1 - i)
                .mapToObj(i -> internalInventory[i])
                .filter(Objects::nonNull)
                .filter(patternMode::allows)
                .map(PatternSlot::getProcessingInventory),
            Stream.of(sharedConsumableInventory))
            .iterator();
    }

    @Override
    public void onBlockDestroyed() {
        refundAll(true);
        super.onBlockDestroyed();
    }

    public void refundAll(boolean shouldDrop) {
        for (int index = 0; index < internalInventory.length; index++) {
            PatternSlot<MTESuperCraftingInputME> slot = internalInventory[index];
            if (slot == null) continue;
            try {
                if (!slot.refund(getProxy(), getRequest(), shouldDrop)) {
                    GTLoggers.GT_FML_LOGGER.warn(
                        "Some inputs remain in TenkeiTech super pattern buffer slot {} after a refund attempt",
                        index);
                }
            } catch (GridAccessException exception) {
                GTLoggers.GT_FML_LOGGER.warn(
                    "Could not refund TenkeiTech super pattern buffer slot {} because its AE network is unavailable",
                    index);
                if (shouldDrop) slot.dropRemainingContents();
            }
        }
        if (!refundSharedConsumableStock(shouldDrop)) {
            GTLoggers.GT_FML_LOGGER
                .warn("Some shared consumable stock remains in TenkeiTech super pattern buffer after a refund attempt");
        }
        if (shouldDrop) {
            for (int index = 0; index < patternAugments.length; index++) {
                if (!refundPatternAugmentsOnDestruction(index)) {
                    GTLoggers.GT_FML_LOGGER.warn(
                        "Some catalysts remain in TenkeiTech super pattern buffer fixed slot {} after destruction",
                        index);
                }
            }
            refundSharedFluidCatalystsOnDestruction();
        }
    }

    private boolean refundPatternAugmentsOnDestruction(int patternSlot) {
        PatternAugmentData augmentData = patternAugments[patternSlot];
        if (augmentData.isEmpty()) return true;
        try {
            if (augmentData.hasItemCatalysts()) {
                IMEMonitor<IAEItemStack> itemStorage = getProxy().getStorage()
                    .getItemInventory();
                for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
                    ItemStack catalyst = augmentData.getItemCatalyst(i);
                    if (catalyst == null) continue;
                    IAEItemStack rest = Platform.poweredInsert(
                        getProxy().getEnergy(),
                        itemStorage,
                        AEApi.instance()
                            .storage()
                            .createItemStack(catalyst),
                        getRequest());
                    catalyst.stackSize = rest != null && rest.getStackSize() > 0 ? (int) rest.getStackSize() : 0;
                    if (Gregtech.machines.allowCribDropItems && catalyst.stackSize > 0) {
                        dropMachineItem(catalyst);
                        catalyst.stackSize = 0;
                    }
                    augmentData.setItemCatalyst(i, catalyst);
                }
            }

            if (augmentData.hasFluidCatalysts()) {
                IMEMonitor<IAEFluidStack> fluidStorage = getProxy().getStorage()
                    .getFluidInventory();
                for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
                    FluidStack catalyst = augmentData.getFluidCatalyst(i);
                    if (catalyst == null) continue;
                    IAEFluidStack rest = Platform.poweredInsert(
                        getProxy().getEnergy(),
                        fluidStorage,
                        AEApi.instance()
                            .storage()
                            .createFluidStack(catalyst),
                        getRequest());
                    catalyst.amount = rest != null && rest.getStackSize() > 0 ? (int) rest.getStackSize() : 0;
                    if (Gregtech.machines.allowCribDropItems && catalyst.amount > 0 && dropMachineFluid(catalyst)) {
                        catalyst.amount = 0;
                    }
                    augmentData.setFluidCatalyst(i, catalyst);
                }
            }
        } catch (GridAccessException exception) {
            GTLoggers.GT_FML_LOGGER.warn(
                "Could not refund catalysts in TenkeiTech super pattern buffer fixed slot {} because its AE network is unavailable",
                patternSlot);
            dropPatternAugments(augmentData);
        }
        return augmentData.isEmpty();
    }

    private void dropPatternAugments(PatternAugmentData augmentData) {
        if (!Gregtech.machines.allowCribDropItems) return;
        for (int i = 0; i < PatternAugmentData.CATALYST_SLOT_COUNT; i++) {
            ItemStack itemCatalyst = augmentData.getItemCatalyst(i);
            if (itemCatalyst != null) {
                dropMachineItem(itemCatalyst);
                augmentData.setItemCatalyst(i, null);
            }
            FluidStack fluidCatalyst = augmentData.getFluidCatalyst(i);
            if (fluidCatalyst != null && dropMachineFluid(fluidCatalyst)) {
                augmentData.setFluidCatalyst(i, null);
            }
        }
    }

    private void refundSharedFluidCatalystsOnDestruction() {
        if (sharedFluidCatalysts.isEmpty()) return;
        try {
            IMEMonitor<IAEFluidStack> storage = getProxy().getStorage()
                .getFluidInventory();
            for (int i = 0; i < FluidCatalystData.TANK_COUNT; i++) {
                FluidStack catalyst = sharedFluidCatalysts.get(i);
                if (catalyst == null) continue;
                IAEFluidStack rest = Platform.poweredInsert(
                    getProxy().getEnergy(),
                    storage,
                    AEApi.instance()
                        .storage()
                        .createFluidStack(catalyst),
                    getRequest());
                catalyst.amount = rest != null && rest.getStackSize() > 0 ? (int) rest.getStackSize() : 0;
                if (Gregtech.machines.allowCribDropItems && catalyst.amount > 0 && dropSharedFluid(catalyst)) {
                    catalyst.amount = 0;
                }
                sharedFluidCatalysts.set(i, catalyst);
            }
        } catch (GridAccessException exception) {
            GTLoggers.GT_FML_LOGGER.warn(
                "Could not refund shared fluid catalysts because the TenkeiTech super pattern buffer AE network is unavailable");
            if (!Gregtech.machines.allowCribDropItems) return;
            for (int i = 0; i < FluidCatalystData.TANK_COUNT; i++) {
                FluidStack catalyst = sharedFluidCatalysts.get(i);
                if (catalyst != null && dropSharedFluid(catalyst)) sharedFluidCatalysts.set(i, null);
            }
        }
    }

    private boolean dropSharedFluid(FluidStack stack) {
        ItemStack fluidPacket = ItemFluidPacket.newStack(stack);
        if (fluidPacket == null) return false;
        dropMachineItem(fluidPacket);
        return true;
    }

    private boolean dropMachineFluid(FluidStack stack) {
        ItemStack fluidPacket = ItemFluidPacket.newStack(stack);
        if (fluidPacket == null) return false;
        dropMachineItem(fluidPacket);
        return true;
    }

    private void dropMachineItem(ItemStack stack) {
        World world = getBaseMetaTileEntity().getWorld();
        EntityItem entityItem = new EntityItem(
            world,
            getBaseMetaTileEntity().getXCoord() + XSTR_INSTANCE.nextFloat() * 0.8F + 0.1F,
            getBaseMetaTileEntity().getYCoord() + XSTR_INSTANCE.nextFloat() * 0.8F + 0.1F,
            getBaseMetaTileEntity().getZCoord() + XSTR_INSTANCE.nextFloat() * 0.8F + 0.1F,
            GTUtility.copy(stack));
        entityItem.motionX = XSTR_INSTANCE.nextGaussian() * 0.05;
        entityItem.motionY = XSTR_INSTANCE.nextGaussian() * 0.25;
        entityItem.motionZ = XSTR_INSTANCE.nextGaussian() * 0.05;
        world.spawnEntityInWorld(entityItem);
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        invalidatePatternModeControllers();
        watchers.add(watcher);
        if (watcher instanceof MTEMultiBlockBase controller) invalidatePatternRecipeCache(controller);
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        invalidatePatternModeControllers();
        watchers.remove(watcher);
    }

    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP)) return;

        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return;

        this.saveToDataStick(aPlayer, dataStick);
    }

    public void saveToDataStick(EntityPlayer aPlayer, ItemStack dataStick) {
        IGregTechTileEntity aBaseMetaTileEntity = getBaseMetaTileEntity();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", "TenkeiTechSuperPatternBuffer");
        tag.setInteger("x", aBaseMetaTileEntity.getXCoord());
        tag.setInteger("y", aBaseMetaTileEntity.getYCoord());
        tag.setInteger("z", aBaseMetaTileEntity.getZCoord());
        tag.setInteger("dim", aBaseMetaTileEntity.getWorld().provider.dimensionId);

        dataStick.stackTagCompound = tag;
        dataStick.setStackDisplayName(
            "Crafting Input Buffer Link Data Stick (" + aBaseMetaTileEntity
                .getXCoord() + ", " + aBaseMetaTileEntity.getYCoord() + ", " + aBaseMetaTileEntity.getZCoord() + ")");
        aPlayer.addChatMessage(new ChatComponentText("Saved Link Data to Data Stick"));
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        final ItemStack is = aPlayer.inventory.getCurrentItem();
        if (is != null && is.getItem() instanceof ToolQuartzCuttingKnife) {
            if (ForgeEventFactory.onItemUseStart(aPlayer, is, 1) <= 0) return false;
            IGregTechTileEntity te = getBaseMetaTileEntity();
            aPlayer.openGui(
                AppEng.instance(),
                GuiBridge.GUI_RENAMER.ordinal() << 5 | (side.ordinal()),
                te.getWorld(),
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    @Override
    public ItemStack getCrafterIcon() {
        return getMachineCraftingIcon();
    }

    private boolean postMEPatternChange() {
        // don't post until it's active
        if (!getProxy().isActive()) return false;
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCraftingPatternChange(this, getProxy().getNode()));
        } catch (GridAccessException ignored) {
            return false;
        }
        return true;
    }

    protected ModularWindow createSlotManualWindow(final EntityPlayer player) {
        final int WIDTH = 68;
        final int HEIGHT = 68;
        final int PARENT_WIDTH = getGUIWidth();
        final int PARENT_HEIGHT = getGUIHeight();
        ModularWindow.Builder builder = ModularWindow.builder(WIDTH, HEIGHT);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());
        builder.setDraggable(true);
        // make sure the manual window is within the parent window
        // otherwise picking up manual items would toss them
        // See GuiContainer.java flag1
        builder.setPos(
            (size, window) -> Alignment.Center.getAlignedPos(size, new Size(PARENT_WIDTH, PARENT_HEIGHT))
                .add(Alignment.TopRight.getAlignedPos(new Size(PARENT_WIDTH, PARENT_HEIGHT), new Size(WIDTH, HEIGHT))));
        builder.widget(
            SlotGroup.ofItemHandler(inventoryHandler, 3)
                .startFromSlot(SLOT_MANUAL_START)
                .endAtSlot(SLOT_MANUAL_START + SLOT_MANUAL_SIZE - 1)
                .phantom(false)
                .background(getGUITextureSet().getItemSlot())
                .widgetCreator(SlotWidget::new)
                .build()
                .setPos(7, 7));
        return builder.build();
    }

    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aIndex >= 0 && aIndex < MAX_PATTERN_COUNT) {
            PatternSlot<MTESuperCraftingInputME> originalPattern = internalInventory[aIndex];
            IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
            if (originalPattern != null && baseMetaTileEntity != null
                && baseMetaTileEntity.isServerSide()
                && originalPattern.hasChanged(aStack, baseMetaTileEntity.getWorld())
                && !preparePatternSlotForEdit(aIndex)) {
                return;
            }
        }
        super.setInventorySlotContents(aIndex, aStack);
        if (aIndex >= MAX_PATTERN_COUNT) return;
        onPatternChange(aIndex, aStack);
        needPatternSync = true;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }

    @Override
    public void setCustomName(String name) {
        customName = name;
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
            if (slot != null && !slot.getProcessingInventory()
                .isEmpty() && patternMode.allows(slot)) return Optional.of(slot.getProcessingInventory());
        }
        return sharedConsumableInventory.isEmpty() ? Optional.empty() : Optional.of(sharedConsumableInventory);
    }

    @Override
    public boolean supportsFluids() {
        return this.supportFluids;
    }

    @Override
    public List<ItemStack> getItemsForHoloGlasses() {
        List<ItemStack> list = new ArrayList<>();
        for (PatternSlot<MTESuperCraftingInputME> slot : internalInventory) {
            if (slot == null) continue;
            if (slot.getPatternDetails() == null) continue;

            IAEItemStack[] outputs = slot.getPatternDetails()
                .getCondensedOutputs();
            if (outputs.length > 0 && outputs[0] != null) list.add(outputs[0].getItemStack());
        }
        return list;
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalizedFormatted(
            "tenkeitech.machine.super_pattern_buffer.desc",
            TIER_COLORS[10] + VN[10],
            MAX_PATTERN_COUNT,
            PAGE_COUNT,
            PATTERNS_PER_PAGE);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        if (index < SLOT_CIRCUIT) {
            // if its a pattern slot, only accept patterns
            return itemStack != null && itemStack.getItem() instanceof ICraftingPatternItem
                && super.isItemValidForSlot(index, itemStack);
        }
        return super.isItemValidForSlot(index, itemStack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }
}
