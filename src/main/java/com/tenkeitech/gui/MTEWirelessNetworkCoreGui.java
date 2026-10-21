/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.tenkeitech.machine.MTEWirelessNetworkCore;
import com.tenkeitech.machine.MTEWirelessNetworkCore.DeviceStatistics;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/** Owner-validated network naming and read-only runtime status for a wireless core. */
public final class MTEWirelessNetworkCoreGui extends MTEHatchBaseGui<MTEWirelessNetworkCore> {

    private final EntityPlayer openingPlayer;

    public MTEWirelessNetworkCoreGui(MTEWirelessNetworkCore core, EntityPlayer openingPlayer) {
        super(core);
        this.openingPlayer = openingPlayer;
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + 45;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        NameSession session = new NameSession(machine, openingPlayer);
        DeviceStatisticsSession statistics = new DeviceStatisticsSession(machine, syncManager.isClient());
        syncManager.onServerTick(statistics::tick);
        StringSyncValue networkName = new StringSyncValue(session::draftName, session::setDraftName).allowC2S();
        StringSyncValue ownerGroup = new StringSyncValue(machine::getOwnerGroupDisplayName);
        BooleanSyncValue isOwner = new BooleanSyncValue(() -> machine.isOwner(openingPlayer));
        BooleanSyncValue connected = new BooleanSyncValue(machine::isConnectedToMeNetwork);
        IntSyncValue totalDevices = new IntSyncValue(statistics::totalDevices);
        IntSyncValue wirelessBindings = new IntSyncValue(statistics::wirelessBindings);
        IntSyncValue carriedDevices = new IntSyncValue(statistics::carriedDevices);
        syncManager.syncValue("wireless_core_owner_group", 0, ownerGroup);
        syncManager.syncValue("wireless_core_is_owner", 0, isOwner);
        syncManager.syncValue("wireless_core_connected", 0, connected);
        syncManager.syncValue("wireless_core_total_devices", 0, totalDevices);
        syncManager.syncValue("wireless_core_wireless_bindings", 0, wirelessBindings);
        syncManager.syncValue("wireless_core_carried_devices", 0, carriedDevices);

        InteractionSyncHandler save = new InteractionSyncHandler().setOnMousePressed(
            mouseDelta -> { if (!mouseDelta.isClient() && mouseDelta.mouseButton == 0) session.save(); });

        return getEmptyContent().child(new TextWidget<>(IKey.lang("tenkeitech.gui.wireless_core.title")).pos(2, 0))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            connected.getBoolValue() ? "tenkeitech.gui.wireless_core.connected"
                                : "tenkeitech.gui.wireless_core.disconnected")))
                                    .color(() -> connected.getBoolValue() ? 0x167A8B : 0x666666)
                                    .pos(2, 14)
                                    .maxWidth(156))
            .child(new TextWidget<>(IKey.lang("tenkeitech.gui.wireless_core.network_name")).pos(2, 30))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "tenkeitech.gui.wireless_core.owner",
                            ownerGroup.getStringValue()))).textAlign(Alignment.TopRight)
                                .pos(52, 30)
                                .size(106, 10)
                                .maxWidth(106))
            .child(
                new TextFieldWidget().setMaxLength(MTEWirelessNetworkCore.MAX_NETWORK_NAME_LENGTH)
                    .setValidator(MTEWirelessNetworkCore::sanitizeNetworkNameInput)
                    .autoUpdateOnChange(true)
                    .value(networkName)
                    .setEnabledIf(widget -> isOwner.getBoolValue())
                    .pos(2, 42)
                    .size(156, 18))
            .child(
                new ButtonWidget<>().syncHandler(save)
                    .overlay(IKey.lang("tenkeitech.gui.wireless_core.save"))
                    .setEnabledIf(widget -> isOwner.getBoolValue())
                    .pos(14, 66)
                    .size(132, 18))
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.wireless_core.total_devices",
                        () -> new Object[] { totalDevices.getIntValue() })).pos(2, 92)
                            .maxWidth(156))
            .child(
                new TextWidget<>(
                    IKey.lang(
                        "tenkeitech.gui.wireless_core.device_details",
                        () -> new Object[] { wirelessBindings.getIntValue(), carriedDevices.getIntValue() }))
                            .pos(2, 105)
                            .maxWidth(156));
    }

    private static final class NameSession {

        private final MTEWirelessNetworkCore machine;
        private final EntityPlayer player;
        private String draftName;

        private NameSession(MTEWirelessNetworkCore machine, EntityPlayer player) {
            this.machine = machine;
            this.player = player;
            this.draftName = machine.getNetworkName();
        }

        private String draftName() {
            return draftName;
        }

        private void setDraftName(String name) {
            draftName = MTEWirelessNetworkCore.sanitizeNetworkNameInput(name);
        }

        private void save() {
            machine.renameNetwork(player, draftName);
            draftName = machine.getNetworkName();
        }
    }

    private static final class DeviceStatisticsSession {

        private final MTEWirelessNetworkCore machine;
        private final boolean client;
        private DeviceStatistics statistics = new DeviceStatistics(0, 0, 0);
        private int refreshTicks;

        private DeviceStatisticsSession(MTEWirelessNetworkCore machine, boolean client) {
            this.machine = machine;
            this.client = client;
            if (!client) refresh();
        }

        private void tick() {
            if (client || ++refreshTicks < 10) return;
            refreshTicks = 0;
            refresh();
        }

        private void refresh() {
            statistics = machine.getDeviceStatistics();
        }

        private int totalDevices() {
            return statistics.totalDevices();
        }

        private int wirelessBindings() {
            return statistics.wirelessBindings();
        }

        private int carriedDevices() {
            return statistics.carriedDevices();
        }
    }
}
