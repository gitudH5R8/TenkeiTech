/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.questskip;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** No BQ types in the transport: absent or unsupported versions remain safe. */
public final class QuestSkipNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tt_questskip");
    private static final Map<UUID, Pending> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Confirmation> CONFIRMATIONS = new ConcurrentHashMap<>();

    private static final class Confirmation {

        final EntityPlayerMP player;
        final UUID quest;
        final int sequence;
        final long expires;

        Confirmation(EntityPlayerMP player, Request request) {
            this.player = player;
            quest = request.quest;
            sequence = request.sequence;
            expires = System.currentTimeMillis() + 60000L;
        }
    }

    public static void confirmable(EntityPlayerMP player, Request request) {
        CONFIRMATIONS.put(player.getUniqueID(), new Confirmation(player, request));
    }

    public static boolean consumeConfirmation(EntityPlayerMP player, Request request) {
        Confirmation confirmation = CONFIRMATIONS.remove(player.getUniqueID());
        return confirmation != null && confirmation.player == player
            && confirmation.quest.equals(request.quest)
            && confirmation.sequence == request.sequence
            && System.currentTimeMillis() <= confirmation.expires;
    }

    public static void clear() {
        REQUESTS.clear();
        CONFIRMATIONS.clear();
    }

    private static final class Pending {

        final EntityPlayerMP player;
        final Request request;

        Pending(EntityPlayerMP player, Request request) {
            this.player = player;
            this.request = request;
        }
    }

    private QuestSkipNetwork() {}

    public static boolean compatible() {
        var mod = Loader.instance()
            .getIndexedModList()
            .get("betterquesting");
        return mod != null && "3.8.84-GTNH".equals(mod.getVersion());
    }

    public static void init() {
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new QuestSkipNetwork());
    }

    public static void request(UUID quest, boolean commit, int sequence) {
        Request request = new Request();
        request.quest = quest;
        request.commit = commit;
        request.sequence = sequence;
        CHANNEL.sendToServer(request);
    }

    public static void reply(EntityPlayerMP player, int sequence, int result) {
        Reply reply = new Reply();
        reply.sequence = sequence;
        reply.result = result;
        CHANNEL.sendTo(reply, player);
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player)
            reply(player, 0, Config.enableQuestSkip && compatible() ? 1 : 0);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.player.getUniqueID());
        CONFIRMATIONS.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            REQUESTS.clear();
            return;
        }
        int budget = 128;
        for (var entry : REQUESTS.entrySet()) {
            if (budget-- <= 0) break;
            Pending pending = entry.getValue();
            if (!REQUESTS.remove(entry.getKey(), pending)) continue;
            if (!server.getConfigurationManager().playerEntityList.contains(pending.player) || pending.player.isDead)
                continue;
            if (!Config.enableQuestSkip || !compatible()) {
                reply(pending.player, pending.request.sequence, 2);
                continue;
            }
            try {
                com.tenkeitech.integration.betterquesting.QuestSkipService.handle(pending.player, pending.request);
            } catch (RuntimeException failure) {
                TenkeiTech.LOG.error("Quest skip request failed for {}", pending.request.quest, failure);
                reply(pending.player, pending.request.sequence, 2);
            }
        }
        CONFIRMATIONS.entrySet()
            .removeIf(entry -> System.currentTimeMillis() > entry.getValue().expires);
    }

    public static final class Request implements IMessage {

        public UUID quest;
        public boolean commit;
        public int sequence;

        public Request() {}

        public void fromBytes(ByteBuf b) {
            quest = new UUID(b.readLong(), b.readLong());
            commit = b.readBoolean();
            sequence = b.readInt();
        }

        public void toBytes(ByteBuf b) {
            b.writeLong(quest.getMostSignificantBits());
            b.writeLong(quest.getLeastSignificantBits());
            b.writeBoolean(commit);
            b.writeInt(sequence);
        }
    }

    public static final class Reply implements IMessage {

        public int sequence, result;

        public Reply() {}

        public void fromBytes(ByteBuf b) {
            sequence = b.readInt();
            result = b.readUnsignedByte();
        }

        public void toBytes(ByteBuf b) {
            b.writeInt(sequence);
            b.writeByte(result);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {

        public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (message.sequence > 0 && (REQUESTS.size() < 128 || REQUESTS.containsKey(player.getUniqueID())))
                REQUESTS.put(player.getUniqueID(), new Pending(player, message));
            return null;
        }
    }

    public static final class ReplyHandler implements IMessageHandler<Reply, IMessage> {

        public IMessage onMessage(Reply message, MessageContext context) {
            TenkeiTech.proxy.receiveQuestSkip(message, context.netHandler);
            return null;
        }
    }
}
