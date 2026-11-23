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

import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The settings of {@code //ruin} and {@code //overgrow}: an amount from 0 to 1
 * and a seed, as numbers in order or {@code name=value}, plus switches typed
 * after them.
 *
 * @param amount how much, 0 to 1
 * @param seed the seed (a random one if none was given)
 * @param flags the switches found among the settings
 */
public record AgeSettings(double amount, long seed, ShapeFlags flags) {

    /**
     * The settings.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("amount", 0.5, 0, 1, "0 = no change, 1 = as much as possible"),
        new ShapeParameter("seed", -1, -1, Integer.MAX_VALUE, "the same seed gives the same result (-1 = random)")
    );

    /**
     * The largest box (in blocks) the commands work on at once.
     */
    public static final long MAX_VOLUME = 8_000_000L;

    /**
     * Parse settings.
     *
     * @param arguments the words typed
     * @param switches the letters of the switches that may be typed among them
     * @return the settings
     * @throws IllegalArgumentException with a friendly message if they are wrong
     */
    public static AgeSettings parse(List<String> arguments, String switches) {
        ShapeFlags flags = ShapeFlags.parse(arguments, switches, "");
        ShapeParameters values = ShapeParameters.parse(PARAMETERS, flags.remaining());
        long seed = Math.round(values.get("seed"));
        if (seed < 0) {
            seed = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        }
        return new AgeSettings(values.get("amount"), seed, flags);
    }

    /**
     * Whether a switch was typed among the settings.
     *
     * @param name the letter
     * @return true if typed
     */
    public boolean has(char name) {
        return flags.has(name);
    }

    /**
     * The amount for messages.
     *
     * @return the amount as text
     */
    public String amountText() {
        return ShapeParameter.format(amount);
    }
}
