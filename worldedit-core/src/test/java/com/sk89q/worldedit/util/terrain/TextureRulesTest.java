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

package com.sk89q.worldedit.util.terrain;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.translation.TranslationManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextureRulesTest extends BaseWorldEditTest {

    @BeforeAll
    static void stubTranslations() {
        TranslationManager translationManager = mock(TranslationManager.class);
        when(translationManager.convertText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(MOCKED_PLATFORM.getTranslationManager()).thenReturn(translationManager);
    }

    private static TextureRules<String> parse(String input) throws InputParseException {
        return TextureRules.parse(input, s -> s);
    }

    @Test
    void parsesExampleRules() throws InputParseException {
        TextureRules<String> rules = parse("snow_block@y>140,stone@slope>45,coarse_dirt@noise>0.7&slope>25,grass_block");
        List<TextureRules.Rule<String>> list = rules.getRules();
        assertEquals(4, list.size());
        assertEquals("snow_block", list.get(0).pattern());
        assertEquals(List.of(new TextureRules.Condition(TextureRules.Variable.Y, true, false, 140)),
            list.get(0).conditions());
        assertEquals(2, list.get(2).conditions().size());
        assertTrue(list.get(3).conditions().isEmpty());
        assertTrue(rules.uses(TextureRules.Variable.NOISE));
        assertTrue(rules.uses(TextureRules.Variable.SLOPE));
    }

    @Test
    void firstMatchingRuleWins() throws InputParseException {
        TextureRules<String> rules = parse("snow_block@y>140,stone@slope>45,coarse_dirt@noise>0.7&slope>25,grass_block");
        assertEquals("snow_block", rules.select(150, 80, 0.9));
        assertEquals("stone", rules.select(100, 50, 0.9));
        assertEquals("coarse_dirt", rules.select(100, 30, 0.8));
        assertEquals("grass_block", rules.select(100, 30, 0.5));
        assertEquals("grass_block", rules.select(100, 10, 0.8));
        assertEquals("grass_block", rules.select(140, 0, 0));
    }

    @Test
    void noFallbackLeavesColumnAlone() throws InputParseException {
        TextureRules<String> rules = parse("sand@y<64");
        assertNull(rules.select(70, 0, 0));
        assertEquals("sand", rules.select(63, 0, 0));
        assertFalse(rules.uses(TextureRules.Variable.NOISE));
    }

    @Test
    void inclusiveAndNegativeComparisons() throws InputParseException {
        TextureRules<String> rules = parse("a@y>=-10&y<=0,b");
        assertEquals("a", rules.select(-10, 0, 0));
        assertEquals("a", rules.select(0, 0, 0));
        assertEquals("b", rules.select(1, 0, 0));
        assertEquals("b", rules.select(-11, 0, 0));
    }

    @Test
    void bracketsInPatternsAreKept() throws InputParseException {
        TextureRules<String> rules = parse("oak_stairs[facing=north,half=top]@slope>30, stone ,#clipboard@[1,0,0]");
        List<TextureRules.Rule<String>> list = rules.getRules();
        assertEquals(3, list.size());
        assertEquals("oak_stairs[facing=north,half=top]", list.get(0).pattern());
        assertEquals("stone", list.get(1).pattern());
        assertEquals("#clipboard@[1,0,0]", list.get(2).pattern());
        assertTrue(list.get(2).conditions().isEmpty());
    }

    @Test
    void bracketedPatternWithOffsetAndCondition() throws InputParseException {
        TextureRules<String> rules = parse("#clipboard@[1,0,0]@y>5");
        assertEquals("#clipboard@[1,0,0]", rules.getRules().get(0).pattern());
        assertEquals(1, rules.getRules().get(0).conditions().size());
    }

    @Test
    void invalidConditionsAreRejected() {
        assertThrows(InputParseException.class, () -> parse("stone@height>5"));
        assertThrows(InputParseException.class, () -> parse("stone@slope=5"));
        assertThrows(InputParseException.class, () -> parse("stone@slope>"));
        assertThrows(InputParseException.class, () -> parse("stone@"));
        assertThrows(InputParseException.class, () -> parse("stone@y>5&"));
        assertThrows(InputParseException.class, () -> parse("@y>5"));
        assertThrows(InputParseException.class, () -> parse(""));
        assertThrows(InputParseException.class, () -> parse(" , "));
    }

    @Test
    void thresholdsOutsideTheirRangeAreRejected() throws InputParseException {
        assertThrows(InputParseException.class, () -> parse("stone@noise>2"));
        assertThrows(InputParseException.class, () -> parse("stone@noise<-0.5"));
        assertThrows(InputParseException.class, () -> parse("stone@slope>120"));
        assertEquals(1, parse("stone@noise>=1").getRules().size());
        assertEquals(1, parse("stone@noise<0").getRules().size());
        assertEquals(1, parse("stone@slope<90").getRules().size());
    }

    @Test
    void patternParserErrorsPropagate() {
        assertThrows(InputParseException.class, () -> TextureRules.<String>parse("bogus@y>1", s -> {
            throw new InputParseException(TextComponent.of("bad " + s));
        }));
    }
}
