/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.tenkeitech.TenkeiTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** One cached server snapshot per catalog revision; login sends data without scanning recipes. */
public final class AutoTemplateCatalogSync {

    public static final int PAGE_BYTES = 24_000;
    public static final int MAX_BYTES = 12 * 1024 * 1024;
    public static final int MAX_PAGES = (MAX_BYTES + PAGE_BYTES - 1) / PAGE_BYTES;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("tenkei_catalog");
    private static byte[] cached;
    private static long cachedRevision = -1;

    public static void clearSnapshot() {
        cached = null;
        cachedRevision = -1;
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Page.class, 0, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new AutoTemplateCatalogSync());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        try {
            long revision = AutoTemplateRecipeRegistry.getRevision();
            if (cached == null || cachedRevision != revision) {
                cached = encode(AutoTemplateRecipeRegistry.snapshot());
                cachedRevision = revision;
            }
            int pages = (cached.length + PAGE_BYTES - 1) / PAGE_BYTES;
            for (int page = 0; page < pages; page++) {
                CHANNEL.sendTo(
                    new Page(
                        revision,
                        page,
                        pages,
                        Arrays
                            .copyOfRange(cached, page * PAGE_BYTES, Math.min(cached.length, (page + 1) * PAGE_BYTES))),
                    player);
            }
        } catch (IOException | RuntimeException failure) {
            TenkeiTech.LOG.error("Failed to send auto-template catalog to {}", player.getCommandSenderName(), failure);
        }
    }

    private static byte[] encode(Map<TemplateType, TemplateRecipeCatalog> catalogs) throws IOException {
        if (catalogs.isEmpty()) throw new IllegalStateException("World catalog is not ready");
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<TemplateType, TemplateRecipeCatalog> catalog : catalogs.entrySet()) {
            for (VirtualPattern pattern : catalog.getValue()
                .getPatterns()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(
                    "Type",
                    catalog.getKey()
                        .getStableId());
                ItemStack input = pattern.getItemInput();
                if (input != null) tag.setTag("Item", writeItem(input));
                else tag.setTag(
                    "Fluid",
                    pattern.getFluidInput()
                        .writeToNBT(new NBTTagCompound()));
                tag.setTag("Output", writeItem(pattern.getOutput()));
                tag.setLong("EUt", pattern.getRecipeEUt());
                tag.setInteger("Tier", pattern.getRecipeTier());
                tag.setInteger(
                    "Priority",
                    pattern.getDetails()
                        .getPriority());
                entries.appendTag(tag);
            }
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Version", 1);
        root.setTag("Patterns", entries);
        byte[] bytes = CompressedStreamTools.compress(root);
        if (bytes.length > MAX_BYTES) throw new IOException("Auto-template catalog exceeds sync limit");
        return bytes;
    }

    private static NBTTagCompound writeItem(ItemStack stack) {
        NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
        tag.setInteger("FullCount", stack.stackSize);
        return tag;
    }

    private static ItemStack readItem(NBTTagCompound tag) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
        if (stack == null || tag.getInteger("FullCount") <= 0)
            throw new IllegalArgumentException("Invalid catalog item");
        stack.stackSize = tag.getInteger("FullCount");
        return stack;
    }

    public static Map<TemplateType, TemplateRecipeCatalog> decode(byte[] bytes) throws IOException {
        NBTTagCompound root = CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(64L * 1024 * 1024));
        if (root.getInteger("Version") != 1 || !root.hasKey("Patterns", 9))
            throw new IOException("Unsupported catalog sync format");
        NBTTagList entries = root.getTagList("Patterns", 10);
        if (entries.tagCount() > 200_000) throw new IOException("Too many catalog patterns");
        Map<TemplateType, ArrayList<VirtualPattern>> grouped = new EnumMap<>(TemplateType.class);
        for (TemplateType type : TemplateType.values())
            if (type != TemplateType.NONE) grouped.put(type, new ArrayList<>());
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound tag = entries.getCompoundTagAt(index);
            TemplateType type = TemplateType.fromId(tag.getInteger("Type"));
            if (type == TemplateType.NONE) throw new IOException("Invalid catalog template");
            ItemStack output = readItem(tag.getCompoundTag("Output"));
            VirtualPattern pattern;
            if (type.getInputKind() == InputKind.INGOT) {
                ItemStack input = readItem(tag.getCompoundTag("Item"));
                PatternKey key = new PatternKey(
                    type,
                    TemplateInputKey.forItem(input),
                    TemplateOutputKey.forItem(output));
                pattern = VirtualPattern.forItem(key, input, output, tag.getLong("EUt"), tag.getInteger("Tier"));
            } else {
                FluidStack input = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("Fluid"));
                PatternKey key = new PatternKey(
                    type,
                    TemplateInputKey.forFluid(input),
                    TemplateOutputKey.forItem(output));
                pattern = VirtualPattern.forFluid(key, input, output, tag.getLong("EUt"), tag.getInteger("Tier"));
            }
            pattern.getDetails()
                .setPriority(tag.getInteger("Priority"));
            ArrayList<VirtualPattern> patterns = grouped.get(type);
            if (patterns.size() >= 65535) throw new IOException("Too many patterns for a template");
            patterns.add(pattern);
        }
        Map<TemplateType, TemplateRecipeCatalog> catalogs = new EnumMap<>(TemplateType.class);
        for (Map.Entry<TemplateType, ArrayList<VirtualPattern>> entry : grouped.entrySet()) {
            catalogs.put(entry.getKey(), TemplateRecipeCatalog.of(entry.getKey(), entry.getValue()));
        }
        return catalogs;
    }

    public static final class Page implements IMessage {

        public long revision;
        public int index;
        public int total;
        public byte[] data;

        public Page() {}

        private Page(long revision, int index, int total, byte[] data) {
            this.revision = revision;
            this.index = index;
            this.total = total;
            this.data = data;
        }

        public void fromBytes(ByteBuf buffer) {
            revision = buffer.readLong();
            index = buffer.readInt();
            total = buffer.readInt();
            int length = buffer.readInt();
            if (total < 1 || total > MAX_PAGES
                || index < 0
                || index >= total
                || length < 1
                || length > PAGE_BYTES
                || length != buffer.readableBytes()) throw new IllegalArgumentException("Invalid catalog page");
            data = new byte[length];
            buffer.readBytes(data);
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeLong(revision);
            buffer.writeInt(index);
            buffer.writeInt(total);
            buffer.writeInt(data.length);
            buffer.writeBytes(data);
        }
    }

    public static final class Handler implements IMessageHandler<Page, IMessage> {

        public IMessage onMessage(Page message, MessageContext context) {
            TenkeiTech.proxy.receiveAutoTemplateCatalog(message, context.netHandler);
            return null;
        }
    }
}
