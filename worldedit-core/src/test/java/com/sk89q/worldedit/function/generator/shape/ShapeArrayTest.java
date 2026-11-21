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

package com.sk89q.worldedit.function.generator.shape;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeArrayTest {

    @Test
    void noFlagsMeansOneCopyAtThePosition() {
        ShapeArray array = ShapeArray.of(0, 0, 0, 0, 10);
        assertEquals(List.of(BlockVector3.ZERO), array.offsets(Vector3.UNIT_X));
    }

    @Test
    void lineFollowsTheFacingDirection() {
        ShapeArray array = ShapeArray.of(3, 20, 0, 0, 10);
        assertEquals(List.of(BlockVector3.ZERO, BlockVector3.at(0, 0, 20), BlockVector3.at(0, 0, 40)),
            array.offsets(ShapeArray.facing(0, 10)));
        // facing west (yaw 90)
        assertEquals(BlockVector3.at(-20, 0, 0), array.offsets(ShapeArray.facing(90, 0)).get(1));
    }

    @Test
    void lineWithoutSpacingUsesTheDefault() {
        ShapeArray array = ShapeArray.of(2, 0, 0, 0, 12);
        assertEquals(BlockVector3.at(12, 0, 0), array.offsets(Vector3.UNIT_X).get(1));
    }

    @Test
    void lineWithRiseIsAStaircase() {
        ShapeArray array = ShapeArray.of(3, 10, 0, 5, 12);
        assertEquals(BlockVector3.at(20, 10, 0), array.offsets(Vector3.UNIT_X).get(2));
    }

    @Test
    void ringPutsCopiesAroundThePosition() {
        ShapeArray array = ShapeArray.of(4, 0, 30, 0, 10);
        List<BlockVector3> offsets = array.offsets(Vector3.UNIT_X);
        assertEquals(List.of(BlockVector3.at(30, 0, 0), BlockVector3.at(0, 0, 30),
            BlockVector3.at(-30, 0, 0), BlockVector3.at(0, 0, -30)), offsets);
        ShapeArray many = ShapeArray.of(12, 0, 25, 0, 10);
        for (BlockVector3 offset : many.offsets(Vector3.UNIT_Z)) {
            assertEquals(25, offset.toVector3().length(), 0.75);
        }
        assertEquals(12, new HashSet<>(many.offsets(Vector3.UNIT_Z)).size());
    }

    @Test
    void spiralRisesPerCopy() {
        ShapeArray array = ShapeArray.of(8, 0, 20, 3, 10);
        List<BlockVector3> offsets = array.offsets(Vector3.UNIT_X);
        for (int i = 0; i < offsets.size(); i++) {
            assertEquals(3 * i, offsets.get(i).y());
        }
    }

    @Test
    void facingFallsBackToEastAndHandlesSteepPitch() {
        assertEquals(Vector3.UNIT_X, ShapeArray.facing(Double.NaN, Double.NaN));
        assertEquals(Vector3.UNIT_Y, ShapeArray.facing(0, -80));
        assertEquals(Vector3.at(0, -1, 0), ShapeArray.facing(0, 80));
        Vector3 diagonal = ShapeArray.facing(-45, 0);
        assertEquals(1, diagonal.length(), 1e-9);
        assertTrue(diagonal.x() > 0 && diagonal.z() > 0);
    }

    @Test
    void badCombinationsAreReported() {
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(0, 10, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(0, 0, 0, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(3, 10, 10, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(2.5, 10, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(-1, 10, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(3, 0, -5, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShapeArray.of(ShapeArray.MAX_COPIES + 1, 5, 0, 0, 10));
    }
}
