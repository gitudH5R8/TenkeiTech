/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.tenkeitech.ModItems;
import com.tenkeitech.resonating.ResonatingBindingCardData;
import com.tenkeitech.resonating.ResonatingNetwork;
import com.tenkeitech.resonating.ResonatingPatternData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Client-only input and a loaded-target outline; no client state authorizes delivery. */
public final class ResonatingPatternClient {

    private ItemStack cachedPattern;
    private ResonatingPatternData cachedDetails;
    private ItemStack cachedCard;
    private ResonatingPatternData.Target[] cachedCardTargets;

    private ResonatingPatternData.Target[] cardTargets(Minecraft mc) {
        var held = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != ModItems.RESONATING_BINDING_CARD
            || !ResonatingBindingCardData.valid(held)) {
            cachedCard = null;
            cachedCardTargets = null;
            return null;
        }
        if (cachedCard == null || !ItemStack.areItemStackTagsEqual(held, cachedCard)) {
            cachedCard = held.copy();
            cachedCardTargets = new ResonatingPatternData.Target[ResonatingBindingCardData.SLOTS];
            for (int slot = 0; slot < cachedCardTargets.length; slot++) {
                cachedCardTargets[slot] = ResonatingBindingCardData
                    .target(held, ResonatingBindingCardData.fluid(held), slot);
            }
        }
        return cachedCardTargets;
    }

    private ResonatingPatternData details(Minecraft mc) {
        var held = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != ModItems.RESONATING_PATTERN) {
            cachedPattern = null;
            cachedDetails = null;
            return null;
        }
        if (cachedPattern == null || !held.isItemEqual(cachedPattern)
            || !ItemStack.areItemStackTagsEqual(held, cachedPattern)) {
            cachedPattern = held.copy();
            cachedDetails = ModItems.RESONATING_PATTERN
                .getPatternForItem(held, mc.theWorld) instanceof ResonatingPatternData parsed ? parsed : null;
        }
        return cachedDetails;
    }

    @SubscribeEvent
    public void scroll(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.thePlayer == null || !mc.thePlayer.isSneaking() || event.dwheel == 0) return;
        var held = mc.thePlayer.getHeldItem();
        if (held == null) return;
        boolean card = held.getItem() == ModItems.RESONATING_BINDING_CARD && ResonatingBindingCardData.valid(held);
        if (!card
            && (held.getItem() != ModItems.RESONATING_PATTERN || !held.hasTagCompound() || !mc.thePlayer.isSneaking()))
            return;
        event.setCanceled(true);
        ResonatingNetwork.select(mc.thePlayer.inventory.currentItem, held, event.dwheel < 0 ? 1 : -1);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        ResonatingPatternData details = details(mc);
        var held = mc.thePlayer.getHeldItem();
        var cardTargets = cardTargets(mc);
        boolean card = cardTargets != null;
        if (!card && details == null) return;
        int selected = card ? ResonatingBindingCardData.selected(held)
            : ResonatingPatternData.selectedInput(held, details);
        var target = card ? cardTargets[selected] : details.target(selected);
        var view = mc.renderViewEntity;
        if (view == null) return;
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * event.partialTicks;
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * event.partialTicks;
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * event.partialTicks;
        BlockFaceOverlay.begin();
        try {
            java.util.List<ResonatingPatternData.Target> drawn = new java.util.ArrayList<>();
            for (int i = 0; i < (card ? ResonatingBindingCardData.SLOTS : details.getAEInputs().length); i++) {
                if (i == selected || !card && details.getAEInputs()[i] == null) continue;
                var other = card ? cardTargets[i] : details.target(i);
                if (other == null || other.samePosition(target) || !visible(mc, other)) continue;
                boolean duplicate = false;
                for (var previous : drawn) if (other.samePosition(previous)) duplicate = true;
                if (!duplicate) {
                    drawFace(other, x, y, z, false);
                    drawn.add(other);
                }
            }
            if (target == null || !visible(mc, target)) return;
            drawFace(target, x, y, z, true);
            BlockFaceOverlay.outline(target.x, target.y, target.z, x, y, z);
        } finally {
            BlockFaceOverlay.end();
        }
    }

    private static boolean visible(Minecraft mc, ResonatingPatternData.Target target) {
        return target.dimension == mc.theWorld.provider.dimensionId
            && mc.theWorld.blockExists(target.x, target.y, target.z)
            && mc.renderViewEntity.getDistanceSq(target.x + 0.5, target.y + 0.5, target.z + 0.5) <= 128 * 128;
    }

    private static void drawFace(ResonatingPatternData.Target target, double cameraX, double cameraY, double cameraZ,
        boolean selected) {
        BlockFaceOverlay.face(target.x, target.y, target.z, target.side, cameraX, cameraY, cameraZ, selected);
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI || mc.currentScreen != null) return;
        ResonatingPatternData details = details(mc);
        var held = mc.thePlayer.getHeldItem();
        var cardTargets = cardTargets(mc);
        boolean card = cardTargets != null;
        if (!card && details == null) return;
        int selected = card ? ResonatingBindingCardData.selected(held)
            : ResonatingPatternData.selectedInput(held, details);
        int ordinal = 0, count = 0;
        for (int i = 0; !card && i < details.getAEInputs().length; i++) {
            if (details.getAEInputs()[i] == null) continue;
            count++;
            if (i == selected) ordinal = count;
        }
        if (card) {
            ordinal = selected + 1;
            count = ResonatingBindingCardData.SLOTS;
        }
        String inputName = card
            ? (ResonatingBindingCardData.fluid(held) ? "\u00a7b" : "\u00a7a")
                + StatCollector.translateToLocal(
                    "tenkeitech.resonating.card." + (ResonatingBindingCardData.fluid(held) ? "fluid" : "item"))
                + "\u00a7r"
            : ResonatingPatternData.displayName(details.getAEInputs()[selected]);
        var target = card ? cardTargets[selected] : details.target(selected);
        String text = target == null
            ? StatCollector.translateToLocalFormatted("tenkeitech.resonating.hud_unbound", ordinal, count, inputName)
            : StatCollector.translateToLocalFormatted(
                "tenkeitech.resonating.hud_bound",
                ordinal,
                count,
                inputName,
                target.x,
                target.y,
                target.z,
                StatCollector.translateToLocal("tenkeitech.resonating.face." + target.side),
                target.dimension == mc.theWorld.provider.dimensionId ? ""
                    : StatCollector.translateToLocalFormatted("tenkeitech.resonating.hud_dimension", target.dimension));
        int y = event.resolution.getScaledHeight() - 76 + (mc.playerController.shouldDrawHUD() ? 0 : 14);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.fontRenderer.drawStringWithShadow(
                text,
                (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
                y,
                target == null ? 0xFFFFCC55 : 0xFF55FF55);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
