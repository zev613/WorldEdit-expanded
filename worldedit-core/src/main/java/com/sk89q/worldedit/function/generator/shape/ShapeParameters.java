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
     * or {@code name=value} pairs. Plain numbers skip parameters already given by name. Parameters that are not given take their default.</p>
     *
     * @param parameters the parameters of the shape
     * @param arguments the user's arguments
     * @return the resolved values
     * @throws IllegalArgumentException if an argument is malformed, unknown or out of range
     */
    public static ShapeParameters parse(List<ShapeParameter> parameters, List<String> arguments) {
        Map<String, Double> values = new LinkedHashMap<>();
        int positional = 0;
        for (String raw : arguments) {
            String arg = raw.trim();
            if (arg.isEmpty()) {
                continue;
            }
            ShapeParameter target;
            String valueText;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                String name = arg.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                valueText = arg.substring(eq + 1).trim();
                target = parameters.stream().filter(p -> p.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parameter '" + name + "'. "
                        + (parameters.isEmpty() ? "This shape has no parameters."
                            : "Valid parameters: " + names(parameters))));
            } else {
                // plain numbers fill the settings in order, skipping ones already given by name
                while (positional < parameters.size() && values.containsKey(parameters.get(positional).name())) {
                    positional++;
                }
                if (positional >= parameters.size()) {
                    throw new IllegalArgumentException("Too many parameters. "
                        + (parameters.isEmpty() ? "This shape has no parameters."
                            : "Valid parameters, in order: " + names(parameters)));
                }
                target = parameters.get(positional++);
                valueText = arg;
            }
            double value;
            try {
                value = Double.parseDouble(valueText);
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("'" + valueText + "' is not a number (for " + target.name() + ")");
            }
            if (!Double.isFinite(value) || value < target.min() || value > target.max()) {
                throw new IllegalArgumentException(target.name() + " must be between "
                    + ShapeParameter.format(target.min()) + " and " + ShapeParameter.format(target.max()));
            }
            values.put(target.name(), value);
        }
        for (ShapeParameter parameter : parameters) {
            values.putIfAbsent(parameter.name(), parameter.defaultValue());
        }
        return new ShapeParameters(values);
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
