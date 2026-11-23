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

import com.google.common.base.Splitter;
import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.NoMatchException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.AbstractFactory;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.translation.TranslationManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SavedInputParserTest extends BaseWorldEditTest {

    @BeforeAll
    static void stubTranslations() {
        TranslationManager translationManager = mock(TranslationManager.class);
        when(translationManager.convertText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(MOCKED_PLATFORM.getTranslationManager()).thenReturn(translationManager);
    }

    /**
     * A tiny factory of strings: plain words parse to themselves, {@code a,b} parses each part,
     * {@code #builtin[...]} is a built-in, and saved {@code #names} are resolved.
     */
    private static final class TestFactory extends AbstractFactory<String> {
        TestFactory(WorldEdit worldEdit) {
            super(worldEdit, new InputParser<>(worldEdit) {
                @Override
                public String parseFromInput(String input, ParserContext context) throws InputParseException {
                    if (!input.matches("[a-z_]+")) {
                        throw new NoMatchException(TextComponent.of("No match for " + input));
                    }
                    return input;
                }
            });
        }
    }

    private final WorldEdit worldEdit = mock(WorldEdit.class);
    private final TestFactory factory = new TestFactory(worldEdit);
    private final SavedInputStore store = new SavedInputStore(null, "pattern");
    private final SavedInputParser<String> parser;
    private final ParserContext context = new ParserContext();

    SavedInputParserTest() {
        factory.register(new InputParser<>(worldEdit) {
            @Override
            public String parseFromInput(String input, ParserContext context) throws InputParseException {
                if (!input.contains(",")) {
                    return null;
                }
                List<String> parts = new ArrayList<>();
                for (String part : Splitter.on(',').split(input)) {
                    parts.add(factory.parseFromInput(part, context));
                }
                return String.join("+", parts);
            }
        });
        factory.register(new InputParser<>(worldEdit) {
            @Override
            public Stream<String> getSuggestions(String input, ParserContext context) {
                return "#builtin".startsWith(input) ? Stream.of("#builtin[a,b]") : Stream.empty();
            }

            @Override
            public String parseFromInput(String input, ParserContext context) throws InputParseException {
                if (!input.startsWith("#builtin[")) {
                    return null;
                }
                return "builtin";
            }
        });
        factory.register(new InputParser<>(worldEdit) {
            @Override
            public String parseFromInput(String input, ParserContext context) throws InputParseException {
                if (!input.equals("#strict")) {
                    return null;
                }
                throw new InputParseException(TextComponent.of("#strict needs a session"));
            }
        });
        parser = new SavedInputParser<>(worldEdit, () -> store, _ -> factory);
        factory.register(parser);
    }

    @Test
    void resolvesSavedNames() throws InputParseException {
        store.put("oldstone", "stone,cobble");
        assertEquals("stone+cobble", factory.parseFromInput("#oldstone", context));
        assertEquals("stone+cobble", factory.parseFromInput("#OldStone", context));
        assertEquals("dirt+stone+cobble", factory.parseFromInput("dirt,#oldstone", context));
    }

    @Test
    void unknownNamesAreLeftToOtherParsers() throws InputParseException {
        InputParseException e = assertThrows(InputParseException.class,
            () -> factory.parseFromInput("#nothing", context));
        assertEquals("No saved pattern called #nothing (see //pattern list).", e.getMessage());
        // a parser registered after the saved names still gets its #names
        factory.register(new InputParser<>(worldEdit) {
            @Override
            public String parseFromInput(String input, ParserContext context) {
                return input.equals("#later") ? "later" : null;
            }
        });
        assertEquals("later", factory.parseFromInput("#later", context));
        assertNull(parser.parseFromInput("##tag", context));
        assertNull(parser.parseFromInput("#bad[name]", context));
        assertNull(parser.parseFromInput("stone", context));
    }

    @Test
    void savedEntriesCanUseOtherSavedEntries() throws InputParseException {
        store.put("a", "stone,dirt");
        store.put("b", "#a,sand");
        assertEquals("stone+dirt+sand", factory.parseFromInput("#b", context));
    }

    @Test
    void loopsGiveAnErrorInsteadOfOverflowing() {
        store.put("a", "#b,stone");
        store.put("b", "dirt,#a");
        InputParseException e = assertThrows(InputParseException.class, () -> factory.parseFromInput("#a", context));
        assertInstanceOf(SavedInputParser.LoopException.class, e);
        assertTrue(e.getMessage().contains("#a -> #b -> #a"), e.getMessage());

        store.put("self", "#self");
        assertThrows(SavedInputParser.LoopException.class, () -> factory.parseFromInput("#self", context));
    }

    @Test
    void brokenSavedEntriesSayWhichOne() {
        store.put("broken", "stone,NOPE");
        InputParseException e = assertThrows(InputParseException.class,
            () -> factory.parseFromInput("#broken", context));
        assertTrue(e.getMessage().contains("#broken"), e.getMessage());
    }

    @Test
    void validateSeesTheNewValueAndCatchesLoopsBeforeSaving() throws InputParseException {
        store.put("a", "stone");
        store.put("b", "#a");
        assertEquals("dirt", parser.validate("c", "dirt", context));
        // saving a = #b would make a loop a -> b -> a
        assertThrows(SavedInputParser.LoopException.class, () -> parser.validate("a", "#b", context));
        assertThrows(InputParseException.class, () -> parser.validate("d", "NOPE", context));
        // nothing changed by validating
        assertEquals("stone", store.get("a").orElseThrow());
        assertFalse(store.get("c").isPresent());
        assertEquals("stone", factory.parseFromInput("#b", context));
    }

    @Test
    void builtInNamesAreReserved() {
        assertTrue(parser.isReserved("builtin", context));
        assertTrue(parser.isReserved("strict", context));
        assertFalse(parser.isReserved("oldstone", context));
    }

    @Test
    void suggestsSavedNames() {
        store.put("oldstone", "stone");
        store.put("oak", "dirt");
        store.put("glass2", "sand");
        assertEquals(List.of("#oak", "#oldstone"), parser.getSuggestions("#o", context).collect(Collectors.toList()));
        assertEquals(List.of("#glass2", "#oak", "#oldstone"),
            parser.getSuggestions("#", context).collect(Collectors.toList()));
        assertEquals(List.of(), parser.getSuggestions("stone", context).collect(Collectors.toList()));
        assertEquals(List.of(), parser.getSuggestions("#oak[", context).collect(Collectors.toList()));
    }

    @Test
    void stripsSuggestionArguments() {
        assertEquals("#stripes", SavedInputParser.stripArguments("#stripes[a,b,2]"));
        assertEquals("#clipboard", SavedInputParser.stripArguments("#clipboard@[x,y,z]"));
        assertEquals("#plain", SavedInputParser.stripArguments("#plain"));
    }

    @Test
    void storeSavesAndLoadsItsFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("saved-patterns.txt");
        SavedInputStore first = new SavedInputStore(file, "pattern");
        first.put("OldStone", "50%stone_bricks,50%mossy_stone_bricks");
        first.put("stripy", "#stripes[#oldstone,glass,2]");
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("oldstone = 50%stone_bricks"));

        SavedInputStore second = new SavedInputStore(file, "pattern");
        assertEquals("50%stone_bricks,50%mossy_stone_bricks", second.get("#oldstone").orElseThrow());
        assertEquals("#stripes[#oldstone,glass,2]", second.get("stripy").orElseThrow());
        assertTrue(second.remove("stripy"));
        assertFalse(second.remove("stripy"));
        assertEquals(List.of("oldstone"), new ArrayList<>(new SavedInputStore(file, "pattern").getAll().keySet()));
    }

    @Test
    void storeRejectsBadNamesAndValues() {
        assertThrows(IllegalArgumentException.class, () -> store.put("bad name", "stone"));
        assertThrows(IllegalArgumentException.class, () -> store.put("x".repeat(33), "stone"));
        assertThrows(IllegalArgumentException.class, () -> store.put("ok", " "));
        assertTrue(SavedInputStore.isValidName("Old_stone-2"));
        assertFalse(SavedInputStore.isValidName("old.stone"));
    }

    @Test
    void fileLinesAreParsedLeniently() {
        assertNull(SavedInputStore.parseLine("# comment"));
        assertNull(SavedInputStore.parseLine("   "));
        assertNull(SavedInputStore.parseLine("no equals sign"));
        assertNull(SavedInputStore.parseLine("bad name = stone"));
        assertEquals("#stripes[a,b]", SavedInputStore.parseLine(" Name = #stripes[a,b] ").getValue());
        assertEquals("name", SavedInputStore.parseLine(" Name = #stripes[a,b] ").getKey());
        assertEquals("a=b", SavedInputStore.parseLine("x = a=b").getValue());
    }
}
