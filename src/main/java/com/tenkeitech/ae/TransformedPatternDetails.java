/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.ae;

import java.util.Arrays;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tenkeitech.item.ItemPatternCatalystMarker;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTUtility;

/**
 * Read-only AE pattern view. Integrated circuits and automatic-catalyst markers describe fixed machine state rather
 * than per-operation consumables, so neither is exposed to AE's ordinary recipe arithmetic.
 */
public final class TransformedPatternDetails implements ICraftingPatternDetails {

    private final ICraftingPatternDetails delegate;
    private final IAEStack<?>[] aeInputs;
    private final IAEItemStack[] inputs;
    private final IAEStack<?>[] condensedAEInputs;
    private final IAEItemStack[] condensedInputs;

    public TransformedPatternDetails(ICraftingPatternDetails delegate) {
        if (delegate == null) throw new IllegalArgumentException("Pattern details cannot be null");
        this.delegate = delegate;
        this.aeInputs = delegate.isCraftable() ? copy(delegate.getAEInputs())
            : filterVirtualInputs(delegate.getAEInputs());
        this.inputs = delegate.isCraftable() ? copy(delegate.getInputs()) : filterVirtualInputs(delegate.getInputs());
        this.condensedAEInputs = delegate.isCraftable() ? copy(delegate.getCondensedAEInputs())
            : filterVirtualInputs(delegate.getCondensedAEInputs());
        this.condensedInputs = delegate.isCraftable() ? copy(delegate.getCondensedInputs())
            : filterVirtualInputs(delegate.getCondensedInputs());
    }

    public ICraftingPatternDetails getRawDetails() {
        return delegate;
    }

    @Override
    public ItemStack getPattern() {
        ItemStack pattern = delegate.getPattern();
        return pattern == null ? null : pattern.copy();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return delegate.isValidItemForSlot(slotIndex, itemStack, world);
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, IAEStack<?> stack, World world) {
        return delegate.isValidItemForSlot(slotIndex, stack, world);
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
        return copy(delegate.getCondensedAEOutputs());
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return copy(delegate.getCondensedOutputs());
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return copy(delegate.getAEOutputs());
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return copy(delegate.getOutputs());
    }

    @Override
    public boolean canSubstitute() {
        return delegate.canSubstitute();
    }

    @Override
    public boolean canBeSubstitute() {
        return delegate.canBeSubstitute();
    }

    @Override
    public ItemStack getOutput(InventoryCrafting inventory, World world) {
        ItemStack output = delegate.getOutput(inventory, world);
        return output == null ? null : output.copy();
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
        return delegate.isInputOnly();
    }

    @Override
    public UUID getInputOnlyUuid() {
        return delegate.getInputOnlyUuid();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof TransformedPatternDetails transformed) return delegate.equals(transformed.delegate);
        return delegate.equals(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    private static IAEStack<?>[] filterVirtualInputs(IAEStack<?>[] stacks) {
        if (stacks == null || stacks.length == 0) return new IAEStack<?>[0];
        return Arrays.stream(stacks)
            .filter(stack -> stack != null && isEffectiveInput(stack))
            .map(IAEStack::copy)
            .toArray(IAEStack<?>[]::new);
    }

    private static IAEItemStack[] filterVirtualInputs(IAEItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) return new IAEItemStack[0];
        return Arrays.stream(stacks)
            .filter(stack -> stack != null && isEffectiveInput(stack))
            .map(IAEItemStack::copy)
            .toArray(IAEItemStack[]::new);
    }

    private static boolean isEffectiveInput(IAEStack<?> stack) {
        if (!(stack instanceof IAEItemStack itemStack)) return true;
        ItemStack item = itemStack.getItemStack();
        return !GTUtility.isAnyIntegratedCircuit(item) && !ItemPatternCatalystMarker.isMarker(item);
    }

    private static IAEStack<?>[] copy(IAEStack<?>[] stacks) {
        if (stacks == null || stacks.length == 0) return new IAEStack<?>[0];
        return Arrays.stream(stacks)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(IAEStack<?>[]::new);
    }

    private static IAEItemStack[] copy(IAEItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) return new IAEItemStack[0];
        return Arrays.stream(stacks)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(IAEItemStack[]::new);
    }
}
