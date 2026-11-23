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

package com.sk89q.worldedit.function.generator.architecture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofTest {

    private static String error(String... words) {
        return assertThrows(IllegalArgumentException.class, () -> Roof.parse(List.of(words))).getMessage();
    }

    @Test
    void typeAndPatternInEitherOrder() {
        assertEquals(new Roof.Request(Roof.Type.HIP, "stone", 0, 1), Roof.parse(List.of("hip", "stone")));
        assertEquals(new Roof.Request(Roof.Type.HIP, "stone", 2, 0.5), Roof.parse(List.of("stone", "HIP", "2", "0.5")));
        assertEquals(new Roof.Request(Roof.Type.GABLE, "50%stone,50%andesite", 1, 1),
            Roof.parse(List.of("50%stone,50%andesite", "1")));
    }

    @Test
    void mistakesGetClearMessages() {
        String unknown = error("banana", "stone");
        assertTrue(unknown.contains("Unknown roof type 'banana'"), unknown);
        assertTrue(unknown.contains("gable, hip, shed, curved or mansard"), unknown);
        String noPattern = error("gable");
        assertTrue(noPattern.startsWith("Give a pattern for the roof"), noPattern);
        assertTrue(error("hip", "stone", "1", "1", "1").startsWith("Too many numbers"));
        assertTrue(error("hip", "stone", "-1").contains("overhang"));
        assertTrue(error("hip", "stone", "1.5").contains("overhang"));
        assertTrue(error("hip", "stone", "0", "100000").contains("pitch"));
        assertTrue(error("hip", "stone", "0", "0").contains("pitch"));
    }

    @Test
    void pitchChangesTheHeightOfFlatAndTallSelections() {
        // flat 11 x 11 footprint: rises 5 blocks at pitch 1
        assertEquals(5, Roof.rise(11, 11, 1, 1), 1e-9);
        assertEquals(2.5, Roof.rise(11, 11, 1, 0.5), 1e-9);
        // tall selection, 7 blocks high: the ridge is 6 above the bottom at pitch 1
        assertEquals(6, Roof.rise(11, 11, 7, 1), 1e-9);
        assertEquals(12, Roof.rise(11, 11, 7, 2), 1e-9);
        assertTrue(Roof.rise(11, 11, 7, 0.5) < Roof.rise(11, 11, 7, 1));
    }

    @Test
    void gableRisesToTheRidge() {
        int[][] heights = Roof.heights(Roof.Type.GABLE, 11, 15, 5);
        assertEquals(0, heights[0][7]);
        assertEquals(5, heights[5][7]);
        assertEquals(0, heights[10][7]);
        // gable ends: the height does not change along the ridge
        assertEquals(heights[3][0], heights[3][14]);
        int[][] hip = Roof.heights(Roof.Type.HIP, 11, 15, 5);
        assertEquals(0, hip[5][0]);
        assertEquals(5, hip[5][7]);
    }

    @Test
    void insetFaceMargins() {
        assertEquals(5, InsetFaces.maxMargin(11, 7, 11));
        assertEquals(3, InsetFaces.maxMargin(1, 7, 11));
        assertEquals(6, InsetFaces.faceCount(11, 7, 11, 1));
        // a one-block thick wall has one panel face (both sides are the same blocks) plus none on the thin sides
        assertEquals(1, InsetFaces.faceCount(11, 7, 1, 1));
        assertEquals(0, InsetFaces.faceCount(11, 7, 11, 6));
    }
}
