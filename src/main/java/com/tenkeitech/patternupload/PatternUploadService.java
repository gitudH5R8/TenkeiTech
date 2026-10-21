/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.patternupload.PatternUploadNetwork.ResultCode;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IInterfaceViewable;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

/** Server-authoritative discovery and transaction handling for pattern uploads. */
final class PatternUploadService {

    private static final int PATTERN_OUTPUT_SLOT = 1;
    private static final int MAX_PROVIDERS = 512;
    private static final int MAX_SLOTS_PER_PROVIDER = 4096;
    private static final int MAX_SCANNED_SLOTS = 65_536;
    private static final int MAX_NAME_LENGTH = 96;
    private static final long SESSION_TTL_NANOS = 30_000_000_000L;
    private static final long EMPTY_SESSION_TTL_NANOS = 2_000_000_000L;
    private static final AtomicLong NEXT_NONCE = new AtomicLong(System.nanoTime());
    private static final Map<EntityPlayerMP, UploadSession> SESSIONS = new WeakHashMap<>();
    private static final Map<EntityPlayerMP, UndoRecord> LAST_UPLOADS = new WeakHashMap<>();

    private PatternUploadService() {}

    static void handleTargetsRequest(EntityPlayerMP player, int windowId, long requestId, long nonce,
        List<String> names, List<String> searchTerms, List<String> pinnedProviders, boolean autoUpload,
        ItemStack expectedSource) {
        if (nonce == 0L) {
            openSession(player, windowId, requestId, names, searchTerms, pinnedProviders, autoUpload, expectedSource);
            return;
        }

        UploadSession session = SESSIONS.get(player);
        Validation validation = validateSession(player, windowId, requestId, nonce, session);
        if (!validation.valid()) {
            discardMatchingSession(player, windowId, requestId, nonce, session);
            sendResult(player, windowId, requestId, false, validation.error, "");
            return;
        }
        sendPage(player, session);
    }

    static void handleTargetSelection(EntityPlayerMP player, int windowId, long requestId, long nonce, int groupIndex) {
        handleTargetSelection(player, windowId, requestId, nonce, groupIndex, null);
    }

    private static void handleTargetSelection(EntityPlayerMP player, int windowId, long requestId, long nonce,
        int groupIndex, Discovery freshDiscovery) {
        UploadSession session = SESSIONS.get(player);
        Validation validation = validateSession(player, windowId, requestId, nonce, session);
        if (!validation.valid()) {
            discardMatchingSession(player, windowId, requestId, nonce, session);
            sendResult(player, windowId, requestId, false, validation.error, "");
            return;
        }
        if (groupIndex < 0 || groupIndex >= session.groups.size()) {
            sendResult(player, windowId, requestId, false, ResultCode.TARGET_UNAVAILABLE, "");
            return;
        }

        TargetGroup group = session.groups.get(groupIndex);
        // A cached page cannot prove absence of a duplicate: another player may have installed it meanwhile.
        if (freshDiscovery == null) {
            freshDiscovery = discoverTargets(session.grid, validation.sourcePattern, group.originalName);
        }
        if (!freshDiscovery.complete) {
            sendResult(player, windowId, requestId, false, ResultCode.DUPLICATE_CHECK_INCOMPLETE, group.name);
            return;
        }
        TargetGroup currentGroup = null;
        for (TargetGroup candidate : freshDiscovery.groups) {
            if (candidate.originalName.equals(group.originalName)) {
                currentGroup = candidate;
                break;
            }
        }
        if (currentGroup == null) {
            sendResult(player, windowId, requestId, false, ResultCode.TARGET_UNAVAILABLE, group.name);
            return;
        }
        group = currentGroup;
        // Check the entire group before trying any empty slot, including duplicates in full members.
        for (TargetMember member : group.members) {
            if (member.duplicateSlot < 0) continue;
            if (!isCurrentMember(session.grid, member, group.originalName) || !PatternUploadDuplicateGuard.matches(
                validation.sourcePattern,
                member.view.getPatterns()
                    .getStackInSlot(member.duplicateSlot))) {
                sendResult(player, windowId, requestId, false, ResultCode.TARGET_UNAVAILABLE, group.name);
                return;
            }
            ResultCode refundResult = refundDuplicate(player, validation);
            boolean refunded = refundResult == ResultCode.DUPLICATE_REFUNDED;
            if (refunded || refundResult == ResultCode.REFUND_UNCERTAIN) SESSIONS.remove(player);
            validation.container.detectAndSendChanges();
            sendResult(player, windowId, requestId, refunded, refundResult, group.name);
            return;
        }
        ResultCode lastFailure = ResultCode.TARGET_FULL;
        for (TargetMember member : group.members) {
            if (!isCurrentMember(session.grid, member, group.originalName)) continue;
            InstalledPattern installed = tryInstall(member, validation.sourcePattern);
            if (installed == null) {
                lastFailure = ResultCode.TARGET_REJECTED;
                continue;
            }

            if (!clearSource(validation.sourceInventory, validation.sourcePattern)) {
                installed.rollback();
                sendResult(player, windowId, requestId, false, ResultCode.INTERNAL_ERROR, group.name);
                return;
            }

            SESSIONS.remove(player);
            LAST_UPLOADS.put(player, installed.undoRecord(session.grid, group.name));
            validation.container.detectAndSendChanges();
            sendResult(player, windowId, requestId, true, ResultCode.SUCCESS, group.name);
            return;
        }

        // Rebuild once so the client sees a current list after a stale/full selection.
        sendResult(player, windowId, requestId, false, lastFailure, group.name);
        SESSIONS.remove(player);
        openSession(
            player,
            windowId,
            requestId,
            session.preferredNames,
            session.searchTerms,
            session.pinnedProviders,
            false,
            session.sourceFingerprint);
    }

    private static void openSession(EntityPlayerMP player, int windowId, long requestId, List<String> names,
        List<String> searchTerms, List<String> pinnedProviders, boolean autoUpload, ItemStack expectedSource) {
        if (names == null) names = Collections.emptyList();
        if (searchTerms == null) searchTerms = Collections.emptyList();
        if (pinnedProviders == null) pinnedProviders = Collections.emptyList();
        UploadSession existing = SESSIONS.get(player);
        Validation validation = validateContext(player, windowId);
        if (!validation.valid()) {
            if (existing != null && existing.windowId == windowId) SESSIONS.remove(player);
            sendResult(player, windowId, requestId, false, validation.error, "");
            return;
        }

        if (!ItemStack.areItemStacksEqual(expectedSource, validation.sourcePattern)) {
            sendResult(player, windowId, requestId, false, ResultCode.SOURCE_CHANGED, "");
            return;
        }

        Discovery discovery;
        try {
            // Match the reference flow: one request enumerates the complete active Grid and sends that snapshot.
            discovery = discoverTargets(validation.grid, validation.sourcePattern, null);
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Pattern upload provider discovery failed", exception);
            SESSIONS.remove(player);
            sendResult(player, windowId, requestId, false, ResultCode.INTERNAL_ERROR, "");
            return;
        }
        List<TargetGroup> orderedGroups = new ArrayList<>(discovery.groups);
        sortTargetGroups(orderedGroups, pinnedProviders);
        List<TargetGroup> matches = PatternUploadRouting
            .select(orderedGroups, group -> group.routingName, names, searchTerms);
        boolean matched = !matches.isEmpty();
        boolean uniqueMatch = matches.size() == 1;
        // Keep the complete interface list in the session. Routing is used only for the unique auto-upload decision;
        // it must not hide the remaining interfaces or make pagination appear to have only one page.
        List<TargetGroup> groups = orderedGroups;
        long nonce = NEXT_NONCE.incrementAndGet();
        if (nonce == 0L) nonce = NEXT_NONCE.incrementAndGet();
        UploadSession session = new UploadSession(
            windowId,
            requestId,
            nonce,
            validation.container,
            validation.grid,
            validation.sourcePattern.copy(),
            groups,
            discovery,
            new ArrayList<>(names),
            new ArrayList<>(searchTerms),
            new ArrayList<>(pinnedProviders),
            matched,
            discovery.expiresAtNanos);
        SESSIONS.put(player, session);
        if (groups.isEmpty()) {
            sendResult(player, windowId, requestId, false, ResultCode.NO_TARGETS, "");
        } else if (PatternUploadRouting.canAutoUpload(autoUpload, discovery.complete, matches.size())) {
            // The just-built snapshot is still current in this same main-thread request; no second scan is needed.
            int matchedIndex = orderedGroups.indexOf(matches.get(0));
            if (matchedIndex < 0) {
                sendPages(player, session);
            } else {
                handleTargetSelection(player, windowId, requestId, nonce, matchedIndex, discovery);
            }
        } else {
            sendPages(player, session);
        }
    }

    static void handleUndoLastUpload(EntityPlayerMP player, int windowId) {
        if (player == null || !(player.openContainer instanceof ContainerPatternTerm container)
            || container.windowId != windowId) {
            sendUndo(player, "invalid_container");
            return;
        }
        if (!container.isPowered()) {
            sendUndo(player, "network_offline");
            return;
        }
        IGridNode terminalNode = container.getNetworkNode();
        IGrid grid = terminalNode == null || !terminalNode.isActive() ? null : terminalNode.getGrid();
        if (grid == null) {
            sendUndo(player, "network_offline");
            return;
        }
        ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);
        if (securityGrid != null && !securityGrid.hasPermission(player, SecurityPermissions.BUILD)) {
            sendUndo(player, "no_permission");
            return;
        }
        UndoRecord record = LAST_UPLOADS.get(player);
        if (record == null) {
            sendUndo(player, "none");
            return;
        }
        if (record.grid != grid) {
            sendUndo(player, "wrong_network");
            return;
        }

        WithdrawStatus status = withdraw(record);
        if (status == WithdrawStatus.UNAVAILABLE) {
            sendUndo(player, "target_unavailable");
            return;
        }
        if (status == WithdrawStatus.REFUND_FAILED) {
            sendUndo(player, "refund_failed");
            return;
        }
        if (status == WithdrawStatus.UNCERTAIN) {
            LAST_UPLOADS.remove(player);
            TenkeiTech.LOG.error(
                "Uncertain pattern upload undo for player {} at provider {} slot {}",
                player.getUniqueID(),
                record.providerName,
                record.slot);
            sendUndo(player, "uncertain");
            return;
        }
        if (status == WithdrawStatus.CHANGED) {
            LAST_UPLOADS.remove(player);
            sendUndo(player, "target_changed");
            return;
        }

        LAST_UPLOADS.remove(player);
        ItemStack returned = record.pattern.copy();
        returned.stackSize = 1;
        player.inventory.addItemStackToInventory(returned);
        boolean dropped = returned.stackSize > 0;
        if (dropped) player.dropPlayerItemWithRandomChoice(returned, false);
        container.detectAndSendChanges();
        player.inventoryContainer.detectAndSendChanges();
        sendUndo(player, dropped ? "success_dropped" : "success", sanitizeName(record.providerName));
    }

    private static WithdrawStatus withdraw(UndoRecord record) {
        try {
            if (!record.node.isActive() || record.node.getGrid() != record.grid
                || record.node.getMachine() != record.view
                || !record.view.shouldDisplay()) return WithdrawStatus.UNAVAILABLE;
            IInventory inventory = record.view.getPatterns();
            int slots = inventory == null ? 0
                : Math.max(0, Math.min(record.view.numSlots(), inventory.getSizeInventory()));
            if (record.slot < 0 || record.slot >= slots) return WithdrawStatus.UNAVAILABLE;
            if (!ItemStack.areItemStacksEqual(record.pattern, inventory.getStackInSlot(record.slot)))
                return WithdrawStatus.CHANGED;
            if (record.superBuffer != null)
                return record.superBuffer.withdrawUploadedPattern(record.slot, record.pattern) ? WithdrawStatus.REMOVED
                    : WithdrawStatus.REFUND_FAILED;

            boolean setterFailed = false;
            try {
                inventory.setInventorySlotContents(record.slot, null);
            } catch (RuntimeException exception) {
                setterFailed = true;
                TenkeiTech.LOG.warn("A provider threw while undoing an upload in slot {}", record.slot, exception);
            }
            ItemStack remaining;
            try {
                remaining = inventory.getStackInSlot(record.slot);
            } catch (RuntimeException exception) {
                return WithdrawStatus.UNCERTAIN;
            }
            if (setterFailed && remaining == null) return WithdrawStatus.UNCERTAIN;
            if (remaining != null)
                return ItemStack.areItemStacksEqual(record.pattern, remaining) ? WithdrawStatus.REFUND_FAILED
                    : WithdrawStatus.CHANGED;
            try {
                inventory.markDirty();
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.warn("A provider cleared an undone upload but failed to mark itself dirty", exception);
            }
            return WithdrawStatus.REMOVED;
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("Could not inspect the target of the last pattern upload", exception);
            return WithdrawStatus.UNAVAILABLE;
        }
    }

    private static void sendUndo(EntityPlayerMP player, String result, Object... arguments) {
        if (player != null)
            player.addChatMessage(new ChatComponentTranslation("tenkeitech.pattern_upload.undo." + result, arguments));
    }

    private static ResultCode refundDuplicate(EntityPlayerMP player, Validation validation) {
        ISecurityGrid security = validation.grid.getCache(ISecurityGrid.class);
        if (security != null && !security.hasPermission(player, SecurityPermissions.INJECT))
            return ResultCode.NO_PERMISSION;
        IStorageGrid storage = validation.grid.getCache(IStorageGrid.class);
        IMEMonitor<IAEItemStack> items = storage == null ? null : storage.getItemInventory();
        if (items == null || validation.container.getPowerSource() == null) return ResultCode.NETWORK_OFFLINE;
        ItemStack blank = IPatternTerminal.createBlankPattern()
            .getItemStack();
        blank.stackSize = 1;
        PatternUploadDuplicateGuard.RefundResult result = PatternUploadDuplicateGuard.refundToNetwork(
            validation.sourceInventory,
            PATTERN_OUTPUT_SLOT,
            validation.sourcePattern,
            blank,
            (stack, simulate) -> {
                IAEItemStack remainder = Platform.poweredInsert(
                    validation.container.getPowerSource(),
                    items,
                    AEItemStack.create(stack),
                    validation.container.getActionSource(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE);
                return remainder == null || remainder.getStackSize() == 0;
            });
        if (result == PatternUploadDuplicateGuard.RefundResult.REFUNDED) return ResultCode.DUPLICATE_REFUNDED;
        if (result == PatternUploadDuplicateGuard.RefundResult.REJECTED) return ResultCode.REFUND_NO_SPACE;
        if (result == PatternUploadDuplicateGuard.RefundResult.UNCERTAIN) {
            TenkeiTech.LOG.error(
                "Uncertain AE pattern refund for player {} in window {}",
                player.getUniqueID(),
                validation.container.windowId);
            return ResultCode.REFUND_UNCERTAIN;
        }
        return ResultCode.INTERNAL_ERROR;
    }

    private static void discardMatchingSession(EntityPlayerMP player, int windowId, long requestId, long nonce,
        UploadSession session) {
        // Delayed packets from an older panel must not invalidate the player's newer live session.
        if (session != null && session.windowId == windowId && session.requestId == requestId && session.nonce == nonce)
            SESSIONS.remove(player);
    }

    private static Validation validateSession(EntityPlayerMP player, int windowId, long requestId, long nonce,
        UploadSession session) {
        if (session == null || session.requestId != requestId
            || session.nonce != nonce
            || System.nanoTime() > session.expiresAtNanos) {
            return Validation.error(ResultCode.SESSION_EXPIRED);
        }
        Validation current = validateContext(player, windowId);
        if (!current.valid()) return current;
        if (session.windowId != windowId || session.container != current.container
            || session.grid != current.grid
            || !ItemStack.areItemStacksEqual(session.sourceFingerprint, current.sourcePattern)) {
            return Validation.error(ResultCode.SOURCE_CHANGED);
        }
        return current;
    }

    private static Validation validateContext(EntityPlayerMP player, int windowId) {
        if (player == null || !(player.openContainer instanceof ContainerPatternTerm container)
            || container.windowId != windowId) {
            return Validation.error(ResultCode.INVALID_CONTAINER);
        }
        if (!container.isPowered()) return Validation.error(ResultCode.NETWORK_OFFLINE);

        IGridNode terminalNode = container.getNetworkNode();
        if (terminalNode == null || !terminalNode.isActive()) return Validation.error(ResultCode.NETWORK_OFFLINE);
        IGrid grid = terminalNode.getGrid();
        if (grid == null) return Validation.error(ResultCode.NETWORK_OFFLINE);

        ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);
        if (securityGrid != null && !securityGrid.hasPermission(player, SecurityPermissions.BUILD)) {
            return Validation.error(ResultCode.NO_PERMISSION);
        }

        IInventory sourceInventory = container.getPatternTerminal()
            .getInventoryByName("pattern");
        if (sourceInventory == null || sourceInventory.getSizeInventory() <= PATTERN_OUTPUT_SLOT) {
            return Validation.error(ResultCode.NO_PATTERN);
        }
        ItemStack sourcePattern = sourceInventory.getStackInSlot(PATTERN_OUTPUT_SLOT);
        if (!isUsableEncodedPattern(sourcePattern, player)) return Validation.error(ResultCode.NO_PATTERN);
        return new Validation(container, grid, sourceInventory, sourcePattern, null);
    }

    private static boolean isUsableEncodedPattern(ItemStack stack, EntityPlayerMP player) {
        if (stack == null || stack.stackSize != 1
            || !IPatternTerminal.isEncodedPattern(stack)
            || !(stack.getItem() instanceof ICraftingPatternItem patternItem)) {
            return false;
        }
        try {
            return patternItem.getPatternForItem(stack, player.worldObj) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Discovery discoverTargets(IGrid grid, ItemStack pattern, String onlyGroup) {
        Map<String, TargetGroup> grouped = new TreeMap<>();
        Set<IInterfaceViewable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int providers = 0;
        int scannedSlots = 0;
        boolean complete = true;

        outer: for (Class<? extends IInterfaceViewable> providerClass : AEApi.instance()
            .registries()
            .interfaceTerminal()
            .getSupportedClasses()) {
            IMachineSet machines = grid.getMachines(providerClass);
            if (machines == null) continue;
            for (IGridNode node : machines) {
                if (providers >= MAX_PROVIDERS || scannedSlots >= MAX_SCANNED_SLOTS) {
                    complete = false;
                    break outer;
                }
                if (node == null || !node.isActive()) continue;
                IGridHost machine = node.getMachine();
                if (!(machine instanceof IInterfaceViewable view) || !seen.add(view)) continue;
                providers++;

                IInventory inventory;
                String groupingName;
                try {
                    if (!view.shouldDisplay() || (inventory = view.getPatterns()) == null) continue;
                    groupingName = normalizeGroupName(view.getName());
                    if (onlyGroup != null && !onlyGroup.equals(groupingName)) continue;
                } catch (RuntimeException exception) {
                    complete = false;
                    continue;
                }
                int availableSlots = Math.max(0, Math.min(view.numSlots(), inventory.getSizeInventory()));
                int slots = Math
                    .min(availableSlots, Math.min(MAX_SLOTS_PER_PROVIDER, MAX_SCANNED_SLOTS - scannedSlots));
                if (slots < availableSlots) complete = false;
                scannedSlots += slots;
                PatternUploadDuplicateGuard.Scan scan = PatternUploadDuplicateGuard.scan(inventory, slots, pattern);
                if (!scan.complete) complete = false;
                if (scan.freeSlots <= 0 && scan.duplicateSlot < 0) continue;
                TargetGroup group = grouped.get(groupingName);
                if (group == null) {
                    try {
                        String terminalName = view.getRawName();
                        net.minecraft.util.IChatComponent suffix = view.getNameSuffix();
                        String terminalSuffix = suffix == null ? ""
                            : net.minecraft.util.IChatComponent.Serializer.func_150696_a(suffix);
                        if (terminalName == null || terminalName.length() > 4096 || terminalSuffix.length() > 4096) {
                            complete = false;
                            continue;
                        }
                        group = new TargetGroup(groupingName, terminalName, terminalSuffix);
                        grouped.put(groupingName, group);
                    } catch (RuntimeException exception) {
                        complete = false;
                        continue;
                    }
                }
                group.members.add(new TargetMember(node, view, scan.freeSlots, scan.duplicateSlot));
                if (view instanceof MTESuperCraftingInputME host) {
                    try {
                        group.machineIds.addAll(host.getUploadMachineIds());
                    } catch (RuntimeException exception) {
                        complete = false;
                        TenkeiTech.LOG.warn("Could not read upload interface machine identities", exception);
                    }
                }
                group.freeSlots += scan.freeSlots;
            }
        }

        List<TargetGroup> result = new ArrayList<>(grouped.values());
        for (TargetGroup group : result) {
            group.members.sort(
                Comparator.comparingInt((TargetMember member) -> member.freeSlots)
                    .reversed());
        }
        return new Discovery(result, complete);
    }

    private static void sortTargetGroups(List<TargetGroup> groups, List<String> pinnedProviders) {
        Set<String> pinned = new HashSet<>(pinnedProviders);
        groups.sort((first, second) -> {
            boolean firstPinned = pinned.contains(first.routingName);
            boolean secondPinned = pinned.contains(second.routingName);
            if (firstPinned != secondPinned) return firstPinned ? -1 : 1;
            return compareNatural(first.originalName, second.originalName);
        });
    }

    private static int compareNatural(String first, String second) {
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.length() && secondIndex < second.length()) {
            char firstCharacter = first.charAt(firstIndex);
            char secondCharacter = second.charAt(secondIndex);
            if (Character.isDigit(firstCharacter) && Character.isDigit(secondCharacter)) {
                int firstEnd = digitEnd(first, firstIndex);
                int secondEnd = digitEnd(second, secondIndex);
                int firstNumber = skipZeroes(first, firstIndex, firstEnd);
                int secondNumber = skipZeroes(second, secondIndex, secondEnd);
                int firstDigits = firstEnd - firstNumber;
                int secondDigits = secondEnd - secondNumber;
                if (firstDigits != secondDigits) return Integer.compare(firstDigits, secondDigits);
                for (int offset = 0; offset < firstDigits; offset++) {
                    int comparison = Character
                        .compare(first.charAt(firstNumber + offset), second.charAt(secondNumber + offset));
                    if (comparison != 0) return comparison;
                }
                int runComparison = Integer.compare(firstEnd - firstIndex, secondEnd - secondIndex);
                if (runComparison != 0) return runComparison;
                firstIndex = firstEnd;
                secondIndex = secondEnd;
                continue;
            }
            int comparison = Character
                .compare(Character.toLowerCase(firstCharacter), Character.toLowerCase(secondCharacter));
            if (comparison != 0) return comparison;
            firstIndex++;
            secondIndex++;
        }
        int lengthComparison = Integer.compare(first.length(), second.length());
        if (lengthComparison != 0) return lengthComparison;
        return first.compareTo(second);
    }

    private static int digitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        return end;
    }

    private static int skipZeroes(String value, int start, int end) {
        int index = start;
        while (index + 1 < end && value.charAt(index) == '0') index++;
        return index;
    }

    private static boolean isCurrentMember(IGrid expectedGrid, TargetMember member, String expectedName) {
        try {
            return member.node.isActive() && member.node.getGrid() == expectedGrid
                && member.node.getMachine() == member.view
                && member.view.shouldDisplay()
                && expectedName.equals(normalizeGroupName(member.view.getName()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static InstalledPattern tryInstall(TargetMember member, ItemStack sourcePattern) {
        IInterfaceViewable view = member.view;
        ItemStack candidate = sourcePattern.copy();
        candidate.stackSize = 1;
        if (view instanceof MTESuperCraftingInputME superBuffer) {
            int slot = superBuffer.tryInstallUploadedPattern(candidate);
            return slot < 0 ? null : new InstalledPattern(member.node, view, superBuffer, null, slot, candidate);
        }

        IInventory inventory;
        try {
            inventory = view.getPatterns();
            if (inventory == null) return null;
            int slots = Math
                .max(0, Math.min(Math.min(view.numSlots(), inventory.getSizeInventory()), MAX_SLOTS_PER_PROVIDER));
            for (int slot = 0; slot < slots; slot++) {
                boolean writeAttempted = false;
                try {
                    if (inventory.getStackInSlot(slot) != null || !inventory.isItemValidForSlot(slot, candidate)) {
                        continue;
                    }
                    writeAttempted = true;
                    inventory.setInventorySlotContents(slot, candidate);
                    inventory.markDirty();
                    if (ItemStack.areItemStacksEqual(candidate, inventory.getStackInSlot(slot))) {
                        return new InstalledPattern(member.node, view, null, inventory, slot, candidate);
                    }
                } catch (RuntimeException exception) {
                    TenkeiTech.LOG.warn("A provider rejected a pattern-terminal upload in slot {}", slot, exception);
                }
                if (writeAttempted) {
                    // The slot was empty immediately before our setter call, so clearing its postcondition is safe.
                    rollbackGenericSlot(inventory, slot);
                }
            }
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.warn("A provider rejected a pattern-terminal upload", exception);
        }
        return null;
    }

    private static boolean clearSource(IInventory sourceInventory, ItemStack expected) {
        try {
            if (!ItemStack.areItemStacksEqual(expected, sourceInventory.getStackInSlot(PATTERN_OUTPUT_SLOT))) {
                return false;
            }
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Failed to verify the pattern-terminal upload source", exception);
            return false;
        }
        try {
            sourceInventory.setInventorySlotContents(PATTERN_OUTPUT_SLOT, null);
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Failed to remove an uploaded pattern from its pattern terminal", exception);
        }
        boolean cleared;
        try {
            cleared = sourceInventory.getStackInSlot(PATTERN_OUTPUT_SLOT) == null;
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Failed to verify the cleared pattern-terminal source", exception);
            return false;
        }
        if (!cleared) return false;
        try {
            sourceInventory.markDirty();
        } catch (RuntimeException exception) {
            // The inventory postcondition is authoritative; do not roll back the target and lose the already-cleared
            // item.
            TenkeiTech.LOG.error("The pattern terminal accepted an upload but failed to mark itself dirty", exception);
        }
        return true;
    }

    private static void rollbackGenericSlot(IInventory inventory, int slot) {
        try {
            if (inventory.getStackInSlot(slot) == null) return;
            inventory.setInventorySlotContents(slot, null);
            inventory.markDirty();
        } catch (RuntimeException exception) {
            TenkeiTech.LOG.error("Could not roll back a rejected provider upload in slot {}", slot, exception);
        }
    }

    /** Sends one complete bounded provider list. The client applies filtering and pagination locally. */
    private static void sendPage(EntityPlayerMP player, UploadSession session) {
        List<PatternUploadNetwork.TargetEntry> entries = new ArrayList<>(session.groups.size());
        for (int index = 0; index < session.groups.size(); index++) {
            TargetGroup group = session.groups.get(index);
            entries.add(
                new PatternUploadNetwork.TargetEntry(
                    index,
                    group.name,
                    group.routingName,
                    group.terminalName,
                    group.terminalSuffix,
                    group.freeSlots,
                    group.members.size(),
                    new ArrayList<>(group.machineIds)));
        }
        PatternUploadNetwork.sendPage(
            player,
            new PatternUploadNetwork.TargetsPageMessage(
                session.windowId,
                session.requestId,
                session.nonce,
                entries,
                session.matched,
                session.discovery.complete,
                session.matched ? PatternUploadRouting.displayedSearch(
                    session.groups,
                    group -> group.routingName,
                    session.preferredNames,
                    session.searchTerms) : ""));
    }

    private static void sendPages(EntityPlayerMP player, UploadSession session) {
        sendPage(player, session);
    }

    private static void sendResult(EntityPlayerMP player, int windowId, long requestId, boolean success,
        ResultCode code, String providerName) {
        PatternUploadNetwork.sendResult(
            player,
            new PatternUploadNetwork.UploadResultMessage(
                windowId,
                requestId,
                success,
                code,
                sanitizeName(providerName)));
    }

    private static String sanitizeName(String name) {
        String safe = name == null || name.trim()
            .isEmpty() ? "?"
                : name.trim()
                    .replace('\n', ' ')
                    .replace('\r', ' ');
        return safe.length() <= MAX_NAME_LENGTH ? safe : safe.substring(0, MAX_NAME_LENGTH);
    }

    private static String normalizeGroupName(String name) {
        return name == null || name.trim()
            .isEmpty() ? "?" : name;
    }

    private static final class UploadSession {

        private final int windowId;
        private final long requestId;
        private final long nonce;
        private final ContainerPatternTerm container;
        private final IGrid grid;
        private final ItemStack sourceFingerprint;
        private final List<TargetGroup> groups;
        private final Discovery discovery;
        private final List<String> preferredNames;
        private final List<String> searchTerms;
        private final List<String> pinnedProviders;
        private final boolean matched;
        private final long expiresAtNanos;

        private UploadSession(int windowId, long requestId, long nonce, ContainerPatternTerm container, IGrid grid,
            ItemStack sourceFingerprint, List<TargetGroup> groups, Discovery discovery, List<String> preferredNames,
            List<String> searchTerms, List<String> pinnedProviders, boolean matched, long expiresAtNanos) {
            this.windowId = windowId;
            this.requestId = requestId;
            this.nonce = nonce;
            this.container = container;
            this.grid = grid;
            this.sourceFingerprint = sourceFingerprint;
            this.groups = groups;
            this.discovery = discovery;
            this.preferredNames = preferredNames;
            this.searchTerms = searchTerms;
            this.pinnedProviders = pinnedProviders;
            this.matched = matched;
            this.expiresAtNanos = expiresAtNanos;
        }
    }

    private static final class TargetGroup {

        private final String name;
        private final String originalName;
        private final String terminalName;
        private final String terminalSuffix;
        private final Set<Integer> machineIds = new java.util.TreeSet<>();
        private final String routingName;
        private final List<TargetMember> members = new ArrayList<>();
        private int freeSlots;

        private TargetGroup(String name, String terminalName, String terminalSuffix) {
            this.terminalName = terminalName;
            this.terminalSuffix = terminalSuffix;
            this.name = sanitizeName(name);
            this.originalName = name;
            this.routingName = name.length() <= PatternUploadNetwork.MAX_ROUTING_TEXT && name.indexOf('\n') < 0
                && name.indexOf('\r') < 0
                && name.indexOf('\0') < 0 ? name : "";
        }
    }

    private static final class Discovery {

        private final List<TargetGroup> groups;
        private final boolean complete;
        private final long expiresAtNanos;

        private Discovery(List<TargetGroup> groups, boolean complete) {
            this.groups = groups;
            this.complete = complete;
            this.expiresAtNanos = System.nanoTime() + (groups.isEmpty() ? EMPTY_SESSION_TTL_NANOS : SESSION_TTL_NANOS);
        }
    }

    private static final class TargetMember {

        private final IGridNode node;
        private final IInterfaceViewable view;
        private final int freeSlots;
        private final int duplicateSlot;

        private TargetMember(IGridNode node, IInterfaceViewable view, int freeSlots, int duplicateSlot) {
            this.node = node;
            this.view = view;
            this.freeSlots = freeSlots;
            this.duplicateSlot = duplicateSlot;
        }
    }

    private static final class Validation {

        private final ContainerPatternTerm container;
        private final IGrid grid;
        private final IInventory sourceInventory;
        private final ItemStack sourcePattern;
        private final ResultCode error;

        private Validation(ContainerPatternTerm container, IGrid grid, IInventory sourceInventory,
            ItemStack sourcePattern, ResultCode error) {
            this.container = container;
            this.grid = grid;
            this.sourceInventory = sourceInventory;
            this.sourcePattern = sourcePattern;
            this.error = error;
        }

        private static Validation error(ResultCode code) {
            return new Validation(null, null, null, null, code);
        }

        private boolean valid() {
            return error == null;
        }
    }

    private static final class InstalledPattern {

        private final IGridNode node;
        private final IInterfaceViewable view;
        private final MTESuperCraftingInputME superBuffer;
        private final IInventory inventory;
        private final int slot;
        private final ItemStack pattern;

        private InstalledPattern(IGridNode node, IInterfaceViewable view, MTESuperCraftingInputME superBuffer,
            IInventory inventory, int slot, ItemStack pattern) {
            this.node = node;
            this.view = view;
            this.superBuffer = superBuffer;
            this.inventory = inventory;
            this.slot = slot;
            this.pattern = pattern;
        }

        private UndoRecord undoRecord(IGrid grid, String providerName) {
            return new UndoRecord(grid, node, view, superBuffer, slot, pattern.copy(), providerName);
        }

        private void rollback() {
            if (superBuffer != null) {
                if (!superBuffer.rollbackUploadedPattern(slot, pattern)) {
                    TenkeiTech.LOG.error("Could not roll back a super pattern buffer upload in slot {}", slot);
                }
                return;
            }
            try {
                if (ItemStack.areItemStacksEqual(pattern, inventory.getStackInSlot(slot))) {
                    rollbackGenericSlot(inventory, slot);
                }
            } catch (RuntimeException exception) {
                TenkeiTech.LOG.error("Could not roll back a provider upload in slot {}", slot, exception);
            }
        }
    }

    private static final class UndoRecord {

        private final IGrid grid;
        private final IGridNode node;
        private final IInterfaceViewable view;
        private final MTESuperCraftingInputME superBuffer;
        private final int slot;
        private final ItemStack pattern;
        private final String providerName;

        private UndoRecord(IGrid grid, IGridNode node, IInterfaceViewable view, MTESuperCraftingInputME superBuffer,
            int slot, ItemStack pattern, String providerName) {
            this.grid = grid;
            this.node = node;
            this.view = view;
            this.superBuffer = superBuffer;
            this.slot = slot;
            this.pattern = pattern;
            this.providerName = providerName;
        }
    }

    private enum WithdrawStatus {
        REMOVED,
        UNAVAILABLE,
        CHANGED,
        REFUND_FAILED,
        UNCERTAIN
    }
}
