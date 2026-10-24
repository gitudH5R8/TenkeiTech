/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autoextract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import appeng.api.storage.data.IAEStack;
import appeng.container.sync.SyncCodec;
import appeng.container.sync.SyncCodecs;
import io.netty.buffer.ByteBuf;

/** A server-owned display snapshot, never an inventory or a writable task. */
public final class AutoExtractionView {

    public static final AutoExtractionView EMPTY = new AutoExtractionView(
        false,
        false,
        false,
        0,
        Collections.emptyList());
    public static final SyncCodec<AutoExtractionView> CODEC = SyncCodecs.of(
        "tenkeitech:auto_extraction_view",
        (buf, value) -> value.write(buf),
        AutoExtractionView::read,
        value -> value,
        AutoExtractionView::same);

    private final boolean present;
    private final boolean locked;
    private final boolean quarantined;
    private final long taskId;
    private final List<IAEStack<?>> remaining;

    public AutoExtractionView(boolean present, boolean locked, boolean quarantined, long taskId,
        List<IAEStack<?>> remaining) {
        this.present = present;
        this.locked = locked;
        this.quarantined = quarantined;
        this.taskId = taskId;
        List<IAEStack<?>> copy = new ArrayList<>();
        for (IAEStack<?> stack : remaining) copy.add(stack.copy());
        this.remaining = Collections.unmodifiableList(copy);
    }

    public boolean present() {
        return present;
    }

    public boolean locked() {
        return locked;
    }

    public boolean quarantined() {
        return quarantined;
    }

    public long taskId() {
        return taskId;
    }

    public List<IAEStack<?>> remaining() {
        List<IAEStack<?>> copy = new ArrayList<>();
        for (IAEStack<?> stack : remaining) copy.add(stack.copy());
        return copy;
    }

    private void write(ByteBuf buf) throws IOException {
        buf.writeBoolean(present);
        buf.writeBoolean(locked);
        buf.writeBoolean(quarantined);
        buf.writeLong(taskId);
        buf.writeByte(remaining.size());
        for (IAEStack<?> stack : remaining) IAEStack.writeToPacketGeneric(buf, stack);
    }

    private static AutoExtractionView read(ByteBuf buf) throws IOException {
        boolean present = buf.readBoolean(), locked = buf.readBoolean(), quarantined = buf.readBoolean();
        long taskId = buf.readLong();
        int size = buf.readUnsignedByte();
        if (size > 127) throw new IOException("Too many automatic extraction outputs");
        List<IAEStack<?>> remaining = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            IAEStack<?> stack = IAEStack.fromPacketGeneric(buf);
            if (stack == null || !stack.isItem() && !stack.isFluid() || stack.getStackSize() <= 0)
                throw new IOException("Invalid automatic extraction output");
            remaining.add(stack);
        }
        return new AutoExtractionView(present, locked, quarantined, taskId, remaining);
    }

    private boolean same(AutoExtractionView other) {
        if (present != other.present || locked != other.locked
            || taskId != other.taskId
            || quarantined != other.quarantined
            || remaining.size() != other.remaining.size()) return false;
        for (int i = 0; i < remaining.size(); i++) {
            IAEStack<?> a = remaining.get(i), b = other.remaining.get(i);
            if (!a.isSameType(b) || a.getStackSize() != b.getStackSize()) return false;
        }
        return true;
    }
}
