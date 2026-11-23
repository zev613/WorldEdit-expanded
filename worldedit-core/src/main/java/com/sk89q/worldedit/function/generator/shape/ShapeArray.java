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

import java.util.ArrayList;
import java.util.List;

/**
 * Where to put the copies of a shape built with {@code //shape ... -a <count>}: in a line
 * ({@code -l <spacing>}), in a ring ({@code -c <radius>}), and optionally rising by
 * {@code -s <rise>} blocks per copy (a staircase for a line, a spiral for a ring).
 *
 * @param count the number of copies, at least 1
 * @param spacing the distance between copies in a line, in blocks (0 when using a ring)
 * @param ringRadius the radius of the ring, in blocks (0 when using a line)
 * @param rise the height added per copy, in blocks
 */
public record ShapeArray(int count, double spacing, double ringRadius, double rise) {

    /**
     * The most copies allowed in one command.
     */
    public static final int MAX_COPIES = 1000;

    /**
     * A single copy at the position.
     */
    public static final ShapeArray SINGLE = new ShapeArray(1, 0, 0, 0);

    /**
     * Check the flags typed by the player and work out the array.
     *
     * @param count the {@code -a} value, or 0 if not given
     * @param spacing the {@code -l} value, or 0 if not given
     * @param ringRadius the {@code -c} value, or 0 if not given
     * @param rise the {@code -s} value, or 0 if not given
     * @param defaultSpacing the spacing of a line when only {@code -a} is given
     * @return the array
     * @throws IllegalArgumentException with a message for the player if the flags don't make sense
     */
    public static ShapeArray of(double count, double spacing, double ringRadius, double rise, double defaultSpacing) {
        if (count == 0) {
            if (spacing != 0 || ringRadius != 0 || rise != 0) {
                throw new IllegalArgumentException("Add -a <count> to say how many copies to build");
            }
            return SINGLE;
        }
        if (count != Math.rint(count) || count < 1) {
            throw new IllegalArgumentException("-a needs a whole number of copies, 1 or more");
        }
        if (count > MAX_COPIES) {
            throw new IllegalArgumentException("At most " + MAX_COPIES + " copies at once");
        }
        if (spacing != 0 && ringRadius != 0) {
            throw new IllegalArgumentException("Use either -l (a line) or -c (a ring), not both");
        }
        if (ringRadius < 0) {
            throw new IllegalArgumentException("-c needs a ring radius above 0");
        }
        if (!Double.isFinite(spacing) || !Double.isFinite(ringRadius) || !Double.isFinite(rise)) {
            throw new IllegalArgumentException("Array distances must be normal numbers");
        }
        if (spacing == 0 && ringRadius == 0) {
            spacing = defaultSpacing;
        }
        return new ShapeArray((int) count, spacing, ringRadius, rise);
    }

    /**
     * Whether the copies go around a ring.
     *
     * @return true for a ring or spiral
     */
    public boolean isRing() {
        return ringRadius > 0;
    }

    /**
     * The offset of each copy from the placement position.
     *
     * @param facing the direction the player faces; a line goes this way and a ring starts this way.
     *     Only the horizontal part is used by a ring.
     * @return one offset per copy, the first one first
     */
    public List<BlockVector3> offsets(Vector3 facing) {
        List<BlockVector3> result = new ArrayList<>(count);
        if (isRing()) {
            double start = Math.atan2(facing.z(), facing.x());
            if (facing.x() == 0 && facing.z() == 0) {
                start = 0;
            }
            for (int i = 0; i < count; i++) {
                double angle = start + 2 * Math.PI * i / count;
                result.add(BlockVector3.at(
                    (int) Math.round(ringRadius * Math.cos(angle)),
                    (int) Math.round(rise * i),
                    (int) Math.round(ringRadius * Math.sin(angle))));
            }
        } else {
            Vector3 step = facing.lengthSq() == 0 ? Vector3.UNIT_X : facing.normalize();
            for (int i = 0; i < count; i++) {
                result.add(BlockVector3.at(
                    (int) Math.round(step.x() * spacing * i),
                    (int) Math.round(step.y() * spacing * i + rise * i),
                    (int) Math.round(step.z() * spacing * i)));
            }
        }
        return result;
    }

    /**
     * The direction a line of copies should go for a player looking this way: straight up or down
     * when looking steeply up or down, otherwise level, in the exact direction the player faces.
     *
     * @param yaw the player's yaw in degrees (0 = south, 90 = west), or NaN if unknown
     * @param pitch the player's pitch in degrees (-90 = up), or NaN if unknown
     * @return a unit vector; east when the direction is unknown
     */
    public static Vector3 facing(double yaw, double pitch) {
        if (Double.isNaN(yaw)) {
            return Vector3.UNIT_X;
        }
        if (!Double.isNaN(pitch) && pitch > 67.5) {
            return Vector3.at(0, -1, 0);
        }
        if (!Double.isNaN(pitch) && pitch < -67.5) {
            return Vector3.UNIT_Y;
        }
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        return Vector3.at(Math.abs(x) < 1e-9 ? 0 : x, 0, Math.abs(z) < 1e-9 ? 0 : z);
    }
}
