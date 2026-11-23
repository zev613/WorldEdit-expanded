/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.extension.factory.parser.saved;

import com.sk89q.worldedit.internal.util.LogManagerCompat;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A server-wide list of named inputs (pattern or mask strings), kept in a text
 * file with one {@code name = value} line per entry. The file is read the first
 * time it is needed.
 */
public final class SavedInputStore {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /**
     * The longest value that can be saved.
     */
    public static final int MAX_VALUE_LENGTH = 1000;

    private final @Nullable Path file;
    private final String kind;
    private final TreeMap<String, String> entries = new TreeMap<>();
    private boolean loaded;

    /**
     * Create a store.
     *
     * @param file the file, or {@code null} to keep entries in memory only
     * @param kind what is stored, e.g. "pattern", used in messages and the file header
     */
    public SavedInputStore(@Nullable Path file, String kind) {
        this.file = file;
        this.kind = kind;
    }

    /**
     * Whether a name can be used: 1 to 32 letters, digits, {@code _} or {@code -}.
     *
     * @param name the name, without {@code #}
     * @return true if valid
     */
    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    /**
     * Normalise a name for lookups.
     *
     * @param name the name, with or without a leading {@code #}
     * @return the lower case name without {@code #}
     */
    public static String normalize(String name) {
        String trimmed = name.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public String getKind() {
        return kind;
    }

    /**
     * Get a saved value.
     *
     * @param name the name
     * @return the value, if saved
     */
    public synchronized Optional<String> get(String name) {
        ensureLoaded();
        return Optional.ofNullable(entries.get(normalize(name)));
    }

    /**
     * Get all entries, sorted by name.
     *
     * @return a copy of the entries
     */
    public synchronized SortedMap<String, String> getAll() {
        ensureLoaded();
        return new TreeMap<>(entries);
    }

    /**
     * Get the names that start with a prefix.
     *
     * @param prefix the prefix, without {@code #}
     * @return the matching names, sorted
     */
    public synchronized List<String> namesStartingWith(String prefix) {
        ensureLoaded();
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String name : entries.tailMap(lower).keySet()) {
            if (!name.startsWith(lower)) {
                break;
            }
            result.add(name);
        }
        return result;
    }

    /**
     * Save a value, replacing any value with the same name, and write the file.
     *
     * @param name the name
     * @param value the value
     * @return the previous value, or {@code null}
     * @throws IllegalArgumentException if the name or value is invalid
     */
    public synchronized @Nullable String put(String name, String value) {
        String key = normalize(name);
        if (!isValidName(key)) {
            throw new IllegalArgumentException("Names can only use letters, digits, _ and - (up to 32 characters).");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_VALUE_LENGTH || trimmed.indexOf('\n') >= 0
            || trimmed.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("The " + kind + " must be one line of at most " + MAX_VALUE_LENGTH
                + " characters.");
        }
        ensureLoaded();
        String previous = entries.put(key, trimmed);
        save();
        return previous;
    }

    /**
     * Delete a saved value and write the file.
     *
     * @param name the name
     * @return true if it existed
     */
    public synchronized boolean remove(String name) {
        ensureLoaded();
        boolean removed = entries.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Map.Entry<String, String> entry = parseLine(line);
                if (entry != null) {
                    entries.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read saved " + kind + "s from " + file, e);
        }
    }

    /**
     * Parse one line of the file.
     *
     * @param line the line
     * @return the name and value, or {@code null} for comments and invalid lines
     */
    static Map.@Nullable Entry<String, String> parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String name = normalize(trimmed.substring(0, eq));
        String value = trimmed.substring(eq + 1).trim();
        if (!isValidName(name) || value.isEmpty()) {
            return null;
        }
        return Map.entry(name, value);
    }

    private void save() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Saved " + kind + "s, used as #name. Each line is: name = " + kind);
        lines.add("# Manage them in game with //" + kind + " save|delete|list|info");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            lines.add(entry.getKey() + " = " + entry.getValue());
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save " + kind + "s to " + file, e);
        }
    }
}
