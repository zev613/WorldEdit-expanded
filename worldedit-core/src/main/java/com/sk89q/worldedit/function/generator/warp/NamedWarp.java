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

package com.sk89q.worldedit.function.generator.warp;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;

import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A deformation of the selection that is used by name, such as {@code twist} or {@code bend}.
 */
public final class NamedWarp {

    /**
     * Builds the map of a warp for one use.
     */
    @FunctionalInterface
    public interface Factory {

        /**
         * Create the map.
         *
         * @param parameters the setting values
         * @param bounds the box being warped
         * @return the map
         * @throws IllegalArgumentException if the settings do not make sense together
         */
        WarpMap create(ShapeParameters parameters, WarpBounds bounds);
    }

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final List<ShapeParameter> parameters;
    private final Factory factory;

    NamedWarp(String name, List<String> aliases, String description, List<ShapeParameter> parameters, Factory factory) {
        this.name = checkNotNull(name).toLowerCase(Locale.ROOT);
        this.aliases = aliases.stream().map(a -> a.toLowerCase(Locale.ROOT)).collect(ImmutableList.toImmutableList());
        this.description = checkNotNull(description);
        this.parameters = ImmutableList.copyOf(parameters);
        this.factory = checkNotNull(factory);
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getDescription() {
        return description;
    }

    public List<ShapeParameter> getParameters() {
        return parameters;
    }

    /**
     * Read the user's settings for this warp. Axis settings may be typed as x, y, z or auto.
     *
     * @param arguments numbers in order, or name=value
     * @return the values
     * @throws IllegalArgumentException if a setting is wrong
     */
    public ShapeParameters parseSettings(List<String> arguments) {
        return ShapeParameters.parse(parameters, ShapeAxis.normalize(parameters, arguments));
    }

    /**
     * Create the map of this warp.
     *
     * @param values the setting values
     * @param bounds the box being warped
     * @return the map
     * @throws IllegalArgumentException if the settings do not make sense together
     */
    public WarpMap create(ShapeParameters values, WarpBounds bounds) {
        return factory.create(values, bounds);
    }
}
