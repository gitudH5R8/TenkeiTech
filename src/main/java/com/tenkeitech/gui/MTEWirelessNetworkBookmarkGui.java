/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.tenkeitech.machine.MTEWirelessNetworkBookmark;
import com.tenkeitech.wireless.WirelessAeRuntimeRegistry;
import com.tenkeitech.wireless.WirelessAeWorldData;
import com.tenkeitech.wireless.WirelessAeWorldData.NetworkRecord;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/** Per-open, server-authoritative browser for a player's current wireless network favorite. */
public final class MTEWirelessNetworkBookmarkGui extends MTEHatchBaseGui<MTEWirelessNetworkBookmark> {

    private static final int VISIBLE_NETWORKS = 3;
    private static final int EXTRA_PANEL_WIDTH = 54;

    private final EntityPlayer openingPlayer;

    public MTEWirelessNetworkBookmarkGui(MTEWirelessNetworkBookmark bookmark, EntityPlayer openingPlayer) {
        super(bookmark);
        this.openingPlayer = openingPlayer;
    }

    @Override
    protected int getBasePanelWidth() {
        return super.getBasePanelWidth() + EXTRA_PANEL_WIDTH;
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + 45;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        BrowserSession session = new BrowserSession(machine, syncManager.isClient(), openingPlayer);
        syncManager.onServerTick(session::tick);
        StringSyncValue favoriteName = new StringSyncValue(session::favoriteName);
        BooleanSyncValue hasOverflow = new BooleanSyncValue(session::hasOverflow);
        DoubleSyncValue scrollPosition = new DoubleSyncValue(session::scrollPosition, session::setScrollPosition)
            .allowC2S();
        InteractionSyncHandler listScroll = createListScrollHandler(session);
        syncManager.syncValue("wireless_bookmark_favorite", 0, favoriteName);
        syncManager.syncValue("wireless_bookmark_overflow", 0, hasOverflow);

        ParentWidget<?> content = getEmptyContent()
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.wireless_bookmark.title")).pos(2, 0))
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.wireless_bookmark.favorite",
                        () -> new Object[] { favoriteName.getStringValue() })).pos(2, 12)
                            .maxWidth(122 + EXTRA_PANEL_WIDTH))
            .child(
                createClearFavoriteButton(session).pos(126 + EXTRA_PANEL_WIDTH, 8)
                    .size(32, 16));

        content.child(
            createListScrollArea(listScroll).setEnabledIf(widget -> hasOverflow.getBoolValue())
                .pos(2, 30)
                .size(145 + EXTRA_PANEL_WIDTH, 61));

        for (int row = 0; row < VISIBLE_NETWORKS; row++) {
            content.child(
                createNetworkRow(syncManager, session, row).pos(2, 30 + row * 21)
                    .size(133 + EXTRA_PANEL_WIDTH, 19));
        }

        return content.child(
            createScrollSlider(listScroll).value(scrollPosition)
                .bounds(0, 1)
                .setAxis(GuiAxis.Y)
                .sliderSize(5, 14)
                .background(GuiTextures.DISPLAY_SMALL)
                .setEnabledIf(widget -> hasOverflow.getBoolValue())
                .pos(140 + EXTRA_PANEL_WIDTH, 30)
                .size(7, 61));
    }

    private static InteractionSyncHandler createListScrollHandler(BrowserSession session) {
        return new InteractionSyncHandler()
            .setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
    }

    private static ButtonWidget<?> createListScrollArea(InteractionSyncHandler scrollHandler) {
        return new ButtonWidget<>().syncHandler(scrollHandler)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .playClickSound(false);
    }

    private static SliderWidget createScrollSlider(InteractionSyncHandler scrollHandler) {
        return new SliderWidget() {

            @Override
            public boolean onMouseScroll(UpOrDown direction, int amount) {
                return scrollHandler.onMouseScroll((int) Math.copySign(amount, direction.modifier));
            }
        };
    }

    private ButtonWidget<?> createNetworkRow(PanelSyncManager syncManager, BrowserSession session, int row) {
        StringSyncValue name = new StringSyncValue(() -> session.rowName(row));
        BooleanSyncValue favorite = new BooleanSyncValue(() -> session.rowIsFavorite(row));
        BooleanSyncValue online = new BooleanSyncValue(() -> session.rowIsOnline(row));
        syncManager.syncValue("wireless_bookmark_name", row, name);
        syncManager.syncValue("wireless_bookmark_selected", row, favorite);
        syncManager.syncValue("wireless_bookmark_online", row, online);

        InteractionSyncHandler select = new InteractionSyncHandler()
            .setOnMousePressed(
                mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.select(row); })
            .setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
        return new ButtonWidget<>().syncHandler(select)
            .setEnabledIf(
                widget -> !name.getStringValue()
                    .isEmpty())
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> formatRow(name.getStringValue(), favorite.getBoolValue(), online.getBoolValue())))
                            .maxWidth(125 + EXTRA_PANEL_WIDTH)
                            .center())
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.wireless_bookmark.select"));
    }

    private static ButtonWidget<?> createClearFavoriteButton(BrowserSession session) {
        InteractionSyncHandler clear = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.clearFavorite(); });
        return new ButtonWidget<>().syncHandler(clear)
            .overlay(IKey.lang("tenkeitech.gui.wireless_bookmark.clear"));
    }

    private static String formatRow(String name, boolean favorite, boolean online) {
        if (name == null || name.isEmpty()) {
            return StatCollector.translateToLocal("tenkeitech.gui.wireless_bookmark.empty");
        }
        String text = StatCollector.translateToLocalFormatted(
            "tenkeitech.gui.wireless_bookmark.row",
            favorite ? "\u2605" : " ",
            name,
            online ? "\u25cf" : "\u25cb");
        return favorite ? EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + text + EnumChatFormatting.RESET
            : text;
    }

    private static final class BrowserSession {

        private final MTEWirelessNetworkBookmark machine;
        private final boolean client;
        private final UUID player;
        private List<NetworkRecord> networks = Collections.emptyList();
        private int offset;
        private int refreshTicks;
        private double clientScrollPosition;

        private BrowserSession(MTEWirelessNetworkBookmark machine, boolean client, EntityPlayer openingPlayer) {
            this.machine = machine;
            this.client = client;
            this.player = openingPlayer == null ? null : openingPlayer.getUniqueID();
            refresh();
            if (!client) scrollToFavorite();
        }

        private void tick() {
            if (client || ++refreshTicks < 20) return;
            refreshTicks = 0;
            refresh();
        }

        private void scroll(int amount) {
            if (client || amount == 0) return;
            refresh();
            offset = clampOffset(offset + (amount > 0 ? -1 : 1));
        }

        private void select(int row) {
            NetworkRecord network = row(row);
            WirelessAeWorldData data = data();
            if (network != null && data != null) data.setFavorite(player, network.frequency());
            refresh();
        }

        private void clearFavorite() {
            WirelessAeWorldData data = data();
            if (data != null) data.clearFavorite(player);
            refresh();
        }

        private void scrollToFavorite() {
            WirelessAeWorldData data = data();
            if (data == null) return;
            UUID favorite = data.getFavorite(player);
            if (favorite == null) return;
            for (int index = 0; index < networks.size(); index++) {
                if (!favorite.equals(
                    networks.get(index)
                        .frequency())) {
                    continue;
                }
                offset = clampOffset(index - VISIBLE_NETWORKS / 2);
                return;
            }
        }

        private String favoriteName() {
            WirelessAeWorldData data = data();
            if (data == null) return "-";
            UUID favorite = data.getFavorite(player);
            NetworkRecord network = data.getAccessibleNetwork(player, favorite);
            return network == null ? "-" : displayNetworkName(network);
        }

        private double scrollPosition() {
            if (client) return clientScrollPosition;
            int maxOffset = maxOffset();
            return maxOffset == 0 ? 0 : (double) offset / maxOffset;
        }

        private boolean hasOverflow() {
            return networks.size() > VISIBLE_NETWORKS;
        }

        private void setScrollPosition(double position) {
            double clamped = Math.max(0, Math.min(1, position));
            if (client) {
                clientScrollPosition = clamped;
                return;
            }
            refresh();
            offset = clampOffset((int) Math.round(clamped * maxOffset()));
        }

        private String rowName(int row) {
            NetworkRecord network = row(row);
            return network == null ? "" : displayNetworkName(network);
        }

        private String displayNetworkName(NetworkRecord network) {
            World world = world();
            return world != null && network.dimensionId() != world.provider.dimensionId
                ? network.name() + " [D" + network.dimensionId() + "]"
                : network.name();
        }

        private boolean rowIsFavorite(int row) {
            NetworkRecord network = row(row);
            WirelessAeWorldData data = data();
            return network != null && data != null
                && network.frequency()
                    .equals(data.getFavorite(player));
        }

        private boolean rowIsOnline(int row) {
            NetworkRecord network = row(row);
            World world = world();
            return network != null && world != null && WirelessAeRuntimeRegistry.isCoreLoaded(world, network);
        }

        @Nullable
        private NetworkRecord row(int row) {
            int index = offset + row;
            return row < 0 || row >= VISIBLE_NETWORKS || index < 0 || index >= networks.size() ? null
                : networks.get(index);
        }

        private int maxOffset() {
            return Math.max(0, networks.size() - VISIBLE_NETWORKS);
        }

        private int clampOffset(int requestedOffset) {
            return Math.max(0, Math.min(requestedOffset, maxOffset()));
        }

        private void refresh() {
            WirelessAeWorldData data = data();
            networks = data == null ? Collections.emptyList() : data.listAccessibleNetworks(player);
            offset = clampOffset(offset);
        }

        @Nullable
        private WirelessAeWorldData data() {
            World world = world();
            return client || player == null || world == null ? null : WirelessAeWorldData.get(world);
        }

        @Nullable
        private World world() {
            IGregTechTileEntity base = machine.getBaseMetaTileEntity();
            return base == null ? null : base.getWorld();
        }
    }
}
