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

package com.sk89q.worldedit.function.generator.style;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * All building styles: the built-in ones plus styles saved by players.
 *
 * <p>Saved styles are stored one per line in a plain text file as
 * {@code name = base role=block role=block ...}, where {@code base} is a built-in style.</p>
 */
public final class StyleLibrary {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final Map<String, BuildingStyle> builtins = new LinkedHashMap<>();
    private final Map<String, BuildingStyle> saved = new LinkedHashMap<>();
    private final Path file;

    /**
     * Create a library.
     *
     * @param file where saved styles are kept, or null to keep them in memory only
     */
    public StyleLibrary(Path file) {
        this.file = file;
        for (BuildingStyle style : BuiltinStyles.create()) {
            builtins.put(style.name(), style);
        }
        load();
    }

    /**
     * Whether a name can be used for a saved style.
     *
     * @param name the name
     * @return true if valid
     */
    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * Find a style by name, ignoring case ({@code art-deco} finds {@code art_deco}).
     *
     * @param name the name
     * @return the style, if any
     */
    public synchronized Optional<BuildingStyle> find(String name) {
        String key = key(name);
        BuildingStyle style = builtins.get(key);
        if (style == null) {
            style = saved.get(key);
        }
        if (style == null) {
            style = saved.get(name.trim().toLowerCase(Locale.ROOT));
        }
        return Optional.ofNullable(style);
    }

    /**
     * Whether a style is built in.
     *
     * @param name the name
     * @return true for built-in styles
     */
    public synchronized boolean isBuiltin(String name) {
        return builtins.containsKey(key(name));
    }

    /**
     * The built-in styles.
     *
     * @return the styles, in display order
     */
    public synchronized List<BuildingStyle> getBuiltins() {
        return ImmutableList.copyOf(builtins.values());
    }

    /**
     * The saved styles, sorted by name.
     *
     * @return the styles
     */
    public synchronized List<BuildingStyle> getSaved() {
        return saved.values().stream().sorted(Comparator.comparing(BuildingStyle::name))
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * All style names, for tab completion.
     *
     * @return the names
     */
    public synchronized List<String> getAllNames() {
        List<String> names = new ArrayList<>(builtins.keySet());
        names.addAll(saved.keySet());
        names.sort(null);
        return names;
    }

    /**
     * Save a style.
     *
     * @param name the new style's name
     * @param baseName the style it starts from (built-in or saved)
     * @param overrides the roles to change
     * @return the saved style
     * @throws IllegalArgumentException if the name or base is invalid
     */
    public synchronized BuildingStyle save(String name, String baseName, Map<StyleRole, String> overrides) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!isValidName(key)) {
            throw new IllegalArgumentException("Style names can use letters, digits, _ and -, up to 32 characters");
        }
        if (builtins.containsKey(key(key))) {
            throw new IllegalArgumentException("'" + key + "' is a built-in style; pick another name");
        }
        BuildingStyle base = find(baseName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown style '" + baseName
                + "'. Use //style list to see them."));
        for (String block : overrides.values()) {
            if (block.isBlank() || block.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Blocks in a style can't contain spaces");
            }
        }
        BuildingStyle created = base.withOverrides(key, overrides);
        saved.put(key, created);
        save();
        return created;
    }

    /**
     * Delete a saved style.
     *
     * @param name the name
     * @return true if it existed
     */
    public synchronized boolean delete(String name) {
        boolean removed = saved.remove(name.trim().toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String[] parts = trimmed.substring(eq + 1).trim().split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty() || !isValidName(name) || builtins.containsKey(key(name))) {
                    continue;
                }
                BuildingStyle base = builtins.get(key(parts[0]));
                if (base == null) {
                    LOGGER.warn("Ignoring building style {}: unknown base style {}", name, parts[0]);
                    continue;
                }
                try {
                    BuildingStyle.Overrides overrides =
                        BuildingStyle.splitOverrides(Arrays.asList(parts).subList(1, parts.length));
                    saved.put(name, base.withOverrides(name, overrides.overrides()));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Ignoring building style {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read building styles from " + file, e);
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Building styles for //skyscraper. Each line is: name = base_style role=block ...");
        lines.add("# Roles: " + StyleRole.names() + ". Manage them in game with //style save|delete|list|info");
        for (BuildingStyle style : getSaved()) {
            BuildingStyle base = builtins.get(style.family());
            lines.add(style.name() + " = " + style.family() + " " + String.join(" ", style.differencesFrom(base)));
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save building styles to " + file, e);
        }
    }
}
