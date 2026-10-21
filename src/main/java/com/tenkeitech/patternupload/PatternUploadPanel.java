/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.patternupload.PatternUploadLayout.Box;

import appeng.api.parts.IPatternTerminal;
import appeng.container.implementations.ContainerPatternTerm;

/** Exclusive transparent client view. The original AE container stays open and owns every item. */
public final class PatternUploadPanel extends Gui {

    private static final AtomicLong REQUESTS = new AtomicLong(System.nanoTime());
    private static final int TARGETS = 0, MAPPINGS = 1, ADD_MAPPING = 2;
    private static final int PREVIOUS = -1, NEXT = -2, CLOSE = -3, TOGGLE_BUTTON = -4, TOGGLE_AUTO = -5;
    private static final int RELOAD = -6, ADD = -7, DELETE = -8;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ContainerPatternTerm container;
    private final PatternUploadRecipeContext context;
    private final List<Control> controls = new ArrayList<>();
    private final List<Integer> mappingIndices = new ArrayList<>();
    private final GuiTextField search = new GuiTextField(mc.fontRenderer, 0, 0, 240, 18);
    private final GuiTextField categoryInput = new GuiTextField(mc.fontRenderer, 0, 0, 120, 20);
    private final GuiTextField mapping = new GuiTextField(mc.fontRenderer, 0, 0, 120, 20);
    private PatternUploadPreferences preferences;
    private List<PatternUploadNetwork.TargetEntry> allEntries = Collections.emptyList();
    private List<PatternUploadNetwork.TargetEntry> entries = Collections.emptyList();
    private PatternUploadLayout geometry;
    private boolean open, waiting, selectionPending, dirty = true;
    private boolean localSearchActive;
    private final java.util.Map<Integer, java.util.Set<String>> exactCategoryNames = new java.util.HashMap<>();
    private boolean categoryAutoPending;
    private boolean candidatesReceived;
    private java.util.Map<Integer, String> recipeCategories = Collections.emptyMap();
    private int tooltipOffset;
    private boolean categoryHovered;
    private int mode, page, pages = 1, selectedMapping = -1, width, height;
    private long requestId, nonce, requestStarted;
    private String category = "", categoryLabel = "", status = "";

    public PatternUploadPanel(ContainerPatternTerm container, PatternUploadRecipeContext context) {
        this.container = container;
        this.context = context;
        search.setMaxStringLength(PatternUploadNetwork.MAX_ROUTING_TEXT);
        mapping.setMaxStringLength(PatternUploadNetwork.MAX_ROUTING_TEXT);
        categoryInput.setMaxStringLength(PatternUploadNetwork.MAX_ROUTING_TEXT);
        loadPreferences(false);
    }

    public boolean isOpen() {
        return open;
    }

    public boolean showUploadButton() {
        return preferences == null || preferences.showButton();
    }

    public boolean hasPattern() {
        return IPatternTerminal.isEncodedPattern(PatternUploadRecipeContext.source(container));
    }

    public void openUpload() {
        if (open && waiting) return;
        loadPreferences(false);
        captureCategory(false);
        open = true;
        mode = TARGETS;
        localSearchActive = false;
        selectedMapping = -1;
        List<String> names = preferences == null ? Collections.emptyList() : preferences.targets(category);
        List<String> terms = names.isEmpty() ? PatternUploadRouting.searchTerms(category, categoryLabel)
            : Collections.emptyList();
        search.setText(names.size() == 1 ? names.get(0) : terms.isEmpty() ? "" : terms.get(0));
        // The initial category/provider key is a real client-side filter too. The server uses it
        // only for the optional unique-match upload decision, while the complete list remains local.
        localSearchActive = !search.getText()
            .trim()
            .isEmpty();
        mapping.setText(names.size() == 1 ? names.get(0) : "");
        categoryInput.setText("");
        search.setFocused(true);
        categoryInput.setFocused(false);
        mapping.setFocused(false);
        request(
            names,
            terms,
            preferences != null && preferences.autoUpload() && (!names.isEmpty() || "crafting".equals(category)));
        categoryAutoPending = preferences != null && preferences.autoUpload()
            && names.isEmpty()
            && !category.isEmpty()
            && !"crafting".equals(category);
    }

    public void openSettings() {
        if (open && waiting) return;
        loadPreferences(false);
        captureCategory(true);
        open = true;
        mode = MAPPINGS;
        page = 0;
        selectedMapping = -1;
        search.setText("");
        mapping.setText("");
        categoryInput.setText(PatternUploadPreferences.validText(category) ? category : "");
        search.setFocused(true);
        categoryInput.setFocused(false);
        mapping.setFocused(false);
        status = preferences == null ? key("config_error") : key("mapping_title");
        dirty = true;
    }

    private void captureCategory(boolean settings) {
        category = context.category(container, mc.theWorld);
        // Managing preferences moves no items, and can use the remembered category between encodings.
        if (settings && category.isEmpty()) category = context.rememberedCategory();
        categoryLabel = category.isEmpty() ? tr("no_category") : context.label();
        if (PatternUploadRecipeContext.isCraftingPattern(container, mc.theWorld)) {
            category = "crafting";
            categoryLabel = tr("crafting_category");
        }
    }

    private void loadPreferences(boolean reload) {
        try {
            PatternUploadPreferences loaded = PatternUploadPreferences.current();
            if (reload) loaded.reload();
            preferences = loaded;
        } catch (IOException | RuntimeException exception) {
            status = key("config_error");
            TenkeiTech.LOG.warn("Could not load pattern upload preferences", exception);
        }
    }

    private void request(List<String> names, List<String> searchTerms, boolean automatic) {
        candidatesReceived = false;
        categoryAutoPending = false;
        exactCategoryNames.clear();
        requestId = REQUESTS.incrementAndGet();
        nonce = 0;
        waiting = true;
        selectionPending = automatic;
        requestStarted = System.nanoTime();
        allEntries = Collections.emptyList();
        entries = Collections.emptyList();
        page = 0;
        pages = 1;
        status = key("loading");
        dirty = true;
        PatternUploadNetwork.requestTargets(
            container.windowId,
            requestId,
            names,
            searchTerms,
            preferences == null ? Collections.emptyList() : preferences.pinnedProviders(),
            automatic,
            PatternUploadRecipeContext.source(container));
    }

    public void acceptPage(PatternUploadNetwork.TargetsPageMessage message) {
        if (!open || mode == MAPPINGS || message.windowId != container.windowId || message.requestId != requestId)
            return;
        if (candidatesReceived || message.entries.size() > PatternUploadNetwork.MAX_TARGET_ENTRIES) {
            return;
        }
        candidatesReceived = true;
        nonce = message.nonce;
        List<PatternUploadNetwork.TargetEntry> received = message.entries;
        waiting = false;
        selectionPending = false;
        page = 0;
        if (!localSearchActive) search.setText(message.searchText);
        allEntries = Collections.unmodifiableList(received);
        exactCategoryNames.clear();
        recipeCategories = cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")
            ? PatternUploadNeiCategories.lookup(received, exactCategoryNames)
            : Collections.emptyMap();
        tooltipOffset = 0;
        entries = localSearchActive ? filteredEntries(search.getText()) : allEntries;
        pages = Math.max(1, (entries.size() + PatternUploadNetwork.PAGE_SIZE - 1) / PatternUploadNetwork.PAGE_SIZE);
        page = Math.min(page, pages - 1);
        status = !message.complete ? key("scan_limit")
            : entries.isEmpty() ? key("no_search_results")
                : mode == ADD_MAPPING ? key("choose_mapping") : message.matched ? key("matched") : "";
        dirty = true;
        boolean automatic = categoryAutoPending;
        categoryAutoPending = false;
        if (automatic && message.complete
            && mode == TARGETS
            && preferences != null
            && preferences.autoUpload()
            && category.equals(context.category(container, mc.theWorld))) {
            List<PatternUploadNetwork.TargetEntry> matches = new ArrayList<>();
            for (PatternUploadNetwork.TargetEntry entry : allEntries) {
                if (exactCategoryMatch(entry, categoryLabel)) matches.add(entry);
            }
            // Failed NEI lookups omit exact-category entries and must not enable the fallback.
            if (matches.isEmpty() && exactCategoryNames.size() == allEntries.size()
                && entries.size() == 1
                && !exactCategoryMatch(entries.get(0), search.getText())) {
                matches.add(entries.get(0));
            }
            if (matches.size() == 1) {
                waiting = true;
                selectionPending = true;
                requestStarted = System.nanoTime();
                status = key("loading");
                PatternUploadNetwork.selectTarget(container.windowId, requestId, nonce, matches.get(0).groupIndex);
            }
        }
    }

    public void acceptResult(PatternUploadNetwork.UploadResultMessage message) {
        if (!open || mode == MAPPINGS || message.windowId != container.windowId || message.requestId != requestId)
            return;
        waiting = false;
        selectionPending = false;
        if (message.success || message.code == PatternUploadNetwork.ResultCode.REFUND_UNCERTAIN) {
            open = false;
            mc.thePlayer.addChatMessage(
                new ChatComponentTranslation(message.code.translationKey(), providerDisplayName(message.providerName)));
        } else {
            status = message.code.translationKey();
        }
        dirty = true;
    }

    public void tick() {
        if (!open) return;
        search.updateCursorCounter();
        categoryInput.updateCursorCounter();
        mapping.updateCursorCounter();
        if (waiting && System.nanoTime() - requestStarted > 15_000_000_000L) {
            categoryAutoPending = false;
            waiting = false;
            selectionPending = false;
            status = key("timeout");
            dirty = true;
        }
    }

    private void searchChanged() {
        categoryAutoPending = false;
        page = 0;
        selectedMapping = -1;
        localSearchActive = mode != MAPPINGS;
        if (mode != MAPPINGS) applyLocalFilter();
        dirty = true;
    }

    private void submitSearch() {
        categoryAutoPending = false;
        if (mode == MAPPINGS) return;
        localSearchActive = true;
        applyLocalFilter();
    }

    private void applyLocalFilter() {
        if (mode == MAPPINGS) return;
        entries = filteredEntries(search.getText());
        pages = Math.max(1, (entries.size() + PatternUploadNetwork.PAGE_SIZE - 1) / PatternUploadNetwork.PAGE_SIZE);
        page = Math.min(page, pages - 1);
        if (!waiting) status = entries.isEmpty() ? key("no_search_results") : "";
        dirty = true;
    }

    private List<PatternUploadNetwork.TargetEntry> filteredEntries(String query) {
        if (query == null || query.trim()
            .isEmpty()) return allEntries;
        return Collections.unmodifiableList(
            PatternUploadRouting.filter(
                allEntries,
                entry -> entry.routingName + " "
                    + PatternUploadNames.display(entry.terminalName, entry.terminalSuffix)
                    + " "
                    + recipeCategories.getOrDefault(entry.groupIndex, ""),
                query));
    }

    public void key(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!selectionPending) open = false;
            return;
        }
        if (selectionPending) return;
        if (keyCode == Keyboard.KEY_TAB) {
            if (mode == MAPPINGS) {
                if (search.isFocused()) {
                    search.setFocused(false);
                    categoryInput.setFocused(true);
                    mapping.setFocused(false);
                } else if (categoryInput.isFocused()) {
                    search.setFocused(false);
                    categoryInput.setFocused(false);
                    mapping.setFocused(true);
                } else {
                    search.setFocused(true);
                    categoryInput.setFocused(false);
                    mapping.setFocused(false);
                }
            } else {
                boolean next = !search.isFocused();
                search.setFocused(next);
                categoryInput.setFocused(false);
                mapping.setFocused(!next);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_F && GuiScreen.isCtrlKeyDown()) {
            search.setFocused(true);
            categoryInput.setFocused(false);
            mapping.setFocused(false);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if ((mapping.isFocused() || categoryInput.isFocused()) && !waiting) addMapping();
            else submitSearch();
            return;
        }
        String before = search.getText();
        search.textboxKeyTyped(character, keyCode);
        if (!before.equals(search.getText())) searchChanged();
        if (categoryInput.textboxKeyTyped(character, keyCode)) dirty = true;
        if (mapping.textboxKeyTyped(character, keyCode)) dirty = true;
    }

    public void click(int mouseX, int mouseY, int button) {
        if (!open || selectionPending) return;
        layout();
        if (geometry == null) return;
        if (button == 1 && geometry.search.contains(mouseX, mouseY)) {
            search.setText("");
            searchChanged();
            return;
        }
        if (button == 1 && geometry.mapping.contains(mouseX, mouseY)) {
            mapping.setText("");
            selectedMapping = -1;
            dirty = true;
            return;
        }
        if (button == 1 && mode == MAPPINGS && geometry.categoryInput.contains(mouseX, mouseY)) {
            categoryInput.setText("");
            selectedMapping = -1;
            dirty = true;
            return;
        }
        if (button == 1 && mode == TARGETS && preferences != null) {
            int start = page * PatternUploadNetwork.PAGE_SIZE;
            int end = Math.min(start + PatternUploadNetwork.PAGE_SIZE, entries.size());
            for (int row = 0; row < end - start; row++) {
                if (!geometry.rows[row].contains(mouseX, mouseY)) continue;
                String provider = entries.get(start + row).routingName;
                if (PatternUploadPreferences.validText(provider)) {
                    try {
                        preferences.togglePinned(provider);
                        request(Collections.emptyList(), Collections.emptyList(), false);
                    } catch (IOException exception) {
                        configFailure(exception);
                    }
                }
                return;
            }
        }
        search.mouseClicked(mouseX, mouseY, button);
        if (mode == MAPPINGS) categoryInput.mouseClicked(mouseX, mouseY, button);
        mapping.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        for (Control control : controls) {
            if (control.enabled && control.box.contains(mouseX, mouseY)) {
                activate(control.id);
                return;
            }
        }
    }

    public void scroll(int wheel) {
        if (categoryHovered && wheel != 0) {
            tooltipOffset = Math.max(0, tooltipOffset + (wheel < 0 ? 1 : -1));
            return;
        }
        if (!waiting && wheel != 0) changePage(wheel < 0 ? 1 : -1);
    }

    private void changePage(int delta) {
        int next = page + delta;
        if (waiting || next < 0 || next >= pages) return;
        selectedMapping = -1;
        page = next;
        dirty = true;
    }

    private void activate(int id) {
        if (id >= 0) {
            if (mode == MAPPINGS) {
                selectedMapping = mappingIndices.get(page * 6 + id);
                PatternUploadPreferences.Mapping selected = preferences.mappings()
                    .get(selectedMapping);
                categoryInput.setText(selected.category);
                mapping.setText(selected.provider);
            } else if (mode == ADD_MAPPING) {
                mapping.setText(entries.get(page * PatternUploadNetwork.PAGE_SIZE + id).routingName);
                saveMapping();
            } else {
                waiting = true;
                selectionPending = true;
                requestStarted = System.nanoTime();
                status = key("loading");
                PatternUploadNetwork.selectTarget(
                    container.windowId,
                    requestId,
                    nonce,
                    entries.get(page * PatternUploadNetwork.PAGE_SIZE + id).groupIndex);
            }
        } else if (id == CLOSE) open = false;
        else if (id == PREVIOUS || id == NEXT) changePage(id == NEXT ? 1 : -1);
        else if (id == ADD) addMapping();
        else if (id == RELOAD) {
            status = key("mapping_reloaded");
            loadPreferences(true);
            selectedMapping = -1;
            categoryInput.setText("");
            mapping.setText("");
        } else if (preferences != null) {
            try {
                if (id == TOGGLE_BUTTON) preferences.toggleButton();
                else if (id == TOGGLE_AUTO) preferences.toggleAutoUpload();
                else if (id == DELETE) {
                    preferences.remove(deletionIndex());
                    selectedMapping = -1;
                    categoryInput.setText("");
                    mapping.setText("");
                    status = key("mapping_deleted");
                }
            } catch (IOException exception) {
                configFailure(exception);
            }
        }
        dirty = true;
    }

    private void addMapping() {
        if (preferences == null || waiting) return;
        String requestedCategory = categoryInput.getText()
            .trim();
        if (requestedCategory.isEmpty()) requestedCategory = category;
        if (!mapping.getText()
            .trim()
            .isEmpty()) {
            categoryInput.setText(requestedCategory);
            saveMapping();
        } else if (PatternUploadPreferences.validText(requestedCategory) && hasPattern()) {
            categoryInput.setText(requestedCategory);
            mode = ADD_MAPPING;
            search.setText("");
            request(Collections.emptyList(), Collections.emptyList(), false);
        }
    }

    private void saveMapping() {
        String newCategory = categoryInput.getText()
            .trim();
        if (newCategory.isEmpty()) newCategory = category;
        String provider = mapping.getText()
            .trim();
        if (!PatternUploadPreferences.validText(newCategory) || !PatternUploadPreferences.validText(provider)) {
            status = key("mapping_required");
            dirty = true;
            return;
        }
        String label = newCategory;
        if (selectedMapping >= 0 && selectedMapping < preferences.mappings()
            .size()) {
            PatternUploadPreferences.Mapping previous = preferences.mappings()
                .get(selectedMapping);
            if (previous.category.equals(newCategory)) label = previous.label;
        } else if (newCategory.equals(category) && PatternUploadPreferences.validText(categoryLabel)) {
            label = categoryLabel;
        }
        try {
            preferences.saveMapping(selectedMapping, newCategory, label, provider);
            mode = MAPPINGS;
            search.setText("");
            categoryInput.setText("");
            mapping.setText("");
            page = 0;
            selectedMapping = -1;
            status = key("mapping_saved");
        } catch (IOException exception) {
            configFailure(exception);
        }
        dirty = true;
    }

    private int deletionIndex() {
        if (preferences == null) return -1;
        List<PatternUploadPreferences.Mapping> mappings = preferences.mappings();
        if (mode == MAPPINGS && selectedMapping >= 0 && selectedMapping < mappings.size()) return selectedMapping;
        for (int index = 0; index < mappings.size(); index++) {
            PatternUploadPreferences.Mapping candidate = mappings.get(index);
            if (candidate.category.equals(category) && candidate.provider.equals(mapping.getText())) return index;
        }
        return -1;
    }

    private void configFailure(IOException exception) {
        status = key("config_error");
        TenkeiTech.LOG.warn("Pattern upload preference edit was not committed", exception);
    }

    public void draw(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!open) return;
        if (width != screenWidth || height != screenHeight) {
            width = screenWidth;
            height = screenHeight;
            geometry = new PatternUploadLayout(width, height);
            position(search, geometry.search);
            position(categoryInput, geometry.categoryInput);
            position(mapping, geometry.mapping);
            dirty = true;
        }
        layout();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1, 1, 1, 1);
            // No background rectangle or default gradient: only our controls are drawn over the world.
            search.drawTextBox();
            if (mode == MAPPINGS) categoryInput.drawTextBox();
            mapping.drawTextBox();
            placeholder(search, "search_hint");
            if (mode == MAPPINGS) placeholder(categoryInput, "mapping_category");
            placeholder(mapping, "mapping_input");
            for (Control control : controls) drawControl(control, mouseX, mouseY);
            centered(
                StatCollector.translateToLocalFormatted(key("page"), page + 1, pages),
                geometry.pageY,
                72,
                0xFFFFFF);
            if (!status.isEmpty())
                centered(StatCollector.translateToLocal(status), geometry.statusY, width - 12, 0xFFD080);
            String tooltip = geometry.search.contains(mouseX, mouseY) ? tr("search_help")
                : mode == MAPPINGS && geometry.categoryInput.contains(mouseX, mouseY) ? tr("mapping_category_help")
                    : geometry.mapping.contains(mouseX, mouseY) ? tr("mapping_help") : "";
            for (Control control : controls) if (control.box.contains(mouseX, mouseY)) tooltip = control.text;
            categoryHovered = false;
            if (mode != MAPPINGS && !waiting) {
                int start = page * PatternUploadNetwork.PAGE_SIZE;
                int end = Math.min(start + PatternUploadNetwork.PAGE_SIZE, entries.size());
                for (int row = 0; row < end - start; row++) {
                    if (geometry.rows[row].contains(mouseX, mouseY)) {
                        PatternUploadNetwork.TargetEntry entry = entries.get(start + row);
                        String categories = recipeCategories.getOrDefault(entry.groupIndex, "");
                        tooltip = PatternUploadNames.display(entry.terminalName, entry.terminalSuffix) + "\n"
                            + (categories.isEmpty() ? tr("no_recipe_types") : categories);
                        if (mode == TARGETS) tooltip += "\n" + tr("pin_help");
                        categoryHovered = true;
                    }
                }
            }
            if (!tooltip.isEmpty()) tooltip(tooltip, mouseX, mouseY);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void position(GuiTextField field, Box box) {
        field.xPosition = box.x;
        field.yPosition = box.y;
        field.width = box.w;
        field.height = box.h;
    }

    private void placeholder(GuiTextField field, String name) {
        if (field.getText()
            .isEmpty() && !field.isFocused())
            mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(tr(name), field.width - 8),
                field.xPosition + 4,
                field.yPosition + (field.height - 8) / 2,
                0x777777);
    }

    private void centered(String text, int y, int maxWidth, int color) {
        drawCenteredString(mc.fontRenderer, mc.fontRenderer.trimStringToWidth(text, maxWidth), width / 2, y, color);
    }

    private void tooltip(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n"))
            lines.addAll(mc.fontRenderer.listFormattedStringToWidth(line, Math.max(20, width - 24)));
        int count = Math.min(lines.size(), Math.max(1, (height - 16) / 10));
        int offset = categoryHovered ? Math.min(tooltipOffset, lines.size() - count) : 0;
        if (categoryHovered) tooltipOffset = offset;
        int w = 8;
        for (int i = offset; i < offset + count; i++) w = Math.max(w, mc.fontRenderer.getStringWidth(lines.get(i)) + 8);
        int left = Math.max(4, Math.min(x + 8, width - w - 4));
        int top = Math.max(4, Math.min(y - 16, height - count * 10 - 10));
        drawRect(left, top, left + w, top + count * 10 + 6, 0xEE101010);
        for (int i = 0; i < count; i++)
            mc.fontRenderer.drawStringWithShadow(lines.get(offset + i), left + 4, top + 3 + i * 10, 0xFFFFFF);
    }

    private void drawControl(Control control, int mouseX, int mouseY) {
        Box box = control.box;
        boolean selected = mode == MAPPINGS && control.id >= 0
            && page * 6 + control.id < mappingIndices.size()
            && mappingIndices.get(page * 6 + control.id) == selectedMapping;
        int fill = !control.enabled ? 0xFF444444
            : selected ? 0xFF52779A : box.contains(mouseX, mouseY) ? 0xFF999999 : 0xFF7D7D7D;
        rounded(box.x, box.y, box.w, box.h, 0xFF151515);
        rounded(box.x + 1, box.y + 1, box.w - 2, box.h - 2, 0xFFB0B0B0);
        rounded(box.x + 2, box.y + 2, box.w - 4, box.h - 4, fill);
        boolean exact = mode == TARGETS && control.id >= 0
            && page * PatternUploadNetwork.PAGE_SIZE + control.id < entries.size()
            && exactCategoryMatch(entries.get(page * PatternUploadNetwork.PAGE_SIZE + control.id), search.getText());
        drawCenteredString(
            mc.fontRenderer,
            mc.fontRenderer.trimStringToWidth(control.text, box.w - 6),
            box.x + box.w / 2,
            box.y + (box.h - 8) / 2,
            control.enabled ? exact ? 0xFFFF55 : 0xFFFFFF : 0x888888);
    }

    private boolean exactCategoryMatch(PatternUploadNetwork.TargetEntry entry, String query) {
        String value = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(query)
            .trim();
        if (value.isEmpty()) return false;
        for (String name : exactCategoryNames.getOrDefault(entry.groupIndex, Collections.emptySet())) {
            if (value.equalsIgnoreCase(
                net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(name)
                    .trim()))
                return true;
        }
        return false;
    }

    private static void rounded(int x, int y, int w, int h, int color) {
        drawRect(x + 2, y, x + w - 2, y + h, color);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, color);
        drawRect(x, y + 2, x + w, y + h - 2, color);
    }

    private void layout() {
        if (!dirty || geometry == null) return;
        dirty = false;
        controls.clear();
        // Search is local to the latest snapshot, so it remains responsive while a background
        // startup refresh is waiting for the Grid to finish joining interfaces.
        search.setEnabled(!selectionPending);
        categoryInput.setEnabled(!waiting);
        mapping.setEnabled(!waiting);
        boolean ready = !waiting;
        if (mode == MAPPINGS) {
            mappingIndices.clear();
            if (preferences != null) {
                for (int index = 0; index < preferences.mappings()
                    .size(); index++) mappingIndices.add(index);
                List<Integer> filtered = PatternUploadRouting.filter(mappingIndices, index -> {
                    PatternUploadPreferences.Mapping entry = preferences.mappings()
                        .get(index);
                    return entry.category + " " + entry.label + " " + entry.provider;
                }, search.getText());
                mappingIndices.clear();
                mappingIndices.addAll(filtered);
            }
            pages = Math.max(1, (mappingIndices.size() + 5) / 6);
            page = Math.min(page, pages - 1);
            for (int index = 0; index < 6 && page * 6 + index < mappingIndices.size(); index++) {
                PatternUploadPreferences.Mapping entry = preferences.mappings()
                    .get(mappingIndices.get(page * 6 + index));
                add(index, geometry.rows[index], entry.category + " -> " + providerDisplayName(entry.provider), true);
            }
        } else {
            if (!waiting) {
                int start = page * PatternUploadNetwork.PAGE_SIZE;
                int end = Math.min(start + PatternUploadNetwork.PAGE_SIZE, entries.size());
                for (int row = 0; row < end - start; row++) {
                    PatternUploadNetwork.TargetEntry entry = entries.get(start + row);
                    add(
                        row,
                        geometry.rows[row],
                        (preferences != null && preferences.isPinned(entry.routingName) ? "* " : "")
                            + StatCollector.translateToLocalFormatted(
                                key("target_format"),
                                PatternUploadNames.display(entry.terminalName, entry.terminalSuffix),
                                entry.freeSlots,
                                entry.providerCount),
                        ready && (mode != ADD_MAPPING || !entry.routingName.isEmpty()));
                }
            }
        }
        add(PREVIOUS, geometry.previous, "<", ready && page > 0);
        add(NEXT, geometry.next, ">", ready && page + 1 < pages);
        add(CLOSE, geometry.cancel, tr("cancel"), !selectionPending);
        add(RELOAD, geometry.reload, tr("reload"), !waiting);
        boolean mappingReady = PatternUploadPreferences.validText(categoryInput.getText())
            && PatternUploadPreferences.validText(mapping.getText());
        boolean canPickProvider = PatternUploadPreferences.validText(categoryInput.getText()) && hasPattern();
        add(
            ADD,
            geometry.add,
            tr(selectedMapping >= 0 ? "save_mapping" : "add_mapping"),
            !waiting && preferences != null
                && (mode == MAPPINGS ? mappingReady || canPickProvider
                    : !category.isEmpty() && (hasPattern() || PatternUploadPreferences.validText(mapping.getText()))));
        add(DELETE, geometry.delete, tr("delete_mapping"), !waiting && deletionIndex() >= 0);
        add(
            TOGGLE_BUTTON,
            geometry.showButton,
            tr("show_button") + ": " + tr(preferences == null || preferences.showButton() ? "on" : "off"),
            !waiting && preferences != null);
        add(
            TOGGLE_AUTO,
            geometry.autoUpload,
            tr("auto_upload") + ": " + tr(preferences != null && preferences.autoUpload() ? "on" : "off"),
            !waiting && preferences != null);
    }

    private void add(int id, Box box, String text, boolean enabled) {
        controls.add(new Control(id, box, text, enabled));
    }

    private static String key(String name) {
        return "tenkeitech.pattern_upload." + name;
    }

    private static String tr(String name) {
        return StatCollector.translateToLocal(key(name));
    }

    private String providerDisplayName(String rawName) {
        for (PatternUploadNetwork.TargetEntry entry : allEntries) {
            if (entry.routingName.equals(rawName) || entry.name.equals(rawName)) {
                return PatternUploadNames.display(entry.terminalName, entry.terminalSuffix);
            }
        }
        return PatternUploadNames.display(rawName, "");
    }

    private static final class Control {

        final int id;
        final Box box;
        final String text;
        final boolean enabled;

        Control(int id, Box box, String text, boolean enabled) {
            this.id = id;
            this.box = box;
            this.text = text;
            this.enabled = enabled;
        }
    }
}
