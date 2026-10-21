/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
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
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
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
            GL11.glLineWidth(2F);
            GL11.glColor4f(0.1F, 0.9F, 1F, 1F);
            RenderGlobal.drawOutlinedBoundingBox(
                AxisAlignedBB.getBoundingBox(
                    target.x - x - 0.002,
                    target.y - y - 0.002,
                    target.z - z - 0.002,
                    target.x - x + 1.002,
                    target.y - y + 1.002,
                    target.z - z + 1.002),
                -1);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean visible(Minecraft mc, ResonatingPatternData.Target target) {
        return target.dimension == mc.theWorld.provider.dimensionId
            && mc.theWorld.blockExists(target.x, target.y, target.z)
            && mc.renderViewEntity.getDistanceSq(target.x + 0.5, target.y + 0.5, target.z + 0.5) <= 128 * 128;
    }

    private static void drawFace(ResonatingPatternData.Target target, double cameraX, double cameraY, double cameraZ,
        boolean selected) {
        double x = target.x - cameraX, y = target.y - cameraY, z = target.z - cameraZ;
        double epsilon = 0.003;
        GL11.glColor4f(selected ? 0.1F : 0.15F, selected ? 1F : 0.4F, selected ? 0.1F : 1F, 0.45F);
        GL11.glBegin(GL11.GL_QUADS);
        switch (target.side) {
            case 0, 1 -> {
                double plane = y + (target.side == 0 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(x, plane, z);
                GL11.glVertex3d(x + 1, plane, z);
                GL11.glVertex3d(x + 1, plane, z + 1);
                GL11.glVertex3d(x, plane, z + 1);
            }
            case 2, 3 -> {
                double plane = z + (target.side == 2 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(x, y, plane);
                GL11.glVertex3d(x + 1, y, plane);
                GL11.glVertex3d(x + 1, y + 1, plane);
                GL11.glVertex3d(x, y + 1, plane);
            }
            case 4, 5 -> {
                double plane = x + (target.side == 4 ? -epsilon : 1 + epsilon);
                GL11.glVertex3d(plane, y, z);
                GL11.glVertex3d(plane, y, z + 1);
                GL11.glVertex3d(plane, y + 1, z + 1);
                GL11.glVertex3d(plane, y + 1, z);
            }
            default -> {}
        }
        GL11.glEnd();
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
