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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The jobs a block can have in a building. A {@link BuildingStyle} picks a block (or pattern) for each.
 */
public enum StyleRole {
    WALL("the main walls"),
    WINDOW("windows (usually glass)"),
    TRIM("bands, sills, parapets and cornices"),
    PILLAR("corner pillars, piers and columns"),
    FLOOR("floors and stairs"),
    ROOF("roofs, terraces and spires"),
    ACCENT("details: door frames, canopies, spandrels"),
    LIGHT("lights");

    private final String description;

    StyleRole(String description) {
        this.description = description;
    }

    /**
     * The name typed by players, such as {@code window}.
     *
     * @return the lower-case name
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * What the role is used for.
     *
     * @return a short description
     */
    public String description() {
        return description;
    }

    /**
     * Find a role by name, ignoring case. {@code glass} and {@code windows} mean {@link #WINDOW},
     * {@code lights}/{@code lamp} mean {@link #LIGHT}, {@code walls} means {@link #WALL} and so on.
     *
     * @param name the name
     * @return the role, if the name is one
     */
    public static Optional<StyleRole> find(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith("s") && key.length() > 1 && !key.equals("glass")) {
            key = key.substring(0, key.length() - 1);
        }
        return switch (key) {
            case "wall" -> Optional.of(WALL);
            case "window", "glass" -> Optional.of(WINDOW);
            case "trim" -> Optional.of(TRIM);
            case "pillar", "column" -> Optional.of(PILLAR);
            case "floor" -> Optional.of(FLOOR);
            case "roof" -> Optional.of(ROOF);
            case "accent", "detail" -> Optional.of(ACCENT);
            case "light", "lamp" -> Optional.of(LIGHT);
            default -> Optional.empty();
        };
    }

    /**
     * All role names, for messages.
     *
     * @return the names joined with commas
     */
    public static String names() {
        return Arrays.stream(values()).map(StyleRole::id).collect(Collectors.joining(", "));
    }
}
