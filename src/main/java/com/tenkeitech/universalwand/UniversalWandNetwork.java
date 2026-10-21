/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.universalwand.building.BuildingContext;
import com.tenkeitech.universalwand.building.BuildingContext.Position;
import com.tenkeitech.universalwand.building.BuildingUndoHistory;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Dedicated client-to-server action for opening the wand settings screen. */
public final class UniversalWandNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tenkeitech_wand");

    private UniversalWandNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(OpenGuiHandler.class, OpenGuiMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ForceBedrockHandler.class, ForceBedrockMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(ChainKeyHandler.class, ChainKeyMessage.class, 2, Side.SERVER);
        CHANNEL.registerMessage(ChainStartHandler.class, ChainStartMessage.class, 3, Side.SERVER);
        CHANNEL.registerMessage(BuildStartHandler.class, BuildStartMessage.class, 4, Side.SERVER);
        CHANNEL.registerMessage(UndoKeyHandler.class, UndoKeyMessage.class, 5, Side.SERVER);
        CHANNEL.registerMessage(UndoPreviewHandler.class, UndoPreviewMessage.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(BuildingSupplyRequestHandler.class, BuildingSupplyRequest.class, 7, Side.SERVER);
        CHANNEL.registerMessage(BuildingSupplyHandler.class, BuildingSupplyMessage.class, 8, Side.CLIENT);
        CHANNEL.registerMessage(FlightSpeedHandler.class, FlightSpeedMessage.class, 9, Side.SERVER);
        CHANNEL.registerMessage(FlightSpeedSyncHandler.class, FlightSpeedSyncMessage.class, 10, Side.CLIENT);
        CHANNEL.registerMessage(ProtectionSyncHandler.class, ProtectionSyncMessage.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(AccelerationSyncHandler.class, AccelerationSyncMessage.class, 13, Side.CLIENT);
        CHANNEL
            .registerMessage(AccelerationManagementHandler.class, AccelerationManagementMessage.class, 14, Side.SERVER);
        CHANNEL.registerMessage(AccelerationNoticeHandler.class, AccelerationNoticeMessage.class, 15, Side.CLIENT);
        CHANNEL.registerMessage(TravelFallbackHandler.class, TravelFallbackRequest.class, 16, Side.SERVER);
        CHANNEL.registerMessage(TravelFallbackGrantHandler.class, TravelFallbackGrant.class, 17, Side.CLIENT);
        CHANNEL.registerMessage(TravelFallbackFinishHandler.class, TravelFallbackFinish.class, 18, Side.SERVER);
        CHANNEL.registerMessage(FeatureHandler.class, FeatureMessage.class, 19, Side.CLIENT);
    }

    public static void syncFeatures(EntityPlayerMP player, long mask) {
        CHANNEL.sendTo(new FeatureMessage(mask), player);
    }

    public static final class FeatureMessage implements IMessage {

        private long mask;

        public FeatureMessage() {}

        FeatureMessage(long mask) {
            this.mask = mask;
        }

        public void fromBytes(ByteBuf buffer) {
            mask = buffer.readLong();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(mask);
        }
    }

    public static final class FeatureHandler implements IMessageHandler<FeatureMessage, IMessage> {

        public IMessage onMessage(FeatureMessage message, MessageContext context) {
            UniversalWandFeaturePolicy.receive(message.mask);
            return null;
        }
    }

    public static void requestTravelFallback(int id) {
        CHANNEL.sendToServer(new TravelFallbackRequest(id));
    }

    public static void allowTravelFallback(EntityPlayerMP player, int id) {
        CHANNEL.sendTo(new TravelFallbackGrant(id), player);
    }

    public static void finishTravelFallback(int id) {
        CHANNEL.sendToServer(new TravelFallbackFinish(id));
    }

    public static class TravelFallbackRequest implements IMessage {

        int id;

        public TravelFallbackRequest() {}

        TravelFallbackRequest(int id) {
            this.id = id;
        }

        public void fromBytes(ByteBuf buffer) {
            id = buffer.readInt();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(id);
        }
    }

    public static final class TravelFallbackGrant extends TravelFallbackRequest {

        public TravelFallbackGrant() {}

        TravelFallbackGrant(int id) {
            super(id);
        }
    }

    public static final class TravelFallbackHandler implements IMessageHandler<TravelFallbackRequest, IMessage> {

        public IMessage onMessage(TravelFallbackRequest message, MessageContext context) {
            UniversalWandRightClick.request(context.getServerHandler().playerEntity, message.id);
            return null;
        }
    }

    public static final class TravelFallbackFinish extends TravelFallbackRequest {

        public TravelFallbackFinish() {}

        TravelFallbackFinish(int id) {
            super(id);
        }
    }

    public static final class TravelFallbackFinishHandler implements IMessageHandler<TravelFallbackFinish, IMessage> {

        public IMessage onMessage(TravelFallbackFinish message, MessageContext context) {
            UniversalWandRightClick.finish(context.getServerHandler().playerEntity, message.id);
            return null;
        }
    }

    public static final class TravelFallbackGrantHandler implements IMessageHandler<TravelFallbackGrant, IMessage> {

        public IMessage onMessage(TravelFallbackGrant message, MessageContext context) {
            TenkeiTech.proxy.allowWandTravel(message.id);
            return null;
        }
    }

    public static void syncAccelerationNotice(EntityPlayerMP player, int rate) {
        CHANNEL.sendTo(new AccelerationNoticeMessage(rate), player);
    }

    public static final class AccelerationNoticeMessage implements IMessage {

        private int rate;

        public AccelerationNoticeMessage() {}

        AccelerationNoticeMessage(int rate) {
            this.rate = rate;
        }

        public void fromBytes(ByteBuf buffer) {
            rate = buffer.readInt();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(rate);
        }
    }

    public static final class AccelerationNoticeHandler
        implements IMessageHandler<AccelerationNoticeMessage, IMessage> {

        public IMessage onMessage(AccelerationNoticeMessage message, MessageContext context) {
            UniversalWandUtilityHandler.showClientAccelerationNotice(message.rate);
            return null;
        }
    }

    public static void requestAccelerationManagement(int action, int dimension, int x, int y, int z) {
        CHANNEL.sendToServer(new AccelerationManagementMessage(action, dimension, x, y, z));
    }

    public static final class AccelerationManagementMessage implements IMessage {

        private int action, dimension, x, y, z;

        public AccelerationManagementMessage() {}

        AccelerationManagementMessage(int action, int dimension, int x, int y, int z) {
            this.action = action;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void fromBytes(ByteBuf buffer) {
            action = buffer.readInt();
            dimension = buffer.readInt();
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(action);
            buffer.writeInt(dimension);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
        }
    }

    public static final class AccelerationManagementHandler
        implements IMessageHandler<AccelerationManagementMessage, IMessage> {

        public IMessage onMessage(AccelerationManagementMessage message, MessageContext context) {
            if (message.action >= 0 && message.action <= 4) UniversalWandAccelerationHandler.requestManagement(
                context.getServerHandler().playerEntity,
                message.action,
                message.dimension,
                message.x,
                message.y,
                message.z);
            return null;
        }
    }

    public static void requestOpenGui(int slot) {
        CHANNEL.sendToServer(new OpenGuiMessage(slot));
    }

    public static void syncAcceleration(EntityPlayerMP player, int dimension, int x, int y, int z, int level,
        int remaining, int mode, boolean throttled) {
        AccelerationSyncMessage message = new AccelerationSyncMessage(
            dimension,
            x,
            y,
            z,
            level,
            remaining,
            mode,
            throttled);
        if (player != null) CHANNEL.sendTo(message, player);
        CHANNEL.sendToAllAround(message, new NetworkRegistry.TargetPoint(dimension, x, y, z, 64));
    }

    public static final class AccelerationSyncMessage implements IMessage {

        public int dimension, x, y, z, level, remaining, mode;
        public boolean throttled;

        public AccelerationSyncMessage() {}

        AccelerationSyncMessage(int dimension, int x, int y, int z, int level, int remaining, int mode,
            boolean throttled) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.remaining = remaining;
            this.mode = mode;
            this.throttled = throttled;
        }

        public void fromBytes(ByteBuf buffer) {
            dimension = buffer.readInt();
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            level = buffer.readInt();
            remaining = buffer.readInt();
            mode = buffer.readInt();
            throttled = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimension);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(level);
            buffer.writeInt(remaining);
            buffer.writeInt(mode);
            buffer.writeBoolean(throttled);
        }
    }

    public static final class AccelerationSyncHandler implements IMessageHandler<AccelerationSyncMessage, IMessage> {

        public IMessage onMessage(AccelerationSyncMessage message, MessageContext context) {
            TenkeiTech.proxy.receiveWandAcceleration(message);
            return null;
        }
    }

    public static void syncProtection(EntityPlayerMP player, boolean enabled, boolean hidden) {
        ProtectionSyncMessage message = new ProtectionSyncMessage(player.getUniqueID(), enabled, hidden);
        CHANNEL.sendTo(message, player);
        CHANNEL.sendToAllAround(
            message,
            new NetworkRegistry.TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 512));
    }

    public static final class ProtectionSyncMessage implements IMessage {

        private java.util.UUID player;
        private boolean enabled;
        private boolean hidden;

        public ProtectionSyncMessage() {}

        ProtectionSyncMessage(java.util.UUID player, boolean enabled, boolean hidden) {
            this.player = player;
            this.enabled = enabled;
            this.hidden = hidden;
        }

        public void fromBytes(ByteBuf buffer) {
            player = new java.util.UUID(buffer.readLong(), buffer.readLong());
            enabled = buffer.readBoolean();
            hidden = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(player.getMostSignificantBits());
            buffer.writeLong(player.getLeastSignificantBits());
            buffer.writeBoolean(enabled);
            buffer.writeBoolean(hidden);
        }
    }

    public static final class ProtectionSyncHandler implements IMessageHandler<ProtectionSyncMessage, IMessage> {

        public IMessage onMessage(ProtectionSyncMessage message, MessageContext context) {
            UniversalWandProtectionHandler.receiveClientProtection(message.player, message.enabled);
            UniversalWandProtectionHandler.receiveClientStealth(message.player, message.hidden);
            return null;
        }
    }

    public static void requestFlightSpeed(boolean decrease) {
        CHANNEL.sendToServer(new FlightSpeedMessage(decrease));
    }

    public static void syncFlightSpeed(EntityPlayerMP player, float speed, boolean notify) {
        CHANNEL.sendTo(new FlightSpeedSyncMessage(player.getUniqueID(), speed, notify), player);
    }

    public static final class FlightSpeedSyncMessage implements IMessage {

        private java.util.UUID player;
        private float speed;
        private boolean notify;

        public FlightSpeedSyncMessage() {}

        FlightSpeedSyncMessage(java.util.UUID player, float speed, boolean notify) {
            this.player = player;
            this.speed = speed;
            this.notify = notify;
        }

        public void fromBytes(ByteBuf buffer) {
            player = new java.util.UUID(buffer.readLong(), buffer.readLong());
            speed = buffer.readFloat();
            notify = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(player.getMostSignificantBits());
            buffer.writeLong(player.getLeastSignificantBits());
            buffer.writeFloat(speed);
            buffer.writeBoolean(notify);
        }
    }

    public static final class FlightSpeedSyncHandler implements IMessageHandler<FlightSpeedSyncMessage, IMessage> {

        public IMessage onMessage(FlightSpeedSyncMessage message, MessageContext context) {
            UniversalWandUtilityHandler.receiveClientSpeed(message.player, message.speed);
            if (message.notify) UniversalWandUtilityHandler.showClientSpeedNotice();
            return null;
        }
    }

    public static final class FlightSpeedMessage implements IMessage {

        private boolean decrease;

        public FlightSpeedMessage() {}

        FlightSpeedMessage(boolean decrease) {
            this.decrease = decrease;
        }

        public void fromBytes(ByteBuf buffer) {
            decrease = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(decrease);
        }
    }

    public static final class FlightSpeedHandler implements IMessageHandler<FlightSpeedMessage, IMessage> {

        public IMessage onMessage(FlightSpeedMessage message, MessageContext context) {
            UniversalWandUtilityHandler.requestSpeed(context.getServerHandler().playerEntity, message.decrease);
            return null;
        }
    }

    public static void requestChainKey(boolean held) {
        CHANNEL.sendToServer(new ChainKeyMessage(held));
    }

    public static void requestChainStart(int x, int y, int z) {
        CHANNEL.sendToServer(new ChainStartMessage(x, y, z));
    }

    public static void requestBuildStart(int x, int y, int z, int side, float hitX, float hitY, float hitZ, int core) {
        requestBuildStart(x, y, z, side, hitX, hitY, hitZ, core, false);
    }

    public static void requestBuildStart(int x, int y, int z, int side, float hitX, float hitY, float hitZ, int core,
        boolean undo) {
        CHANNEL.sendToServer(new BuildStartMessage(x, y, z, side, hitX, hitY, hitZ, core, undo));
    }

    public static void requestUndoKey(boolean held) {
        CHANNEL.sendToServer(new UndoKeyMessage(held));
    }

    public static void sendUndoPreview(EntityPlayerMP player, List<Position> positions) {
        CHANNEL.sendTo(new UndoPreviewMessage(player.dimension, positions), player);
    }

    public static void requestBuildingSupply(BuildingContext context) {
        CHANNEL.sendToServer(new BuildingSupplyRequest(context));
    }

    public static void sendBuildingSupply(EntityPlayerMP player, BuildingContext context, ItemStack material,
        int count) {
        CHANNEL.sendTo(new BuildingSupplyMessage(context, material, count), player);
    }

    public static final class BuildingSupplyRequest implements IMessage {

        private int x, y, z, side;
        private float hitX, hitY, hitZ;

        public BuildingSupplyRequest() {}

        BuildingSupplyRequest(BuildingContext context) {
            x = context.origin.x;
            y = context.origin.y;
            z = context.origin.z;
            side = context.face.ordinal();
            hitX = context.hitX;
            hitY = context.hitY;
            hitZ = context.hitZ;
        }

        public void fromBytes(ByteBuf buffer) {
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            side = buffer.readUnsignedByte();
            hitX = buffer.readFloat();
            hitY = buffer.readFloat();
            hitZ = buffer.readFloat();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeByte(side);
            buffer.writeFloat(hitX);
            buffer.writeFloat(hitY);
            buffer.writeFloat(hitZ);
        }
    }

    public static final class BuildingSupplyRequestHandler implements IMessageHandler<BuildingSupplyRequest, IMessage> {

        public IMessage onMessage(BuildingSupplyRequest message, MessageContext context) {
            UniversalWandBuildingHandler.requestSupply(
                context.getServerHandler().playerEntity,
                message.x,
                message.y,
                message.z,
                message.side,
                message.hitX,
                message.hitY,
                message.hitZ);
            return null;
        }
    }

    public static final class BuildingSupplyMessage implements IMessage {

        public int dimension, x, y, z, count;
        public ItemStack wand, material;

        public BuildingSupplyMessage() {}

        BuildingSupplyMessage(BuildingContext context, ItemStack material, int count) {
            dimension = context.player.dimension;
            x = context.origin.x;
            y = context.origin.y;
            z = context.origin.z;
            wand = context.wand.copy();
            this.material = material;
            this.count = count;
        }

        public void fromBytes(ByteBuf buffer) {
            dimension = buffer.readInt();
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            count = buffer.readInt();
            if (count < 0 || count > BuildingContext.MAX_BLOCKS)
                throw new IllegalArgumentException("Invalid building supply count");
            wand = ByteBufUtils.readItemStack(buffer);
            material = ByteBufUtils.readItemStack(buffer);
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimension);
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(count);
            ByteBufUtils.writeItemStack(buffer, wand);
            ByteBufUtils.writeItemStack(buffer, material);
        }
    }

    public static final class BuildingSupplyHandler implements IMessageHandler<BuildingSupplyMessage, IMessage> {

        public IMessage onMessage(BuildingSupplyMessage message, MessageContext context) {
            TenkeiTech.proxy.enqueueWandBuildingSupply(message);
            return null;
        }
    }

    public static final class UndoKeyMessage implements IMessage {

        private boolean held;

        public UndoKeyMessage() {}

        public UndoKeyMessage(boolean held) {
            this.held = held;
        }

        public void fromBytes(ByteBuf buffer) {
            held = buffer.readBoolean();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(held);
        }
    }

    public static final class UndoKeyHandler implements IMessageHandler<UndoKeyMessage, IMessage> {

        public IMessage onMessage(UndoKeyMessage message, MessageContext context) {
            BuildingUndoHistory.requestKey(context.getServerHandler().playerEntity, message.held);
            return null;
        }
    }

    public static final class UndoPreviewMessage implements IMessage {

        public int dimension;
        public List<Position> positions = new ArrayList<>();

        public UndoPreviewMessage() {}

        public UndoPreviewMessage(int dimension, List<Position> positions) {
            this.dimension = dimension;
            this.positions = positions;
        }

        public void fromBytes(ByteBuf buffer) {
            dimension = buffer.readInt();
            int count = buffer.readInt();
            if (count < 0 || count > BuildingContext.MAX_BLOCKS)
                throw new IllegalArgumentException("Invalid undo preview size");
            for (int i = 0; i < count; i++)
                positions.add(new Position(buffer.readInt(), buffer.readInt(), buffer.readInt()));
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimension);
            buffer.writeInt(positions.size());
            for (Position position : positions) {
                buffer.writeInt(position.x);
                buffer.writeInt(position.y);
                buffer.writeInt(position.z);
            }
        }
    }

    public static final class UndoPreviewHandler implements IMessageHandler<UndoPreviewMessage, IMessage> {

        public IMessage onMessage(UndoPreviewMessage message, MessageContext context) {
            TenkeiTech.proxy.enqueueWandUndoPreview(message);
            return null;
        }
    }

    public static final class BuildStartMessage implements IMessage {

        private int x, y, z, side, core;
        private float hitX, hitY, hitZ;
        private boolean undo;

        public BuildStartMessage() {}

        public BuildStartMessage(int x, int y, int z, int side, float hitX, float hitY, float hitZ, int core,
            boolean undo) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.core = core;
            this.undo = undo;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            side = buffer.readUnsignedByte();
            hitX = buffer.readFloat();
            hitY = buffer.readFloat();
            hitZ = buffer.readFloat();
            core = buffer.readUnsignedByte();
            undo = buffer.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeByte(side);
            buffer.writeFloat(hitX);
            buffer.writeFloat(hitY);
            buffer.writeFloat(hitZ);
            buffer.writeByte(core);
            buffer.writeBoolean(undo);
        }
    }

    public static final class BuildStartHandler implements IMessageHandler<BuildStartMessage, IMessage> {

        @Override
        public IMessage onMessage(BuildStartMessage message, MessageContext context) {
            UniversalWandBuildingHandler.requestStart(
                context.getServerHandler().playerEntity,
                message.x,
                message.y,
                message.z,
                message.side,
                message.hitX,
                message.hitY,
                message.hitZ,
                message.core,
                message.undo);
            return null;
        }
    }

    public static final class ChainKeyMessage implements IMessage {

        private boolean held;

        public ChainKeyMessage() {}

        public ChainKeyMessage(boolean held) {
            this.held = held;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            held = buffer.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(held);
        }
    }

    public static final class ChainKeyHandler implements IMessageHandler<ChainKeyMessage, IMessage> {

        @Override
        public IMessage onMessage(ChainKeyMessage message, MessageContext context) {
            UniversalWandActions.request(
                context.getServerHandler().playerEntity,
                UniversalWandActions.Action.CHAIN_KEY,
                message.held ? 1 : 0,
                0,
                0);
            return null;
        }
    }

    public static final class ChainStartMessage implements IMessage {

        private int x;
        private int y;
        private int z;

        public ChainStartMessage() {}

        public ChainStartMessage(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
        }
    }

    public static final class ChainStartHandler implements IMessageHandler<ChainStartMessage, IMessage> {

        @Override
        public IMessage onMessage(ChainStartMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            UniversalWandMiningHandler.requestChainStart(player, message.x, message.y, message.z);
            return null;
        }
    }

    public static void requestForceBedrock(int x, int y, int z) {
        CHANNEL.sendToServer(new ForceBedrockMessage(x, y, z));
    }

    public static final class OpenGuiMessage implements IMessage {

        private int slot;

        public OpenGuiMessage() {}

        OpenGuiMessage(int slot) {
            this.slot = slot;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            slot = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(slot);
        }
    }

    public static final class OpenGuiHandler implements IMessageHandler<OpenGuiMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenGuiMessage message, MessageContext context) {
            UniversalWandActions.request(
                context.getServerHandler().playerEntity,
                UniversalWandActions.Action.OPEN_GUI,
                message.slot,
                0,
                0);
            return null;
        }
    }

    public static final class ForceBedrockMessage implements IMessage {

        private int x;
        private int y;
        private int z;

        public ForceBedrockMessage() {}

        public ForceBedrockMessage(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
        }
    }

    public static final class ForceBedrockHandler implements IMessageHandler<ForceBedrockMessage, IMessage> {

        @Override
        public IMessage onMessage(ForceBedrockMessage message, MessageContext context) {
            UniversalWandActions.request(
                context.getServerHandler().playerEntity,
                UniversalWandActions.Action.BEDROCK,
                message.x,
                message.y,
                message.z);
            return null;
        }
    }
}
