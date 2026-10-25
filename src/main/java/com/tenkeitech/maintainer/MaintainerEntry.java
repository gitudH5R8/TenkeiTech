/*
 * SPDX-FileCopyrightText: 2026 Aedial
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 * Adapted from AE2-PowerTools, be56992b0ab8e64cf254f603dad22cca2931aa6b.
 */
package com.tenkeitech.maintainer;

import java.util.concurrent.Future;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** Configuration, an optional native requester link, and transient display/operation state. */
public final class MaintainerEntry {

    public enum State {

        DISABLED(0x40303040, 0x808080),
        IDLE(0, 0x707070),
        NETWORK(0x40FFFF00, 0x909000),
        SCHEDULED(0x4080C0FF, 0x50A0D0),
        CALCULATING(0x4080C0FF, 0x50A0D0),
        CPU(0x40FFFF00, 0x909000),
        RUNNING(0x4040FF40, 0x30B030),
        ERROR(0x40FF4040, 0xD03030),
        OUTPUT(0x40C040FF, 0xA030D0);

        public final int background;
        public final int color;

        State(int background, int color) {
            this.background = background;
            this.color = color;
        }
    }

    public long id;
    public int revision;
    public IAEStack<?> material;
    public long target = 64;
    public long batch = 1;
    public int seconds = 60;
    public boolean enabled = true;
    public long current;
    public long nextCheck;
    long stockChecked;
    public State state = State.IDLE;
    public String error = "";
    ICraftingLink link;
    Future<ICraftingJob> future;
    ICraftingJob plan;
    IGrid calculationGrid;
    long calculationStarted;
    long retryAt;
    boolean terminal;
    boolean uncertain;
    NBTTagCompound uncertainRequest;

    public boolean busy() {
        return link != null || future != null || plan != null;
    }

    public static boolean supported(IAEStack<?> material) {
        return material instanceof IAEItemStack || material instanceof IAEFluidStack;
    }

    public NBTTagCompound settings() {
        NBTTagCompound tag = new NBTTagCompound();
        if (material != null) tag.setTag("material", material.toNBTGeneric());
        tag.setLong("target", target);
        tag.setLong("batch", batch);
        tag.setInteger("seconds", seconds);
        tag.setBoolean("enabled", enabled);
        if (uncertain) {
            tag.setBoolean("uncertain", true);
            if (uncertainRequest != null) tag.setTag("uncertainRequest", uncertainRequest.copy());
        }
        return tag;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = settings();
        tag.setLong("id", id);
        tag.setInteger("revision", revision);
        tag.setLong("nextCheck", nextCheck);
        if (link != null) {
            NBTTagCompound savedLink = new NBTTagCompound();
            link.writeToNBT(savedLink);
            tag.setTag("link", savedLink);
        }
        return tag;
    }

    public NBTTagCompound snapshot(long now) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("id", id);
        tag.setInteger("revision", revision);
        if (material != null) tag.setTag("material", material.toNBTGeneric());
        tag.setLong("target", target);
        tag.setLong("batch", batch);
        tag.setInteger("seconds", seconds);
        tag.setBoolean("enabled", enabled);
        tag.setLong("current", current);
        tag.setLong("remaining", Math.max(0, nextCheck - now));
        tag.setInteger("state", state.ordinal());
        tag.setString("error", error);
        tag.setBoolean("busy", busy());
        return tag;
    }
}
