/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.maintainer;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.tenkeitech.TenkeiTech;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;
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

/** Bounded actions and server snapshots; direct ghost identities are configuration, never inventory. */
public final class MaintainerNetwork {

    public static final int HELLO = 0, EDIT = 1, RUN = 2, TOGGLE = 3, CATALOG = 4, MATERIAL = 5;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tenkei_maint1");
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();

    private MaintainerNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(ActionHandler.class, Action.class, 0, Side.SERVER);
        CHANNEL.registerMessage(DataHandler.class, Data.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new MaintainerNetwork());
    }

    public static void clear() {
        TASKS.clear();
        QUEUED.set(0);
        MaintainerCalculations.clear();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MaintainerCalculations.prune();
        for (int i = 0; i < 64; i++) {
            Runnable task = TASKS.poll();
            if (task == null) break;
            QUEUED.decrementAndGet();
            try {
                task.run();
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.warn("Maintainer action rejected", exception);
            }
        }
    }

    public static void send(Action action) {
        CHANNEL.sendToServer(action);
    }

    static void send(EntityPlayerMP player, int window, long nonce, NBTTagCompound tag) {
        CHANNEL.sendTo(new Data(window, nonce, tag), player);
    }

    static byte[] materialBytes(IAEStack<?> material) {
        if (!MaintainerEntry.supported(material)) throw new IllegalArgumentException("Unsupported maintainer material");
        NBTTagCompound tag = material.copy()
            .setStackSize(1)
            .toNBTGeneric();
        if (tag.toString()
            .length() > 8192) throw new IllegalArgumentException("Maintainer material size");
        try {
            byte[] bytes = CompressedStreamTools.compress(tag);
            if (bytes.length > 4096) throw new IllegalArgumentException("Maintainer material size");
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Maintainer material", exception);
        }
    }

    static IAEStack<?> materialFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 4096)
            throw new IllegalArgumentException("Maintainer material size");
        try {
            NBTTagCompound tag = CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(65536));
            if (tag.toString()
                .length() > 8192) throw new IllegalArgumentException("Maintainer material size");
            IAEStackType<?> type = AEStackTypeRegistry.getType(tag.getString("StackType"));
            if (type != AEItemStackType.ITEM_STACK_TYPE && type != AEFluidStackType.FLUID_STACK_TYPE)
                throw new IllegalArgumentException("Unsupported maintainer material");
            IAEStack<?> material = type.loadStackFromNBT(tag);
            if (!MaintainerEntry.supported(material))
                throw new IllegalArgumentException("Unsupported maintainer material");
            material = material.copy()
                .setStackSize(1);
            materialBytes(material);
            return material;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Maintainer material", exception);
        }
    }

    public static final class Action implements IMessage {

        public int request;

        public int window, op, slot, revision, catalogVersion, selection = -1, seconds = 60;
        public long nonce, id, target = 64, batch = 1;
        public boolean enabled = true;
        public byte[] material;

        public Action() {}

        public Action(ContainerBetterLevelMaintainer container, int operation) {
            window = container.windowId;
            nonce = container.nonce;
            op = operation;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            window = buf.readInt();
            request = buf.readInt();
            nonce = buf.readLong();
            op = buf.readUnsignedByte();
            slot = buf.readInt();
            id = buf.readLong();
            revision = buf.readInt();
            catalogVersion = buf.readInt();
            selection = buf.readInt();
            target = buf.readLong();
            batch = buf.readLong();
            seconds = buf.readInt();
            enabled = buf.readBoolean();
            if ((op == EDIT || op == MATERIAL) && selection == -3) {
                int length = buf.readInt();
                if (length <= 0 || length > 4096 || length > buf.readableBytes())
                    throw new IllegalArgumentException("Maintainer material size");
                material = new byte[length];
                buf.readBytes(material);
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(window);
            buf.writeInt(request);
            buf.writeLong(nonce);
            buf.writeByte(op);
            buf.writeInt(slot);
            buf.writeLong(id);
            buf.writeInt(revision);
            buf.writeInt(catalogVersion);
            buf.writeInt(selection);
            buf.writeLong(target);
            buf.writeLong(batch);
            buf.writeInt(seconds);
            buf.writeBoolean(enabled);
            if ((op == EDIT || op == MATERIAL) && selection == -3) {
                if (material == null || material.length == 0 || material.length > 4096)
                    throw new IllegalArgumentException("Maintainer material size");
                buf.writeInt(material.length);
                buf.writeBytes(material);
            }
        }
    }

    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {

        @Override
        public IMessage onMessage(Action action, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (QUEUED.incrementAndGet() > 256) {
                QUEUED.decrementAndGet();
                return null;
            }
            TASKS.offer(() -> {
                if (player.openContainer instanceof ContainerBetterLevelMaintainer container
                    && container.windowId == action.window
                    && container.canInteractWith(player)) container.act(action);
            });
            return null;
        }
    }

    public static final class Data implements IMessage {

        public int window;
        public long nonce;
        public NBTTagCompound tag;

        public Data() {}

        Data(int window, long nonce, NBTTagCompound tag) {
            this.window = window;
            this.nonce = nonce;
            this.tag = tag;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            window = buf.readInt();
            nonce = buf.readLong();
            int length = buf.readInt();
            if (length < 0 || length > 131072 || length > buf.readableBytes())
                throw new IllegalArgumentException("Maintainer page size");
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            try {
                tag = CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(2L * 1024 * 1024));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Maintainer page", exception);
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            try {
                byte[] bytes = CompressedStreamTools.compress(tag);
                if (bytes.length > 131072) throw new IllegalArgumentException("Maintainer page size");
                buf.writeInt(window);
                buf.writeLong(nonce);
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Maintainer page", exception);
            }
        }
    }

    public static final class DataHandler implements IMessageHandler<Data, IMessage> {

        @Override
        public IMessage onMessage(Data data, MessageContext context) {
            TenkeiTech.proxy.receiveMaintainerData(data);
            return null;
        }
    }
}
