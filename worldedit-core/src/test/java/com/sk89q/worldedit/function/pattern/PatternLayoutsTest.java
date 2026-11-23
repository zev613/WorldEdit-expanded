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

package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatternLayoutsTest {

    @Test
    void stripesFollowTheAxisAndWidth() {
        PatternLayouts.Layout layout = PatternLayouts.stripes(3, ShapeAxis.X);
        assertEquals(0, layout.index(0, 50, 9));
        assertEquals(0, layout.index(2, -7, 9));
        assertEquals(1, layout.index(3, 0, 0));
        assertEquals(-1, layout.index(-1, 0, 0));
    }

    @Test
    void checkerAlternatesEveryCube() {
        PatternLayouts.Layout layout = PatternLayouts.checker(2);
        assertEquals(0, Math.floorMod(layout.index(0, 0, 0), 2));
        assertEquals(1, Math.floorMod(layout.index(2, 0, 0), 2));
        assertEquals(1, Math.floorMod(layout.index(0, 1, 3), 2));
        assertEquals(0, Math.floorMod(layout.index(-1, -1, 0), 2));
    }

    @Test
    void ringsGrowOutwards() {
        PatternLayouts.Layout layout = PatternLayouts.rings(Vector3.ZERO, 2, ShapeAxis.Y);
        assertEquals(0, layout.index(1, 99, 0));
        assertEquals(1, layout.index(0, 0, 3));
        assertEquals(2, layout.index(-4, 5, 1));
    }

    @Test
    void spiralHasTheRightNumberOfArms() {
        PatternLayouts.Layout layout = PatternLayouts.spiral(Vector3.ZERO, 4, 0, ShapeAxis.Y);
        Set<Integer> seen = new HashSet<>();
        for (int degrees = 0; degrees < 360; degrees += 5) {
            double angle = Math.toRadians(degrees + 2.5);
            int x = (int) Math.round(Math.cos(angle) * 50);
            int z = (int) Math.round(Math.sin(angle) * 50);
            seen.add(Math.floorMod(layout.index(x, 0, z), 4));
        }
        assertEquals(Set.of(0, 1, 2, 3), seen);
    }

    @Test
    void bricksHaveMortarLinesAndOffsetCourses() {
        PatternLayouts.Layout layout = PatternLayouts.bricks(3, 1, ShapeAxis.Y);
        // row 0: brick brick brick mortar ...
        assertEquals(List.of(0, 0, 0, 1, 0, 0, 0, 1), row(layout, 0));
        // row 1 is a mortar line
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1, 1), row(layout, 1));
        // row 2 is shifted by half a brick
        assertEquals(List.of(0, 1, 0, 0, 0, 1, 0, 0), row(layout, 2));
        // walls running along z look the same
        assertEquals(layout.index(5, 0, 0), layout.index(0, 0, 5));
    }

    private static List<Integer> row(PatternLayouts.Layout layout, int y) {
        return IntStream.range(0, 8).mapToObj(x -> layout.index(x, y, 0)).toList();
    }

    @Test
    void ditherGivesTheRightShareOnEverySlice() {
        PatternLayouts.Layout layout = PatternLayouts.dither(0.25);
        // every 8x8 tile of any flat slice gets exactly a quarter of the second pattern
        int xy = 0;
        int xz = 0;
        int yz = 0;
        for (int a = 0; a < 8; a++) {
            for (int b = 0; b < 8; b++) {
                xy += layout.index(a, b, 3);
                xz += layout.index(a, 5, b);
                yz += layout.index(7, a, b);
            }
        }
        assertEquals(16, xy);
        assertEquals(16, xz);
        assertEquals(16, yz);
        assertEquals(0, PatternLayouts.dither(0).index(1, 2, 3));
        assertEquals(1, PatternLayouts.dither(1).index(1, 2, 3));
    }

    @Test
    void ditherBlendGoesFromFirstToSecond() {
        PatternLayouts.Layout layout = PatternLayouts.ditherBlend(ShapeAxis.Y, 10, 20);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                assertEquals(0, layout.index(x, 10, z));
                assertEquals(1, layout.index(x, 20, z));
            }
        }
        int middle = 0;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                middle += layout.index(x, 15, z);
            }
        }
        assertEquals(32, middle);
    }

    @Test
    void layoutPatternWrapsAroundThePatterns() {
        List<BaseBlock> blocks = List.of(mock(BaseBlock.class), mock(BaseBlock.class), mock(BaseBlock.class));
        List<Pattern> patterns = blocks.stream().map(b -> {
            Pattern p = mock(Pattern.class);
            when(p.applyBlock(any())).thenReturn(b);
            return p;
        }).toList();
        LayoutPattern pattern = new LayoutPattern(PatternLayouts.stripes(1, ShapeAxis.Y), patterns);
        assertSame(blocks.get(0), pattern.applyBlock(BlockVector3.at(0, 0, 0)));
        assertSame(blocks.get(1), pattern.applyBlock(BlockVector3.at(0, 1, 0)));
        assertSame(blocks.get(2), pattern.applyBlock(BlockVector3.at(0, -1, 0)));
        assertSame(blocks.get(0), pattern.applyBlock(BlockVector3.at(0, 3, 0)));
    }
}
