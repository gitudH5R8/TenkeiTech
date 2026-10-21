/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Packets and main-thread dispatch for pattern-terminal uploads. */
public final class PatternUploadNetwork {

    public static final int PAGE_SIZE = 6;
    public static final int MAX_TARGET_ENTRIES = 512;
    public static final int MAX_PREFERRED_NAMES = 16;
    public static final int MAX_PINNED_PROVIDERS = 64;
    public static final int MAX_SEARCH_TERMS = 2;
    public static final int MAX_ROUTING_TEXT = 256;
    private static final int MAX_SERVER_TASKS = 256;
    private static final int MAX_TASKS_PER_TICK = 64;
    // Search terms change the routing packet: both ends must use this version of the upload protocol.
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tenkei_upload7");
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SERVER_TASK_COUNT = new AtomicInteger();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private PatternUploadNetwork() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        int discriminator = 0;
        CHANNEL.registerMessage(RequestTargetsHandler.class, RequestTargetsMessage.class, discriminator++, Side.SERVER);
        CHANNEL.registerMessage(SelectTargetHandler.class, SelectTargetMessage.class, discriminator++, Side.SERVER);
        CHANNEL.registerMessage(UndoLastUploadHandler.class, UndoLastUploadMessage.class, discriminator++, Side.SERVER);
        CHANNEL.registerMessage(TargetsPageHandler.class, TargetsPageMessage.class, discriminator++, Side.CLIENT);
        CHANNEL.registerMessage(UploadResultHandler.class, UploadResultMessage.class, discriminator, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerQueueDrainer());
    }

    public static void requestTargets(int windowId, long requestId, List<String> names, List<String> searchTerms,
        List<String> pinnedProviders, boolean autoUpload, ItemStack source) {
        if (!Config.enablePatternUpload) return;
        CHANNEL.sendToServer(
            new RequestTargetsMessage(
                windowId,
                requestId,
                0L,
                names,
                searchTerms,
                pinnedProviders,
                autoUpload,
                source));
    }

    public static void selectTarget(int windowId, long requestId, long nonce, int groupIndex) {
        if (!Config.enablePatternUpload) return;
        CHANNEL.sendToServer(new SelectTargetMessage(windowId, requestId, nonce, groupIndex));
    }

    public static void undoLastUpload(int windowId) {
        if (!Config.enablePatternUpload) return;
        CHANNEL.sendToServer(new UndoLastUploadMessage(windowId));
    }

    static void sendPage(EntityPlayerMP player, TargetsPageMessage message) {
        CHANNEL.sendTo(message, player);
    }

    static void sendResult(EntityPlayerMP player, UploadResultMessage message) {
        CHANNEL.sendTo(message, player);
    }

    private static boolean enqueueServer(Runnable task) {
        int queued = SERVER_TASK_COUNT.incrementAndGet();
        if (queued > MAX_SERVER_TASKS) {
            SERVER_TASK_COUNT.decrementAndGet();
            TenkeiTech.LOG.warn("Dropping pattern upload packet because the server work queue is full");
            return false;
        }
        SERVER_TASKS.offer(task);
        return true;
    }

    private static void enqueueServer(EntityPlayerMP player, int windowId, long requestId, Runnable task) {
        boolean accepted = enqueueServer(() -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.error("Pattern upload request failed", exception);
                sendResult(player, new UploadResultMessage(windowId, requestId, false, ResultCode.INTERNAL_ERROR, ""));
            }
        });
        if (!accepted) {
            sendResult(player, new UploadResultMessage(windowId, requestId, false, ResultCode.INTERNAL_ERROR, ""));
        }
    }

    public enum ResultCode {

        SUCCESS,
        INVALID_CONTAINER,
        NO_PATTERN,
        NETWORK_OFFLINE,
        NO_PERMISSION,
        NO_TARGETS,
        SESSION_EXPIRED,
        SOURCE_CHANGED,
        TARGET_UNAVAILABLE,
        TARGET_FULL,
        TARGET_REJECTED,
        INTERNAL_ERROR,
        DUPLICATE_REFUNDED,
        DUPLICATE_CHECK_INCOMPLETE,
        REFUND_NO_SPACE,
        REFUND_UNCERTAIN,
        FEATURE_DISABLED;

        public String translationKey() {
            return "tenkeitech.pattern_upload.result." + name().toLowerCase(java.util.Locale.ROOT);
        }

        static ResultCode fromOrdinal(int ordinal) {
            ResultCode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INTERNAL_ERROR;
        }
    }

    public static final class RequestTargetsMessage implements IMessage {

        private int windowId;
        private long requestId;
        private long nonce;
        private List<String> names = Collections.emptyList();
        private List<String> searchTerms = Collections.emptyList();
        private List<String> pinnedProviders = Collections.emptyList();
        private boolean autoUpload;
        private ItemStack expectedSource;

        public RequestTargetsMessage() {}

        RequestTargetsMessage(int windowId, long requestId, long nonce, List<String> names, List<String> searchTerms,
            List<String> pinnedProviders, boolean autoUpload, ItemStack source) {
            this.windowId = windowId;
            this.requestId = requestId;
            this.nonce = nonce;
            this.names = names;
            this.searchTerms = searchTerms;
            this.pinnedProviders = pinnedProviders;
            this.autoUpload = autoUpload;
            this.expectedSource = source == null ? null : source.copy();
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            requestId = buffer.readLong();
            nonce = buffer.readLong();
            autoUpload = buffer.readBoolean();
            int count = buffer.readUnsignedByte();
            if (count > MAX_PREFERRED_NAMES) throw new IllegalArgumentException("Too many upload targets");
            names = new ArrayList<>(count);
            for (int index = 0; index < count; index++) names.add(readRoutingText(buffer));
            int searchCount = buffer.readUnsignedByte();
            if (searchCount > MAX_SEARCH_TERMS) throw new IllegalArgumentException("Too many upload search terms");
            searchTerms = new ArrayList<>(searchCount);
            for (int index = 0; index < searchCount; index++) searchTerms.add(readRoutingText(buffer));
            int pinnedCount = buffer.readUnsignedShort();
            if (pinnedCount > MAX_PINNED_PROVIDERS) throw new IllegalArgumentException("Too many pinned providers");
            pinnedProviders = new ArrayList<>(pinnedCount);
            for (int index = 0; index < pinnedCount; index++) pinnedProviders.add(readRoutingText(buffer));
            expectedSource = ByteBufUtils.readItemStack(buffer);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(requestId);
            buffer.writeLong(nonce);
            buffer.writeBoolean(autoUpload);
            buffer.writeByte(names.size());
            for (String name : names) ByteBufUtils.writeUTF8String(buffer, name);
            buffer.writeByte(searchTerms.size());
            for (String term : searchTerms) ByteBufUtils.writeUTF8String(buffer, term);
            buffer.writeShort(pinnedProviders.size());
            for (String provider : pinnedProviders) ByteBufUtils.writeUTF8String(buffer, provider);
            ByteBufUtils.writeItemStack(buffer, expectedSource);
        }
    }

    public static final class SelectTargetMessage implements IMessage {

        private int windowId;
        private long requestId;
        private long nonce;
        private int groupIndex;

        public SelectTargetMessage() {}

        SelectTargetMessage(int windowId, long requestId, long nonce, int groupIndex) {
            this.windowId = windowId;
            this.requestId = requestId;
            this.nonce = nonce;
            this.groupIndex = groupIndex;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            requestId = buffer.readLong();
            nonce = buffer.readLong();
            groupIndex = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(requestId);
            buffer.writeLong(nonce);
            buffer.writeInt(groupIndex);
        }
    }

    public static final class UndoLastUploadMessage implements IMessage {

        private int windowId;

        public UndoLastUploadMessage() {}

        UndoLastUploadMessage(int windowId) {
            this.windowId = windowId;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
        }
    }

    public static final class TargetEntry {

        public final int groupIndex;
        public final String name;
        public final String routingName;
        public final String terminalName;
        public final String terminalSuffix;
        public final int freeSlots;
        public final int providerCount;
        public final List<Integer> machineIds;

        public TargetEntry(int groupIndex, String name, String routingName, String terminalName, String terminalSuffix,
            int freeSlots, int providerCount, List<Integer> machineIds) {
            this.groupIndex = groupIndex;
            this.name = name;
            this.routingName = routingName;
            this.terminalName = terminalName;
            this.terminalSuffix = terminalSuffix;
            if (terminalName == null || terminalSuffix == null
                || terminalName.length() > 4096
                || terminalSuffix.length() > 4096) throw new IllegalArgumentException("Invalid terminal name payload");
            this.freeSlots = freeSlots;
            this.providerCount = providerCount;
            if (machineIds.size() > 32768) throw new IllegalArgumentException("Too many machine IDs");
            this.machineIds = Collections.unmodifiableList(new ArrayList<>(machineIds));
        }
    }

    public static final class TargetsPageMessage implements IMessage {

        public int windowId;
        public long requestId;
        public long nonce;
        public boolean matched;
        public boolean complete;
        public String searchText = "";
        public List<TargetEntry> entries = Collections.emptyList();

        public TargetsPageMessage() {}

        TargetsPageMessage(int windowId, long requestId, long nonce, List<TargetEntry> entries, boolean matched,
            boolean complete, String searchText) {
            this.windowId = windowId;
            this.requestId = requestId;
            this.nonce = nonce;
            this.entries = entries;
            this.matched = matched;
            this.complete = complete;
            this.searchText = searchText;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            requestId = buffer.readLong();
            nonce = buffer.readLong();
            matched = buffer.readBoolean();
            complete = buffer.readBoolean();
            searchText = readRoutingText(buffer);
            int count = ByteBufUtils.readVarInt(buffer, 1);
            if (count < 0 || count > MAX_TARGET_ENTRIES)
                throw new IllegalArgumentException("Invalid upload target list size");
            List<TargetEntry> decoded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                decoded.add(
                    new TargetEntry(
                        buffer.readInt(),
                        ByteBufUtils.readUTF8String(buffer),
                        readRoutingText(buffer),
                        ByteBufUtils.readUTF8String(buffer),
                        ByteBufUtils.readUTF8String(buffer),
                        buffer.readInt(),
                        buffer.readInt(),
                        readMachineIds(buffer)));
            }
            entries = Collections.unmodifiableList(decoded);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(requestId);
            buffer.writeLong(nonce);
            buffer.writeBoolean(matched);
            buffer.writeBoolean(complete);
            ByteBufUtils.writeUTF8String(buffer, searchText);
            ByteBufUtils.writeVarInt(buffer, entries.size(), 1);
            for (TargetEntry entry : entries) {
                buffer.writeInt(entry.groupIndex);
                ByteBufUtils.writeUTF8String(buffer, entry.name);
                ByteBufUtils.writeUTF8String(buffer, entry.routingName);
                ByteBufUtils.writeUTF8String(buffer, entry.terminalName);
                ByteBufUtils.writeUTF8String(buffer, entry.terminalSuffix);
                buffer.writeInt(entry.freeSlots);
                buffer.writeInt(entry.providerCount);
                buffer.writeInt(entry.machineIds.size());
                for (int id : entry.machineIds) buffer.writeInt(id);
            }
        }
    }

    private static List<Integer> readMachineIds(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > 32768 || count > buffer.readableBytes() / 4)
            throw new IllegalArgumentException("Invalid machine ID count");
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buffer.readInt();
            if (id < 0 || id >= 32768) throw new IllegalArgumentException("Invalid machine ID");
            ids.add(id);
        }
        return ids;
    }

    public static final class UploadResultMessage implements IMessage {

        public int windowId;
        public long requestId;
        public boolean success;
        public ResultCode code = ResultCode.INTERNAL_ERROR;
        public String providerName = "";

        public UploadResultMessage() {}

        UploadResultMessage(int windowId, long requestId, boolean success, ResultCode code, String providerName) {
            this.windowId = windowId;
            this.requestId = requestId;
            this.success = success;
            this.code = code;
            this.providerName = providerName == null ? "" : providerName;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readInt();
            requestId = buffer.readLong();
            success = buffer.readBoolean();
            code = ResultCode.fromOrdinal(buffer.readUnsignedByte());
            providerName = ByteBufUtils.readUTF8String(buffer);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeLong(requestId);
            buffer.writeBoolean(success);
            buffer.writeByte(code.ordinal());
            ByteBufUtils.writeUTF8String(buffer, providerName);
        }
    }

    public static final class RequestTargetsHandler implements IMessageHandler<RequestTargetsMessage, IMessage> {

        @Override
        public IMessage onMessage(RequestTargetsMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!Config.enablePatternUpload) {
                enqueueServer(
                    () -> sendResult(
                        player,
                        new UploadResultMessage(
                            message.windowId,
                            message.requestId,
                            false,
                            ResultCode.FEATURE_DISABLED,
                            "")));
                return null;
            }
            enqueueServer(
                player,
                message.windowId,
                message.requestId,
                () -> PatternUploadService.handleTargetsRequest(
                    player,
                    message.windowId,
                    message.requestId,
                    message.nonce,
                    message.names,
                    message.searchTerms,
                    message.pinnedProviders,
                    message.autoUpload,
                    message.expectedSource));
            return null;
        }
    }

    public static final class SelectTargetHandler implements IMessageHandler<SelectTargetMessage, IMessage> {

        @Override
        public IMessage onMessage(SelectTargetMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!Config.enablePatternUpload) {
                enqueueServer(
                    () -> sendResult(
                        player,
                        new UploadResultMessage(
                            message.windowId,
                            message.requestId,
                            false,
                            ResultCode.FEATURE_DISABLED,
                            "")));
                return null;
            }
            enqueueServer(
                player,
                message.windowId,
                message.requestId,
                () -> PatternUploadService.handleTargetSelection(
                    player,
                    message.windowId,
                    message.requestId,
                    message.nonce,
                    message.groupIndex));
            return null;
        }
    }

    public static final class UndoLastUploadHandler implements IMessageHandler<UndoLastUploadMessage, IMessage> {

        @Override
        public IMessage onMessage(UndoLastUploadMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!enqueueServer(() -> {
                if (!Config.enablePatternUpload) {
                    player.addChatMessage(
                        new ChatComponentTranslation("tenkeitech.pattern_upload.result.feature_disabled"));
                    return;
                }
                try {
                    PatternUploadService.handleUndoLastUpload(player, message.windowId);
                } catch (RuntimeException exception) {
                    TenkeiTech.LOG.error("Pattern upload undo failed", exception);
                    player
                        .addChatMessage(new ChatComponentTranslation("tenkeitech.pattern_upload.undo.internal_error"));
                }
            })) TenkeiTech.LOG.warn("Dropping pattern upload undo because the server work queue is full");
            return null;
        }
    }

    public static final class TargetsPageHandler implements IMessageHandler<TargetsPageMessage, IMessage> {

        @Override
        public IMessage onMessage(TargetsPageMessage message, MessageContext context) {
            TenkeiTech.proxy.enqueuePatternUploadPage(message);
            return null;
        }
    }

    public static final class UploadResultHandler implements IMessageHandler<UploadResultMessage, IMessage> {

        @Override
        public IMessage onMessage(UploadResultMessage message, MessageContext context) {
            TenkeiTech.proxy.enqueuePatternUploadResult(message);
            return null;
        }
    }

    public static final class ServerQueueDrainer {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            // Drain after the world/AE tick. Requests sent while a terminal is opening then see the
            // same complete Grid machine set that the reference implementation enumerates.
            if (event.phase != TickEvent.Phase.END) return;
            for (int handled = 0; handled < MAX_TASKS_PER_TICK; handled++) {
                Runnable task = SERVER_TASKS.poll();
                if (task == null) return;
                SERVER_TASK_COUNT.decrementAndGet();
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    TenkeiTech.LOG.error("Pattern upload server task failed", exception);
                }
            }
        }
    }

    private static String readRoutingText(ByteBuf buffer) {
        String value = ByteBufUtils.readUTF8String(buffer);
        if (value.length() > MAX_ROUTING_TEXT || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0
            || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid upload routing name");
        return value;
    }
}
