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

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingModellerTest {

    /**
     * A filled rectangle drawn on the plane z = 5, from x = 10 to 10 + width - 1 and y = 20 to 20 + height - 1,
     * with a gold top row.
     */
    private static Map<BlockVector3, String> rectangle(int width, int height) {
        Map<BlockVector3, String> blocks = new HashMap<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blocks.put(BlockVector3.at(10 + x, 20 + y, 5), y == height - 1 ? "gold" : "stone");
            }
        }
        return blocks;
    }

    @Test
    void detectsTheThinAxis() {
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(4, 3), null, false);
        assertEquals(FlatDrawing.Axis.Z, drawing.getThinAxis());
        assertEquals(10, drawing.getMinU());
        assertEquals(13, drawing.getMaxU());
        assertEquals("gold", drawing.get(11, 22));
        assertEquals("stone", drawing.get(11, 20));
        assertNull(drawing.get(14, 20));

        Map<BlockVector3, String> wall = new HashMap<>();
        for (int z = 0; z < 5; z++) {
            for (int y = 0; y < 3; y++) {
                wall.put(BlockVector3.at(7, y, z), "a");
                wall.put(BlockVector3.at(8, y, z), "b");
            }
        }
        FlatDrawing<String> thick = FlatDrawing.of(wall, null, false);
        assertEquals(FlatDrawing.Axis.X, thick.getThinAxis());
        assertEquals("a", thick.get(2, 1), "the low layer wins");
        assertEquals(15, thick.count());

        Map<BlockVector3, String> floor = new HashMap<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                floor.put(BlockVector3.at(x, 3, z), "a");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> FlatDrawing.of(floor, null, false));
        assertEquals(FlatDrawing.Axis.Y, FlatDrawing.of(floor, null, true).getThinAxis());

        Map<BlockVector3, String> cube = new HashMap<>();
        for (int i = 0; i < 27; i++) {
            cube.put(BlockVector3.at(i % 3, i / 9, (i / 3) % 3), "a");
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> FlatDrawing.of(cube, null, true));
        assertTrue(error.getMessage().contains("not flat"));
        assertThrows(IllegalArgumentException.class, () -> FlatDrawing.of(Map.of(), null, true));
    }

    @Test
    void revolvingAFilledRectangleMakesASolidCylinderWithNoHoles() {
        // axis on the left edge of the drawing (x = 10): radius 0..5
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(6, 4), null, false);
        Map<BlockVector3, String> model = DrawingModeller.revolve(drawing, 10, 360);
        int expectedPerLayer = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean inside = Math.round(Math.sqrt(dx * dx + dz * dz)) <= 5;
                if (inside) {
                    expectedPerLayer++;
                }
                for (int y = 20; y < 24; y++) {
                    String block = model.get(BlockVector3.at(10 + dx, y, 5 + dz));
                    if (inside) {
                        assertEquals(y == 23 ? "gold" : "stone", block, "at " + dx + "," + y + "," + dz);
                    } else {
                        assertNull(block);
                    }
                }
            }
        }
        assertEquals(expectedPerLayer * 4, model.size());
    }

    @Test
    void revolvingAWallMakesAClosedRing() {
        // a 1-block-wide wall 4 blocks from the axis
        Map<BlockVector3, String> blocks = new HashMap<>();
        for (int y = 0; y < 3; y++) {
            blocks.put(BlockVector3.at(4, y, 0), "brick");
        }
        FlatDrawing<String> drawing = FlatDrawing.of(blocks, null, false);
        Map<BlockVector3, String> model = DrawingModeller.revolve(drawing, 0, 360);
        // walking around the ring, every step to the next angle stays on a block or a face/edge neighbour
        for (int degree = 0; degree < 360; degree++) {
            double angle = Math.toRadians(degree);
            BlockVector3 spot = BlockVector3.at((int) Math.round(4 * Math.cos(angle)), 1, (int) Math.round(4 * Math.sin(angle)));
            assertTrue(model.containsKey(spot), "hole at " + spot);
        }
        assertFalse(model.containsKey(BlockVector3.at(0, 1, 0)));
    }

    @Test
    void partialRevolveCoversOnlyThatAngle() {
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(6, 1), null, false);
        Map<BlockVector3, String> quarter = DrawingModeller.revolve(drawing, 10, 90);
        for (BlockVector3 block : quarter.keySet()) {
            // clockwise from above, starting east: east then south (+z)
            assertTrue(block.x() >= 10 && block.z() >= 5, "outside the quarter: " + block);
        }
        assertTrue(quarter.containsKey(BlockVector3.at(15, 20, 5)));
        assertTrue(quarter.containsKey(BlockVector3.at(10, 20, 10)));
        assertTrue(quarter.containsKey(BlockVector3.at(13, 20, 8)));
        assertThrows(IllegalArgumentException.class, () -> DrawingModeller.revolve(drawing, 10, 0));
    }

    @Test
    void axisInTheMiddleUsesBothSides() {
        // a whole drawing, axis in its middle: the left half is a different material
        Map<BlockVector3, String> blocks = new HashMap<>();
        for (int x = -3; x <= 3; x++) {
            blocks.put(BlockVector3.at(x, 0, 0), x < 0 ? "left" : "right");
        }
        FlatDrawing<String> drawing = FlatDrawing.of(blocks, null, false);
        Map<BlockVector3, String> half = DrawingModeller.revolve(drawing, 0, 180);
        assertEquals("right", half.get(BlockVector3.at(0, 0, 3)));
        assertEquals("left", half.get(BlockVector3.at(0, 0, -3)));
        assertEquals("left", half.get(BlockVector3.at(-3, 0, -1)));
        assertEquals("right", half.get(BlockVector3.at(-3, 0, 1)));
    }

    @Test
    void extrudeCopiesTheDrawing() {
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(3, 3), null, false);
        Map<BlockVector3, String> model = DrawingModeller.extrude(drawing, 1, 5, 0, 1);
        assertEquals(45, model.size());
        for (int z = 6; z <= 10; z++) {
            assertEquals("gold", model.get(BlockVector3.at(11, 22, z)));
            assertEquals("stone", model.get(BlockVector3.at(10, 20, z)));
        }
        Map<BlockVector3, String> back = DrawingModeller.extrude(drawing, -1, 2, 0, 1);
        assertTrue(back.containsKey(BlockVector3.at(10, 20, 3)));
        assertFalse(back.containsKey(BlockVector3.at(10, 20, 5)));
    }

    @Test
    void taperedExtrudeHasNoHoles() {
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(5, 5), null, false);
        Map<BlockVector3, String> model = DrawingModeller.extrude(drawing, 1, 4, 0, 3);
        // the last layer is three times as big: a solid 15 x 15 square around the middle (12, 22)
        for (int x = 12 - 7; x <= 12 + 7; x++) {
            for (int y = 22 - 7; y <= 22 + 7; y++) {
                assertTrue(model.containsKey(BlockVector3.at(x, y, 9)), "hole at " + x + "," + y);
            }
        }
        assertEquals("gold", model.get(BlockVector3.at(12, 22 + 7, 9)));
        Map<BlockVector3, String> small = DrawingModeller.extrude(drawing, 1, 4, 0, 0.2);
        assertEquals(1, small.keySet().stream().filter(b -> b.z() == 9).count());
    }

    @Test
    void twistedExtrudeHasNoHoles() {
        FlatDrawing<String> drawing = FlatDrawing.of(rectangle(7, 7), null, false);
        Map<BlockVector3, String> model = DrawingModeller.extrude(drawing, 1, 8, 360, 1);
        for (int z = 6; z <= 13; z++) {
            // a disc around the middle is always inside the turned square
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    if (x * x + y * y <= 9) {
                        assertTrue(model.containsKey(BlockVector3.at(13 + x, 23 + y, z)),
                            "hole at " + x + "," + y + " layer " + z);
                    }
                }
            }
        }
        // the 45 degree layer reaches further out than the drawing along the diagonal
        long layer = model.keySet().stream().filter(b -> b.z() == 6).count();
        assertTrue(layer >= 40 && layer <= 60, "layer size " + layer);
        assertTrue(model.containsKey(BlockVector3.at(13, 23 + 4, 6)));
    }
}
