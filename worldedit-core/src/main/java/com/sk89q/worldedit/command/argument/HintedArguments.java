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
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ConversionResult;
import org.enginehub.piston.converter.SuccessfulConversion;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * A setting such as {@code radius=5}, {@code crown=spire} or {@code -c}. The suggestions depend on
     * the command and on what was typed before it (the shape, part or warp chosen).
     *
     * @param value the word typed
     */
    public record SettingWord(String value) {
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
        commandManager.registerConverter(Key.of(SettingWord.class), new SettingConverter());
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

    /**
     * Accepts any word; suggests the settings of the command being typed.
     */
    private static final class SettingConverter implements ArgumentConverter<SettingWord> {

        @Override
        public Component describeAcceptableArguments() {
            return TextComponent.of("a setting (a number, name=value or a flag)");
        }

        @Override
        public List<String> getSuggestions(String input, InjectedValueAccess context) {
            String line = context.injectedValue(Key.of(Arguments.class)).map(Arguments::get).orElse("");
            return limitByPrefix(SettingHints.suggest(line, input).stream(), input.toLowerCase(Locale.ROOT));
        }

        @Override
        public ConversionResult<SettingWord> convert(String argument, InjectedValueAccess context) {
            return SuccessfulConversion.fromSingle(new SettingWord(argument));
        }
    }
}
