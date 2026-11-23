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

/**
 * The numbers {@code //measure} shows for two blocks. Sizes count both end blocks, as WorldEdit's
 * selection sizes do: from x=0 to x=10 is 11 blocks wide. Distances are between block centres, so
 * from x=0 to x=10 is 10 blocks apart.
 *
 * @param from the first block
 * @param to the second block
 * @param dx blocks to go east (negative = west) from the first block to the second
 * @param dy blocks to go up (negative = down)
 * @param dz blocks to go south (negative = north)
 * @param sizeX the width of the box spanned by both blocks, counting both
 * @param sizeY the height of that box
 * @param sizeZ the length of that box
 */
public record Measurement(BlockVector3 from, BlockVector3 to, int dx, int dy, int dz,
                          int sizeX, int sizeY, int sizeZ) {

    /**
     * Measure from one block to another.
     *
     * @param from the first block
     * @param to the second block
     * @return the measurement
     */
    public static Measurement between(BlockVector3 from, BlockVector3 to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        return new Measurement(from, to, dx, dy, dz, Math.abs(dx) + 1, Math.abs(dy) + 1, Math.abs(dz) + 1);
    }

    /**
     * The straight-line distance between the block centres.
     *
     * @return the distance in blocks
     */
    public double distance() {
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    /**
     * The distance ignoring height.
     *
     * @return the distance in blocks
     */
    public double horizontalDistance() {
        return Math.hypot(dx, dz);
    }

    /**
     * The distance walking only along the axes.
     *
     * @return the distance in blocks
     */
    public long walkingDistance() {
        return (long) Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
    }

    /**
     * The compass direction from the first block to the second, as Minecraft's yaw.
     *
     * @return degrees, 0 = south, 90 = west, 180/-180 = north, -90 = east
     */
    public double yaw() {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * The up/down angle from the first block to the second, as Minecraft's pitch.
     *
     * @return degrees, negative = looking up, positive = looking down
     */
    public double pitch() {
        return Math.toDegrees(Math.atan2(-dy, horizontalDistance()));
    }

    /**
     * The number of blocks in the box spanned by both blocks.
     *
     * @return the volume
     */
    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    /**
     * The number of block faces on the outside of that box.
     *
     * @return the surface area
     */
    public long surfaceArea() {
        return 2L * ((long) sizeX * sizeY + (long) sizeY * sizeZ + (long) sizeX * sizeZ);
    }
}
