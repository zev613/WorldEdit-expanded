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

package com.sk89q.worldedit.regions.smart;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootprintOutlineTest {

    private static List<BlockVector2> rect(int x0, int z0, int x1, int z1) {
        List<BlockVector2> cells = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                cells.add(BlockVector2.at(x, z));
            }
        }
        return cells;
    }

    private static boolean inside(List<BlockVector2> outline, int x, int z) {
        return Polygonal2DRegion.contains(outline, 0, 0, BlockVector3.at(x, 0, z));
    }

    private static long volume(List<BlockVector2> outline) {
        return new Polygonal2DRegion(null, outline, 0, 0).getVolume();
    }

    @Test
    void rectangleHasFourCorners() {
        FootprintOutline footprint = FootprintOutline.of(rect(0, 0, 9, 4));
        List<BlockVector2> outline = footprint.trace();
        assertEquals(4, outline.size());
        assertEquals(Set.of(BlockVector2.at(0, 0), BlockVector2.at(9, 0), BlockVector2.at(9, 4), BlockVector2.at(0, 4)),
            Set.copyOf(outline));
        assertEquals(0, footprint.countOutside(outline));
        assertEquals(50, volume(outline));
    }

    @Test
    void lShapeIsConcave() {
        // a 10x10 square with the 6x6 corner at high x / high z missing
        List<BlockVector2> cells = new ArrayList<>(rect(0, 0, 9, 3));
        cells.addAll(rect(0, 4, 3, 9));
        FootprintOutline footprint = FootprintOutline.of(cells);
        List<BlockVector2> outline = footprint.trace();
        assertEquals(6, outline.size());
        assertEquals(0, footprint.countOutside(outline));
        // the missing corner is not selected: the outline is concave
        assertFalse(inside(outline, 8, 8));
        assertFalse(inside(outline, 5, 5));
        assertTrue(inside(outline, 3, 9));
        assertEquals(cells.size(), volume(outline));
        // still fine after simplifying to a typical point limit
        List<BlockVector2> simplified = FootprintOutline.simplify(outline, 20);
        assertEquals(outline, simplified);
    }

    @Test
    void uShapeIsConcave() {
        // a U: two arms joined at low z, open at high z
        List<BlockVector2> cells = new ArrayList<>(rect(0, 0, 11, 2));
        cells.addAll(rect(0, 3, 2, 11));
        cells.addAll(rect(9, 3, 11, 11));
        FootprintOutline footprint = FootprintOutline.of(cells);
        List<BlockVector2> outline = footprint.trace();
        assertEquals(8, outline.size());
        assertEquals(0, footprint.countOutside(outline));
        assertFalse(inside(outline, 6, 8));
        assertFalse(inside(outline, 5, 11));
        assertEquals(cells.size(), volume(outline));
    }

    @Test
    void diagonalAndThinParts() {
        // a block, a diagonal line and a one-block-wide tail
        List<BlockVector2> cells = new ArrayList<>(rect(0, 0, 4, 4));
        for (int i = 1; i <= 5; i++) {
            cells.add(BlockVector2.at(4 + i, 4 + i));
        }
        for (int x = -6; x < 0; x++) {
            cells.add(BlockVector2.at(x, 2));
        }
        FootprintOutline footprint = FootprintOutline.of(cells);
        List<BlockVector2> outline = footprint.trace();
        assertEquals(0, footprint.countOutside(outline));
        assertFalse(inside(outline, 8, 4));
        assertFalse(inside(outline, -3, 0));
    }

    @Test
    void singleColumnAndLine() {
        FootprintOutline single = FootprintOutline.of(List.of(BlockVector2.at(3, 3)));
        List<BlockVector2> outline = FootprintOutline.atLeastThreePoints(single.trace());
        assertEquals(3, outline.size());
        assertEquals(0, single.countOutside(outline));

        FootprintOutline line = FootprintOutline.of(rect(0, 0, 7, 0));
        List<BlockVector2> lineOutline = FootprintOutline.atLeastThreePoints(line.trace());
        assertEquals(0, line.countOutside(lineOutline));
        assertFalse(inside(lineOutline, 3, 1));
    }

    @Test
    void circleSimplifiedToLimit() {
        List<BlockVector2> cells = new ArrayList<>();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (x * x + z * z <= 400) {
                    cells.add(BlockVector2.at(x, z));
                }
            }
        }
        FootprintOutline footprint = FootprintOutline.of(cells);
        List<BlockVector2> traced = footprint.trace();
        assertTrue(traced.size() > 20);
        assertEquals(0, footprint.countOutside(traced));
        List<BlockVector2> simplified = FootprintOutline.simplify(traced, 20);
        assertTrue(simplified.size() <= 20, "got " + simplified.size());
        assertTrue(simplified.size() >= 8);
        // the simplified outline may cut a few edge columns, but keeps nearly all of them
        assertTrue(footprint.countOutside(simplified) < cells.size() / 20);
    }

    @Test
    void concaveOutlineSimplifiedKeepsEverythingWhenPossible() {
        // a jagged comb: many teeth, so the outline needs simplifying, but cutting inwards is allowed
        List<BlockVector2> cells = new ArrayList<>(rect(0, 0, 40, 4));
        for (int x = 0; x <= 40; x += 2) {
            cells.add(BlockVector2.at(x, 5));
        }
        FootprintOutline footprint = FootprintOutline.of(cells);
        List<BlockVector2> simplified = FootprintOutline.simplify(footprint.trace(), 10);
        assertTrue(simplified.size() <= 10);
        assertEquals(0, footprint.countOutside(simplified));
    }
}
