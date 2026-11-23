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

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolved parameter values for one use of a {@link NamedShape}.
 */
public final class ShapeParameters {

    private final Map<String, Double> values;

    private ShapeParameters(Map<String, Double> values) {
        this.values = ImmutableMap.copyOf(values);
    }

    /**
     * Resolve user arguments against a list of parameters.
     *
     * <p>Arguments are either plain numbers, which fill the parameters in order,
     * or {@code name=value} pairs. Plain numbers skip parameters already given by name. Parameters that are not
     * given take their default. A setting with names (such as a type or an axis) also takes one of its names,
     * and {@code name = value} typed with spaces is read as {@code name=value}.</p>
     *
     * @param parameters the parameters of the shape
     * @param arguments the user's arguments
     * @return the resolved values
     * @throws IllegalArgumentException if an argument is malformed, unknown, given twice or out of range
     */
    public static ShapeParameters parse(List<ShapeParameter> parameters, List<String> arguments) {
        Map<String, Double> values = new LinkedHashMap<>();
        int positional = 0;
        for (String arg : joinSpacedEquals(arguments)) {
            ShapeParameter target;
            String valueText;
            int eq = arg.indexOf('=');
            boolean named = eq >= 0;
            if (named) {
                String name = arg.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                valueText = arg.substring(eq + 1).trim();
                target = parameters.stream().filter(p -> p.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parameter '" + name + "'. "
                        + (parameters.isEmpty() ? "This shape has no parameters."
                            : "Valid parameters: " + names(parameters))));
                if (valueText.isEmpty()) {
                    throw new IllegalArgumentException(name + "= needs a value after it, e.g. "
                        + name + "=" + ShapeParameter.format(target.defaultValue()));
                }
            } else {
                // plain numbers fill the settings in order, skipping ones already given by name
                while (positional < parameters.size() && values.containsKey(parameters.get(positional).name())) {
                    positional++;
                }
                if (positional >= parameters.size()) {
                    if (!isNumber(arg)) {
                        ShapeParameter choice = firstWithChoices(parameters);
                        throw new IllegalArgumentException(choice != null ? choice.unknownChoice(arg)
                            : "'" + arg + "' is not a number or a setting.");
                    }
                    throw new IllegalArgumentException("Too many parameters. "
                        + (parameters.isEmpty() ? "This shape has no parameters."
                            : "Valid parameters, in order: " + names(parameters)));
                }
                target = parameters.get(positional++);
                valueText = arg;
            }
            if (values.containsKey(target.name())) {
                throw new IllegalArgumentException(target.name() + " was given twice");
            }
            values.put(target.name(), value(parameters, target, valueText, named));
        }
        for (ShapeParameter parameter : parameters) {
            values.putIfAbsent(parameter.name(), parameter.defaultValue());
        }
        return new ShapeParameters(values);
    }

    private static double value(List<ShapeParameter> parameters, ShapeParameter target, String valueText,
                                boolean named) {
        if (!target.choices().isEmpty()) {
            int number = target.choiceNumber(valueText);
            if (number >= 0) {
                return number;
            }
        }
        double value;
        try {
            value = Double.parseDouble(valueText);
        } catch (NumberFormatException _) {
            if (!target.choices().isEmpty()) {
                throw new IllegalArgumentException(target.unknownChoice(valueText));
            }
            ShapeParameter choice = named ? null : firstWithChoices(parameters);
            if (choice != null) {
                // a word where a number was expected is most likely a misspelt name
                throw new IllegalArgumentException(choice.unknownChoice(valueText));
            }
            throw new IllegalArgumentException("'" + valueText + "' is not a number (for " + target.name() + ")");
        }
        if (!Double.isFinite(value) || value < target.min() || value > target.max()) {
            if (!target.choices().isEmpty()) {
                throw new IllegalArgumentException(target.unknownChoice(valueText));
            }
            throw new IllegalArgumentException(target.name() + " must be between "
                + ShapeParameter.format(target.min()) + " and " + ShapeParameter.format(target.max()));
        }
        if (target.integer() && value != Math.rint(value)) {
            throw new IllegalArgumentException(target.name() + " must be a whole number");
        }
        return value;
    }

    private static ShapeParameter firstWithChoices(List<ShapeParameter> parameters) {
        return parameters.stream().filter(p -> !p.choices().isEmpty()).findFirst().orElse(null);
    }

    private static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    /**
     * Join settings typed with spaces around the {@code =}: {@code ring = 0.3}, {@code ring =0.3} and
     * {@code ring= 0.3} all become {@code ring=0.3}. Empty arguments are dropped.
     *
     * @param arguments the arguments
     * @return the joined arguments
     */
    public static List<String> joinSpacedEquals(List<String> arguments) {
        List<String> result = new ArrayList<>(arguments.size());
        boolean open = false;
        for (String raw : arguments) {
            String arg = raw == null ? "" : raw.trim();
            if (arg.isEmpty()) {
                continue;
            }
            if (!result.isEmpty() && arg.startsWith("=") && !result.getLast().contains("=")
                && isWord(result.getLast())) {
                // "ring" then "=..." : join
                result.set(result.size() - 1, result.getLast() + arg);
                open = arg.length() == 1;
                continue;
            }
            if (open) {
                // "ring=" (or "ring" "=") then the value
                result.set(result.size() - 1, result.getLast() + arg);
                open = false;
                continue;
            }
            result.add(arg);
            open = arg.indexOf('=') == arg.length() - 1 && isWord(arg.substring(0, arg.length() - 1));
        }
        return result;
    }

    private static boolean isWord(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> Character.isLetter(c) || c == '_');
    }

    /**
     * The default values of the given parameters.
     *
     * @param parameters the parameters
     * @return the defaults
     */
    public static ShapeParameters defaults(List<ShapeParameter> parameters) {
        return parse(parameters, List.of());
    }

    private static String names(List<ShapeParameter> parameters) {
        return parameters.stream().map(ShapeParameter::name).collect(Collectors.joining(", "));
    }

    /**
     * Get a value.
     *
     * @param name the parameter name
     * @return the value
     */
    public double get(String name) {
        Double value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No parameter " + name);
        }
        return value;
    }

    /**
     * Get a value rounded to an integer.
     *
     * @param name the parameter name
     * @return the value
     */
    public int getInt(String name) {
        return (int) Math.round(get(name));
    }

    /**
     * Get all values, in parameter order.
     *
     * @return the values
     */
    public Map<String, Double> asMap() {
        return values;
    }
}
