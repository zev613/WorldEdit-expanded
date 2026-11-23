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

package com.sk89q.worldedit.command.argument;

import com.sk89q.worldedit.command.util.ShapeLibraryHolder;
import com.sk89q.worldedit.function.generator.age.AgeSettings;
import com.sk89q.worldedit.function.generator.nature.CoralGenerator;
import com.sk89q.worldedit.function.generator.nature.CrystalGenerator;
import com.sk89q.worldedit.function.generator.nature.GiantMushroomGenerator;
import com.sk89q.worldedit.function.generator.nature.RockGenerator;
import com.sk89q.worldedit.function.generator.nature.RootGenerator;
import com.sk89q.worldedit.function.generator.parts.BuildingParts;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.skyscraper.Skyscraper;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.function.generator.warp.BuiltinWarps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Works out which settings to suggest while a command is being typed, from the command and the
 * shape, part or warp already chosen. Plain logic, so it can be tested without a server.
 */
public final class SettingHints {

    /**
     * What a command accepts.
     *
     * @param parameters the numeric settings ({@code name=value})
     * @param values words a setting can take, e.g. {@code crown -> flat, spire}
     * @param words bare words that are accepted, e.g. coral types
     * @param flags flags such as {@code -c}
     */
    record Spec(List<ShapeParameter> parameters, Map<String, List<String>> values, List<String> words,
                List<String> flags) {

        static Spec of(List<ShapeParameter> parameters, String... flags) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (ShapeParameter parameter : parameters) {
                if (parameter.name().equals(ShapeAxis.NAME)) {
                    values.put(ShapeAxis.NAME, List.of("x", "y", "z", "auto"));
                }
            }
            return new Spec(parameters, values, List.of(), List.of(flags));
        }

        Spec withValues(String setting, List<String> options, boolean bare) {
            Map<String, List<String>> newValues = new LinkedHashMap<>(values);
            newValues.put(setting, options);
            List<String> newWords = new ArrayList<>(words);
            if (bare) {
                newWords.addAll(options);
            }
            return new Spec(parameters, newValues, newWords, flags);
        }
    }

    private SettingHints() {
    }

    /**
     * Suggestions for the word being typed.
     *
     * @param line the whole command line typed so far
     * @param input the word being typed
     * @return the suggestions (not yet filtered by what was typed)
     */
    public static List<String> suggest(String line, String input) {
        return suggest(line, input, name -> ShapeLibraryHolder.get().find(name));
    }

    /**
     * Suggestions for the word being typed.
     *
     * @param line the whole command line typed so far
     * @param input the word being typed
     * @param shapes looks up shape names
     * @return the suggestions (not yet filtered by what was typed)
     */
    static List<String> suggest(String line, String input, Function<String, Optional<NamedShape>> shapes) {
        List<String> tokens = new ArrayList<>(Arrays.asList(line.trim().split("\\s+")));
        if (tokens.isEmpty() || tokens.get(0).isEmpty()) {
            return List.of();
        }
        String command = tokens.get(0).replaceFirst("^/+", "").toLowerCase(Locale.ROOT);
        List<String> positional = new ArrayList<>();
        for (String token : tokens.subList(1, tokens.size())) {
            if (!token.startsWith("-") || token.length() < 2 || Character.isDigit(token.charAt(1))) {
                positional.add(token.toLowerCase(Locale.ROOT));
            }
        }
        Spec spec = spec(command, positional, shapes);
        if (spec == null) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        int eq = input.indexOf('=');
        if (eq > 0) {
            String key = input.substring(0, eq).toLowerCase(Locale.ROOT);
            List<String> options = spec.values().get(key);
            if (options != null) {
                options.forEach(option -> suggestions.add(key + "=" + option));
            } else {
                spec.parameters().stream().filter(p -> p.name().equals(key)).findFirst()
                    .ifPresent(p -> suggestions.add(key + "=" + ShapeParameter.format(p.defaultValue())));
            }
            return suggestions;
        }
        String typed = " " + line.toLowerCase(Locale.ROOT) + " ";
        for (ShapeParameter parameter : spec.parameters()) {
            if (!typed.contains(" " + parameter.name() + "=")) {
                suggestions.add(parameter.name() + "=");
            }
        }
        for (String key : spec.values().keySet()) {
            if (spec.parameters().stream().noneMatch(p -> p.name().equals(key)) && !typed.contains(" " + key + "=")) {
                suggestions.add(key + "=");
            }
        }
        suggestions.addAll(spec.words());
        for (String flag : spec.flags()) {
            if (!typed.contains(" " + flag + " ")) {
                suggestions.add(flag);
            }
        }
        return suggestions;
    }

    private static String at(List<String> positional, int index) {
        return index < positional.size() ? positional.get(index) : "";
    }

    private static List<String> firstNames(List<String> types) {
        return types.stream().map(t -> t.split("\\|", -1)[0]).toList();
    }

    private static Spec spec(String command, List<String> positional, Function<String, Optional<NamedShape>> shapes) {
        String first = at(positional, 0);
        return switch (command) {
            case "shape", "namedshape" ->
                shapeSpec(shapes, first, "-h", "-b", "-f", "-x", "-r", "-t", "-m", "-a", "-l", "-c", "-s");
            case "selshape", "shapesel" -> shapeSpec(shapes, first, "-h", "-b", "-f", "-c", "-r", "-t");
            case "brush", "br" -> first.equals("shape") || first.equals("namedshape")
                ? shapeSpec(shapes, at(positional, 1), "-h", "-r", "-t") : null;
            case "combine", "compound" -> first.equals("add") || first.equals("cut") || first.equals("keep")
                ? shapeSpec(shapes, at(positional, 1), "-o", "-r", "-t", "-c") : null;
            case "shapedef", "shapepreset" -> first.equals("save") || first.equals("add") || first.equals("set")
                ? shapeSpec(shapes, at(positional, 2)) : null;
            case "build", "buildpart" ->
                BuildingParts.find(first).map(p -> Spec.of(p.parameters(), "-w", "-s", "-c")).orElse(null);
            case "skyscraper" -> skyscraperSpec();
            case "warp", "namedwarp", "reshape" ->
                BuiltinWarps.find(first).map(w -> Spec.of(w.getParameters(), "-c")).orElse(null);
            case "rock", "boulder" -> Spec.of(RockGenerator.PARAMETERS, "-c");
            case "crystals", "crystal", "geode" -> Spec.of(CrystalGenerator.PARAMETERS, "-c");
            case "giantmushroom" -> Spec.of(GiantMushroomGenerator.PARAMETERS, "-c", "-u")
                .withValues("type", firstNames(GiantMushroomGenerator.TYPES), true);
            case "roots" -> Spec.of(RootGenerator.PARAMETERS, "-c");
            case "coral", "reef" ->
                Spec.of(CoralGenerator.PARAMETERS, "-c").withValues("type", firstNames(CoralGenerator.TYPES), true);
            case "ruin", "decay" -> Spec.of(AgeSettings.PARAMETERS, "-r", "-g");
            case "overgrow", "ivy" -> Spec.of(AgeSettings.PARAMETERS, "-n", "-l");
            default -> null;
        };
    }

    private static Spec skyscraperSpec() {
        Spec spec = Spec.of(Skyscraper.PARAMETERS, "-c")
            .withValues("shape", Skyscraper.SHAPE_NAMES, false)
            .withValues("crown", Skyscraper.CROWN_NAMES, false);
        for (StyleRole role : StyleRole.values()) {
            spec = spec.withValues(role.id(), List.of(), false);
        }
        return spec;
    }

    private static Spec shapeSpec(Function<String, Optional<NamedShape>> shapes, String name, String... flags) {
        Optional<NamedShape> shape = name.isEmpty() ? Optional.empty() : shapes.apply(name);
        return shape.map(s -> Spec.of(s.getParameters(), flags)).orElse(null);
    }
}
