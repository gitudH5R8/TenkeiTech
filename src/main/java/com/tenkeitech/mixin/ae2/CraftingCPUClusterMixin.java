/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.mixin.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.Constants;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tenkeitech.ae.TransformedPatternRecovery;
import com.tenkeitech.item.ItemPatternCatalystMarker;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;
import com.tenkeitech.machine.MTESuperCraftingInputME;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.CraftUpdateListener;
import appeng.container.ContainerNull;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.inv.MEInventoryCrafting;
import gregtech.GTLoggers;
import gregtech.api.util.GTUtility;

/**
 * Batches TenkeiTech processing-pattern dispatches and folds super-buffer automatic catalyst work into the CPU that
 * owns the original order. Fixed catalysts never enter ordinary pattern arithmetic: a missing catalyst is pulled into
 * the CPU directly when available, otherwise a one-item crafting branch is planned and attached transactionally.
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUClusterMixin {

    private static final long AUTOMATIC_CATALYST_RETRY_TICKS = 200;
    private static final String NBT_AUTOMATIC_CATALYSTS = "tenkeitechAutomaticCatalysts";
    private static final String NBT_AUTOMATIC_CATALYST_RESERVATIONS = "reservations";
    private static final int AUTOMATIC_CATALYST_SCHEMA = 1;

    @Shadow
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks;

    @Shadow
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> workableTasks;

    @Shadow
    protected MECraftingInventory inventory;

    @Shadow
    protected IItemList<IAEStack<?>> waitingFor;

    @Shadow
    protected IItemList<IAEStack<?>> waitingForMissing;

    @Shadow
    protected MachineSource machineSrc;

    @Shadow
    protected BaseActionSource currentJobSource;

    @Shadow
    protected long availableStorage;

    @Shadow
    protected long usedStorage;

    @Shadow
    protected boolean isMissingMode;

    @Shadow
    protected boolean waiting;

    @Shadow
    protected boolean somethingChanged;

    @Shadow
    protected String sourcePlayer;

    @Shadow
    protected List<CraftUpdateListener> craftUpdateListeners;

    @Shadow
    protected abstract boolean isCraftingDiagnosticsEnabled();

    @Shadow
    protected abstract void postChange(IAEStack<?> stack, BaseActionSource source);

    @Shadow
    protected abstract void postCraftingStatusChange(IAEStack<?> stack);

    @Shadow
    protected abstract void updateElapsedTime(IAEStack<?> stack);

    @Shadow
    protected abstract void recordReturnedOutputs(IAEStack<?> stack);

    @Shadow
    protected abstract void prepareStepCount();

    @Shadow
    protected abstract void markDirty();

    @Shadow
    protected abstract void updateCPU();

    @Shadow
    protected abstract EntityPlayerMP getPlayerByName(String playerName);

    @Unique
    private final TransformedPatternRecovery tenkeitech$patternRecovery = new TransformedPatternRecovery();

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void tenkeitech$saveTransformedTasks(NBTTagCompound tag, CallbackInfo callback) {
        tenkeitech$patternRecovery.write(tasks, tag);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void tenkeitech$restoreTransformedTasks(NBTTagCompound tag, CallbackInfo callback) {
        tenkeitech$patternRecovery.read(tasks, tag);
    }

    @Unique
    private ICraftingPatternDetails tenkeitech$batchedDetails;

    @Unique
    private long tenkeitech$batchFactor = 1;

    @Unique
    private Future<ICraftingJob> tenkeitech$automaticCatalystJob;

    @Unique
    private ItemStack tenkeitech$automaticCatalystRequest;

    @Unique
    private final List<ItemStack> tenkeitech$reservedAutomaticCatalysts = new ArrayList<>();

    @Unique
    private long tenkeitech$automaticCatalystRetryAfter;

    @Unique
    private boolean tenkeitech$capturingReservedCatalyst;

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void tenkeitech$writeAutomaticCatalystReservations(NBTTagCompound nbt, CallbackInfo callbackInfo) {
        if (tenkeitech$reservedAutomaticCatalysts.isEmpty()) {
            nbt.removeTag(NBT_AUTOMATIC_CATALYSTS);
            return;
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("schemaVersion", AUTOMATIC_CATALYST_SCHEMA);
        NBTTagList reservations = new NBTTagList();
        for (ItemStack catalyst : tenkeitech$reservedAutomaticCatalysts) {
            reservations.appendTag(GTUtility.saveItem(catalyst));
        }
        state.setTag(NBT_AUTOMATIC_CATALYST_RESERVATIONS, reservations);
        nbt.setTag(NBT_AUTOMATIC_CATALYSTS, state);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false)
    private void tenkeitech$readAutomaticCatalystReservations(NBTTagCompound nbt, CallbackInfo callbackInfo) {
        tenkeitech$reservedAutomaticCatalysts.clear();
        NBTTagCompound state = nbt.getCompoundTag(NBT_AUTOMATIC_CATALYSTS);
        if (state.getInteger("schemaVersion") != AUTOMATIC_CATALYST_SCHEMA) return;
        NBTTagList reservations = state.getTagList(NBT_AUTOMATIC_CATALYST_RESERVATIONS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < Math.min(reservations.tagCount(), 9); index++) {
            ItemStack catalyst;
            try {
                catalyst = GTUtility.loadItem(reservations.getCompoundTagAt(index));
            } catch (RuntimeException exception) {
                GTLoggers.GT_FML_LOGGER
                    .warn("Ignoring an invalid TenkeiTech automatic catalyst reservation", exception);
                continue;
            }
            if (catalyst == null || catalyst.getItem() == null
                || catalyst.stackSize <= 0
                || catalyst.getItem() instanceof ItemPatternCatalystMarker
                || catalyst.getItem() instanceof ItemUniversalWand
                || GTUtility.isAnyIntegratedCircuit(catalyst)) {
                continue;
            }
            catalyst.stackSize = 1;
            if (tenkeitech$findReservedCatalyst(catalyst) < 0) tenkeitech$reservedAutomaticCatalysts.add(catalyst);
        }
    }

    @Inject(method = "cancel", at = @At("RETURN"), remap = false)
    private void tenkeitech$clearAutomaticCatalystState(CallbackInfo callbackInfo) {
        tenkeitech$resetAutomaticCatalystState();
    }

    @Inject(method = "completeJob", at = @At("TAIL"), remap = false)
    private void tenkeitech$clearAutomaticCatalystStateAfterCompletion(CallbackInfo callbackInfo) {
        tenkeitech$resetAutomaticCatalystState();
    }

    @Unique
    private void tenkeitech$resetAutomaticCatalystState() {
        if (tenkeitech$automaticCatalystJob != null) tenkeitech$automaticCatalystJob.cancel(true);
        tenkeitech$automaticCatalystJob = null;
        tenkeitech$automaticCatalystRequest = null;
        tenkeitech$reservedAutomaticCatalysts.clear();
        tenkeitech$automaticCatalystRetryAfter = 0;
        tenkeitech$capturingReservedCatalyst = false;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void tenkeitech$prepareDispatch(IEnergyGrid energyGrid, CraftingGridCache craftingGridCache,
        CallbackInfo callbackInfo) {
        tenkeitech$clearBatch();
        tenkeitech$advanceAutomaticCatalystJob();
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), remap = false)
    private void tenkeitech$clearBatchAtReturn(IEnergyGrid energyGrid, CraftingGridCache craftingGridCache,
        CallbackInfo callbackInfo) {
        tenkeitech$clearBatch();
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void tenkeitech$reserveAutomaticCatalystOutput(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> callbackInfo) {
        if (tenkeitech$capturingReservedCatalyst || !(input instanceof IAEItemStack itemInput)
            || (mode != Actionable.SIMULATE && mode != Actionable.MODULATE)) {
            return;
        }

        int reservation = tenkeitech$findReservedCatalyst(itemInput.getItemStack());
        if (reservation < 0) return;
        IAEStack<?> expected = waitingFor.findPrecise(input);
        if (expected == null || expected.getStackSize() <= 0) return;

        long capturedAmount = Math.min(input.getStackSize(), expected.getStackSize());
        capturedAmount = Math.min(capturedAmount, 1);
        if (capturedAmount <= 0) return;

        IAEStack<?> remainder = input.copy();
        remainder.decStackSize(capturedAmount);
        if (mode == Actionable.MODULATE) {
            IAEStack<?> captured = input.copy()
                .setStackSize(capturedAmount);
            inventory.injectItems(captured, Actionable.MODULATE);
            waiting = false;
            postChange(expected, source);
            expected.decStackSize(capturedAmount);
            IAEStack<?> missing = waitingForMissing.findPrecise(input);
            if (missing != null) missing.decStackSize(Math.min(missing.getStackSize(), capturedAmount));
            updateElapsedTime(captured);
            recordReturnedOutputs(captured);
            postCraftingStatusChange(expected);
            for (CraftUpdateListener listener : craftUpdateListeners) listener.accept(1);
            somethingChanged = true;
            markDirty();
            tenkeitech$reservedAutomaticCatalysts.remove(reservation);
        }

        if (remainder.getStackSize() <= 0) {
            callbackInfo.setReturnValue(null);
            return;
        }

        tenkeitech$capturingReservedCatalyst = true;
        try {
            callbackInfo.setReturnValue(((CraftingCPUCluster) (Object) this).injectItems(remainder, mode, source));
        } finally {
            tenkeitech$capturingReservedCatalyst = false;
        }
    }

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z",
            remap = false),
        remap = false,
        require = 1)
    private boolean tenkeitech$pushPatternBatch(ICraftingMedium medium, ICraftingPatternDetails details,
        InventoryCrafting craftingTable, IEnergyGrid energyGrid, CraftingGridCache craftingGridCache) {
        tenkeitech$clearBatch();
        MTESuperCraftingInputME superPatternBuffer = medium instanceof MTESuperCraftingInputME superBuffer ? superBuffer
            : null;
        if ((superPatternBuffer == null && !(medium instanceof MTEHatchAutoTemplateInputME)) || details.isCraftable()
            || !(craftingTable instanceof MEInventoryCrafting meCraftingTable)) {
            return medium.pushPattern(details, craftingTable);
        }
        if (superPatternBuffer != null
            && !tenkeitech$ensureAutomaticCatalystsAvailable(superPatternBuffer, details, craftingGridCache))
            return false;
        if (isCraftingDiagnosticsEnabled()) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        CraftingCPUCluster.TaskProgress taskProgress = workableTasks.get(details);
        if (taskProgress == null) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        CraftingTaskProgressAccessor progressAccessor = (CraftingTaskProgressAccessor) (Object) taskProgress;
        long factor = progressAccessor.tenkeitech$getValue();
        if (factor <= 1) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        IAEStack<?>[] originalInputs = tenkeitech$copyInputs(meCraftingTable);
        List<BatchInput> aggregateInputs = tenkeitech$aggregateInputs(originalInputs);
        if (aggregateInputs == null || aggregateInputs.isEmpty()) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        factor = tenkeitech$capForLongStackSizes(
            factor,
            originalInputs,
            aggregateInputs,
            details.getCondensedAEOutputs());
        factor = tenkeitech$capForAvailableInputs(factor, aggregateInputs);
        double powerPerCraft = tenkeitech$calculatePatternPower(originalInputs);
        factor = tenkeitech$capForAvailablePower(factor, powerPerCraft, energyGrid);
        if (factor <= 1) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        List<IAEStack<?>> extractedInputs = tenkeitech$extractAdditionalInputs(aggregateInputs, factor - 1);
        if (extractedInputs == null) {
            return tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        }

        tenkeitech$scaleCraftingTable(meCraftingTable, originalInputs, factor);
        final boolean accepted;
        try {
            accepted = tenkeitech$pushSupportedPattern(medium, details, meCraftingTable);
        } catch (RuntimeException exception) {
            tenkeitech$restoreAfterRejectedPush(meCraftingTable, originalInputs, extractedInputs);
            throw exception;
        }
        if (!accepted) {
            tenkeitech$restoreAfterRejectedPush(meCraftingTable, originalInputs, extractedInputs);
            return false;
        }

        for (IAEStack<?> extractedInput : extractedInputs) postChange(extractedInput, machineSrc);
        double extraPower = powerPerCraft * (factor - 1);
        if (extraPower > 0) {
            energyGrid.extractAEPower(extraPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        progressAccessor.tenkeitech$setValue(progressAccessor.tenkeitech$getValue() - (factor - 1));
        tenkeitech$batchedDetails = details;
        tenkeitech$batchFactor = factor;
        return true;
    }

    @Unique
    private boolean tenkeitech$pushSupportedPattern(ICraftingMedium medium, ICraftingPatternDetails details,
        MEInventoryCrafting craftingTable) {
        if (medium instanceof MTESuperCraftingInputME superPatternBuffer) {
            return tenkeitech$pushWithAutomaticCatalysts(superPatternBuffer, details, craftingTable);
        }
        return medium.pushPattern(details, craftingTable);
    }

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingPatternDetails;getCondensedAEOutputs()[Lappeng/api/storage/data/IAEStack;",
            remap = false),
        remap = false,
        require = 1)
    private IAEStack<?>[] tenkeitech$scaleExpectedOutputs(ICraftingPatternDetails details) {
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (details != tenkeitech$batchedDetails || tenkeitech$batchFactor <= 1) return outputs;

        IAEStack<?>[] scaledOutputs = new IAEStack<?>[outputs.length];
        for (int index = 0; index < outputs.length; index++) {
            IAEStack<?> output = outputs[index];
            if (output == null) continue;
            scaledOutputs[index] = output.copy()
                .setStackSize(output.getStackSize() * tenkeitech$batchFactor);
        }
        tenkeitech$clearBatch();
        return scaledOutputs;
    }

    @Unique
    private boolean tenkeitech$ensureAutomaticCatalystsAvailable(MTESuperCraftingInputME medium,
        ICraftingPatternDetails details, CraftingGridCache craftingGridCache) {
        List<ItemStack> missing = medium.getMissingAutomaticItemCatalysts(details);
        if (missing.isEmpty()) return true;

        for (ItemStack catalyst : missing) {
            if (tenkeitech$cpuContains(catalyst)) continue;
            if (tenkeitech$hasPendingAutomaticCatalyst(catalyst)) return false;
            if (tenkeitech$pullCatalystFromNetwork(catalyst)) continue;
            tenkeitech$scheduleAutomaticCatalyst(catalyst, medium, craftingGridCache);
            return false;
        }
        return true;
    }

    @Unique
    private boolean tenkeitech$pushWithAutomaticCatalysts(MTESuperCraftingInputME medium,
        ICraftingPatternDetails details, MEInventoryCrafting craftingTable) {
        List<ItemStack> missing = medium.getMissingAutomaticItemCatalysts(details);
        if (missing.isEmpty()) return medium.pushPattern(details, craftingTable);

        List<IAEStack<?>> catalysts = new ArrayList<>(missing.size());
        for (ItemStack catalyst : missing) {
            IAEItemStack request = tenkeitech$createCatalystStack(catalyst);
            IAEStack<?> extracted = tenkeitech$extractExact(inventory, request);
            if (extracted == null || extracted.getStackSize() != 1) {
                if (extracted != null) catalysts.add(extracted);
                tenkeitech$returnToCpuInventory(catalysts);
                return false;
            }
            catalysts.add(extracted);
        }

        MEInventoryCrafting augmented = new MEInventoryCrafting(
            new ContainerNull(),
            craftingTable.getSizeInventory() + catalysts.size(),
            1);
        for (int index = 0; index < craftingTable.getSizeInventory(); index++) {
            IAEStack<?> input = craftingTable.getAEStackInSlot(index);
            if (input != null) augmented.setInventorySlotContents(index, input.copy());
        }
        for (int index = 0; index < catalysts.size(); index++) {
            augmented.setInventorySlotContents(
                craftingTable.getSizeInventory() + index,
                catalysts.get(index)
                    .copy());
        }

        final boolean accepted;
        try {
            accepted = medium.pushPattern(details, augmented);
        } catch (RuntimeException exception) {
            tenkeitech$returnToCpuInventory(catalysts);
            throw exception;
        }
        if (!accepted) {
            tenkeitech$returnToCpuInventory(catalysts);
            return false;
        }

        for (IAEStack<?> catalyst : catalysts) postChange(catalyst, machineSrc);
        return true;
    }

    @Unique
    private boolean tenkeitech$cpuContains(ItemStack catalyst) {
        IAEItemStack request = tenkeitech$createCatalystStack(catalyst);
        IAEStack<?> available = inventory.extractItems(request, Actionable.SIMULATE);
        return available != null && available.getStackSize() >= 1;
    }

    @Unique
    private boolean tenkeitech$pullCatalystFromNetwork(ItemStack catalyst) {
        IGrid grid = ((CraftingCPUCluster) (Object) this).getGrid();
        if (grid == null) return false;
        IStorageGrid storageGrid = (IStorageGrid) grid.getCache(IStorageGrid.class);
        IAEItemStack request = tenkeitech$createCatalystStack(catalyst);
        BaseActionSource source = currentJobSource == null ? machineSrc : currentJobSource;
        IAEItemStack extracted = storageGrid.getItemInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() != 1) return false;
        ((CraftingCPUCluster) (Object) this).addStorage(extracted);
        return true;
    }

    @Unique
    private void tenkeitech$scheduleAutomaticCatalyst(ItemStack catalyst, MTESuperCraftingInputME medium,
        CraftingGridCache craftingGridCache) {
        long currentTick = CraftingCPUCluster.getServerTick();
        if (tenkeitech$automaticCatalystJob != null || tenkeitech$automaticCatalystRequest != null
            || currentTick < tenkeitech$automaticCatalystRetryAfter) {
            return;
        }
        IGrid grid = ((CraftingCPUCluster) (Object) this).getGrid();
        if (grid == null || medium.getBaseMetaTileEntity() == null) return;

        try {
            IAEItemStack request = tenkeitech$createCatalystStack(catalyst);
            BaseActionSource source = currentJobSource == null ? machineSrc : currentJobSource;
            tenkeitech$automaticCatalystRequest = catalyst.copy();
            tenkeitech$automaticCatalystJob = craftingGridCache.beginCraftingJob(
                medium.getBaseMetaTileEntity()
                    .getWorld(),
                grid,
                source,
                request,
                null);
            tenkeitech$notifyPlayer("tenkeitech.chat.automatic_catalyst.started", catalyst.getDisplayName());
        } catch (RuntimeException exception) {
            GTLoggers.GT_FML_LOGGER.warn("Failed to plan an automatic catalyst branch for {}", catalyst, exception);
            tenkeitech$failAutomaticCatalyst("tenkeitech.chat.automatic_catalyst.failed", catalyst.getDisplayName());
        }
    }

    @Unique
    private void tenkeitech$advanceAutomaticCatalystJob() {
        Future<ICraftingJob> future = tenkeitech$automaticCatalystJob;
        if (future == null || !future.isDone()) return;

        ItemStack requested = tenkeitech$automaticCatalystRequest;
        try {
            ICraftingJob job = future.get();
            if (job == null || job.isSimulation()) {
                tenkeitech$failAutomaticCatalyst(
                    "tenkeitech.chat.automatic_catalyst.missing",
                    requested == null ? "?" : requested.getDisplayName());
                return;
            }
            IAEStack<?> output = job.getOutput();
            if (requested == null || !(output instanceof IAEItemStack itemOutput)
                || output.getStackSize() <= 0
                || !ItemPatternCatalystMarker.isSameCatalyst(requested, itemOutput.getItemStack())) {
                tenkeitech$failAutomaticCatalyst(
                    "tenkeitech.chat.automatic_catalyst.failed",
                    requested == null ? "?" : requested.getDisplayName());
                return;
            }
            if (!job.supportsCPUCluster((CraftingCPUCluster) (Object) this)) {
                tenkeitech$failAutomaticCatalyst(
                    "tenkeitech.chat.automatic_catalyst.failed",
                    requested.getDisplayName());
                return;
            }
            if (!tenkeitech$attachAutomaticCatalystJob(job)) {
                tenkeitech$failAutomaticCatalyst(
                    "tenkeitech.chat.automatic_catalyst.capacity",
                    requested == null ? "?" : requested.getDisplayName());
                return;
            }
            if (requested != null && tenkeitech$findReservedCatalyst(requested) < 0
                && tenkeitech$reservedAutomaticCatalysts.size() < 9) {
                tenkeitech$reservedAutomaticCatalysts.add(requested.copy());
            }
            tenkeitech$automaticCatalystJob = null;
            tenkeitech$automaticCatalystRequest = null;
        } catch (CancellationException | ExecutionException exception) {
            GTLoggers.GT_FML_LOGGER.warn("Automatic catalyst planning failed", exception);
            tenkeitech$failAutomaticCatalyst(
                "tenkeitech.chat.automatic_catalyst.failed",
                requested == null ? "?" : requested.getDisplayName());
        } catch (InterruptedException exception) {
            Thread.currentThread()
                .interrupt();
            tenkeitech$failAutomaticCatalyst(
                "tenkeitech.chat.automatic_catalyst.failed",
                requested == null ? "?" : requested.getDisplayName());
        }
    }

    @Unique
    private boolean tenkeitech$attachAutomaticCatalystJob(ICraftingJob job) {
        long byteCost = job.getByteTotal();
        if (byteCost < 0 || byteCost > availableStorage - usedStorage) return false;
        IGrid grid = ((CraftingCPUCluster) (Object) this).getGrid();
        if (grid == null) return false;

        IStorageGrid storageGrid = (IStorageGrid) grid.getCache(IStorageGrid.class);
        MECraftingInventory staging = new MECraftingInventory(storageGrid, true, false, false);
        staging.setMissingMode(false);
        staging.setCpuInventory(inventory);

        MECraftingInventory inventoryBackup = new MECraftingInventory(inventory);
        IItemList<IAEStack<?>> missingBackup = AEApi.instance()
            .storage()
            .createAEStackList();
        for (IAEStack<?> missing : waitingForMissing) missingBackup.add(missing.copy());
        List<TaskBackup> taskBackups = new ArrayList<>(tasks.size());
        for (Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : tasks.entrySet()) {
            CraftingTaskProgressAccessor progress = (CraftingTaskProgressAccessor) (Object) entry.getValue();
            CraftingCPUCluster.TaskProgress snapshot = new CraftingCPUCluster.TaskProgress();
            CraftingTaskProgressAccessor snapshotAccessor = (CraftingTaskProgressAccessor) (Object) snapshot;
            snapshotAccessor.tenkeitech$setValue(progress.tenkeitech$getValue());
            snapshotAccessor.tenkeitech$copyDiagnosticSessionsFrom(entry.getValue());
            taskBackups.add(new TaskBackup(entry.getKey(), snapshot));
        }
        boolean oldMissingMode = isMissingMode;
        long oldUsedStorage = usedStorage;
        BaseActionSource source = currentJobSource == null ? machineSrc : currentJobSource;

        try {
            job.startCrafting(staging, (CraftingCPUCluster) (Object) this, source);
            if (!staging.commit(source)) {
                tenkeitech$restoreAutomaticCatalystAttach(
                    inventoryBackup,
                    missingBackup,
                    taskBackups,
                    oldMissingMode,
                    oldUsedStorage);
                return false;
            }
        } catch (RuntimeException exception) {
            tenkeitech$restoreAutomaticCatalystAttach(
                inventoryBackup,
                missingBackup,
                taskBackups,
                oldMissingMode,
                oldUsedStorage);
            GTLoggers.GT_FML_LOGGER
                .warn("Failed to attach an automatic catalyst branch to the active AE CPU", exception);
            return false;
        }
        tenkeitech$commitAutomaticCatalystExpectations(missingBackup);
        usedStorage += byteCost;
        prepareStepCount();
        markDirty();
        updateCPU();
        return true;
    }

    @Unique
    private void tenkeitech$commitAutomaticCatalystExpectations(IItemList<IAEStack<?>> previousMissing) {
        for (IAEStack<?> expected : waitingForMissing) {
            IAEStack<?> previous = previousMissing.findPrecise(expected);
            long added = expected.getStackSize() - (previous == null ? 0 : previous.getStackSize());
            if (added <= 0) continue;
            IAEStack<?> delta = expected.copy()
                .setStackSize(added);
            waitingFor.add(delta);
            postCraftingStatusChange(delta);
        }
    }

    @Unique
    private void tenkeitech$restoreAutomaticCatalystAttach(MECraftingInventory inventoryBackup,
        IItemList<IAEStack<?>> missingBackup, List<TaskBackup> taskBackups, boolean oldMissingMode,
        long oldUsedStorage) {
        inventory = inventoryBackup;
        waitingForMissing = missingBackup;
        isMissingMode = oldMissingMode;
        usedStorage = oldUsedStorage;
        tasks.clear();
        for (TaskBackup backup : taskBackups) {
            tasks.put(backup.details, backup.progress);
        }
    }

    @Unique
    private void tenkeitech$failAutomaticCatalyst(String messageKey, Object... arguments) {
        tenkeitech$automaticCatalystJob = null;
        tenkeitech$automaticCatalystRequest = null;
        tenkeitech$automaticCatalystRetryAfter = CraftingCPUCluster.getServerTick() + AUTOMATIC_CATALYST_RETRY_TICKS;
        tenkeitech$notifyPlayer(messageKey, arguments);
    }

    @Unique
    private void tenkeitech$notifyPlayer(String messageKey, Object... arguments) {
        if (sourcePlayer == null || sourcePlayer.isEmpty()) return;
        EntityPlayerMP player = getPlayerByName(sourcePlayer);
        if (player != null) player.addChatMessage(new ChatComponentTranslation(messageKey, arguments));
    }

    @Unique
    private boolean tenkeitech$hasPendingAutomaticCatalyst(ItemStack catalyst) {
        if (ItemPatternCatalystMarker.isSameCatalyst(catalyst, tenkeitech$automaticCatalystRequest)) return true;
        return tenkeitech$findReservedCatalyst(catalyst) >= 0;
    }

    @Unique
    private int tenkeitech$findReservedCatalyst(ItemStack catalyst) {
        for (int index = 0; index < tenkeitech$reservedAutomaticCatalysts.size(); index++) {
            if (ItemPatternCatalystMarker.isSameCatalyst(catalyst, tenkeitech$reservedAutomaticCatalysts.get(index))) {
                return index;
            }
        }
        return -1;
    }

    @Unique
    private static IAEItemStack tenkeitech$createCatalystStack(ItemStack catalyst) {
        IAEItemStack stack = AEApi.instance()
            .storage()
            .createItemStack(catalyst);
        stack.setStackSize(1);
        return stack;
    }

    @Unique
    private void tenkeitech$clearBatch() {
        tenkeitech$batchedDetails = null;
        tenkeitech$batchFactor = 1;
    }

    @Unique
    private static IAEStack<?>[] tenkeitech$copyInputs(MEInventoryCrafting craftingTable) {
        IAEStack<?>[] inputs = new IAEStack<?>[craftingTable.getSizeInventory()];
        for (int index = 0; index < inputs.length; index++) {
            IAEStack<?> input = craftingTable.getAEStackInSlot(index);
            if (input != null) inputs[index] = input.copy();
        }
        return inputs;
    }

    @Unique
    private static List<BatchInput> tenkeitech$aggregateInputs(IAEStack<?>[] inputs) {
        List<BatchInput> aggregateInputs = new ArrayList<>();
        for (IAEStack<?> input : inputs) {
            if (input == null) continue;
            long amount = input.getStackSize();
            if (amount <= 0) return null;

            BatchInput matchingInput = null;
            for (BatchInput aggregateInput : aggregateInputs) {
                if (aggregateInput.template.isSameType(input)) {
                    matchingInput = aggregateInput;
                    break;
                }
            }
            if (matchingInput == null) {
                aggregateInputs.add(new BatchInput(input.copy(), amount));
            } else {
                if (matchingInput.amountPerCraft > Long.MAX_VALUE - amount) return null;
                matchingInput.amountPerCraft += amount;
            }
        }
        return aggregateInputs;
    }

    @Unique
    private static long tenkeitech$capForLongStackSizes(long factor, IAEStack<?>[] inputs,
        List<BatchInput> aggregateInputs, IAEStack<?>[] outputs) {
        for (IAEStack<?> input : inputs) {
            if (input != null) factor = Math.min(factor, Long.MAX_VALUE / input.getStackSize());
        }
        for (BatchInput input : aggregateInputs) {
            factor = Math.min(factor, Long.MAX_VALUE / input.amountPerCraft);
        }
        for (IAEStack<?> output : outputs) {
            if (output != null && output.getStackSize() > 0) {
                factor = Math.min(factor, Long.MAX_VALUE / output.getStackSize());
            }
        }
        return factor;
    }

    @Unique
    private long tenkeitech$capForAvailableInputs(long factor, List<BatchInput> aggregateInputs) {
        for (BatchInput input : aggregateInputs) {
            IAEStack<?> available = inventory.getAvailableItem(input.template);
            long availableAmount = available == null ? 0 : available.getStackSize();
            long additionalCrafts = availableAmount / input.amountPerCraft;
            long availableFactor = additionalCrafts == Long.MAX_VALUE ? Long.MAX_VALUE : additionalCrafts + 1;
            factor = Math.min(factor, availableFactor);
        }
        return factor;
    }

    @Unique
    private static double tenkeitech$calculatePatternPower(IAEStack<?>[] inputs) {
        double patternPower = 0;
        for (IAEStack<?> input : inputs) {
            if (input != null && input.getAmountPerUnit() > 0) {
                patternPower += (double) input.getStackSize() / input.getAmountPerUnit();
            }
        }
        return patternPower;
    }

    @Unique
    private static long tenkeitech$capForAvailablePower(long factor, double powerPerCraft, IEnergyGrid energyGrid) {
        if (factor <= 1 || powerPerCraft <= 0) return factor;
        double requestedPower = powerPerCraft * factor;
        double availablePower = energyGrid.extractAEPower(requestedPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (availablePower >= requestedPower - 0.01) return factor;
        long energyFactor = (long) Math.floor((availablePower + 0.01) / powerPerCraft);
        return Math.max(1, Math.min(factor, energyFactor));
    }

    @Unique
    private List<IAEStack<?>> tenkeitech$extractAdditionalInputs(List<BatchInput> aggregateInputs,
        long additionalCrafts) {
        List<IAEStack<?>> extractedInputs = new ArrayList<>(aggregateInputs.size());
        for (BatchInput input : aggregateInputs) {
            IAEStack<?> request = input.template.copy()
                .setStackSize(input.amountPerCraft * additionalCrafts);
            IAEStack<?> extracted = tenkeitech$extractExact(inventory, request);
            if (extracted == null || extracted.getStackSize() != request.getStackSize()) {
                if (extracted != null) extractedInputs.add(extracted);
                tenkeitech$returnToCpuInventory(extractedInputs);
                return null;
            }
            extractedInputs.add(extracted);
        }
        return extractedInputs;
    }

    @Unique
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IAEStack<?> tenkeitech$extractExact(MECraftingInventory craftingInventory, IAEStack<?> request) {
        return craftingInventory.extractItems((IAEStack) request, Actionable.MODULATE);
    }

    @Unique
    private static void tenkeitech$scaleCraftingTable(MEInventoryCrafting craftingTable, IAEStack<?>[] originalInputs,
        long factor) {
        for (int index = 0; index < originalInputs.length; index++) {
            IAEStack<?> input = originalInputs[index];
            if (input != null) {
                craftingTable.setInventorySlotContents(
                    index,
                    input.copy()
                        .setStackSize(input.getStackSize() * factor));
            }
        }
    }

    @Unique
    private void tenkeitech$restoreAfterRejectedPush(MEInventoryCrafting craftingTable, IAEStack<?>[] originalInputs,
        List<IAEStack<?>> extractedInputs) {
        for (int index = 0; index < originalInputs.length; index++) {
            IAEStack<?> input = originalInputs[index];
            craftingTable.setInventorySlotContents(index, input == null ? null : input.copy());
        }
        tenkeitech$returnToCpuInventory(extractedInputs);
    }

    @Unique
    private void tenkeitech$returnToCpuInventory(List<IAEStack<?>> extractedInputs) {
        for (IAEStack<?> extracted : extractedInputs) {
            inventory.injectItems(extracted, Actionable.MODULATE);
        }
    }

    @Unique
    private static final class BatchInput {

        private final IAEStack<?> template;
        private long amountPerCraft;

        private BatchInput(IAEStack<?> template, long amountPerCraft) {
            this.template = template;
            this.amountPerCraft = amountPerCraft;
        }
    }

    @Unique
    private static final class TaskBackup {

        private final ICraftingPatternDetails details;
        private final CraftingCPUCluster.TaskProgress progress;

        private TaskBackup(ICraftingPatternDetails details, CraftingCPUCluster.TaskProgress progress) {
            this.details = details;
            this.progress = progress;
        }
    }
}
