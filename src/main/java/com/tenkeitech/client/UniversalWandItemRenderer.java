/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

/** Thin extrusion for the wand's high-resolution sprite, using Forge's item-renderer API. */
public final class UniversalWandItemRenderer implements IItemRenderer {

    private static final float THICKNESS = 1.0F / 256.0F;
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        IIcon icon = item.getIconIndex();
        if (icon == null) return;
        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-0.5F, -0.25F, 0.0F);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            ItemRenderer.renderItemIn2D(
                Tessellator.instance,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                icon.getIconWidth(),
                icon.getIconHeight(),
                THICKNESS);
            if (item.hasEffect(0)) renderGlint();
        } finally {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void renderGlint() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(GLINT);
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(0.38F, 0.19F, 0.608F, 1.0F);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        try {
            for (int pass = 0; pass < 2; pass++) {
                GL11.glPushMatrix();
                try {
                    GL11.glScalef(0.125F, 0.125F, 0.125F);
                    long period = pass == 0 ? 3000L : 4873L;
                    float offset = (float) (Minecraft.getSystemTime() % period) / period * 8.0F;
                    GL11.glTranslatef(pass == 0 ? offset : -offset, 0.0F, 0.0F);
                    GL11.glRotatef(pass == 0 ? -50.0F : 10.0F, 0.0F, 0.0F, 1.0F);
                    ItemRenderer.renderItemIn2D(Tessellator.instance, 0.0F, 0.0F, 1.0F, 1.0F, 256, 256, THICKNESS);
                } finally {
                    GL11.glPopMatrix();
                }
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }
}
