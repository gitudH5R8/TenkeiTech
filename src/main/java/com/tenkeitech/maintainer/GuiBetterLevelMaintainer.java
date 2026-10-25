/*
 * SPDX-FileCopyrightText: 2026 Aedial
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: MIT
 * Layout adapted from AE2-PowerTools, be56992b0ab8e64cf254f603dad22cca2931aa6b.
 */
package com.tenkeitech.maintainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Original three-column and tall layouts, backed by server snapshots and a local edit draft. */
@SideOnly(Side.CLIENT)
public final class GuiBetterLevelMaintainer extends GuiContainer {

    private static final ResourceLocation SMALL = texture("maintainer_gui.png"),
        TALL = texture("maintainer_gui_tall.png"), MODAL = texture("maintainer_modal.png"),
        SELECTOR = texture("recipe_selector.png");
    private static final ResourceLocation BUTTONS = new ResourceLocation("minecraft", "textures/gui/widgets.png"),
        SCROLLBAR = new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");
    private static final int SELECTOR_WIDTH = 195, SELECTOR_HEIGHT = 186, SELECTOR_COLUMNS = 9, SELECTOR_ROWS = 9;
    private static final long FEEDBACK_DURATION_MS = 4000;
    private static final int[] INTERVAL_DELTAS = { -1, 1, -60, 60, -3600, 3600, -86400, 86400 };
    private static final String[] INTERVAL_LABELS = { "1s", "1s", "1m", "1m", "1h", "1h", "1d", "1d" };
    private static final Pattern INTERVAL_PART = Pattern.compile("(\\d+)([smhd])");
    private static final Pattern AMOUNT = Pattern.compile("([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([kKmMgGbBtTsSiI]?)");
    private final ContainerBetterLevelMaintainer container;
    private final NBTTagCompound[] entries = new NBTTagCompound[TileBetterLevelMaintainer.CAPACITY];
    private final IAEStack<?>[] displayMaterials = new IAEStack<?>[TileBetterLevelMaintainer.CAPACITY];
    private final long[] snapshotTicks = new long[TileBetterLevelMaintainer.CAPACITY],
        toggleStarted = new long[TileBetterLevelMaintainer.CAPACITY],
        toggleIds = new long[TileBetterLevelMaintainer.CAPACITY];
    private final int[] toggleRequests = new int[TileBetterLevelMaintainer.CAPACITY];
    private final Boolean[] desiredEnabled = new Boolean[TileBetterLevelMaintainer.CAPACITY];
    private final Set<Integer> pendingToggles = new HashSet<>();
    private final List<Integer> filteredCatalog = new ArrayList<>();
    private final IAEStack<?>[] catalog = new IAEStack<?>[4096];
    private final String[] catalogNames = new String[4096], catalogSearchNames = new String[4096];
    private GuiTextField search, target, batch, seconds, selectorSearch;
    private int slots = 18, rows = 6, scroll, selectorScroll, catalogVersion, selected = -1, editSlot = -1, helloTicks;
    private int running, enabled, errors, blocked, busyCpus, totalCpus;
    private int requestSequence, waitingRequest, materialRequest;
    private int selectorDragOffset;
    private boolean active, dragging, selecting, waiting;
    private boolean catalogLoading, selectorDragging, catalogFilterDirty = true;
    private String catalogFilter = "";
    private NBTTagCompound draft;
    private IAEStack<?> draftMaterial;
    private String feedback = "";
    private long feedbackUntil, waitingSince, materialErrorUntil, clientTicks;

    public GuiBetterLevelMaintainer(ContainerBetterLevelMaintainer container) {
        super(container);
        this.container = container;
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("tenkeitech", "textures/guis/" + name);
    }

    private static String tr(String key, Object... args) {
        return I18n.format("tenkeitech.maintainer." + key, args);
    }

    @Override
    public void initGui() {
        String query = search == null ? "" : search.getText();
        String targetText = target == null ? null : target.getText();
        String batchText = batch == null ? null : batch.getText();
        String secondsText = seconds == null ? null : seconds.getText();
        String selectorQuery = selectorSearch == null ? "" : selectorSearch.getText();
        xSize = 238;
        rows = MaintainerClient.tall ? Math.max(3, (height - 20 - 69) / 23 + 1) : 6;
        ySize = MaintainerClient.tall ? 69 + (rows - 1) * 23 : 206;
        super.initGui();
        search = field(guiLeft + 130, guiTop + 4, 80, 12, query, 50);
        if (draft != null) {
            initEditorFields();
            if (targetText != null) target.setText(targetText);
            if (batchText != null) batch.setText(batchText);
            if (secondsText != null) seconds.setText(secondsText);
            if (selecting) initSelectorSearch(selectorQuery);
        }
        Keyboard.enableRepeatEvents(true);
    }

    private GuiTextField field(int x, int y, int w, int h, String text, int max) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, w, h);
        field.setMaxStringLength(max);
        field.setText(text);
        field.setTextColor(0xFFFFFF);
        return field;
    }

    public void receive(MaintainerNetwork.Data data) {
        if (data.window != container.windowId || container.nonce != 0 && data.nonce != container.nonce) return;
        container.nonce = data.nonce;
        NBTTagCompound tag = data.tag;
        if (tag.getInteger("kind") == 2) {
            int slot = tag.getInteger("slot");
            applySnapshot(slot, tag.getCompoundTag("entry"));
            if (tag.getInteger("op") == MaintainerNetwork.TOGGLE && slot >= 0
                && slot < entries.length
                && toggleRequests[slot] != 0
                && toggleRequests[slot] == tag.getInteger("request")) {
                toggleRequests[slot] = 0;
                pendingToggles.remove(slot);
                if (tag.getBoolean("ok") && entries[slot] != null && entries[slot].getLong("id") == toggleIds[slot])
                    sendToggle(slot);
                else desiredEnabled[slot] = null;
            }
            boolean savedDraft = waiting && tag.getInteger("op") == MaintainerNetwork.EDIT
                && tag.getInteger("request") == waitingRequest;
            if (savedDraft) waiting = false;
            if (tag.getBoolean("ok")) {
                if (savedDraft) {
                    draft = null;
                    selecting = false;
                }
                feedback = "saved";
            } else feedback = tag.getBoolean("entryLimit") ? "entry_limit" : "rejected";
            feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
        } else if (tag.getInteger("kind") == 4) {
            if (draft == null || waiting || selected != -3 || materialRequest != tag.getInteger("request")) return;
            if (!tag.getBoolean("valid")) {
                feedback = "rejected";
                feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
            } else if (tag.getBoolean("known") && !tag.getBoolean("hasPattern")) {
                draftMaterial = null;
                selected = -2;
                selecting = false;
                selectorDragging = false;
                materialErrorUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
                feedback = "missing_pattern";
                feedbackUntil = materialErrorUntil;
            }
        } else if (tag.getInteger("kind") == 3) {
            catalogVersion = tag.getInteger("version");
            catalogLoading = true;
            Arrays.fill(catalog, null);
            Arrays.fill(catalogNames, null);
            Arrays.fill(catalogSearchNames, null);
            catalogFilterDirty = true;
        } else if (tag.getInteger("kind") == 1) {
            if (catalogVersion != tag.getInteger("version")) return;
            NBTTagList materials = tag.getTagList("materials", 10);
            for (int i = 0; i < Math.min(128, materials.tagCount()); i++) {
                NBTTagCompound entry = materials.getCompoundTagAt(i);
                int index = entry.getInteger("index");
                if (index >= 0 && index < catalog.length) {
                    catalog[index] = entry.hasKey("material") ? material(entry) : null;
                    catalogNames[index] = name(catalog[index]);
                    catalogSearchNames[index] = catalogNames[index].toLowerCase(Locale.ROOT);
                }
            }
            catalogLoading = !tag.getBoolean("complete");
            if (materials.tagCount() != 0) catalogFilterDirty = true;
        } else {
            slots = Math.max(1, Math.min(TileBetterLevelMaintainer.CAPACITY, tag.getInteger("slots")));
            active = tag.getBoolean("active");
            NBTTagList list = tag.getTagList("entries", 10);
            int start = tag.getInteger("start");
            for (int i = 0; i < Math.min(16, list.tagCount()); i++)
                if (start + i >= 0 && start + i < TileBetterLevelMaintainer.CAPACITY) {
                    applySnapshot(start + i, list.getCompoundTagAt(i));
                }
            Arrays.fill(entries, slots, entries.length, null);
            Arrays.fill(displayMaterials, slots, displayMaterials.length, null);
            enabled = tag.getInteger("enabled");
            running = tag.getInteger("running");
            errors = tag.getInteger("errors");
            blocked = tag.getInteger("blocked");
            busyCpus = tag.getInteger("busyCpus");
            totalCpus = tag.getInteger("totalCpus");
        }
    }

    private void applySnapshot(int slot, NBTTagCompound row) {
        if (slot < 0 || slot >= entries.length) return;
        NBTTagCompound old = entries[slot];
        if (old != null && row.getLong("id") == old.getLong("id")
            && row.getInteger("revision") < old.getInteger("revision")) return;
        entries[slot] = row.getLong("id") == 0 ? null : row;
        displayMaterials[slot] = material(entries[slot]);
        snapshotTicks[slot] = clientTicks;
    }

    private void toggle(int slot) {
        if (desiredEnabled[slot] == null) toggleIds[slot] = entries[slot].getLong("id");
        desiredEnabled[slot] = !(desiredEnabled[slot] == null ? entries[slot].getBoolean("enabled")
            : desiredEnabled[slot]);
        if (toggleRequests[slot] == 0) sendToggle(slot);
    }

    private void sendToggle(int slot) {
        NBTTagCompound row = entries[slot];
        if (desiredEnabled[slot] == null || row == null
            || row.getLong("id") != toggleIds[slot]
            || desiredEnabled[slot] == row.getBoolean("enabled")) {
            desiredEnabled[slot] = null;
            return;
        }
        MaintainerNetwork.Action action = action(MaintainerNetwork.TOGGLE, slot);
        action.enabled = desiredEnabled[slot];
        action.request = ++requestSequence;
        toggleRequests[slot] = action.request;
        pendingToggles.add(slot);
        toggleStarted[slot] = System.currentTimeMillis();
        MaintainerNetwork.send(action);
    }

    private MaintainerEntry.State displayState(NBTTagCompound row) {
        if (!row.getBoolean("enabled")) return MaintainerEntry.State.DISABLED;
        if (!active && !row.getBoolean("busy")) return MaintainerEntry.State.NETWORK;
        int ordinal = Math.max(0, Math.min(row.getInteger("state"), MaintainerEntry.State.values().length - 1));
        return MaintainerEntry.State.values()[ordinal];
    }

    private String stateText(NBTTagCompound row, MaintainerEntry.State state) {
        return state == MaintainerEntry.State.ERROR && "no_recipe".equals(row.getString("error"))
            ? tr("missing_pattern")
            : tr(
                "state." + state.name()
                    .toLowerCase(Locale.ROOT));
    }

    private long remainingSeconds(int slot) {
        NBTTagCompound row = entries[slot];
        if (!active || !row.getBoolean("enabled") || row.getBoolean("busy")) return 0;
        long remaining = Math.max(0, row.getLong("remaining") - Math.max(0, clientTicks - snapshotTicks[slot]));
        return remaining / 20 + (remaining % 20 == 0 ? 0 : 1);
    }

    private List<Integer> visibleEntries() {
        List<Integer> result = new ArrayList<>();
        String query = search.getText()
            .toLowerCase(Locale.ROOT);
        for (int i = 0; i < slots; i++) {
            NBTTagCompound entry = entries[i];
            if (query.isEmpty() || entry != null && name(displayMaterials[i]).toLowerCase(Locale.ROOT)
                .contains(query)) result.add(i);
        }
        return result;
    }

    private int columns() {
        return MaintainerClient.tall ? 1 : 3;
    }

    private int cellWidth() {
        return MaintainerClient.tall ? 204 : 68;
    }

    private int maxScroll(List<Integer> entries) {
        return Math.max(0, (entries.size() + columns() - 1) / columns() - rows);
    }

    private int statusY() {
        return MaintainerClient.tall ? guiTop + ySize - 18 : guiTop + 163;
    }

    private static IAEStack<?> material(NBTTagCompound entry) {
        return entry == null ? null : IAEStack.fromNBTGeneric(entry.getCompoundTag("material"));
    }

    private static String name(IAEStack<?> stack) {
        return stack == null ? "" : stack.getDisplayName();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(MaintainerClient.tall ? TALL : SMALL);
        if (MaintainerClient.tall) {
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, 238, 19);
            for (int r = 0; r < rows - 1; r++) drawTexturedModalRect(guiLeft, guiTop + 19 + r * 23, 0, 19, 238, 23);
            drawTexturedModalRect(guiLeft, guiTop + 19 + (rows - 1) * 23, 0, 42, 238, 50);
        } else drawTexturedModalRect(guiLeft, guiTop, 0, 0, 238, 206);
        search.drawTextBox();
        button(guiLeft - 18, guiTop + 4, 16, 16, "");
        for (int r = 0; r < 4; r++) for (int c = 0; c < (MaintainerClient.tall ? 1 : 3); c++)
            drawRect(guiLeft - 14 + c * 3, guiTop + 7 + r * 2, guiLeft - 12 + c * 3, guiTop + 8 + r * 2, 0xFF404040);
        List<Integer> visible = visibleEntries();
        scroll = Math.max(0, Math.min(scroll, maxScroll(visible)));
        for (int n = 0; n < rows * columns(); n++) {
            int index = scroll * columns() + n;
            if (index >= visible.size()) break;
            int slot = visible.get(index), x = guiLeft + 9 + n % columns() * cellWidth(),
                y = guiTop + 18 + n / columns() * 23;
            drawEntry(entries[slot], displayMaterials[slot], x, y);
        }
        int track = MaintainerClient.tall ? rows * 23 - 2 : 136;
        int thumbY = guiTop + 19 + (maxScroll(visible) == 0 ? 0 : scroll * (track - 15) / maxScroll(visible));
        button(guiLeft + 218, thumbY, 12, 15, "");
        for (int y = 3; y < 12; y += 2) drawRect(guiLeft + 220, thumbY + y, guiLeft + 228, thumbY + y + 1, 0xFF777777);
        centered("§a" + busyCpus + "§0 / " + totalCpus, guiLeft + 58, statusY());
        centered("§a" + running + "§0 / " + enabled + " / §c" + errors + "§0 / §5" + blocked, guiLeft + 165, statusY());
    }

    private void drawEntry(NBTTagCompound entry, IAEStack<?> stack, int x, int y) {
        if (entry == null) {
            if (MaintainerClient.tall) fontRendererObj.drawString(tr("empty"), x + 3, y + 9, 0x808080);
            return;
        }
        MaintainerEntry.State state = displayState(entry);
        if (state.background != 0) drawRect(x, y + 1, x + cellWidth() - 1, y + 23, state.background);
        icon(stack, x + 1, y + 2);
        int right = x + cellWidth() - 3;
        String quantity = shortNumber(entry.getLong("current")) + "/" + shortNumber(entry.getLong("target"));
        right(quantity, right, y + 3, 0x000000);
        int frequencyY = y + (MaintainerClient.tall ? 12 : 14);
        right(time(entry.getInteger("seconds")), right - 10, frequencyY, 0x404040);
        button(right - 8, frequencyY, 8, 8, "");
        drawRect(right - 5, frequencyY + 2, right - 4, frequencyY + 6, 0xFF303030);
        drawRect(right - 4, frequencyY + 3, right - 3, frequencyY + 5, 0xFF303030);
        if (MaintainerClient.tall) {
            int max = Math.max(20, cellWidth() - 28 - fontRendererObj.getStringWidth(quantity));
            fontRendererObj
                .drawString(trim(name(stack), max), x + 22, y + 3, entry.getBoolean("enabled") ? 0 : 0x808080);
            fontRendererObj.drawString(trim(stateText(entry, state), 125), x + 22, y + 12, state.color);
        }
    }

    private void icon(IAEStack<?> stack, int x, int y) {
        if (stack == null) return;
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        stack.drawInGui(mc, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(tr("title"), 8, 6, 0);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (draft != null) {
            if (selecting) drawSelector(mouseX, mouseY);
            else drawEditor(mouseX, mouseY);
        } else {
            List<String> tooltip = new ArrayList<>();
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0 && entries[slot] != null) {
                NBTTagCompound row = entries[slot];
                IAEStack<?> stack = displayMaterials[slot];
                tooltip.add(name(stack));
                tooltip.add(
                    tr("stock", row.getLong("current"), row.getLong("target"))
                        + (stack instanceof IAEFluidStack ? " mB" : ""));
                tooltip.add(tr("batch_value", row.getLong("batch")));
                tooltip.add(tr("interval_value", row.getInteger("seconds")));
                tooltip.add(tr("next_execution", remainingSeconds(slot)));
                tooltip.add(stateText(row, displayState(row)));
                if (!row.getString("error")
                    .isEmpty()) tooltip.add("§c" + tr("error." + row.getString("error")));
                tooltip.add(tr("controls"));
                tooltip.add(tr("wheel"));
            } else if (inside(mouseX, mouseY, guiLeft - 18, guiTop + 4, 16, 16)) tooltip.add(tr("layout"));
            else if (inside(mouseX, mouseY, guiLeft + 8, statusY() - 3, 100, 16)) tooltip.add(tr("cpus"));
            else if (inside(mouseX, mouseY, guiLeft + 112, statusY() - 3, 100, 16)) tooltip.add(tr("counts"));
            if (!tooltip.isEmpty()) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
        drawFeedback();
        if (Loader.isModLoaded("NotEnoughItems") && covers(mouseX - 8, mouseY - 8, 16, 16))
            MaintainerNEI.drawGhost(mouseX, mouseY);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void drawFeedback() {
        if (System.currentTimeMillis() >= feedbackUntil) return;
        int panelWidth = draft == null ? xSize : selecting ? SELECTOR_WIDTH : 176;
        int panelTop = draft == null ? guiTop : selecting ? selectorTop() : (height - 107) / 2;
        int panelBottom = draft == null ? guiTop + ySize
            : selecting ? selectorTop() + SELECTOR_HEIGHT : (height - 107) / 2 + 127;
        int boxWidth = Math.min(panelWidth, width - 8);
        List<String> lines = fontRendererObj.listFormattedStringToWidth(tr(feedback), boxWidth - 8);
        int boxHeight = lines.size() * fontRendererObj.FONT_HEIGHT + 8;
        int x = Math.max(4, Math.min((width - panelWidth) / 2, width - boxWidth - 4));
        int y = panelTop - boxHeight - 4;
        if (y < 4) y = Math.max(4, Math.min(panelBottom + 4, height - boxHeight - 4));
        boolean success = "saved".equals(feedback) || "ghost_selected".equals(feedback);
        drawRect(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 0xFF707070);
        drawRect(x, y, x + boxWidth, y + boxHeight, 0xEE101010);
        for (int i = 0; i < lines.size(); i++) fontRendererObj.drawStringWithShadow(
            lines.get(i),
            x + 4,
            y + 4 + i * fontRendererObj.FONT_HEIGHT,
            success ? 0x80FF80 : 0xFF8080);
    }

    private int slotAt(int mouseX, int mouseY) {
        int x = mouseX - guiLeft - 9, y = mouseY - guiTop - 18;
        if (x < 0 || x >= 204 || y < 0 || y >= rows * 23) return -1;
        List<Integer> visible = visibleEntries();
        int position = (scroll + y / 23) * columns() + x / cellWidth();
        return position < visible.size() ? visible.get(position) : -1;
    }

    private MaintainerNetwork.Action action(int op, int slot) {
        MaintainerNetwork.Action action = new MaintainerNetwork.Action(container, op);
        action.slot = slot;
        NBTTagCompound row = entries[slot];
        if (row != null) {
            action.id = row.getLong("id");
            action.revision = row.getInteger("revision");
        }
        return action;
    }

    @Override
    protected void mouseClicked(int x, int y, int mouseButton) {
        if (!covers(x, y, 1, 1)) clearOwnFocus();
        // GuiContainer's overridden methods bypass NEI's transformed base implementation.
        if (Loader.isModLoaded("NotEnoughItems") && MaintainerNEI.mouseClicked(this, x, y, mouseButton)) return;
        if (draft != null) {
            if (!waiting) {
                if (selecting) clickSelector(x, y, mouseButton);
                else clickEditor(x, y, mouseButton);
            }
            return;
        }
        search.mouseClicked(x, y, mouseButton);
        if (mouseButton == 0 && inside(x, y, guiLeft - 18, guiTop + 4, 16, 16)) {
            MaintainerClient.layout(!MaintainerClient.tall);
            scroll = 0;
            initGui();
            return;
        }
        if (mouseButton == 0 && inside(x, y, guiLeft + 218, guiTop + 19, 12, rows * 23 - 2)) {
            dragging = true;
            drag(y);
            return;
        }
        int slot = slotAt(x, y);
        if (slot < 0 || container.nonce == 0) return;
        if (mouseButton == 1 && entries[slot] != null) {
            toggle(slot);
            return;
        }
        if (mouseButton != 0) return;
        int localX = (x - guiLeft - 9) % cellWidth(), localY = (y - guiTop - 18) % 23;
        if (entries[slot] != null && localX >= cellWidth() - 11 && localY >= 12) {
            MaintainerNetwork.send(action(MaintainerNetwork.RUN, slot));
            return;
        }
        editSlot = slot;
        draft = entries[slot] == null ? new NBTTagCompound() : (NBTTagCompound) entries[slot].copy();
        if (entries[slot] == null) {
            draft.setLong("target", 0);
            draft.setLong("batch", 1);
            draft.setInteger("seconds", 60);
            draft.setBoolean("enabled", true);
        }
        draftMaterial = material(draft);
        selected = -1;
        waiting = false;
        feedbackUntil = 0;
        materialErrorUntil = 0;
        initEditorFields();
        requestCatalog();
    }

    private void initEditorFields() {
        int x = (width - 176) / 2, y = (height - 107) / 2;
        target = field(x + 60, y + 38, 110, 12, Long.toString(draft.getLong("target")), 32);
        batch = field(x + 60, y + 64, 110, 12, Long.toString(draft.getLong("batch")), 32);
        seconds = field(x + 60, y + 90, 110, 12, time(draft.getInteger("seconds")), 24);
    }

    private void drawEditor(int mouseX, int mouseY) {
        int x = (width - 176) / 2, y = (height - 107) / 2;
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(MODAL);
        drawTexturedModalRect(x, y, 0, 0, 176, 107);
        target.drawTextBox();
        batch.drawTextBox();
        seconds.drawTextBox();
        fontRendererObj.drawString(tr("target"), x + 60, y + 28, 0x404040);
        fontRendererObj.drawString(tr("batch"), x + 60, y + 54, 0x404040);
        fontRendererObj.drawString(tr("interval"), x + 60, y + 80, 0x404040);
        boolean materialError = System.currentTimeMillis() < materialErrorUntil;
        icon(draftMaterial, x + 6, y + 6);
        if (materialError) drawRect(x + 6, y + 6, x + 22, y + 22, 0x40FF4040);
        fontRendererObj.drawString(
            trim(draftMaterial == null ? tr("select") : name(draftMaterial), 145),
            x + 25,
            y + 10,
            0x404040);
        for (int i = 0; i < 8; i++) smallButton(
            x + 3 + i % 2 * 28,
            y + 48 + i / 2 * 14,
            26,
            12,
            (INTERVAL_DELTAS[i] > 0 ? "+" : "-")
                + (isShiftKeyDown() ? "10" + INTERVAL_LABELS[i].substring(1) : INTERVAL_LABELS[i]),
            mouseX,
            mouseY,
            !waiting);
        smallButton(x, y + 111, 84, 16, tr(waiting ? "waiting" : "confirm"), mouseX, mouseY, !waiting);
        smallButton(x + 92, y + 111, 84, 16, tr("cancel"), mouseX, mouseY, !waiting);
        if (inside(mouseX, mouseY, x + 5, y + 5, 18, 18)) drawHoveringText(
            Arrays.asList(tr("select"), tr("drag_hint"), tr("clear")),
            mouseX,
            mouseY,
            fontRendererObj);
        else if (inside(mouseX, mouseY, x + 60, y + 38, 110, 12) || inside(mouseX, mouseY, x + 60, y + 64, 110, 12))
            drawHoveringText(Arrays.asList(tr("amount_units"), tr("amount_example")), mouseX, mouseY, fontRendererObj);
    }

    private void clickEditor(int mx, int my, int mouseButton) {
        int x = (width - 176) / 2, y = (height - 107) / 2;
        if (inside(mx, my, x + 5, y + 5, 18, 18)) {
            if (mouseButton == 1) {
                draftMaterial = null;
                selected = -2;
                materialErrorUntil = 0;
                feedbackUntil = 0;
            } else if (mouseButton == 0) {
                selecting = true;
                selectorScroll = 0;
                selectorDragging = false;
                initSelectorSearch("");
            }
            return;
        }
        target.mouseClicked(mx, my, mouseButton);
        batch.mouseClicked(mx, my, mouseButton);
        seconds.mouseClicked(mx, my, mouseButton);
        if (mouseButton != 0) return;
        if (inside(mx, my, x, y + 111, 84, 16)) {
            save();
            return;
        }
        if (inside(mx, my, x + 92, y + 111, 84, 16)) {
            draft = null;
            feedbackUntil = 0;
            return;
        }
        for (int i = 0; i < 8; i++) if (inside(mx, my, x + 3 + i % 2 * 28, y + 48 + i / 2 * 14, 26, 12)) {
            try {
                seconds.setText(
                    time(
                        Math.max(
                            1,
                            Math.min(
                                86400,
                                parseInterval(seconds.getText()) + INTERVAL_DELTAS[i] * (isShiftKeyDown() ? 10 : 1)))));
            } catch (NumberFormatException ignored) {}
            return;
        }
    }

    private void save() {
        try {
            long targetValue = parseAmount(target.getText()), batchValue = parseAmount(batch.getText());
            int secondsValue = parseInterval(seconds.getText());
            if (targetValue < 0 || batchValue < 1 || secondsValue < 1 || secondsValue > 86400)
                throw new NumberFormatException();
            MaintainerNetwork.Action action = new MaintainerNetwork.Action(container, MaintainerNetwork.EDIT);
            action.slot = editSlot;
            action.id = draft.getLong("id");
            action.revision = draft.getInteger("revision");
            action.selection = selected;
            action.request = ++requestSequence;
            waitingRequest = action.request;
            action.target = targetValue;
            action.batch = batchValue;
            action.seconds = secondsValue;
            action.enabled = draft.getBoolean("enabled");
            if (selected == -3) try {
                action.material = MaintainerNetwork.materialBytes(draftMaterial);
            } catch (IllegalArgumentException exception) {
                feedback = "material_too_large";
                feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
                return;
            }
            MaintainerNetwork.send(action);
            waiting = true;
            waitingSince = System.currentTimeMillis();
        } catch (NumberFormatException exception) {
            feedback = "invalid";
            feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
        }
    }

    private List<Integer> selectorEntries() {
        String query = selectorSearch.getText()
            .toLowerCase(Locale.ROOT);
        if (catalogFilterDirty || !query.equals(catalogFilter)) {
            filteredCatalog.clear();
            for (int i = 0; i < catalog.length; i++)
                if (catalog[i] != null && catalogSearchNames[i].contains(query)) filteredCatalog.add(i);
            filteredCatalog.sort(Comparator.comparing(index -> catalogNames[index], String.CASE_INSENSITIVE_ORDER));
            catalogFilter = query;
            catalogFilterDirty = false;
        }
        return filteredCatalog;
    }

    private void drawSelector(int mx, int my) {
        int x = selectorLeft(), y = selectorTop();
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(SELECTOR);
        drawTexturedModalRect(x, y, 0, 0, SELECTOR_WIDTH, SELECTOR_HEIGHT);
        fontRendererObj.drawString(tr("selector") + (catalogLoading ? "..." : ""), x + 8, y + 6, 0);
        selectorSearch.drawTextBox();
        List<Integer> list = selectorEntries();
        selectorScroll = Math.max(0, Math.min(selectorScroll, selectorMaxScroll(list)));
        for (int n = 0; n < SELECTOR_COLUMNS * SELECTOR_ROWS; n++) {
            int cx = x + 8 + n % SELECTOR_COLUMNS * 18, cy = y + 17 + n / SELECTOR_COLUMNS * 18;
            if (inside(mx, my, cx, cy, 18, 18)) drawRect(cx + 1, cy + 1, cx + 17, cy + 17, 0x80FFFFFF);
            int index = selectorScroll * SELECTOR_COLUMNS + n;
            if (index < list.size()) icon(catalog[list.get(index)], cx + 1, cy + 1);
        }
        int thumbY = selectorThumbY(list);
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(SCROLLBAR);
        drawTexturedModalRect(x + 175, thumbY, selectorMaxScroll(list) == 0 ? 244 : 232, 0, 12, 15);
        if (inside(mx, my, x + 8, y + 17, 162, 162)) {
            int index = (selectorScroll + (my - y - 17) / 18) * SELECTOR_COLUMNS + (mx - x - 8) / 18;
            if (index < list.size())
                drawHoveringText(Arrays.asList(name(catalog[list.get(index)])), mx, my, fontRendererObj);
        } else if (inside(mx, my, x + 4, y + 4, 70, 12))
            drawHoveringText(Arrays.asList(tr("selector_hint"), tr("drag_hint")), mx, my, fontRendererObj);
    }

    private void clickSelector(int mx, int my, int mouseButton) {
        int x = selectorLeft(), y = selectorTop();
        if (mouseButton == 1 && inside(mx, my, x + 80, y + 4, 90, 12)) {
            selectorSearch.setText("");
            selectorSearch.setFocused(true);
            selectorScroll = 0;
            return;
        }
        selectorSearch.mouseClicked(mx, my, mouseButton);
        if (mouseButton != 0) return;
        List<Integer> list = selectorEntries();
        if (inside(mx, my, x + 175, y + 18, 12, 162)) {
            selectorDragging = true;
            int thumb = selectorThumbY(list);
            selectorDragOffset = my >= thumb && my < thumb + 15 ? my - thumb : 7;
            dragSelector(my);
            return;
        }
        if (!inside(mx, my, x + 8, y + 17, 162, 162)) return;
        int index = (selectorScroll + (my - y - 17) / 18) * SELECTOR_COLUMNS + (mx - x - 8) / 18;
        if (index < list.size()) {
            chooseCatalog(list.get(index));
        }
    }

    private int selectorLeft() {
        return (width - SELECTOR_WIDTH) / 2;
    }

    private int selectorTop() {
        return (height - SELECTOR_HEIGHT) / 2;
    }

    private int selectorMaxScroll(List<Integer> list) {
        return Math.max(0, (list.size() + SELECTOR_COLUMNS - 1) / SELECTOR_COLUMNS - SELECTOR_ROWS);
    }

    private int selectorThumbY(List<Integer> list) {
        int max = selectorMaxScroll(list);
        return selectorTop() + 18 + (max == 0 ? 0 : selectorScroll * 147 / max);
    }

    private void dragSelector(int y) {
        int max = selectorMaxScroll(selectorEntries());
        selectorScroll = Math
            .max(0, Math.min(max, Math.round((y - selectorTop() - 18 - selectorDragOffset) * max / 147F)));
    }

    private void initSelectorSearch(String query) {
        selectorSearch = field(selectorLeft() + 80, selectorTop() + 4, 90, 12, query, 50);
        selectorSearch.setFocused(true);
    }

    private void requestCatalog() {
        if (catalogLoading) return;
        if (catalogVersion == 0) catalogLoading = true;
        MaintainerNetwork.Action action = new MaintainerNetwork.Action(container, MaintainerNetwork.CATALOG);
        action.catalogVersion = catalogVersion;
        MaintainerNetwork.send(action);
    }

    private void chooseCatalog(int index) {
        chooseMaterial(catalog[index]);
    }

    public boolean materialDropAt(int x, int y) {
        if (draft == null || waiting) return false;
        return selecting ? inside(x, y, selectorLeft() + 8, selectorTop() + 17, 162, 162)
            : inside(x, y, (width - 176) / 2 + 5, (height - 107) / 2 + 5, 18, 18);
    }

    public void chooseGhost(IAEStack<?> item, IAEStack<?> fluid) {
        // A filled container may itself have a pattern; retain the existing item-before-fluid preference.
        if (item != null) for (IAEStack<?> material : catalog) if (material != null && material.isSameType(item)) {
            chooseMaterial(item);
            return;
        }
        chooseMaterial(fluid == null ? item : fluid);
    }

    private void chooseMaterial(IAEStack<?> candidate) {
        byte[] bytes;
        try {
            bytes = MaintainerNetwork.materialBytes(candidate);
        } catch (IllegalArgumentException exception) {
            feedback = "material_too_large";
            feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
            return;
        }
        materialErrorUntil = 0;
        draftMaterial = candidate.copy()
            .setStackSize(1);
        selected = -3;
        selecting = false;
        selectorDragging = false;
        MaintainerNetwork.Action action = new MaintainerNetwork.Action(container, MaintainerNetwork.MATERIAL);
        action.slot = editSlot;
        action.id = draft.getLong("id");
        action.revision = draft.getInteger("revision");
        action.selection = -3;
        action.material = bytes;
        action.request = ++requestSequence;
        materialRequest = action.request;
        MaintainerNetwork.send(action);
        feedback = "ghost_selected";
        feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
    }

    private void clearOwnFocus() {
        for (GuiTextField field : new GuiTextField[] { search, target, batch, seconds, selectorSearch })
            if (field != null) field.setFocused(false);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (selecting) {
                selecting = false;
                selectorDragging = false;
            } else if (draft != null && !waiting) {
                draft = null;
                feedbackUntil = 0;
            } else if (draft == null) super.keyTyped(character, key);
            return;
        }
        if (draft != null) {
            if (waiting) return;
            if (selecting) {
                if (selectorSearch.textboxKeyTyped(character, key)) selectorScroll = 0;
            } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) save();
            else if (key == Keyboard.KEY_TAB) {
                if (target.isFocused()) {
                    target.setFocused(false);
                    batch.setFocused(true);
                } else if (batch.isFocused()) {
                    batch.setFocused(false);
                    seconds.setFocused(true);
                } else {
                    seconds.setFocused(false);
                    target.setFocused(true);
                }
            } else {
                target.textboxKeyTyped(character, key);
                batch.textboxKeyTyped(character, key);
                seconds.textboxKeyTyped(character, key);
            }
            return;
        }
        if (search.isFocused()) {
            if (search.textboxKeyTyped(character, key)) scroll = 0;
            return;
        }
        super.keyTyped(character, key);
    }

    public boolean capturesInput() {
        return draft == null ? search != null && search.isFocused()
            : selecting ? selectorSearch != null && selectorSearch.isFocused()
                : target.isFocused() || batch.isFocused() || seconds.isFocused();
    }

    @Override
    public void handleKeyboardInput() {
        if (!capturesInput()) {
            super.handleKeyboardInput();
            return;
        }
        if (Keyboard.getEventKeyState()
            || Keyboard.getEventKey() == 0 && Character.isDefined(Keyboard.getEventCharacter()))
            keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
        mc.func_152348_aa();
    }

    public boolean modalOpen() {
        return draft != null;
    }

    public boolean covers(int x, int y, int w, int h) {
        return intersects(x, y, w, h, guiLeft, guiTop, xSize, MaintainerClient.tall ? ySize : 183)
            || intersects(x, y, w, h, guiLeft - 18, guiTop + 4, 16, 16)
            || draft != null
                && (selecting ? intersects(x, y, w, h, selectorLeft(), selectorTop(), SELECTOR_WIDTH, SELECTOR_HEIGHT)
                    : intersects(x, y, w, h, (width - 176) / 2, (height - 107) / 2, 176, 127));
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            super.handleMouseInput();
            return;
        }
        int x = Mouse.getEventX() * width / mc.displayWidth,
            y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!covers(x, y, 1, 1)) {
            if (Loader.isModLoaded("NotEnoughItems")) MaintainerNEI.mouseScrolled(this, wheel > 0 ? 1 : -1);
            return;
        }
        if (draft != null) {
            if (selecting) selectorScroll = Math
                .max(0, Math.min(selectorMaxScroll(selectorEntries()), selectorScroll + (wheel > 0 ? -1 : 1)));
            return;
        }
        int slot = slotAt(x, y);
        if (slot >= 0 && entries[slot] != null && (isShiftKeyDown() || isCtrlKeyDown())) {
            NBTTagCompound row = entries[slot];
            MaintainerNetwork.Action action = action(MaintainerNetwork.EDIT, slot);
            action.target = row.getLong("target");
            action.batch = row.getLong("batch");
            action.seconds = row.getInteger("seconds");
            action.enabled = row.getBoolean("enabled");
            if (isShiftKeyDown())
                action.target = wheel > 0 ? action.target <= Long.MAX_VALUE / 2 ? action.target * 2 : Long.MAX_VALUE
                    : Math.max(1, action.target / 2);
            else action.seconds = wheel > 0 ? Math.min(86400, action.seconds * 2) : Math.max(1, action.seconds / 2);
            MaintainerNetwork.send(action);
        } else if (inside(x, y, guiLeft + 8, guiTop + 18, 223, rows * 23))
            scroll = Math.max(0, scroll + (wheel > 0 ? -1 : 1));
    }

    private void drag(int y) {
        int track = (MaintainerClient.tall ? rows * 23 - 2 : 136) - 15;
        scroll = Math.max(
            0,
            Math.min(
                maxScroll(visibleEntries()),
                Math.round((y - guiTop - 26) * maxScroll(visibleEntries()) / (float) track)));
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (Loader.isModLoaded("NotEnoughItems")) MaintainerNEI.mouseDragged(this, x, y, button, elapsed);
        if (selecting && selectorDragging && button == 0) dragSelector(y);
        if (draft == null && dragging) drag(y);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (Loader.isModLoaded("NotEnoughItems")) MaintainerNEI.mouseUp(this, x, y, button);
        dragging = false;
        selectorDragging = false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        clientTicks++;
        for (Iterator<Integer> iterator = pendingToggles.iterator(); iterator.hasNext();) {
            int slot = iterator.next();
            if (System.currentTimeMillis() - toggleStarted[slot] > 10000) {
                iterator.remove();
                toggleRequests[slot] = 0;
                desiredEnabled[slot] = null;
                feedback = "no_reply";
                feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
            }
        }
        // Forge assigns the window ID after displayGuiScreen/initGui, so handshake on the next client tick.
        if (container.nonce == 0 && container.windowId != 0 && helloTicks++ % 20 == 0)
            MaintainerNetwork.send(new MaintainerNetwork.Action(container, MaintainerNetwork.HELLO));
        if (waiting && System.currentTimeMillis() - waitingSince > 10000) {
            waiting = false;
            feedback = "no_reply";
            feedbackUntil = System.currentTimeMillis() + FEEDBACK_DURATION_MS;
        }
        search.updateCursorCounter();
        if (draft != null) {
            target.updateCursorCounter();
            batch.updateCursorCounter();
            seconds.updateCursorCounter();
            if (selecting) selectorSearch.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void button(int x, int y, int w, int h, String label) {
        panel(x, y, w, h);
        centered(label, x + w / 2, y + (h - 8) / 2);
    }

    private void smallButton(int x, int y, int w, int h, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        int textureY = enabled ? hovered ? 86 : 66 : 46;
        int left = w / 2, right = w - left;
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(BUTTONS);
        drawTexturedModalRect(x, y, 0, textureY, left, h - 2);
        drawTexturedModalRect(x + left, y, 200 - right, textureY, right, h - 2);
        drawTexturedModalRect(x, y + h - 2, 0, textureY + 18, left, 2);
        drawTexturedModalRect(x + left, y + h - 2, 200 - right, textureY + 18, right, 2);
        drawCenteredString(
            fontRendererObj,
            label,
            x + w / 2,
            y + (h - 8) / 2,
            !enabled ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0);
    }

    private void panel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF373737);
        drawRect(x, y, x + w - 1, y + h - 1, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF999999);
        drawRect(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);
    }

    private void centered(String text, int x, int y) {
        fontRendererObj.drawString(text, x - fontRendererObj.getStringWidth(text) / 2, y, 0);
    }

    private void right(String text, int x, int y, int color) {
        fontRendererObj.drawString(text, x - fontRendererObj.getStringWidth(text), y, color);
    }

    private String trim(String text, int width) {
        return fontRendererObj.getStringWidth(text) <= width ? text
            : fontRendererObj.trimStringToWidth(text, width - 9) + "...";
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean intersects(int x, int y, int w, int h, int otherX, int otherY, int otherW, int otherH) {
        return x < otherX + otherW && x + w > otherX && y < otherY + otherH && y + h > otherY;
    }

    private static long parseAmount(String text) {
        Matcher amount = AMOUNT.matcher(text.trim());
        if (!amount.matches()) throw new NumberFormatException();
        BigDecimal value = new BigDecimal(amount.group(1));
        String unit = amount.group(2)
            .toLowerCase(Locale.ROOT);
        // Match AE's decimal suffixes without routing exact long quantities through its double calculator.
        value = switch (unit) {
            case "k" -> value.scaleByPowerOfTen(3);
            case "m" -> value.scaleByPowerOfTen(6);
            case "g", "b" -> value.scaleByPowerOfTen(9);
            case "t" -> value.scaleByPowerOfTen(12);
            case "s" -> value.multiply(BigDecimal.valueOf(64));
            case "i" -> value.multiply(BigDecimal.valueOf(144));
            default -> value;
        };
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new NumberFormatException();
        }
    }

    private static int parseInterval(String text) {
        String value = text.toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");
        if (value.matches("\\d+")) {
            int seconds = Integer.parseInt(value);
            if (seconds < 1 || seconds > 86400) throw new NumberFormatException();
            return seconds;
        }
        Matcher parts = INTERVAL_PART.matcher(value);
        int end = 0;
        long total = 0;
        while (parts.find()) {
            if (parts.start() != end) throw new NumberFormatException();
            long number = Long.parseLong(parts.group(1));
            char unit = parts.group(2)
                .charAt(0);
            int multiplier = unit == 'd' ? 86400 : unit == 'h' ? 3600 : unit == 'm' ? 60 : 1;
            if (number > 86400 / multiplier) throw new NumberFormatException();
            total += number * multiplier;
            if (total > 86400) throw new NumberFormatException();
            end = parts.end();
        }
        if (end != value.length() || total < 1) throw new NumberFormatException();
        return (int) total;
    }

    private static String time(int seconds) {
        if (seconds >= 86400) return seconds / 86400 + "d";
        String result = seconds >= 3600 ? seconds / 3600 + "h" : "";
        if (seconds % 3600 >= 60) result += (result.isEmpty() ? "" : " ") + seconds % 3600 / 60 + "m";
        if (seconds % 60 > 0 || result.isEmpty()) result += (result.isEmpty() ? "" : " ") + seconds % 60 + "s";
        return result;
    }

    private static String shortNumber(long value) {
        if (value < 1000) return Long.toString(value);
        String[] units = { "k", "M", "G", "T", "P", "E" };
        double number = value;
        int unit = -1;
        while (number >= 1000 && unit < units.length - 1) {
            number /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, number >= 100 ? "%.0f%s" : "%.1f%s", number, units[unit]);
    }
}
