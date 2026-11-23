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

package com.sk89q.worldedit.function.clipboard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementMathTest {

    private static final int M = ConformOffsets.MISSING;

    @Test
    void missingColumnsTakeTheNearestShift() {
        int[][] filled = ConformOffsets.fillMissing(new int[][] { { 3, M, M, M, 7 } });
        assertArrayEquals(new int[] { 3, 3, 3, 7, 7 }, filled[0]);
    }

    @Test
    void noGroundAnywhereGivesNull() {
        assertNull(ConformOffsets.compute(new int[][] { { M, M }, { M, M } }, 1));
    }

    @Test
    void radiusZeroFollowsTheTerrainExactly() {
        int[][] raw = { { 0, 1, 5, -3 } };
        assertArrayEquals(raw[0], ConformOffsets.compute(raw, 0)[0]);
    }

    @Test
    void evenSlopeIsKeptExactly() {
        // a road along x on a hill rising one block per column, three columns wide in z
        int[][] raw = new int[10][3];
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 3; z++) {
                raw[x][z] = x;
            }
        }
        int[][] smooth = ConformOffsets.smooth(raw, 1);
        for (int x = 1; x < 9; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(x, smooth[x][z]);
            }
        }
    }

    @Test
    void singleBumpIsSmoothedAway() {
        int[][] raw = new int[5][3];
        raw[2][1] = 1;
        int[][] smooth = ConformOffsets.smooth(raw, 1);
        assertEquals(0, smooth[2][1]);
    }

    @Test
    void smoothingStaysWithinOneOfTheTerrain() {
        int[][] raw = { { 0, 0, 0, 10, 10, 10 } };
        int[][] smooth = ConformOffsets.smooth(raw, 3);
        for (int i = 0; i < raw[0].length; i++) {
            assertTrue(Math.abs(smooth[0][i] - raw[0][i]) <= 1, "column " + i);
        }
    }

    @Test
    void radiusOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ConformOffsets.smooth(new int[][] { { 0 } }, 9));
    }

    @Test
    void forwardSnapsToAxes() {
        assertArrayEquals(new int[] { 0, 1 }, GridLayout.forwardFromYaw(0));
        assertArrayEquals(new int[] { -1, 0 }, GridLayout.forwardFromYaw(90));
        assertArrayEquals(new int[] { 0, -1 }, GridLayout.forwardFromYaw(180));
        assertArrayEquals(new int[] { 1, 0 }, GridLayout.forwardFromYaw(-90));
        assertArrayEquals(new int[] { 1, 0 }, GridLayout.forwardFromYaw(275));
    }

    @Test
    void rightHandSide() {
        // facing south (+z) the right hand points west (-x); facing east (+x) it points south (+z)
        assertArrayEquals(new int[] { -1, 0 }, GridLayout.rightOf(new int[] { 0, 1 }));
        assertArrayEquals(new int[] { 0, 1 }, GridLayout.rightOf(new int[] { 1, 0 }));
    }

    @Test
    void cityBlocksWithStreets() {
        List<GridLayout.Cell> cells = GridLayout.layout(5, 5, 6, 6, 20, 20,
            new int[] { 1, 0 }, new int[] { 0, 1 }, 0, null);
        assertEquals(25, cells.size());
        GridLayout.Cell last = cells.getLast();
        assertEquals(4, last.column());
        assertEquals(4, last.row());
        assertEquals(4 * 26, last.dx());
        assertEquals(4 * 26, last.dz());
        // the gap between copy 0 (x 0-19) and copy 1 (x 26-45) is 6 blocks
        assertEquals(26, cells.get(1).dx());
    }

    @Test
    void negativeDirectionsAndRectangularCopies() {
        // facing east: forward +x, right +z; copy is 4 (x) by 2 (z)
        List<GridLayout.Cell> cells = GridLayout.layout(2, 2, 1, 3, 4, 2,
            new int[] { 0, 1 }, new int[] { 1, 0 }, 0, null);
        assertEquals(new GridLayout.Cell(1, 0, 0, 0, 0, 3), cells.get(1));
        assertEquals(new GridLayout.Cell(0, 1, 0, 7, 0, 0), cells.get(2));
        // facing south: right is -x
        cells = GridLayout.layout(3, 1, 2, 0, 4, 2, new int[] { -1, 0 }, new int[] { 0, 1 }, 0, null);
        assertEquals(-6, cells.get(1).dx());
        assertEquals(-12, cells.get(2).dx());
    }

    @Test
    void riseStepsEachRow() {
        List<GridLayout.Cell> cells = GridLayout.layout(2, 3, 0, 0, 3, 3,
            new int[] { 1, 0 }, new int[] { 0, 1 }, 2, null);
        assertEquals(0, cells.get(1).dy());
        assertEquals(2, cells.get(2).dy());
        assertEquals(4, cells.get(5).dy());
    }

    @Test
    void randomTurnsAreSeededAndCentred() {
        List<GridLayout.Cell> a = GridLayout.layout(4, 4, 1, 1, 6, 2,
            new int[] { 1, 0 }, new int[] { 0, 1 }, 0, new Random(42));
        List<GridLayout.Cell> b = GridLayout.layout(4, 4, 1, 1, 6, 2,
            new int[] { 1, 0 }, new int[] { 0, 1 }, 0, new Random(42));
        assertEquals(a, b);
        boolean sawTurned = false;
        for (GridLayout.Cell cell : a) {
            assertTrue(cell.quarterTurns() >= 0 && cell.quarterTurns() < 4);
            // cells are 6x6 squares with a gap of 1
            int cellX = cell.column() * 7;
            int cellZ = cell.row() * 7;
            if (cell.quarterTurns() % 2 == 0) {
                assertEquals(cellX, cell.dx());
                assertEquals(cellZ + 2, cell.dz());
            } else {
                sawTurned = true;
                assertEquals(cellX + 2, cell.dx());
                assertEquals(cellZ, cell.dz());
            }
        }
        assertTrue(sawTurned);
    }

    @Test
    void limitsGiveFriendlyErrors() {
        assertThrows(IllegalArgumentException.class, () -> GridLayout.check(101, 100, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GridLayout.check(0, 5, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GridLayout.check(5, 5, -1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GridLayout.check(100, 100, 0, 0, 0, 10_000));
        GridLayout.check(100, 100, 6, 6, 1, 1000);
    }
}
