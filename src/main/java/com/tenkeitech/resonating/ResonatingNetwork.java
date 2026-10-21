/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.resonating;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.tenkeitech.ModItems;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Coalesces selection requests, then validates the exact held stack on the server thread. */
public final class ResonatingNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tt_resonating");
    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();

    private static final class Request {

        final EntityPlayerMP player;
        final Select message;

        Request(EntityPlayerMP player, Select message) {
            this.player = player;
            this.message = message;
        }
    }

    private ResonatingNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Select.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new ResonatingNetwork());
    }

    public static void select(int slot, ItemStack stack, int step) {
        Select message = new Select();
        message.slot = slot;
        message.hash = stack.hasTagCompound() ? stack.getTagCompound()
            .hashCode() : 0;
        message.step = step < 0 ? -1 : 1;
        CHANNEL.sendToServer(message);
    }

    public static final class Select implements IMessage {

        int slot, hash, step;

        public Select() {}

        @Override
        public void fromBytes(ByteBuf buffer) {
            slot = buffer.readInt();
            hash = buffer.readInt();
            step = buffer.readByte();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(slot);
            buffer.writeInt(hash);
            buffer.writeByte(step);
        }
    }

    public static final class Handler implements IMessageHandler<Select, IMessage> {

        @Override
        public IMessage onMessage(Select message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (message.slot >= 0 && message.slot < 9 && (message.step == -1 || message.step == 1)) {
                if (REQUESTS.size() < 128 || REQUESTS.containsKey(player.getUniqueID()))
                    REQUESTS.put(player.getUniqueID(), new Request(player, message));
            }
            return null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int budget = 128;
        for (var entry : REQUESTS.entrySet()) {
            if (budget-- <= 0) break;
            Request request = entry.getValue();
            if (!REQUESTS.remove(entry.getKey(), request)) continue;
            EntityPlayerMP player = request.player;
            Select message = request.message;
            ItemStack held = player.getHeldItem();
            if (!net.minecraft.server.MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList.contains(player) || player.isDead
                || !player.isSneaking()
                || player.inventory.currentItem != message.slot
                || held == null
                || (held.hasTagCompound() ? held.getTagCompound()
                    .hashCode() : 0) != message.hash)
                continue;
            if (held.getItem() == ModItems.RESONATING_BINDING_CARD && ResonatingBindingCardData.valid(held)) {
                ResonatingBindingCardData.select(held, message.step);
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
            } else if (held.getItem() == ModItems.RESONATING_PATTERN && player.isSneaking()
                && ModItems.RESONATING_PATTERN
                    .getPatternForItem(held, player.worldObj) instanceof ResonatingPatternData details) {
                        ResonatingPatternData.select(held, details, message.step);
                        player.inventory.markDirty();
                        player.inventoryContainer.detectAndSendChanges();
                    }
        }
    }
}
