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

package com.sk89q.worldedit.function.generator.shape;

import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A numeric parameter of a {@link NamedShape}.
 *
 * @param name the name, used for {@code name=value} arguments
 * @param defaultValue the value used when the parameter is not given
 * @param min the smallest allowed value
 * @param max the largest allowed value
 * @param description a short description for help output
 */
public record ShapeParameter(String name, double defaultValue, double min, double max, String description) {

    public ShapeParameter {
        checkNotNull(name);
        checkNotNull(description);
        checkArgument(min <= max, "min > max");
        checkArgument(defaultValue >= min && defaultValue <= max, "default out of range for %s", name);
    }

    /**
     * Format a value of this parameter for display.
     *
     * @param value the value
     * @return the formatted value
     */
    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "");
    }
}
