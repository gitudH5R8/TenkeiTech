/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import appeng.helpers.UltimatePatternHelper;

/** Immutable processing details retain sparse input indices for remote routing. */
public final class ResonatingPatternData extends UltimatePatternHelper {

    public static final String ROUTING = "tenkeitechResonating";
    public static final int MAX_INPUTS = 127;
    public static final int MAX_AMOUNT = 1024;
    public static final int MAX_FLUID_AMOUNT = Integer.MAX_VALUE;
    private final ItemStack encoded;
    private final List<Target> targets;

    public ResonatingPatternData(ItemStack stack, World world) {
        super(checkedCopy(stack));
        if (isCraftable() || canSubstitute()
            || isInputOnly()
            || getAEInputs().length > MAX_INPUTS
            || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey(ROUTING, 10)
            || stack.getTagCompound()
                .getCompoundTag(ROUTING)
                .getInteger("schema") != 1) {
            throw new IllegalArgumentException("Unsupported resonating pattern");
        }
        for (var input : getAEInputs()) {
            if (input != null && !supportedInput(input)) {
                throw new IllegalArgumentException("Unsupported resonating input");
            }
        }
        for (var output : getAEOutputs()) {
            if (output != null && !supportedMaterial(output))
                throw new IllegalArgumentException("Unsupported resonating output");
        }
        encoded = stack.copy();
        encoded.stackSize = 1;
        NBTTagCompound routes = encoded.getTagCompound()
            .getCompoundTag(ROUTING);
        List<Target> loaded = new ArrayList<>(getAEInputs().length);
        for (int slot = 0; slot < getAEInputs().length; slot++) {
            loaded.add(routes.hasKey("target" + slot, 10) ? Target.read(routes.getCompoundTag("target" + slot)) : null);
        }
        targets = Collections.unmodifiableList(loaded);
    }

    private static ItemStack checkedCopy(ItemStack stack) {
        if (!stack.hasTagCompound() || stack.getTagCompound()
            .getBoolean("crafting")) throw new IllegalArgumentException("Oversized resonating pattern");
        ItemStack copy = stack.copy();
        for (String key : new String[] { "in", "out" }) {
            var list = copy.getTagCompound()
                .getTagList(key, 10);
            if (list.tagCount() > MAX_INPUTS) throw new IllegalArgumentException("Oversized resonating pattern");
        }
        return copy;
    }

    public static boolean supportedMaterial(appeng.api.storage.data.IAEStack<?> stack) {
        return stack.getStackSize() > 0 && (stack instanceof appeng.api.storage.data.IAEFluidStack
            || stack instanceof appeng.api.storage.data.IAEItemStack item && !(item.getItemStack()
                .getItem() instanceof com.glodblock.github.common.item.ItemFluidPacket));
    }

    public static boolean supportedInput(appeng.api.storage.data.IAEStack<?> input) {
        if (!supportedMaterial(input)) return false;
        if (input instanceof appeng.api.storage.data.IAEFluidStack) return input.getStackSize() <= MAX_FLUID_AMOUNT;
        var itemInput = (appeng.api.storage.data.IAEItemStack) input;
        ItemStack item = itemInput.getItemStack();
        return input.getStackSize() > 0 && input.getStackSize() <= MAX_AMOUNT
            && input.getStackSize() <= 16L * Math.max(1, item.getMaxStackSize())
            && !(item.getItem() instanceof com.glodblock.github.common.item.ItemFluidPacket);
    }

    public static String displayName(appeng.api.storage.data.IAEStack<?> stack) {
        return stack.getDisplayName() + (stack.isFluid() ? " (mB)" : "");
    }

    public Target target(int slot) {
        return slot >= 0 && slot < targets.size() ? targets.get(slot) : null;
    }

    /** Indexed by input slot; null entries represent unbound or invalid targets. */
    public List<Target> targets() {
        return targets;
    }

    public static void setTarget(ItemStack stack, ResonatingPatternData details, int slot, Target target) {
        if (slot < 0 || slot >= details.targets.size() || details.getAEInputs()[slot] == null)
            throw new IllegalArgumentException("Invalid resonating input slot");
        NBTTagCompound routes = stack.getTagCompound()
            .getCompoundTag(ROUTING);
        if (target == null) routes.removeTag("target" + slot);
        else routes.setTag("target" + slot, target.write());
    }

    public boolean allInputsBound() {
        for (int i = 0; i < getAEInputs().length; i++) {
            if (getAEInputs()[i] != null && target(i) == null) return false;
        }
        return true;
    }

    public static int selected(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(ROUTING)
            .getInteger("selected") : 0;
    }

    public static void select(ItemStack stack, ResonatingPatternData details, int direction) {
        int count = details.getAEInputs().length;
        int slot = selectedInput(stack, details);
        for (int n = 0; n < count; n++) {
            slot = Math.floorMod(slot + (direction < 0 ? -1 : 1), count);
            if (details.getAEInputs()[slot] != null) break;
        }
        stack.getTagCompound()
            .getCompoundTag(ROUTING)
            .setInteger("selected", slot);
    }

    public static int selectedInput(ItemStack stack, ResonatingPatternData details) {
        int selected = selected(stack);
        if (selected >= 0 && selected < details.getAEInputs().length && details.getAEInputs()[selected] != null)
            return selected;
        for (int i = 0; i < details.getAEInputs().length; i++) if (details.getAEInputs()[i] != null) return i;
        throw new IllegalStateException("Pattern has no nonempty input");
    }

    public static final class Target {

        public final int dimension, x, y, z, side, metadata, metaTileId;
        public final UUID owner;
        public final String ownerName, block, tileClass;

        private Target(int dimension, int x, int y, int z, int side, int metadata, int metaTileId, UUID owner,
            String ownerName, String block, String tileClass) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.metadata = metadata;
            this.metaTileId = metaTileId;
            this.owner = owner;
            this.ownerName = ownerName;
            this.block = block;
            this.tileClass = tileClass;
        }

        public static Target capture(EntityPlayer player, World world, int x, int y, int z, int side) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile == null || side < 0 || side > 5) return null;
            return new Target(
                world.provider.dimensionId,
                x,
                y,
                z,
                side,
                world.getBlockMetadata(x, y, z),
                tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt ? gt.getMetaTileID() : -1,
                player.getUniqueID(),
                player.getCommandSenderName(),
                String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(x, y, z))),
                tile.getClass()
                    .getName());
        }

        public boolean samePosition(Target other) {
            return other != null && dimension == other.dimension
                && x == other.x
                && y == other.y
                && z == other.z
                && side == other.side;
        }

        public NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dimension", dimension);
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            tag.setInteger("side", side);
            tag.setInteger("metadata", metadata);
            tag.setInteger("metaTileId", metaTileId);
            tag.setString("owner", owner.toString());
            tag.setString("ownerName", ownerName);
            tag.setString("block", block);
            tag.setString("tileClass", tileClass);
            return tag;
        }

        public static Target read(NBTTagCompound tag) {
            try {
                for (String key : new String[] { "dimension", "x", "y", "z", "side", "metadata", "metaTileId" }) {
                    if (!tag.hasKey(key, 3)) return null;
                }
                if (tag.getInteger("metadata") < 0 || tag.getInteger("metadata") > 15
                    || Math.abs((long) tag.getInteger("x")) > 30000000
                    || Math.abs((long) tag.getInteger("z")) > 30000000) return null;
                int side = tag.getInteger("side");
                int y = tag.getInteger("y");
                String name = tag.getString("ownerName"), block = tag.getString("block"),
                    tile = tag.getString("tileClass");
                if (side < 0 || side > 5
                    || y < 0
                    || y > 255
                    || name.isEmpty()
                    || name.length() > 16
                    || block.isEmpty()
                    || block.length() > 256
                    || tile.isEmpty()
                    || tile.length() > 256) return null;
                return new Target(
                    tag.getInteger("dimension"),
                    tag.getInteger("x"),
                    y,
                    tag.getInteger("z"),
                    side,
                    tag.getInteger("metadata"),
                    tag.getInteger("metaTileId"),
                    UUID.fromString(tag.getString("owner")),
                    name,
                    block,
                    tile);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
