/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.patternupload;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Client-owned, per-player/per-world routing preferences. Never contains items or server authority. */
public final class PatternUploadPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final int MAX_FILE_BYTES = 262_144;
    private static final int MAX_MAPPINGS = 256;
    private static Path directory;
    private static String activeScope;
    private static PatternUploadPreferences active;
    private final Path file;
    private Data data = new Data();
    private boolean writable = true;

    PatternUploadPreferences(Path file) {
        this.file = file;
    }

    public static void configure(File configDirectory) {
        directory = new File(configDirectory, "tenkeitech-pattern-upload").toPath();
    }

    public static PatternUploadPreferences current() throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        if (directory == null || mc.thePlayer == null) throw new IOException("No active upload profile");
        String world = mc.func_147104_D() != null ? "server:" + mc.func_147104_D().serverIP
            : mc.getIntegratedServer() != null ? "local:" + mc.getIntegratedServer()
                .getFolderName() : null;
        if (world == null) throw new IOException("No active world");
        String scope = world + "\n" + mc.thePlayer.getUniqueID();
        if (!scope.equals(activeScope)) {
            PatternUploadPreferences next = new PatternUploadPreferences(directory.resolve(hash(scope) + ".json"));
            next.reload();
            active = next;
            activeScope = scope;
        }
        return active;
    }

    public boolean showButton() {
        return data.showButton;
    }

    public boolean autoUpload() {
        return data.autoUpload;
    }

    public List<Mapping> mappings() {
        return Collections.unmodifiableList(data.mappings);
    }

    public List<String> pinnedProviders() {
        return Collections.unmodifiableList(data.pinnedProviders);
    }

    public boolean isPinned(String provider) {
        return data.pinnedProviders.contains(provider);
    }

    public List<String> targets(String category) {
        List<String> result = new ArrayList<>();
        for (Mapping mapping : data.mappings) {
            if (mapping.category.equals(category)) result.add(mapping.provider);
        }
        return result;
    }

    public void toggleButton() throws IOException {
        Data next = copy();
        next.showButton = !next.showButton;
        save(next);
    }

    public void toggleAutoUpload() throws IOException {
        Data next = copy();
        next.autoUpload = !next.autoUpload;
        save(next);
    }

    public void add(String category, String label, String provider) throws IOException {
        saveMapping(-1, category, label, provider);
    }

    public void saveMapping(int index, String category, String label, String provider) throws IOException {
        if (index < -1 || index >= data.mappings.size()) throw new IOException("Invalid mapping index");
        Data next = copy();
        if (index >= 0) next.mappings.remove(index);
        for (Mapping mapping : next.mappings) {
            if (mapping.category.equals(category) && mapping.provider.equals(provider)) {
                if (index >= 0) throw new IOException("Duplicate mapping");
                return;
            }
        }
        next.mappings.add(new Mapping(category, label, provider));
        save(next);
    }

    public boolean togglePinned(String provider) throws IOException {
        if (!validText(provider)) throw new IOException("Invalid pinned provider");
        Data next = copy();
        boolean pinned;
        if (next.pinnedProviders.remove(provider)) {
            pinned = false;
        } else {
            next.pinnedProviders.add(provider);
            pinned = true;
        }
        save(next);
        return pinned;
    }

    public void remove(int index) throws IOException {
        if (index < 0 || index >= data.mappings.size()) return;
        Data next = copy();
        next.mappings.remove(index);
        save(next);
    }

    public void reload() throws IOException {
        writable = false;
        if (!Files.exists(file)) {
            data = new Data();
            writable = true;
            return;
        }
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Upload profile too large");
        try {
            JsonObject json = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!json.has("schema") || !"1".equals(
                json.get("schema")
                    .getAsString()))
                throw new IOException("Unsupported upload profile schema");
            Data next = GSON.fromJson(json, Data.class);
            if (next != null && next.pinnedProviders == null) next.pinnedProviders = new ArrayList<>();
            validate(next);
            data = next;
            writable = true;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid upload profile", exception);
        }
    }

    private Data copy() {
        Data next = new Data();
        next.showButton = data.showButton;
        next.autoUpload = data.autoUpload;
        next.mappings.addAll(data.mappings);
        next.pinnedProviders.addAll(data.pinnedProviders);
        return next;
    }

    private void save(Data next) throws IOException {
        if (!writable) throw new IOException("Reload a valid profile before saving changes");
        validate(next);
        byte[] bytes = GSON.toJson(next)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Upload profile too large");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "upload-", ".tmp");
        try {
            Files.write(temporary, bytes);
            // Refuse unsupported atomic replacement rather than risk destroying a previous valid profile.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validate(Data candidate) throws IOException {
        if (candidate == null || candidate.schema != 1
            || candidate.mappings == null
            || candidate.pinnedProviders == null
            || candidate.mappings.size() > MAX_MAPPINGS) throw new IOException("Unsupported upload profile");
        Set<String> pairs = new HashSet<>();
        for (Mapping mapping : candidate.mappings) {
            if (mapping == null || !validText(mapping.category)
                || !validText(mapping.label)
                || !validText(mapping.provider)
                || !pairs.add(mapping.category + "\n" + mapping.provider)) throw new IOException("Invalid mapping");
            int count = 0;
            for (Mapping other : candidate.mappings) {
                if (other != null && mapping.category.equals(other.category)) count++;
            }
            if (count > PatternUploadNetwork.MAX_PREFERRED_NAMES) throw new IOException("Too many category targets");
        }
        candidate.mappings.sort(
            Comparator.comparing((Mapping mapping) -> mapping.category)
                .thenComparing(mapping -> mapping.provider));
        if (candidate.pinnedProviders.size() > PatternUploadNetwork.MAX_PINNED_PROVIDERS)
            throw new IOException("Too many pinned providers");
        Set<String> pinned = new HashSet<>();
        for (String provider : candidate.pinnedProviders) {
            if (!validText(provider) || !pinned.add(provider)) throw new IOException("Invalid pinned provider");
        }
        Collections.sort(candidate.pinnedProviders);
    }

    public static boolean validText(String value) {
        return value != null && !value.trim()
            .isEmpty()
            && value.length() <= PatternUploadNetwork.MAX_ROUTING_TEXT
            && value.indexOf('\n') < 0
            && value.indexOf('\r') < 0
            && value.indexOf('\0') < 0;
    }

    private static String hash(String scope) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(scope.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class Data {

        private int schema = 1;
        private boolean showButton = true;
        private boolean autoUpload = true;
        private List<Mapping> mappings = new ArrayList<>();
        private List<String> pinnedProviders = new ArrayList<>();
    }

    public static final class Mapping {

        public final String category;
        public final String label;
        public final String provider;

        private Mapping(String category, String label, String provider) {
            this.category = category;
            this.label = label;
            this.provider = provider;
        }
    }
}
