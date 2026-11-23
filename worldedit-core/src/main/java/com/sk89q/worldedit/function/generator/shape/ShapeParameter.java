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

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * @param integer whether only whole numbers are allowed (counts such as points or sides)
 * @param choices names that may be typed instead of the numbers 0, 1, 2, ..., by number; several
 *     names for one number are joined with {@code |} (empty when the setting has no names)
 */
public record ShapeParameter(String name, double defaultValue, double min, double max, String description,
                             boolean integer, List<String> choices) {

    /**
     * Names of settings that count things. When such a setting's default and range are whole
     * numbers, it only takes whole numbers.
     */
    private static final Set<String> COUNT_NAMES = Set.of("points", "count", "sides", "level", "loops", "winds",
        "arms", "coils", "floors", "rungs", "spokes", "strands", "twists", "windows", "setbacks", "seed",
        "detail", "story");

    private static final Splitter CHOICE_SPLITTER = Splitter.on('|');

    public ShapeParameter {
        checkNotNull(name);
        checkNotNull(description);
        choices = List.copyOf(choices);
        checkArgument(min <= max, "min > max");
        checkArgument(defaultValue >= min && defaultValue <= max, "default out of range for %s", name);
    }

    /**
     * Create a parameter. Settings named like a count ({@code points}, {@code sides}, ...) with a
     * whole-number default and range only take whole numbers.
     *
     * @param name the name
     * @param defaultValue the default
     * @param min the smallest allowed value
     * @param max the largest allowed value
     * @param description a short description
     */
    public ShapeParameter(String name, double defaultValue, double min, double max, String description) {
        this(name, defaultValue, min, max, description,
            COUNT_NAMES.contains(name) && isWhole(defaultValue) && isWhole(min) && isWhole(max), List.of());
    }

    private static boolean isWhole(double value) {
        return value == Math.rint(value);
    }

    /**
     * Get a copy that only takes whole numbers.
     *
     * @return the copy
     */
    public ShapeParameter whole() {
        return new ShapeParameter(name, defaultValue, min, max, description, true, choices);
    }

    /**
     * Get a copy where the numbers can also be typed as names.
     *
     * @param names the names, by number (several names for one number joined with {@code |})
     * @return the copy
     */
    public ShapeParameter withChoices(List<String> names) {
        return new ShapeParameter(name, defaultValue, min, max, description, true, names);
    }

    /**
     * Get a copy with another default.
     *
     * @param value the new default
     * @return the copy
     */
    public ShapeParameter withDefault(double value) {
        return new ShapeParameter(name, value, min, max, description, integer, choices);
    }

    /**
     * Find the number of a choice name.
     *
     * @param word the word typed
     * @return the number, or -1 if it is not one of the names
     */
    public int choiceNumber(String word) {
        String lower = word.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < choices.size(); i++) {
            for (String choice : CHOICE_SPLITTER.split(choices.get(i))) {
                if (choice.equals(lower)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The message for a word that is not one of the choices, e.g.
     * "Unknown type 'square'. Choose round, flat or bell.".
     *
     * @param word the word typed
     * @return the message
     */
    public String unknownChoice(String word) {
        return unknownChoice(name, choices, word);
    }

    /**
     * The message for a word that is not one of a setting's names, e.g.
     * "Unknown type 'square'. Choose round, flat or bell.".
     *
     * @param setting the setting's name
     * @param names the names, by number (several names for one number joined with {@code |})
     * @param word the word typed
     * @return the message
     */
    public static String unknownChoice(String setting, List<String> names, String word) {
        List<String> first = new ArrayList<>();
        for (String choice : names) {
            first.add(CHOICE_SPLITTER.split(choice).iterator().next());
        }
        String list = first.size() <= 1 ? String.join("", first)
            : String.join(", ", first.subList(0, first.size() - 1)) + " or " + first.getLast();
        return "Unknown " + setting + " '" + word + "'. Choose " + list + ".";
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
