/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ConstructableUtility;
import com.gtnewhorizon.structurelib.item.ItemConstructableTrigger;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.gui.BetterMachineProjectorGui;
import com.tenkeitech.machineprojector.BetterMachineProjectorEvents;
import com.tenkeitech.machineprojector.BetterMachineProjectorExecution;
import com.tenkeitech.machineprojector.BetterMachineProjectorSettings;

/** StructureLib trigger subclass retains native registry configuration and channel compatibility. */
public final class ItemBetterMachineProjector extends ItemConstructableTrigger implements IGuiHolder<GuiData> {

    public ItemBetterMachineProjector() {
        setUnlocalizedName(TenkeiTech.MOD_ID + ".better_machine_projector");
        setTextureName(TenkeiTech.MOD_ID + ":better_machine_projector");
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (getMovingObjectPositionFromPlayer(world, player, true) != null) return stack;
        if (player.isSneaking()) return super.onItemRightClick(stack, world, player);
        if (player instanceof EntityPlayerMP serverPlayer)
            BetterMachineProjectorEvents.requestSettings(serverPlayer, stack);
        return stack;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return ConstructableUtility
            .handle(BetterMachineProjectorSettings.trigger(stack), player, world, x, y, z, side);
        if (world.isRemote) {
            // Native survival errors mark existing client hints; regenerate them before the server reply arrives.
            if (BetterMachineProjectorSettings.mode(stack) == BetterMachineProjectorSettings.Mode.BUILD)
                return ConstructableUtility
                    .handle(BetterMachineProjectorSettings.trigger(stack), player, world, x, y, z, side);
            return false;
        }
        if (player instanceof EntityPlayerMP serverPlayer)
            return BetterMachineProjectorExecution.execute(stack, serverPlayer, world, x, y, z, side);
        return false;
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager manager, UISettings settings) {
        ItemStack stack = data instanceof PlayerInventoryGuiData inventoryData ? inventoryData.getUsedItemStack()
            : data.getPlayer()
                .getHeldItem();
        settings.canInteractWith(player -> BetterMachineProjectorSettings.owns(player, stack));
        return BetterMachineProjectorGui.build(data.getPlayer(), stack, manager);
    }

    @Override
    public ModularScreen createScreen(GuiData data, ModularPanel panel) {
        return new ModularScreen(TenkeiTech.MOD_ID, panel);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            super.addInformation(stack, player, tooltip, advanced);
            return;
        }
        tooltip.add(StatCollector.translateToLocal("tenkeitech.better_machine_projector.tooltip.use"));
        tooltip.add(StatCollector.translateToLocal("tenkeitech.better_machine_projector.tooltip.settings"));
        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "tenkeitech.better_machine_projector.tooltip.state",
                StatCollector.translateToLocal(
                    "tenkeitech.better_machine_projector.mode." + BetterMachineProjectorSettings.mode(stack)
                        .name()
                        .toLowerCase(java.util.Locale.ROOT)),
                BetterMachineProjectorSettings.repeat(stack),
                BetterMachineProjectorSettings.budget(stack),
                BetterMachineProjectorSettings.cooldown(stack)));
        tooltip.add(StatCollector.translateToLocal("tenkeitech.better_machine_projector.tooltip.hatches"));
        if (ChannelDataAccessor.hasSubChannel(stack))
            tooltip.add(StatCollector.translateToLocal("item.structurelib.constructableTrigger.desc.5"));
    }
}
