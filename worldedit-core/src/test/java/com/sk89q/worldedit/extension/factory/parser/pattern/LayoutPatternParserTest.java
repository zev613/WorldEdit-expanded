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

package com.sk89q.worldedit.extension.factory.parser.pattern;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.PatternFactory;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.LayoutPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.translation.TranslationManager;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayoutPatternParserTest extends BaseWorldEditTest {

    @BeforeAll
    static void stubTranslations() {
        TranslationManager translationManager = mock(TranslationManager.class);
        when(translationManager.convertText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(MOCKED_PLATFORM.getTranslationManager()).thenReturn(translationManager);
    }

    private final WorldEdit worldEdit = mock(WorldEdit.class);
    private final PatternFactory patternFactory = mock(PatternFactory.class);
    private final ParserContext context = new ParserContext();
    private final List<String> parsedTokens = new ArrayList<>();
    private final Map<String, BaseBlock> blocks = new HashMap<>();
    private final LayoutPatternParser parser;

    LayoutPatternParserTest() throws InputParseException {
        when(worldEdit.getPatternFactory()).thenReturn(patternFactory);
        when(patternFactory.parseFromInput(anyString(), any())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            parsedTokens.add(token);
            BaseBlock block = blocks.computeIfAbsent(token, _ -> mock(BaseBlock.class));
            Pattern pattern = mock(Pattern.class);
            when(pattern.applyBlock(any())).thenReturn(block);
            return pattern;
        });
        parser = new LayoutPatternParser(worldEdit);
    }

    @Test
    void stripesTakePatternsThenSettings() throws InputParseException {
        Pattern pattern = parser.parseFromInput("#stripes[stone, oak_log[axis=x], 2, x]", context);
        assertInstanceOf(LayoutPattern.class, pattern);
        assertEquals(List.of("stone", "oak_log[axis=x]"), parsedTokens);
        assertSame(blocks.get("stone"), pattern.applyBlock(BlockVector3.at(1, 9, 9)));
        assertSame(blocks.get("oak_log[axis=x]"), pattern.applyBlock(BlockVector3.at(2, 0, 0)));
    }

    @Test
    void settingsCanBeNamed() throws InputParseException {
        Pattern pattern = parser.parseFromInput("#checker[size=3,white_wool,black_wool]", context);
        assertSame(blocks.get("white_wool"), pattern.applyBlock(BlockVector3.at(2, 2, 2)));
        assertSame(blocks.get("black_wool"), pattern.applyBlock(BlockVector3.at(3, 2, 2)));
    }

    @Test
    void moreThanTwoPatternsCycle() throws InputParseException {
        Pattern pattern = parser.parseFromInput("#diagonal[a,b,c]", context);
        assertSame(blocks.get("c"), pattern.applyBlock(BlockVector3.at(1, 1, 0)));
    }

    @Test
    void everyLayoutParses() throws InputParseException {
        for (String input : List.of("#stripes[a,b]", "#checker[a,b]", "#rings[a,b,3]", "#spiral[a,b,2,curl=0]",
            "#diagonal[a,b,4]", "#bricks[a,b]", "#bricks[a,b,4,2]", "#brick[a,b,z]", "#dither[a,b,0.3]")) {
            assertInstanceOf(LayoutPattern.class, parser.parseFromInput(input, context), input);
        }
        for (LayoutPatternParser.Definition definition : LayoutPatternParser.DEFINITIONS) {
            assertInstanceOf(LayoutPattern.class, parser.parseFromInput(
                definition.name().equals("dither") ? definition.example().replace(",y]", ",0.5]")
                    : definition.example(), context), definition.name());
        }
    }

    @Test
    void badInputIsReported() {
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#stripes[stone]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#stripes", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#stripes[a,b,0]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#bricks[a,b,c]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#checker[a,b,1,2]", context));
        // no selection to blend across
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#dither[a,b]", context));
    }

    @Test
    void unrelatedInputIsIgnored() throws InputParseException {
        assertNull(parser.parseFromInput("stone", context));
        assertNull(parser.parseFromInput("#clipboard", context));
        assertNull(parser.parseFromInput("##logs", context));
        assertNull(parser.parseFromInput("#gradient[stone,dirt]", context));
    }

    @Test
    void tellsSettingsFromPatterns() {
        var parameters = LayoutPatternParser.find("stripes").parameters();
        assertTrue(LayoutPatternParser.isSetting("2", parameters));
        assertTrue(LayoutPatternParser.isSetting("x", parameters));
        assertTrue(LayoutPatternParser.isSetting("width=3", parameters));
        assertFalse(LayoutPatternParser.isSetting("stone", parameters));
        assertFalse(LayoutPatternParser.isSetting("oak_log[axis=x]", parameters));
        assertFalse(LayoutPatternParser.isSetting("x", LayoutPatternParser.find("checker").parameters()));
    }
}
