/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.value.sync.SyncHandler;

/** Localized search for one GUI session; the server owns the catalog and all mutations. */
final class AutoTemplateSearchSyncHandler extends SyncHandler<AutoTemplateSearchSyncHandler> {

    static final int MAX_QUERY_LENGTH = 64;
    private static final int MAX_PATTERNS = 65_535;
    private static final int CHUNK_SIZE = 128;
    private final Supplier<List<ItemStack>> outputs;
    private final Consumer<BitSet> applyFilter;
    private final IntConsumer togglePattern;
    private final IntConsumer toggleTier;
    private List<ItemStack> sentOutputs;
    private int revision;
    private int sentCount;
    private int sequence;
    private int acknowledgedSequence = -1;
    private int expectedCount;
    private final List<String> names = new ArrayList<String>();
    private String query = "";
    private boolean ready;

    AutoTemplateSearchSyncHandler(Supplier<List<ItemStack>> outputs, Consumer<BitSet> applyFilter,
        IntConsumer togglePattern, IntConsumer toggleTier) {
        this.outputs = outputs;
        this.applyFilter = applyFilter;
        this.togglePattern = togglePattern;
        this.toggleTier = toggleTier;
        allowC2S();
    }

    String query() {
        return query;
    }

    void setQuery(String value) {
        String next = value == null ? "" : value.substring(0, Math.min(value.length(), MAX_QUERY_LENGTH));
        if (query.equals(next)) return;
        query = next;
        if (ready && isValid() && getSyncManager().isClient()) sendFilter();
    }

    boolean isPending() {
        if (isValid() && !getSyncManager().isClient()) return false;
        return !ready || acknowledgedSequence != sequence;
    }

    void requestPatternToggle(int index) {
        sendAction(2, index);
    }

    void requestTierToggle(int tier) {
        sendAction(3, tier);
    }

    private void sendAction(int id, int value) {
        if (isPending()) return;
        syncToServer(id, buffer -> {
            buffer.writeInt(revision);
            buffer.writeInt(sequence);
            buffer.writeInt(value);
        });
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        List<ItemStack> current = outputs.get();
        if (init || current != sentOutputs) {
            sentOutputs = current;
            if (current.size() > MAX_PATTERNS) throw new IllegalStateException("Template search catalog too large");
            revision++;
            sentCount = 0;
            sequence = 0;
            acknowledgedSequence = -1;
            syncToClient(0, buffer -> {
                buffer.writeInt(revision);
                buffer.writeInt(sentOutputs.size());
            });
        }
        if (sentCount < sentOutputs.size()) {
            int start = sentCount;
            int end = Math.min(start + CHUNK_SIZE, sentOutputs.size());
            syncToClient(1, buffer -> {
                buffer.writeInt(revision);
                buffer.writeInt(start);
                buffer.writeInt(end - start);
                for (int index = start; index < end; index++) buffer.writeItemStackToBuffer(sentOutputs.get(index));
            });
            sentCount = end;
        }
        // Registered after the visible cell values: acknowledge only after their updates.
        if (sequence > 0 && acknowledgedSequence != sequence) {
            syncToClient(2, buffer -> {
                buffer.writeInt(revision);
                buffer.writeInt(sequence);
            });
            acknowledgedSequence = sequence;
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        int receivedRevision = buffer.readInt();
        if (id == 0) {
            int count = buffer.readInt();
            if (count < 0 || count > MAX_PATTERNS) throw new IOException("Invalid template search catalog size");
            revision = receivedRevision;
            expectedCount = count;
            names.clear();
            sequence = 0;
            acknowledgedSequence = -1;
            ready = count == 0;
            if (ready) sendFilter();
        } else if (id == 1 && receivedRevision == revision) {
            int start = buffer.readInt();
            int count = buffer.readInt();
            if (start != names.size() || count < 1 || count > CHUNK_SIZE || count > expectedCount - start) {
                throw new IOException("Invalid template search catalog chunk");
            }
            for (int index = 0; index < count; index++) {
                ItemStack output = buffer.readItemStackFromBuffer();
                names.add(output == null ? "" : normalize(output.getDisplayName()));
            }
            ready = names.size() == expectedCount;
            if (ready) sendFilter();
        } else if (id == 2 && receivedRevision == revision) {
            acknowledgedSequence = buffer.readInt();
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        int receivedRevision = buffer.readInt();
        int receivedSequence = buffer.readInt();
        if (receivedRevision != revision || sentOutputs != outputs.get()) return;
        if (id == 1) {
            if (receivedSequence <= sequence || sentCount != sentOutputs.size()) return;
            boolean active = buffer.readBoolean();
            int length = buffer.readInt();
            if (length < 0 || length > (sentOutputs.size() + 7) / 8) return;
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            BitSet matches = BitSet.valueOf(bytes);
            if (matches.length() > sentOutputs.size()) return;
            applyFilter.accept(active ? matches : null);
            sequence = receivedSequence;
        } else if (receivedSequence == sequence && sequence > 0) {
            int value = buffer.readInt();
            if (id == 2) togglePattern.accept(value);
            else if (id == 3) toggleTier.accept(value);
        }
    }

    private void sendFilter() {
        String normalized = normalize(query);
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        BitSet matches = new BitSet(names.size());
        if (terms.length > 0) {
            for (int index = 0; index < names.size(); index++) {
                boolean match = true;
                for (String term : terms) {
                    if (!names.get(index)
                        .contains(term)) {
                        match = false;
                        break;
                    }
                }
                if (match) matches.set(index);
            }
        }
        byte[] bytes = matches.toByteArray();
        sequence++;
        syncToServer(1, buffer -> {
            buffer.writeInt(revision);
            buffer.writeInt(sequence);
            buffer.writeBoolean(terms.length > 0);
            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        });
    }

    private static String normalize(String value) {
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return plain == null ? ""
            : plain.trim()
                .toLowerCase(Locale.ROOT);
    }
}
