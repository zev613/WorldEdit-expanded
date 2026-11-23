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

package com.sk89q.worldedit.util.buildtools;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeasurementTest {

    @Test
    void sizesCountBothEndBlocks() {
        Measurement m = Measurement.between(BlockVector3.at(50, 2, 50), BlockVector3.at(0, 0, 0));
        assertEquals(-50, m.dx());
        assertEquals(51, m.sizeX());
        assertEquals(3, m.sizeY());
        assertEquals(51, m.sizeZ());
        assertEquals(51L * 3 * 51, m.volume());
        assertEquals(Math.sqrt(50 * 50 + 4 + 50 * 50), m.distance(), 1e-9);
        assertEquals(102, m.walkingDistance());
    }

    @Test
    void flatAndSingleBlockMeasurementsHaveVolume() {
        Measurement flat = Measurement.between(BlockVector3.at(0, 5, 0), BlockVector3.at(9, 5, 4));
        assertEquals(50, flat.volume());
        assertEquals(2L * (10 + 5 + 50), flat.surfaceArea());
        Measurement one = Measurement.between(BlockVector3.at(3, 3, 3), BlockVector3.at(3, 3, 3));
        assertEquals(1, one.volume());
        assertEquals(6, one.surfaceArea());
        assertEquals(0, one.distance());
    }

    @Test
    void anglesFollowMinecraft() {
        // south is yaw 0, east is -90, straight up is pitch -90
        assertEquals(0, Measurement.between(BlockVector3.ZERO, BlockVector3.at(0, 0, 5)).yaw(), 1e-9);
        assertEquals(-90, Measurement.between(BlockVector3.ZERO, BlockVector3.at(5, 0, 0)).yaw(), 1e-9);
        assertEquals(-90, Measurement.between(BlockVector3.ZERO, BlockVector3.at(0, 5, 0)).pitch(), 1e-9);
    }
}
