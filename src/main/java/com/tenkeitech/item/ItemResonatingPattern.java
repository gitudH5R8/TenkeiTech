/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.tenkeitech.resonating.ResonatingPatternData;
import com.tenkeitech.resonating.ResonatingTargetAccess;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemResonatingPattern extends appeng.items.misc.ItemEncodedPattern implements ICraftingPatternItem {

    public ItemResonatingPattern() {
        setUnlocalizedName("tenkeitech.resonating_pattern");
        setTextureName("tenkeitech:resonating_pattern");
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Binding cancellation belongs to our interaction handler; never invoke AE's blank-pattern conversion.
        return stack;
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        try {
            return new ResonatingPatternData(stack, world);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false;
        var details = getPatternForItem(stack, world);
        if (!(details instanceof ResonatingPatternData routed)) return false;
        int slot = ResonatingPatternData.selectedInput(stack, routed);
        stack.getTagCompound()
            .getCompoundTag(ResonatingPatternData.ROUTING)
            .setInteger("selected", slot);
        var target = ResonatingPatternData.Target.capture(player, world, x, y, z, side);
        if (target == null || !ResonatingTargetAccess.allowed(world, target, player, stack)) {
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.resonating.bind_denied"));
            return true;
        }
        boolean clear = target.samePosition(routed.target(slot));
        ResonatingPatternData.setTarget(stack, routed, slot, clear ? null : target);
        player.inventory.markDirty();
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        int start = lines.size();
        super.addCheckedInformation(stack, player, lines, advanced);
        if (!(getPatternForItem(stack, player.worldObj) instanceof ResonatingPatternData details)) {
            lines.add("\u00a7e" + StatCollector.translateToLocal("tenkeitech.resonating.conversion"));
            return;
        }
        if (!net.minecraft.client.gui.GuiScreen.isShiftKeyDown()) return;
        int condensedCount = details.getCondensedAEInputs().length;
        String heading = "\u00a72" + (condensedCount > 1 ? appeng.core.localization.GuiText.Ingredients
            : appeng.core.localization.GuiText.Ingredient).getLocal() + ": \u00a7r";
        int first = lines.subList(start, lines.size())
            .indexOf(heading);
        if (first < 0) return;
        first += start + 1;
        List<String> inputs = new ArrayList<>();
        for (int slot = 0; slot < details.getAEInputs().length; slot++) {
            var input = details.getAEInputs()[slot];
            if (input == null) continue;
            var target = details.target(slot);
            String route = target == null ? StatCollector.translateToLocal("tenkeitech.resonating.unset")
                : StatCollector.translateToLocalFormatted(
                    "tenkeitech.resonating.target",
                    target.dimension,
                    target.x,
                    target.y,
                    target.z,
                    StatCollector.translateToLocal("tenkeitech.resonating.face." + target.side));
            String name = appeng.util.Platform.getItemDisplayName(input);
            if (appeng.util.Platform.isGTLoaded && input instanceof appeng.api.storage.data.IAEItemStack item
                && item.getItem() instanceof gregtech.common.items.ItemIntegratedCircuit) {
                name += " " + item.getItemStack()
                    .getItemDamage();
            }
            inputs.add(
                "   \u00a7f" + NumberFormat.getNumberInstance()
                    .format(input.getStackSize())
                    + "\u00a7r\u00a7f"
                    + input.getStackType()
                        .getDisplayUnit()
                    + " \u00a7r"
                    + input.getStackType()
                        .getColorDefinition()
                    + name
                    + " \u00a77\u2192 "
                    + route);
        }
        lines.subList(first, first + condensedCount)
            .clear();
        lines.addAll(first, inputs);
        lines.set(
            first - 1,
            "\u00a72" + (inputs.size() > 1 ? appeng.core.localization.GuiText.Ingredients
                : appeng.core.localization.GuiText.Ingredient).getLocal() + ": \u00a7r");
    }
}
