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

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A named set of blocks for the {@link StyleRole roles} in a building, such as walls, windows and trim.
 *
 * <p>The blocks are kept as the text a player would type (a block or any pattern, e.g.
 * {@code 80%bricks,20%granite}); turning them into patterns is up to the caller. The {@code family}
 * is the built-in style this one derives from, so generators can keep that style's look (window
 * patterns, crown...) while using the player's blocks.</p>
 *
 * @param name the style's name
 * @param family the name of the built-in style it is based on (its own name for built-in styles)
 * @param description a short description
 * @param blocks the block (pattern text) for every role
 */
public record BuildingStyle(String name, String family, String description, Map<StyleRole, String> blocks) {

    public BuildingStyle {
        checkNotNull(name);
        checkNotNull(family);
        checkNotNull(description);
        EnumMap<StyleRole, String> copy = new EnumMap<>(StyleRole.class);
        copy.putAll(blocks);
        for (StyleRole role : StyleRole.values()) {
            checkArgument(copy.containsKey(role), "style %s has no block for %s", name, role.id());
            checkArgument(!copy.get(role).isBlank(), "style %s has an empty block for %s", name, role.id());
        }
        blocks = ImmutableMap.copyOf(copy);
    }

    /**
     * The block (pattern text) for a role.
     *
     * @param role the role
     * @return the block
     */
    public String block(StyleRole role) {
        return blocks.get(role);
    }

    /**
     * A copy of this style with some roles changed.
     *
     * @param newName the name of the copy
     * @param overrides the roles to change
     * @return the new style
     */
    public BuildingStyle withOverrides(String newName, Map<StyleRole, String> overrides) {
        EnumMap<StyleRole, String> merged = new EnumMap<>(blocks);
        merged.putAll(overrides);
        return new BuildingStyle(newName, family, description, merged);
    }

    /**
     * A copy of this style with some roles changed, keeping the name.
     *
     * @param overrides the roles to change
     * @return the new style
     */
    public BuildingStyle withOverrides(Map<StyleRole, String> overrides) {
        return withOverrides(name, overrides);
    }

    /**
     * The roles whose block differs from another style, as {@code role=block} words.
     *
     * @param base the style to compare with
     * @return the differences, in role order
     */
    public List<String> differencesFrom(BuildingStyle base) {
        List<String> words = new ArrayList<>();
        for (StyleRole role : StyleRole.values()) {
            if (!block(role).equals(base.block(role))) {
                words.add(role.id() + "=" + block(role));
            }
        }
        return words;
    }

    /**
     * Role overrides picked out of a list of settings, and the settings that are left.
     *
     * @param overrides the {@code role=block} settings
     * @param remaining all other settings, in order
     */
    public record Overrides(Map<StyleRole, String> overrides, List<String> remaining) {

        public Overrides {
            overrides = ImmutableMap.copyOf(overrides);
            remaining = List.copyOf(remaining);
        }
    }

    /**
     * Pick {@code role=block} words (e.g. {@code window=cyan_stained_glass}) out of a list of settings.
     * Words whose part before {@code =} is not a role name are left alone, so number settings such as
     * {@code width=20} can be mixed in.
     *
     * @param arguments the settings
     * @return the overrides and the other settings
     * @throws IllegalArgumentException if a role is given without a block
     */
    public static Overrides splitOverrides(List<String> arguments) {
        Map<StyleRole, String> overrides = new LinkedHashMap<>();
        List<String> remaining = new ArrayList<>();
        for (String argument : arguments) {
            int eq = argument.indexOf('=');
            StyleRole role = eq > 0 ? StyleRole.find(argument.substring(0, eq)).orElse(null) : null;
            if (role == null) {
                remaining.add(argument);
                continue;
            }
            String value = argument.substring(eq + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Give a block for " + role.id() + ", e.g. "
                    + role.id() + "=stone_bricks");
            }
            overrides.put(role, value);
        }
        return new Overrides(overrides, remaining);
    }
}
