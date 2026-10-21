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
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.MCHelper;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.tenkeitech.Config;
import com.tenkeitech.ModMetaTileEntities;
import com.tenkeitech.machine.MTESuperCraftingInputME;
import com.tenkeitech.machine.MTEWirelessNetworkCore;
import com.tenkeitech.machine.MTEWirelessNetworkCore.BindResult;
import com.tenkeitech.machine.MTEWirelessNetworkCore.UnbindResult;
import com.tenkeitech.wireless.WirelessAeEventHandler;
import com.tenkeitech.wireless.WirelessAeRuntimeRegistry;
import com.tenkeitech.wireless.WirelessAeTargetLocation;
import com.tenkeitech.wireless.WirelessAeTargetResolver;
import com.tenkeitech.wireless.WirelessAeWorldData;
import com.tenkeitech.wireless.WirelessAeWorldData.MembershipRecord;
import com.tenkeitech.wireless.WirelessAeWorldData.NetworkRecord;

import appeng.api.networking.IGridHost;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.common.modularui2.panel.GTPopUpPanel;

/** Adds a wireless network management page to supported GT ME hatch GUIs. */
public final class WirelessMeHatchGuiSupport {

    private static final String DEFAULT_SIDE_DOCK_PANEL = "tenkeitech_wireless_hatch_side_dock";
    private static final String BINDING_PANEL = "tenkeitech_wireless_hatch_binding";
    private static final int VISIBLE_NETWORKS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int SIDE_DOCK_WIDTH = 22;
    private static final int SIDE_DOCK_HEIGHT = 22;
    private static final int SIDE_DOCK_TOP = 16;
    private static final int SIDE_DOCK_GAP = 3;
    private static final int SIDE_DOCK_LEFT_OFFSET = -(SIDE_DOCK_WIDTH + SIDE_DOCK_GAP);
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 163;

    private WirelessMeHatchGuiSupport() {}

    /** Mixin entry for fixed GT5U ME hatch GUIs which do not already provide TenkeiTech's side dock. */
    public static void attachDefaultSideDock(MTETieredMachineBlock machine, ModularPanel mainPanel,
        PanelSyncManager syncManager) {
        if (!isSupportedMeHatch(machine) || machine instanceof MTESuperCraftingInputME) return;
        MTEHatch hatch = (MTEHatch) machine;
        IPanelHandler sideDock = syncManager.syncedPanel(
            DEFAULT_SIDE_DOCK_PANEL,
            true,
            (manager, handler) -> createDefaultSideDock(hatch, manager, mainPanel));
        syncManager.addOpenListener(player -> { if (syncManager.isClient()) sideDock.openPanel(); });
    }

    public static IPanelHandler registerBindingPanel(MTEHatch hatch, PanelSyncManager syncManager,
        ModularPanel mainPanel) {
        return syncManager
            .syncedPanel(BINDING_PANEL, true, (manager, handler) -> createBindingPanel(hatch, manager, mainPanel));
    }

    public static ButtonWidget<?> createWirelessButton(IPanelHandler bindingPanel, IPanelHandler... otherPanels) {
        return new ButtonWidget<>().overlay(createWirelessIcon())
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.wireless_hatch.tooltip"))
            .onMousePressed(mouseButton -> mouseButton == 0 && togglePanel(bindingPanel, otherPanels));
    }

    private static boolean isSupportedMeHatch(Object machine) {
        return Config.enableWirelessAe && machine instanceof MTEHatch
            && machine instanceof IGridHost
            && !(machine instanceof MTEWirelessNetworkCore);
    }

    private static ModularPanel createDefaultSideDock(MTEHatch hatch, PanelSyncManager syncManager,
        ModularPanel mainPanel) {
        IPanelHandler bindingPanel = registerBindingPanel(hatch, syncManager, mainPanel);
        Flow buttons = Flow.column()
            .coverChildren()
            .child(createWirelessButton(bindingPanel).size(SLOT_SIZE, SLOT_SIZE));

        ModularPanel sideDock = new ModularPanel(DEFAULT_SIDE_DOCK_PANEL) {

            @Override
            public boolean isDraggable() {
                return false;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public boolean onKeyPressed(char typedChar, int keyCode) {
                if (MCHelper.hasMc() && keyCode == MCHelper.getMc().gameSettings.keyBindInventory.getKeyCode()) {
                    getScreen().getPanelManager()
                        .closePanelsAndScreen();
                    return true;
                }
                return super.onKeyPressed(typedChar, keyCode);
            }
        };

        return sideDock.invisible()
            .size(SIDE_DOCK_WIDTH, SIDE_DOCK_HEIGHT)
            .relative(mainPanel)
            .leftRelOffset(0f, SIDE_DOCK_LEFT_OFFSET)
            .topRelOffset(0f, SIDE_DOCK_TOP)
            .child(buttons.pos(2, 2));
    }

    private static ModularPanel createBindingPanel(MTEHatch hatch, PanelSyncManager syncManager,
        ModularPanel mainPanel) {
        BrowserSession session = new BrowserSession(hatch, syncManager.isClient(), syncManager.getPlayer());
        syncManager.onServerTick(session::tick);

        StringSyncValue currentName = new StringSyncValue(session::currentName);
        BooleanSyncValue hasOverflow = new BooleanSyncValue(session::hasOverflow);
        BooleanSyncValue canDisconnect = new BooleanSyncValue(session::canDisconnect);
        DoubleSyncValue scrollPosition = new DoubleSyncValue(session::scrollPosition, session::setScrollPosition)
            .allowC2S();
        InteractionSyncHandler listScroll = createListScrollHandler(session);
        syncManager.syncValue("wireless_hatch_current", 0, currentName);
        syncManager.syncValue("wireless_hatch_overflow", 0, hasOverflow);
        syncManager.syncValue("wireless_hatch_disconnect", 0, canDisconnect);

        ModularPanel panel = new GTPopUpPanel(BINDING_PANEL, false, false) {

            @Override
            public boolean isDraggable() {
                return false;
            }
        }.size(PANEL_WIDTH, PANEL_HEIGHT)
            .relative(mainPanel)
            .leftRelOffset(0f, 0)
            .topRelOffset(0f, 0)
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.wireless_hatch.title")).pos(7, 8))
            .child(
                createWirelessIcon().asWidget()
                    .pos(8, 23)
                    .size(12, 12))
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.wireless_hatch.current",
                        () -> new Object[] { currentName.getStringValue() })).pos(23, 25)
                            .maxWidth(144));

        panel.child(
            createListScrollArea(listScroll).setEnabledIf(widget -> hasOverflow.getBoolValue())
                .pos(9, 42)
                .size(153, 63));

        for (int row = 0; row < VISIBLE_NETWORKS; row++) {
            panel.child(
                createNetworkRow(syncManager, session, row).pos(9, 42 + row * 22)
                    .size(140, 19));
        }

        return panel.child(
            createScrollSlider(listScroll).value(scrollPosition)
                .bounds(0, 1)
                .setAxis(GuiAxis.Y)
                .sliderSize(5, 14)
                .background(GuiTextures.DISPLAY_SMALL)
                .setEnabledIf(widget -> hasOverflow.getBoolValue())
                .pos(155, 42)
                .size(7, 61))
            .child(
                createDisconnectButton(session).setEnabledIf(widget -> canDisconnect.getBoolValue())
                    .pos(9, 113)
                    .size(153, 18));
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

    private static ButtonWidget<?> createNetworkRow(PanelSyncManager syncManager, BrowserSession session, int row) {
        StringSyncValue name = new StringSyncValue(() -> session.rowName(row));
        BooleanSyncValue current = new BooleanSyncValue(() -> session.rowIsCurrent(row));
        syncManager.syncValue("wireless_hatch_name", row, name);
        syncManager.syncValue("wireless_hatch_current_row", row, current);

        InteractionSyncHandler select = new InteractionSyncHandler()
            .setOnMousePressed(
                mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.select(row); })
            .setOnMouseScroll(mouseDelta -> { if (!mouseDelta.isClient()) session.scroll(mouseDelta.mouseButton); });
        return new ButtonWidget<>().syncHandler(select)
            .setEnabledIf(
                widget -> !name.getStringValue()
                    .isEmpty())
            .child(
                new TextWidget<>(IKey.dynamic(() -> formatRow(name.getStringValue(), current.getBoolValue())))
                    .maxWidth(132)
                    .center())
            .addTooltipLine(StatCollector.translateToLocal("tenkeitech.gui.wireless_hatch.select"));
    }

    private static ButtonWidget<?> createDisconnectButton(BrowserSession session) {
        InteractionSyncHandler disconnect = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.disconnect(); });
        return new ButtonWidget<>().syncHandler(disconnect)
            .overlay(
                IKey.lang("tenkeitech.gui.wireless_hatch.disconnect")
                    .style(IKey.RED));
    }

    private static String formatRow(String name, boolean current) {
        if (name == null || name.isEmpty()) return "";
        return current ? name + "  " + EnumChatFormatting.GREEN + "\u2714" + EnumChatFormatting.RESET : name;
    }

    private static IDrawable createWirelessIcon() {
        ItemStack stack = ModMetaTileEntities.wirelessNetworkCore;
        return stack == null ? IKey.str("W") : new ItemDrawable(stack);
    }

    private static boolean togglePanel(IPanelHandler targetPanel, IPanelHandler... otherPanels) {
        if (targetPanel == null) return false;
        if (targetPanel.isPanelOpen()) {
            targetPanel.closePanel();
        } else {
            if (otherPanels != null) {
                for (IPanelHandler otherPanel : otherPanels) {
                    if (otherPanel != null && otherPanel.isPanelOpen()) otherPanel.closePanel();
                }
            }
            targetPanel.openPanel();
        }
        return true;
    }

    private static final class BrowserSession {

        private final MTEHatch hatch;
        private final boolean client;
        private final EntityPlayer openingPlayer;
        private final UUID playerId;
        private List<NetworkRecord> networks = Collections.emptyList();
        private MembershipRecord currentMembership;
        private NetworkRecord currentNetwork;
        private int offset;
        private int refreshTicks;
        private double clientScrollPosition;

        private BrowserSession(MTEHatch hatch, boolean client, EntityPlayer openingPlayer) {
            this.hatch = hatch;
            this.client = client;
            this.openingPlayer = openingPlayer;
            this.playerId = openingPlayer == null ? null : openingPlayer.getUniqueID();
            refresh();
            if (!client) scrollToCurrent();
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
            if (client) return;
            refresh();
            NetworkRecord selected = row(row);
            if (selected == null) return;
            if (currentMembership != null) {
                if (!currentMembership.frequency()
                    .equals(selected.frequency())) {
                    send("tenkeitech.wireless.disconnect_first");
                }
                return;
            }

            World world = world();
            WirelessAeWorldData data = data();
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (world == null || data == null || base == null) return;
            NetworkRecord verified = data.getAccessibleNetwork(playerId, selected.frequency());
            if (verified == null) {
                send("tenkeitech.wireless.access_denied");
                refresh();
                return;
            }
            if (verified.dimensionId() != world.provider.dimensionId && !Config.enableWirelessCrossDimension) {
                send("tenkeitech.wireless.cross_dimension_disabled");
                return;
            }

            WirelessAeTargetLocation target = WirelessAeTargetResolver
                .resolveLoadedGtMeHatch(world, base.getXCoord(), base.getYCoord(), base.getZCoord());
            if (target == null) {
                send("tenkeitech.wireless.invalid_target");
                return;
            }
            MTEWirelessNetworkCore core = WirelessAeRuntimeRegistry.findLoadedCore(world, verified);
            if (core == null) {
                send("tenkeitech.wireless.core_offline");
                return;
            }

            BindResult result = core.bindTarget(openingPlayer, target);
            send(WirelessAeEventHandler.getTranslationKey(result));
            refresh();
        }

        private void disconnect() {
            if (client) return;
            refresh();
            World world = world();
            if (world == null || currentMembership == null || currentNetwork == null) {
                send("tenkeitech.wireless.current_unavailable");
                return;
            }
            MTEWirelessNetworkCore core = WirelessAeRuntimeRegistry.findLoadedCore(world, currentNetwork);
            if (core == null) {
                send("tenkeitech.wireless.core_offline");
                return;
            }

            UnbindResult result = core.unbindTarget(openingPlayer, currentMembership.target());
            send(switch (result) {
                case UNBOUND -> "tenkeitech.wireless.unbind_success";
                case NOT_BOUND -> "tenkeitech.wireless.not_bound";
                case ACCESS_DENIED -> "tenkeitech.wireless.access_denied";
                case CORE_OFFLINE -> "tenkeitech.wireless.core_offline";
            });
            refresh();
        }

        private void scrollToCurrent() {
            if (currentMembership == null) return;
            for (int index = 0; index < networks.size(); index++) {
                if (!currentMembership.frequency()
                    .equals(
                        networks.get(index)
                            .frequency())) {
                    continue;
                }
                offset = clampOffset(index - VISIBLE_NETWORKS / 2);
                return;
            }
        }

        private String currentName() {
            if (currentMembership == null) return "-";
            return currentNetwork == null ? StatCollector.translateToLocal("tenkeitech.gui.wireless_hatch.foreign")
                : displayNetworkName(currentNetwork);
        }

        private boolean canDisconnect() {
            World world = world();
            return currentMembership != null && currentNetwork != null
                && world != null
                && WirelessAeRuntimeRegistry.findLoadedCore(world, currentNetwork) != null;
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

        private boolean rowIsCurrent(int row) {
            NetworkRecord network = row(row);
            return network != null && currentMembership != null
                && currentMembership.frequency()
                    .equals(network.frequency());
        }

        @Nullable
        private NetworkRecord row(int row) {
            int index = offset + row;
            return row < 0 || row >= VISIBLE_NETWORKS || index < 0 || index >= networks.size() ? null
                : networks.get(index);
        }

        private double scrollPosition() {
            if (client) return clientScrollPosition;
            int maxOffset = maxOffset();
            return maxOffset == 0 ? 0 : (double) offset / maxOffset;
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

        private boolean hasOverflow() {
            return networks.size() > VISIBLE_NETWORKS;
        }

        private int maxOffset() {
            return Math.max(0, networks.size() - VISIBLE_NETWORKS);
        }

        private int clampOffset(int requestedOffset) {
            return Math.max(0, Math.min(requestedOffset, maxOffset()));
        }

        private void refresh() {
            WirelessAeWorldData data = data();
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (data == null || base == null) {
                networks = Collections.emptyList();
                currentMembership = null;
                currentNetwork = null;
                offset = 0;
                return;
            }
            networks = data.listAccessibleNetworks(playerId);
            currentMembership = data.getMembershipAtBlock(
                base.getWorld().provider.dimensionId,
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord());
            currentNetwork = currentMembership == null ? null
                : data.getAccessibleNetwork(playerId, currentMembership.frequency());
            offset = clampOffset(offset);
        }

        private void send(String translationKey) {
            WirelessAeEventHandler.send(openingPlayer, translationKey);
        }

        @Nullable
        private WirelessAeWorldData data() {
            World world = world();
            return client || playerId == null || world == null ? null : WirelessAeWorldData.get(world);
        }

        @Nullable
        private World world() {
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            return base == null ? null : base.getWorld();
        }
    }
}
