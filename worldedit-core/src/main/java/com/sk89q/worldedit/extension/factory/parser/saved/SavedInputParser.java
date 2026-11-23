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

package com.sk89q.worldedit.extension.factory.parser.saved;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.AbstractFactory;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Resolves {@code #name} to a saved pattern or mask by parsing the saved text
 * with the same {@link ParserContext}. A saved entry that (directly or through
 * other saved entries) refers to itself gives an error instead of recursing forever.
 *
 * @param <E> the parsed type
 */
public class SavedInputParser<E> extends InputParser<E> {

    /**
     * How deep saved entries may refer to other saved entries.
     */
    static final int MAX_DEPTH = 16;

    private static final ThreadLocal<Deque<String>> RESOLVING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Map<String, String>> OVERRIDES = ThreadLocal.withInitial(HashMap::new);

    /**
     * Thrown when saved entries refer to each other in a loop.
     */
    public static final class LoopException extends InputParseException {
        LoopException(String message) {
            super(TextComponent.of(message));
        }
    }

    private final Supplier<SavedInputStore> store;
    private final Function<WorldEdit, ? extends AbstractFactory<E>> factory;

    /**
     * Create a parser.
     *
     * @param worldEdit the WorldEdit instance
     * @param store supplies the store (called lazily)
     * @param factory the factory used to parse saved text, e.g. {@code WorldEdit::getPatternFactory}
     */
    public SavedInputParser(WorldEdit worldEdit, Supplier<SavedInputStore> store,
                            Function<WorldEdit, ? extends AbstractFactory<E>> factory) {
        super(worldEdit);
        this.store = store;
        this.factory = factory;
    }

    public SavedInputStore getStore() {
        return store.get();
    }

    private String kind() {
        return getStore().getKind();
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (!input.isEmpty() && (!input.startsWith("#") || input.startsWith("##"))) {
            return Stream.empty();
        }
        String prefix = input.isEmpty() ? "" : input.substring(1);
        if (!prefix.isEmpty() && !SavedInputStore.isValidName(prefix)) {
            return Stream.empty();
        }
        return getStore().namesStartingWith(prefix).stream().map(name -> "#" + name);
    }

    @Override
    public @Nullable E parseFromInput(String input, ParserContext context) throws InputParseException {
        if (!input.startsWith("#") || input.startsWith("##")) {
            return null;
        }
        String name = input.substring(1);
        if (!SavedInputStore.isValidName(name)) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        String token = kind() + ":" + key;
        String value = OVERRIDES.get().get(token);
        if (value == null) {
            value = getStore().get(key).orElse(null);
        }
        if (value == null) {
            return null;
        }
        Deque<String> resolving = RESOLVING.get();
        if (resolving.contains(token)) {
            List<String> chain = new ArrayList<>();
            resolving.descendingIterator().forEachRemaining(t -> chain.add("#" + t.substring(t.indexOf(':') + 1)));
            chain.add("#" + key);
            throw new LoopException("Saved " + kind() + " #" + key + " refers to itself: "
                + String.join(" -> ", chain));
        }
        if (resolving.size() >= MAX_DEPTH) {
            throw new LoopException("Saved " + kind() + "s refer to each other more than " + MAX_DEPTH
                + " levels deep at #" + key);
        }
        resolving.push(token);
        try {
            return factory.apply(worldEdit).parseFromInput(value, context);
        } catch (LoopException e) {
            throw e;
        } catch (InputParseException e) {
            throw new InputParseException(TextComponent.of("Saved " + kind() + " #" + key + " (" + value + "): ")
                .append(e.getRichMessage()), e);
        } finally {
            resolving.pop();
        }
    }

    /**
     * Parse {@code #name} as if {@code value} were already saved under that name,
     * to check a value before saving it (this also catches loops through other saved entries).
     *
     * @param name the name
     * @param value the value
     * @param context the parser context
     * @return the parsed value
     * @throws InputParseException if it can't be parsed
     */
    public E validate(String name, String value, ParserContext context) throws InputParseException {
        String key = SavedInputStore.normalize(name);
        String token = kind() + ":" + key;
        Map<String, String> overrides = OVERRIDES.get();
        String old = overrides.put(token, value);
        try {
            E result = parseFromInput("#" + key, context);
            if (result == null) {
                throw new InputParseException(TextComponent.of("Could not parse " + value));
            }
            return result;
        } finally {
            if (old == null) {
                overrides.remove(token);
            } else {
                overrides.put(token, old);
            }
        }
    }

    /**
     * Whether {@code #name} already means something to another parser of the factory
     * (e.g. {@code #clipboard}, {@code #stripes} or {@code #region}).
     *
     * @param name the name, without {@code #}
     * @param context a parser context
     * @return true if the name is taken
     */
    public boolean isReserved(String name, ParserContext context) {
        String input = "#" + SavedInputStore.normalize(name);
        List<? extends InputParser<E>> parsers = factory.apply(worldEdit).getParsers();
        // the last parser is the factory's default (plain blocks), which rejects any #name
        for (int i = 0; i < parsers.size() - 1; i++) {
            InputParser<E> parser = parsers.get(i);
            if (parser == this || parser instanceof SavedInputParser) {
                continue;
            }
            if (claims(parser, input, context) || claims(parser, input + "[]", context)) {
                return true;
            }
            try {
                if (parser.getSuggestions(input, context).anyMatch(s -> stripArguments(s).equalsIgnoreCase(input))) {
                    return true;
                }
            } catch (RuntimeException _) {
                // ignore broken suggestions
            }
        }
        return false;
    }

    private static <E> boolean claims(InputParser<E> parser, String input, ParserContext context) {
        try {
            return parser.parseFromInput(input, context) != null;
        } catch (InputParseException | RuntimeException _) {
            // the parser recognised the name but didn't like the (missing) arguments
            return true;
        }
    }

    /**
     * Strip the arguments from a suggestion like {@code #stripes[a,b]} or {@code #clipboard@[x,y,z]}.
     *
     * @param suggestion the suggestion
     * @return the part before {@code [} or {@code @}
     */
    static String stripArguments(String suggestion) {
        int end = suggestion.length();
        int bracket = suggestion.indexOf('[');
        if (bracket >= 0) {
            end = bracket;
        }
        int at = suggestion.indexOf('@');
        if (at >= 0 && at < end) {
            end = at;
        }
        return suggestion.substring(0, end);
    }
}
