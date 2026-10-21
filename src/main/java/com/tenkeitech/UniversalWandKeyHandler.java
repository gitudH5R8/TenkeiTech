/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.universalwand.UniversalWandNetwork;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.InputEvent.MouseInputEvent;

/** Client key entry point; the key can be rebound through the normal Minecraft controls screen. */
public final class UniversalWandKeyHandler {

    private static final KeyBinding OPEN_GUI = new KeyBinding(
        "key.tenkeitech.universal_wand",
        Keyboard.KEY_G,
        "key.categories.tenkeitech");
    private static final KeyBinding FORCE_BEDROCK = new KeyBinding(
        "key.tenkeitech.universal_wand.force_bedrock",
        Keyboard.KEY_R,
        "key.categories.tenkeitech");
    private static final KeyBinding CHAIN = new KeyBinding(
        "key.tenkeitech.universal_wand.chain",
        Keyboard.KEY_GRAVE,
        "key.categories.tenkeitech");
    private boolean lastChainHeld;
    private int chainHeartbeat;
    private static final KeyBinding UNDO = new KeyBinding(
        "key.tenkeitech.universal_wand.undo",
        Keyboard.KEY_LCONTROL,
        "key.categories.tenkeitech");
    private boolean lastUndoHeld;
    private int undoHeartbeat;
    private static final KeyBinding FLIGHT_SPEED = new KeyBinding(
        "key.tenkeitech.universal_wand.flight_speed",
        Keyboard.KEY_V,
        "key.categories.tenkeitech");

    public static boolean isChainKeyHeld() {
        return com.tenkeitech.universalwand.UniversalWandFeature.CHAIN.allowed() && CHAIN.getIsKeyPressed();
    }

    public static boolean isUndoKeyHeld() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.currentScreen == null && minecraft.inGameHasFocus
            && net.minecraft.client.settings.GameSettings.isKeyDown(UNDO);
    }

    private UniversalWandKeyHandler() {}

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN_GUI);
        ClientRegistry.registerKeyBinding(FORCE_BEDROCK);
        ClientRegistry.registerKeyBinding(CHAIN);
        ClientRegistry.registerKeyBinding(UNDO);
        ClientRegistry.registerKeyBinding(FLIGHT_SPEED);
        FMLCommonHandler.instance()
            .bus()
            .register(new UniversalWandKeyHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new UniversalWandKeyHandler());
    }

    @SubscribeEvent
    public void onScroll(net.minecraftforge.client.event.MouseEvent event) {
        if (event.dwheel == 0 || event.isCanceled()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null || minecraft.thePlayer == null
            || !minecraft.inGameHasFocus
            || !(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) return;
        ItemStack stack = minecraft.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)
            || !ItemUniversalWand.isTimeAccelerationEnabled(stack)) return;
        event.setCanceled(true);
        UniversalWandNetwork.requestAccelerationManagement(event.dwheel > 0 ? 1 : 0, 0, 0, 0, 0);
    }

    @SubscribeEvent
    public void onClientTick(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            com.tenkeitech.universalwand.UniversalWandUtilityHandler.receiveClientSpeed(null, 0);
            lastChainHeld = false;
            lastUndoHeld = false;
            return;
        }
        ItemStack stack = minecraft.thePlayer.getHeldItem();
        boolean undo = isUndoKeyHeld() && ItemUniversalWand.isHeldBy(minecraft.thePlayer)
            && ItemUniversalWand.isBuildingEnabled(stack);
        if (undo != lastUndoHeld || undo && ++undoHeartbeat >= 10) {
            UniversalWandNetwork.requestUndoKey(undo);
            lastUndoHeld = undo;
            undoHeartbeat = 0;
        }
        boolean held = minecraft.currentScreen == null && minecraft.inGameHasFocus
            && isChainKeyHeld()
            && stack != null
            && stack.getItem() instanceof ItemUniversalWand;
        if (held != lastChainHeld || (held && ++chainHeartbeat >= 10)) {
            UniversalWandNetwork.requestChainKey(held);
            lastChainHeld = held;
            chainHeartbeat = 0;
        }
    }

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent event) {
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null || minecraft.thePlayer == null) return;
        ItemStack stack = minecraft.thePlayer.getHeldItem();
        if (isChainKeyHeld() && stack != null && stack.getItem() instanceof ItemUniversalWand) {
            UniversalWandNetwork.requestChainKey(true);
            if (minecraft.objectMouseOver != null && minecraft.objectMouseOver.typeOfHit
                == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                UniversalWandNetwork.requestChainStart(
                    minecraft.objectMouseOver.blockX,
                    minecraft.objectMouseOver.blockY,
                    minecraft.objectMouseOver.blockZ);
            }
            lastChainHeld = true;
            chainHeartbeat = 0;
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null || minecraft.thePlayer == null) return;
        if (FLIGHT_SPEED.isPressed() && com.tenkeitech.universalwand.UniversalWandFeature.FLIGHT.allowed())
            UniversalWandNetwork
                .requestFlightSpeed(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        ItemStack stack = minecraft.thePlayer.getHeldItem();
        boolean chainHeld = isChainKeyHeld() && stack != null && stack.getItem() instanceof ItemUniversalWand;
        if (chainHeld != lastChainHeld) {
            UniversalWandNetwork.requestChainKey(chainHeld);
            lastChainHeld = chainHeld;
            chainHeartbeat = 0;
        }
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)) return;
        if (OPEN_GUI.isPressed()) UniversalWandNetwork.requestOpenGui(minecraft.thePlayer.inventory.currentItem);
        if (FORCE_BEDROCK.isPressed()) {
            net.minecraft.util.MovingObjectPosition target = minecraft.objectMouseOver;
            if (target != null && target.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                UniversalWandNetwork.requestForceBedrock(target.blockX, target.blockY, target.blockZ);
            }
        }
    }
}
