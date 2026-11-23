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

import com.sk89q.worldedit.command.util.StyleLibraryHolder;
import com.sk89q.worldedit.function.generator.parts.BuildingPart;
import com.sk89q.worldedit.function.generator.parts.BuildingParts;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.skyscraper.Skyscraper;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ConversionResult;
import org.enginehub.piston.converter.SuccessfulConversion;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.enginehub.piston.converter.SuggestionHelper.limitByPrefix;

/**
 * Words that tab-complete from a list (building parts, styles, settings), so players can pick an
 * option instead of remembering it. The words themselves are checked by the commands as before.
 */
public final class HintedArguments {

    /**
     * A building part name for {@code //build}.
     *
     * @param value the word typed
     */
    public record PartName(String value) {
    }

    /**
     * A building style name for {@code //skyscraper} and {@code //style}.
     *
     * @param value the word typed
     */
    public record StyleName(String value) {
    }

    /**
     * A {@code //build} setting such as {@code radius=5}.
     *
     * @param value the word typed
     */
    public record PartSetting(String value) {
    }

    /**
     * A {@code //skyscraper} setting such as {@code crown=spire} or {@code window=glass}.
     *
     * @param value the word typed
     */
    public record SkyscraperSetting(String value) {
    }

    private HintedArguments() {
    }

    /**
     * Register the converters.
     *
     * @param commandManager the command manager
     */
    public static void register(CommandManager commandManager) {
        commandManager.registerConverter(Key.of(PartName.class), new Hinted<>(PartName::new, "a building part",
            HintedArguments::partNames));
        commandManager.registerConverter(Key.of(StyleName.class), new Hinted<>(StyleName::new, "a building style",
            () -> StyleLibraryHolder.get().getAllNames()));
        commandManager.registerConverter(Key.of(PartSetting.class), new Hinted<>(PartSetting::new, "a setting",
            HintedArguments::partSettings));
        commandManager.registerConverter(Key.of(SkyscraperSetting.class), new Hinted<>(SkyscraperSetting::new,
            "a setting", HintedArguments::skyscraperSettings));
    }

    /**
     * Turn hinted words back into plain strings.
     *
     * @param words the words
     * @param value how to read each word
     * @param <T> the word type
     * @return the strings
     */
    public static <T> List<String> strings(List<T> words, Function<T, String> value) {
        return words.stream().map(value).toList();
    }

    private static List<String> partNames() {
        List<String> names = new ArrayList<>();
        for (BuildingPart part : BuildingParts.all()) {
            names.add(part.name());
        }
        return names;
    }

    static List<String> partSettings() {
        Set<String> names = new LinkedHashSet<>();
        for (BuildingPart part : BuildingParts.all()) {
            for (ShapeParameter parameter : part.parameters()) {
                names.add(parameter.name() + "=");
            }
        }
        names.addAll(List.of("-w", "-s", "-c"));
        return new ArrayList<>(names);
    }

    static List<String> skyscraperSettings() {
        List<String> words = new ArrayList<>();
        for (ShapeParameter parameter : Skyscraper.PARAMETERS) {
            switch (parameter.name()) {
                case "shape" -> Skyscraper.SHAPE_NAMES.forEach(n -> words.add("shape=" + n));
                case "crown" -> Skyscraper.CROWN_NAMES.forEach(n -> words.add("crown=" + n));
                default -> words.add(parameter.name() + "=");
            }
        }
        Arrays.stream(StyleRole.values()).forEach(role -> words.add(role.id() + "="));
        words.add("-c");
        return words;
    }

    /**
     * Accepts any word and suggests from a list.
     */
    private static final class Hinted<T> implements ArgumentConverter<T> {
        private final Function<String, T> wrap;
        private final String what;
        private final Supplier<List<String>> options;

        Hinted(Function<String, T> wrap, String what, Supplier<List<String>> options) {
            this.wrap = wrap;
            this.what = what;
            this.options = options;
        }

        @Override
        public Component describeAcceptableArguments() {
            return TextComponent.of(what);
        }

        @Override
        public List<String> getSuggestions(String input, InjectedValueAccess context) {
            String lower = input.toLowerCase(Locale.ROOT);
            return limitByPrefix(options.get().stream(), lower);
        }

        @Override
        public ConversionResult<T> convert(String argument, InjectedValueAccess context) {
            return SuccessfulConversion.fromSingle(wrap.apply(argument));
        }
    }
}
