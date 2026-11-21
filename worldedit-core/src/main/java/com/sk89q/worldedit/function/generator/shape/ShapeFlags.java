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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Switches and flags found among the shape settings, e.g. {@code //shape star stone 5 points=6 -h -r 45}.
 */
public final class ShapeFlags {

    private final Set<Character> switches = new HashSet<>();
    private final Map<Character, Double> values = new HashMap<>();
    private final Map<Character, String> texts = new HashMap<>();
    private final List<String> remaining = new ArrayList<>();

    private ShapeFlags() {
    }

    /**
     * Pick switches ({@code -h}) and flags with a number ({@code -r 45}) out of a list of arguments.
     *
     * @param arguments the arguments
     * @param switchNames letters of switches
     * @param valueNames letters of flags that take a number
     * @return the result
     */
    public static ShapeFlags parse(List<String> arguments, String switchNames, String valueNames) {
        return parse(arguments, switchNames, valueNames, "");
    }

    /**
     * Pick switches ({@code -h}), flags with a number ({@code -r 45}) and flags with a word
     * ({@code -m stone}) out of a list of arguments.
     *
     * @param arguments the arguments
     * @param switchNames letters of switches
     * @param valueNames letters of flags that take a number
     * @param textNames letters of flags that take a word, such as a mask
     * @return the result
     */
    public static ShapeFlags parse(List<String> arguments, String switchNames, String valueNames, String textNames) {
        ShapeFlags flags = new ShapeFlags();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.length() < 2 || argument.charAt(0) != '-' || !Character.isLetter(argument.charAt(1))) {
                flags.remaining.add(argument);
                continue;
            }
            String letters = argument.substring(1);
            for (int j = 0; j < letters.length(); j++) {
                char c = letters.charAt(j);
                if (textNames.indexOf(c) >= 0) {
                    if (j != letters.length() - 1 || i + 1 >= arguments.size()) {
                        throw new IllegalArgumentException("-" + c + " needs a value after it");
                    }
                    flags.texts.put(c, arguments.get(++i));
                } else if (valueNames.indexOf(c) >= 0) {
                    if (j != letters.length() - 1 || i + 1 >= arguments.size()) {
                        throw new IllegalArgumentException("-" + c + " needs a number after it");
                    }
                    String value = arguments.get(++i);
                    try {
                        flags.values.put(c, Double.parseDouble(value));
                    } catch (NumberFormatException _) {
                        throw new IllegalArgumentException("-" + c + " needs a number after it, not '" + value + "'");
                    }
                } else if (switchNames.indexOf(c) >= 0) {
                    flags.switches.add(c);
                } else {
                    throw new IllegalArgumentException("Unknown flag -" + c);
                }
            }
        }
        return flags;
    }

    /**
     * Whether a switch was given.
     *
     * @param name the letter
     * @return true if given
     */
    public boolean has(char name) {
        return switches.contains(name);
    }

    /**
     * The number given with a flag.
     *
     * @param name the letter
     * @param fallback the value if the flag was not given
     * @return the value
     */
    public double value(char name, double fallback) {
        return values.getOrDefault(name, fallback);
    }

    /**
     * The word given with a flag.
     *
     * @param name the letter
     * @return the value, if the flag was given
     */
    public Optional<String> text(char name) {
        return Optional.ofNullable(texts.get(name));
    }

    /**
     * The arguments that were not flags.
     *
     * @return the arguments
     */
    public List<String> remaining() {
        return remaining;
    }
}
