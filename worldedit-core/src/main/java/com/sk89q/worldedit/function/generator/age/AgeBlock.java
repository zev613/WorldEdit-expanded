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

package com.sk89q.worldedit.function.generator.age;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * A block as an id plus its state properties as plain strings, e.g.
 * {@code minecraft:vine[north=true,up=false]}. Used by the ageing generators so
 * they can run against any world, including an in-memory one in tests.
 *
 * @param id the namespaced block id
 * @param properties the state properties (name to value), sorted by name
 */
public record AgeBlock(String id, Map<String, String> properties) {

    /**
     * Plain air.
     */
    public static final AgeBlock AIR = new AgeBlock("minecraft:air", Map.of());

    /**
     * Create a block.
     */
    public AgeBlock {
        id = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /**
     * A block without properties.
     *
     * @param id the id, with or without the {@code minecraft:} namespace
     * @return the block
     */
    public static AgeBlock of(String id) {
        return new AgeBlock(id, Map.of());
    }

    /**
     * Parse {@code id[name=value,...]}.
     *
     * @param text the text
     * @return the block
     */
    public static AgeBlock parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        int open = trimmed.indexOf('[');
        if (open < 0) {
            return of(trimmed);
        }
        Map<String, String> props = new TreeMap<>();
        String inner = trimmed.substring(open + 1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
        for (String pair : inner.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return new AgeBlock(trimmed.substring(0, open), props);
    }

    /**
     * The id without namespace.
     *
     * @return the path, e.g. {@code stone_bricks}
     */
    public String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /**
     * A property value.
     *
     * @param name the property
     * @return the value or null
     */
    public String get(String name) {
        return properties.get(name);
    }

    /**
     * This block with one property set.
     *
     * @param name the property
     * @param value the value
     * @return the new block
     */
    public AgeBlock with(String name, String value) {
        Map<String, String> props = new TreeMap<>(properties);
        props.put(name, value);
        return new AgeBlock(id, props);
    }

    /**
     * Another block type keeping this block's properties (facing, half, axis...).
     * Properties the new type does not have are ignored when it is placed.
     *
     * @param newId the new id
     * @return the new block
     */
    public AgeBlock withId(String newId) {
        return new AgeBlock(newId, properties);
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return id;
        }
        StringJoiner joiner = new StringJoiner(",", id + "[", "]");
        properties.forEach((k, v) -> joiner.add(k + "=" + v));
        return joiner.toString();
    }
}
