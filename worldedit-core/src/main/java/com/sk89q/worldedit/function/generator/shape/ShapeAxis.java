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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lets an {@code axis} setting be typed as a word ({@code x}, {@code y}, {@code z} or {@code auto})
 * instead of a number.
 *
 * <p>Internally the axis is stored as a number: {@link #AUTO}, {@link #X}, {@link #Y} or {@link #Z}.</p>
 */
public final class ShapeAxis {

    /**
     * The name of axis settings.
     */
    public static final String NAME = "axis";
    public static final int AUTO = 0;
    public static final int X = 1;
    public static final int Y = 2;
    public static final int Z = 3;

    private ShapeAxis() {
    }

    /**
     * Create an axis setting.
     *
     * @param defaultAxis the default, one of the constants in this class
     * @param description the description
     * @return the parameter
     */
    public static ShapeParameter parameter(int defaultAxis, String description) {
        return new ShapeParameter(NAME, defaultAxis, AUTO, Z, description)
            .withChoices(List.of("auto", "x", "y", "z"));
    }

    /**
     * Rewrite axis words among the arguments into numbers that {@link ShapeParameters#parse} understands.
     *
     * <p>A bare {@code x}, {@code y}, {@code z} or {@code auto} becomes {@code axis=<number>}, and so
     * does {@code axis=x}. Arguments are left alone when the parameters have no axis setting.</p>
     *
     * @param parameters the parameters
     * @param arguments the user's arguments
     * @return the rewritten arguments
     */
    public static List<String> normalize(List<ShapeParameter> parameters, List<String> arguments) {
        boolean hasAxis = parameters.stream().anyMatch(p -> p.name().equals(NAME));
        if (!hasAxis) {
            return arguments;
        }
        List<String> result = new ArrayList<>(arguments.size());
        for (String argument : ShapeParameters.joinSpacedEquals(arguments)) {
            String lower = argument.trim().toLowerCase(Locale.ROOT);
            String word = lower.startsWith(NAME + "=") ? lower.substring(NAME.length() + 1).trim() : lower;
            int axis = fromWord(word);
            result.add(axis >= 0 ? NAME + "=" + axis : argument);
        }
        return result;
    }

    /**
     * Whether a word names an axis.
     *
     * @param word the word
     * @return true for x, y, z and auto
     */
    public static boolean isWord(String word) {
        return fromWord(word.trim().toLowerCase(Locale.ROOT)) >= 0;
    }

    private static int fromWord(String word) {
        return switch (word) {
            case "auto" -> AUTO;
            case "x" -> X;
            case "y" -> Y;
            case "z" -> Z;
            default -> -1;
        };
    }

    /**
     * The name of an axis value, for display.
     *
     * @param axis the axis number
     * @return auto, x, y or z
     */
    public static String name(double axis) {
        return switch ((int) Math.round(axis)) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            default -> "auto";
        };
    }

    /**
     * Format the value of a parameter for display, showing axis settings as words.
     *
     * @param parameter the parameter
     * @param value the value
     * @return the text
     */
    public static String format(ShapeParameter parameter, double value) {
        return parameter.name().equals(NAME) ? name(value) : ShapeParameter.format(value);
    }
}
