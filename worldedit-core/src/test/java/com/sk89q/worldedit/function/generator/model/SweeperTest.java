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

package com.sk89q.worldedit.function.generator.model;

import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweeperTest {

    private static CrossSection<Boolean> profile(SweepProfile profile, double width, String... settings) {
        return profile.create(width, ShapeParameters.parse(profile.getParameters(), List.of(settings)));
    }

    @Test
    void straightSquareBeamHasTheRightCrossSection() {
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 10, 0), BlockVector3.at(9, 10, 0)));
        Map<BlockVector3, Boolean> beam = Sweeper.sweep(path, profile(SweepProfile.SQUARE, 3), false);
        assertEquals(90, beam.size());
        for (int x = 0; x <= 9; x++) {
            for (int y = 9; y <= 11; y++) {
                for (int z = -1; z <= 1; z++) {
                    assertTrue(beam.containsKey(BlockVector3.at(x, y, z)));
                }
            }
        }
    }

    @Test
    void curvedTubeHasNoGaps() {
        SweepPath path = SweepPath.through(List.of(
            BlockVector3.at(0, 10, 0), BlockVector3.at(12, 14, 6), BlockVector3.at(4, 10, 18), BlockVector3.at(20, 12, 22)));
        Map<BlockVector3, Boolean> tube = Sweeper.sweep(path, profile(SweepProfile.CIRCLE, 5), false);
        // everything within 2 blocks of the path is solid (away from the flat end caps)
        for (int i = 12; i < path.size() - 12; i++) {
            Vector3 point = path.position(i);
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockVector3 block = point.toBlockPoint().add(x, y, z);
                        if (block.toVector3().add(0.5, 0.5, 0.5).distance(point) <= 2.0) {
                            assertTrue(tube.containsKey(block), "gap at " + block + " near sample " + i);
                        }
                    }
                }
            }
        }
        // and nothing much further away
        for (BlockVector3 block : tube.keySet()) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < path.size(); i++) {
                best = Math.min(best, block.toVector3().add(0.5, 0.5, 0.5).distance(path.position(i)));
            }
            assertTrue(best <= 2.7, "stray block " + block + " at " + best);
        }
    }

    @Test
    void upIsUpAlongALevelCurve() {
        SweepPath path = SweepPath.through(List.of(
            BlockVector3.at(0, 5, 0), BlockVector3.at(10, 5, 10), BlockVector3.at(0, 5, 20)));
        for (int i = 0; i < path.size(); i++) {
            assertEquals(1, path.up(i).y(), 1e-6);
            assertEquals(0, path.right(i).y(), 1e-6);
        }
        assertTrue(path.length() > 20);
    }

    @Test
    void verticalPathKeepsAStableFrame() {
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(0, 10, 0)));
        Vector3 right = path.right(0);
        for (int i = 0; i < path.size(); i++) {
            assertEquals(right, path.right(i));
        }
        Map<BlockVector3, Boolean> column = Sweeper.sweep(path, profile(SweepProfile.SQUARE, 3), false);
        assertEquals(99, column.size());
    }

    @Test
    void hollowTubeIsOpenAtTheEnds() {
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 10, 0), BlockVector3.at(9, 10, 0)));
        Map<BlockVector3, Boolean> tube = Sweeper.sweep(path, profile(SweepProfile.SQUARE, 5), true);
        // 10 slices of a 5 x 5 square outline
        assertEquals(160, tube.size());
        assertFalse(tube.containsKey(BlockVector3.at(0, 10, 0)));
        assertFalse(tube.containsKey(BlockVector3.at(9, 10, 0)));
        assertTrue(tube.containsKey(BlockVector3.at(0, 12, 0)));
    }

    @Test
    void archIsOpenAtTheBottom() {
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(0, 0, 4)));
        Map<BlockVector3, Boolean> arch = Sweeper.sweep(path, profile(SweepProfile.ARCH, 7), false);
        // looking south, right is west (-x)
        for (int z = 0; z <= 4; z++) {
            assertTrue(arch.containsKey(BlockVector3.at(3, 0, z)), "left leg");
            assertTrue(arch.containsKey(BlockVector3.at(-3, 0, z)), "right leg");
            assertTrue(arch.containsKey(BlockVector3.at(0, 6, z)), "keystone");
            for (int x = -2; x <= 2; x++) {
                assertFalse(arch.containsKey(BlockVector3.at(x, 0, z)), "the floor is open");
            }
            assertFalse(arch.containsKey(BlockVector3.at(0, 4, z)), "the opening");
            assertFalse(arch.containsKey(BlockVector3.at(0, 7, z)));
        }
    }

    @Test
    void railHasRailsAndSleepers() {
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 0, 0)));
        Map<BlockVector3, Boolean> rail = Sweeper.sweep(path, profile(SweepProfile.RAIL, 3, "spacing=3"), false);
        for (int x = 0; x <= 10; x++) {
            assertTrue(rail.containsKey(BlockVector3.at(x, 1, -1)));
            assertTrue(rail.containsKey(BlockVector3.at(x, 1, 1)));
            assertFalse(rail.containsKey(BlockVector3.at(x, 1, 0)));
            assertEquals(x % 3 == 0, rail.containsKey(BlockVector3.at(x, 0, 0)), "sleeper at " + x);
            assertEquals(x % 3 == 0, rail.containsKey(BlockVector3.at(x, 0, 2)), "sleeper end at " + x);
        }
    }

    @Test
    void drawingProfileKeepsItsMaterials() {
        Map<BlockVector3, String> blocks = new HashMap<>();
        // a T drawn on the plane x = 0: a glass top bar and a stone stem; the path runs through the stem bottom
        for (int z = -2; z <= 2; z++) {
            blocks.put(BlockVector3.at(0, 2, z), "glass");
        }
        blocks.put(BlockVector3.at(0, 1, 0), "stone");
        blocks.put(BlockVector3.at(0, 0, 0), "stone");
        FlatDrawing<String> drawing = FlatDrawing.of(blocks, null, false);
        assertEquals(FlatDrawing.Axis.X, drawing.getThinAxis());
        CrossSection<String> section = CrossSection.of(drawing, 0, 0);
        SweepPath path = SweepPath.through(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(6, 0, 0)));
        Map<BlockVector3, String> wall = Sweeper.sweep(path, section, false);
        assertEquals(7 * 7, wall.size());
        for (int x = 0; x <= 6; x++) {
            assertEquals("stone", wall.get(BlockVector3.at(x, 0, 0)));
            assertEquals("stone", wall.get(BlockVector3.at(x, 1, 0)));
            for (int z = -2; z <= 2; z++) {
                assertEquals("glass", wall.get(BlockVector3.at(x, 2, z)));
            }
        }
    }

    @Test
    void refusesBadPaths() {
        assertThrows(IllegalArgumentException.class, () -> SweepPath.through(List.of(BlockVector3.ZERO)));
        assertThrows(IllegalArgumentException.class,
            () -> SweepPath.through(List.of(BlockVector3.ZERO, BlockVector3.ZERO)));
        assertTrue(SweepProfile.find("RING").isPresent());
        assertFalse(SweepProfile.find("blob").isPresent());
    }
}
