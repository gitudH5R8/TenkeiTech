/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.glodblock.github.util.FluidPatternDetails;
import com.tenkeitech.ModItems;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTUtility;

/**
 * Immutable, synthetic processing-pattern view for one scanned template recipe.
 *
 * <p>
 * The AE2 Fluid Craft details implementation is deliberately used as the processing-pattern
 * adapter: it supports mixed item/fluid AE stacks while keeping the pattern non-craftable, which is
 * the same semantic used by GT processing recipes. The backing item is our hidden pattern item, so
 * AE job persistence can resolve the stable {@link PatternKey} after a restart.
 * </p>
 */
public final class VirtualPatternDetails implements ICraftingPatternDetails {

    private static final String NBT_PATTERN_KEY = "tenkeitechAutoTemplatePatternKey";

    private final PatternKey key;
    private final VirtualPattern pattern;
    private final FluidPatternDetails delegate;
    private final ItemStack patternStack;
    private final IAEStack<?>[] aeInputs;
    private final IAEItemStack[] inputs;
    private final IAEStack<?>[] condensedAEInputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEStack<?>[] aeOutputs;
    private final IAEItemStack[] outputs;
    private final IAEStack<?>[] condensedAEOutputs;
    private final IAEItemStack[] condensedOutputs;

    public VirtualPatternDetails(VirtualPattern pattern) {
        if (pattern == null) throw new IllegalArgumentException("Virtual pattern cannot be null");
        this.pattern = pattern;
        this.key = pattern.getKey();
        this.patternStack = createPatternStack(key);
        this.delegate = new FluidPatternDetails(patternStack);
        IAEStack<?> input = pattern.getKey()
            .getInput()
            .getKind() == InputKind.FLUID ? AEApi.instance()
                .storage()
                .createFluidStack(pattern.getFluidInput())
                : AEApi.instance()
                    .storage()
                    .createItemStack(pattern.getItemInput());
        IAEStack<?> output = AEApi.instance()
            .storage()
            .createItemStack(pattern.getOutput());
        if (input == null || output == null
            || !delegate.setInputs(new IAEStack<?>[] { input })
            || !delegate.setOutputs(new IAEStack<?>[] { output })) {
            throw new IllegalStateException("Unable to initialize synthetic AE pattern " + key);
        }
        this.aeInputs = new IAEStack<?>[] { input.copy() };
        this.condensedAEInputs = copy(this.aeInputs);
        this.aeOutputs = new IAEStack<?>[] { output.copy() };
        this.condensedAEOutputs = copy(this.aeOutputs);

        // The auto-template catalog only exposes one input and one output per pattern. Keep the
        // item views locally instead of relying on FluidPatternDetails' stack conversion. AE2's
        // planner consumes the condensed AE view, while terminals and diagnostics still inspect
        // the legacy IAEItemStack arrays. For fluid inputs the adapter remains authoritative for
        // the legacy item projection (a fluid packet, when supported by AE2FC).
        if (input instanceof IAEItemStack itemInput) {
            this.inputs = new IAEItemStack[] { itemInput.copy() };
            this.condensedInputs = copy(this.inputs);
        } else {
            this.inputs = copy(delegate.getInputs());
            this.condensedInputs = copy(delegate.getCondensedInputs());
        }
        if (output instanceof IAEItemStack itemOutput) {
            this.outputs = new IAEItemStack[] { itemOutput.copy() };
            this.condensedOutputs = copy(this.outputs);
        } else {
            this.outputs = copy(delegate.getOutputs());
            this.condensedOutputs = copy(delegate.getCondensedOutputs());
        }
    }

    public PatternKey getKey() {
        return key;
    }

    public static ItemStack createPatternStack(PatternKey key) {
        if (ModItems.AUTO_TEMPLATE_PATTERN == null) {
            throw new IllegalStateException("Auto-template pattern item is not initialized");
        }
        ItemStack stack = new ItemStack(ModItems.AUTO_TEMPLATE_PATTERN, 1, 0);
        NBTTagCompound tag = key.writeToNBT();
        stack.setTagCompound(tag);
        return stack;
    }

    public static PatternKey readPatternKey(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_PATTERN_KEY)) {
            // Current schema stores the key fields at the root for compactness. Keep the marker
            // constant to make future nested schemas distinguishable without changing the item id.
            return PatternKey.readFromNBT(tag);
        }
        return PatternKey.readFromNBT(tag.getCompoundTag(NBT_PATTERN_KEY));
    }

    public static void writePatternKeyMarker(ItemStack stack, PatternKey key) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        root.setTag(NBT_PATTERN_KEY, key.writeToNBT());
        stack.setTagCompound(root);
    }

    @Override
    public ItemStack getPattern() {
        return patternStack.copy();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        if (slotIndex != 0 || itemStack == null
            || itemStack.stackSize <= 0
            || key.getInput()
                .getKind() != InputKind.INGOT)
            return false;
        ItemStack expected = pattern.getItemInput();
        if (expected == null) return false;
        expected.stackSize = 0;
        ItemStack candidate = itemStack.copy();
        candidate.stackSize = 0;
        return GTUtility.areStacksEqual(expected, candidate);
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, IAEStack<?> stack, World world) {
        if (!(stack instanceof IAEItemStack item)) return false;
        return isValidItemForSlot(slotIndex, item.getItemStack(), world);
    }

    @Override
    public boolean isCraftable() {
        return delegate.isCraftable();
    }

    @Override
    public IAEStack<?>[] getAEInputs() {
        return copy(aeInputs);
    }

    @Override
    public IAEItemStack[] getInputs() {
        return copy(inputs);
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return copy(condensedAEInputs);
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return copy(condensedInputs);
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return copy(condensedAEOutputs);
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return copy(condensedOutputs);
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return copy(aeOutputs);
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return copy(outputs);
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public boolean canBeSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting inventory, World world) {
        return null;
    }

    @Override
    public int getPriority() {
        return delegate.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        delegate.setPriority(priority);
    }

    @Override
    public boolean isInputOnly() {
        return false;
    }

    @Override
    public UUID getInputOnlyUuid() {
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof VirtualPatternDetails details && key.equals(details.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    private static IAEStack<?>[] copy(IAEStack<?>[] stacks) {
        if (stacks == null) return new IAEStack<?>[0];
        IAEStack<?>[] result = new IAEStack<?>[stacks.length];
        for (int index = 0; index < stacks.length; index++)
            result[index] = stacks[index] == null ? null : stacks[index].copy();
        return result;
    }

    private static IAEItemStack[] copy(IAEItemStack[] stacks) {
        if (stacks == null) return new IAEItemStack[0];
        IAEItemStack[] result = new IAEItemStack[stacks.length];
        for (int index = 0; index < stacks.length; index++)
            result[index] = stacks[index] == null ? null : stacks[index].copy();
        return result;
    }
}
