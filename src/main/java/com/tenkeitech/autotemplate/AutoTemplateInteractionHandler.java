/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputME;
import com.tenkeitech.machine.MTEHatchAutoTemplateInputProxy;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Prepares world catalogs and handles rebinding before vanilla suppresses sneak-use block interaction. */
public final class AutoTemplateInteractionHandler {

    private static final AutoTemplateInteractionHandler INSTANCE = new AutoTemplateInteractionHandler();
    private static boolean registered;

    private AutoTemplateInteractionHandler() {}

    public static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        AutoTemplateRecipeRegistry.initializeForWorld();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.isCanceled()
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            || event.entityPlayer == null
            || !event.entityPlayer.isSneaking()
            || event.world == null
            || event.world.isRemote
            || event.useBlock == Event.Result.DENY) {
            return;
        }

        ItemStack held = event.entityPlayer.getHeldItem();
        TemplateType heldTemplate = TemplateType.fromTemplateStack(held);
        if (heldTemplate == TemplateType.NONE) return;

        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        if (tile == null || tile.isInvalid()) return;
        if (!(tile instanceof IGregTechTileEntity gtTile)) return;
        Object target = gtTile.getMetaTileEntity();
        MTEHatchAutoTemplateInputME machine = target instanceof MTEHatchAutoTemplateInputME direct ? direct
            : target instanceof MTEHatchAutoTemplateInputProxy proxy ? proxy.getMaster() : null;
        if (machine == null || machine.hasCircuitSelector()) return;
        if (!gtTile.canAccessData() || (target == machine ? machine.getBaseMetaTileEntity() != gtTile
            : !(target instanceof MTEHatchAutoTemplateInputProxy proxy) || proxy.getBaseMetaTileEntity() != gtTile
                || proxy.getMaster() != machine)
            || !machine.isAccessAllowed(event.entityPlayer)) return;

        event.useBlock = Event.Result.DENY;
        event.useItem = Event.Result.DENY;
        event.setCanceled(true);

        boolean bound;
        try {
            bound = machine.bindTemplate(heldTemplate);
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Ignoring failed auto-template input rebind during player interaction", exception);
            bound = false;
        }
        EntityPlayer player = event.entityPlayer;
        ItemStack display = heldTemplate.getTemplateStack();
        player.addChatMessage(
            new ChatComponentTranslation(
                bound ? "tenkeitech.machine.auto_template_input.bind_success"
                    : "tenkeitech.machine.auto_template_input.bind_failed",
                display == null ? heldTemplate.name() : display.getDisplayName()));
    }
}
