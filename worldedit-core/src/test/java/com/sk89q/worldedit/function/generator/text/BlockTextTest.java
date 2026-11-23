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

package com.sk89q.worldedit.function.generator.text;

import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockTextTest {

    @Test
    void fontCoversLettersDigitsAndPunctuation() {
        String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,!?:;'\"-+=/\\()[]_#&*<>@%$^|";
        for (int i = 0; i < all.length(); i++) {
            char c = all.charAt(i);
            assertTrue(BlockFont.supports(c), "no glyph for " + c);
            BlockFont.Glyph glyph = BlockFont.glyph(c);
            boolean any = false;
            for (int x = 0; x < glyph.width(); x++) {
                for (int y = 0; y < BlockFont.HEIGHT; y++) {
                    any |= glyph.filled(x, y);
                }
            }
            assertTrue(any, "empty glyph for " + c);
        }
        assertEquals(BlockFont.WIDTH, BlockFont.glyph('W').width());
        assertEquals(1, BlockFont.glyph('.').width());
        assertFalse(BlockFont.supports('é'));
    }

    @Test
    void lowerCaseIsDrawnAsCapitals() {
        BlockText lower = BlockText.layout("hello");
        BlockText upper = BlockText.layout("HELLO");
        assertEquals(upper.width(), lower.width());
        assertEquals(upper.pixels().size(), lower.pixels().size());
        for (int i = 0; i < upper.pixels().size(); i++) {
            assertEquals(upper.pixels().get(i)[0], lower.pixels().get(i)[0]);
            assertEquals(upper.pixels().get(i)[1], lower.pixels().get(i)[1]);
        }
    }

    @Test
    void layoutSpacesCharacters() {
        assertEquals(5, BlockText.layout("A").width());
        assertEquals(11, BlockText.layout("AB").width());
        assertEquals(5 + 3 + 1 + 5, BlockText.layout("A B").width());
        assertEquals(7, BlockText.layout("A.").width());
        // the I's stem is the middle column, all 7 rows
        long stem = BlockText.layout("I").pixels().stream().filter(p -> p[0] == 2).count();
        assertEquals(7, stem);
        assertThrows(IllegalArgumentException.class, () -> BlockText.layout("   "));
        assertThrows(IllegalArgumentException.class, () -> BlockText.layout("x".repeat(BlockText.MAX_LENGTH + 1)));
    }

    @Test
    void unknownCharactersBecomeQuestionMarks() {
        BlockText text = BlockText.layout("Aé");
        assertEquals(Set.of('é'), text.unsupported());
        assertEquals(BlockText.layout("A?").pixels().size(), text.pixels().size());
    }

    @Test
    void blocksAreScaledAndReadLeftToRight() {
        BlockText text = BlockText.layout("L");
        // L: the left column is 7 tall, the bottom row 5 wide
        List<BlockVector3> blocks = text.blocks(new FacingPlane(Direction.NORTH, false), 1, 1);
        assertEquals(11, blocks.size());
        Set<BlockVector3> set = new HashSet<>(blocks);
        assertTrue(set.contains(BlockVector3.at(0, 6, 0)));
        assertTrue(set.contains(BlockVector3.at(4, 0, 0)));
        assertFalse(set.contains(BlockVector3.at(4, 6, 0)));

        // looking south, the reader's right is west
        Set<BlockVector3> south = new HashSet<>(text.blocks(new FacingPlane(Direction.SOUTH, false), 1, 1));
        assertTrue(south.contains(BlockVector3.at(-4, 0, 0)));

        List<BlockVector3> scaled = text.blocks(new FacingPlane(Direction.NORTH, false), 2, 3);
        assertEquals(11 * 4 * 3, scaled.size());
        assertEquals(11 * 4 * 3, new HashSet<>(scaled).size());

        // flat: the top of the letter is furthest from the player
        Set<BlockVector3> flat = new HashSet<>(text.blocks(new FacingPlane(Direction.NORTH, true), 1, 1));
        assertTrue(flat.contains(BlockVector3.at(0, 0, -6)));
        assertTrue(flat.stream().allMatch(p -> p.y() == 0));
    }
}
