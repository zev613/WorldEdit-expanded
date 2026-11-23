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

package com.sk89q.worldedit.function.generator.nature;

import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.math.Vector3;

import java.util.Random;

/**
 * Small helpers shared by the nature generators.
 */
final class NatureMath {

    private NatureMath() {
    }

    /**
     * The seed setting shared by all nature generators.
     */
    static final ShapeParameter SEED = new ShapeParameter("seed", -1, -1, Integer.MAX_VALUE,
        "the same seed builds the same thing again (-1 = random)");

    /**
     * Fill a ball.
     *
     * @param model the model
     * @param center the middle
     * @param radius the radius
     * @param part the part number
     */
    static void ball(NatureModel model, Vector3 center, double radius, int part) {
        int x0 = (int) Math.floor(center.x() - radius);
        int x1 = (int) Math.ceil(center.x() + radius);
        int y0 = (int) Math.floor(center.y() - radius);
        int y1 = (int) Math.ceil(center.y() + radius);
        int z0 = (int) Math.floor(center.z() - radius);
        int z1 = (int) Math.ceil(center.z() + radius);
        double r2 = radius * radius;
        for (int x = x0; x <= x1; x++) {
            double dx = x - center.x();
            for (int y = y0; y <= y1; y++) {
                double dy = y - center.y();
                for (int z = z0; z <= z1; z++) {
                    double dz = z - center.z();
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        model.set(x, y, z, part);
                    }
                }
            }
        }
    }

    /**
     * Two unit vectors at right angles to a direction and to each other.
     *
     * @param direction a unit vector
     * @return the two vectors
     */
    static Vector3[] perpendiculars(Vector3 direction) {
        Vector3 helper = Math.abs(direction.y()) < 0.9 ? Vector3.UNIT_Y : Vector3.UNIT_X;
        Vector3 u = direction.cross(helper).normalize();
        Vector3 v = direction.cross(u).normalize();
        return new Vector3[] {u, v};
    }

    /**
     * A unit vector from angles.
     *
     * @param azimuth the angle round the vertical, in radians
     * @param elevation the angle above the horizontal, in radians
     * @return the vector
     */
    static Vector3 direction(double azimuth, double elevation) {
        double horizontal = Math.cos(elevation);
        return Vector3.at(Math.cos(azimuth) * horizontal, Math.sin(elevation), Math.sin(azimuth) * horizontal);
    }

    /**
     * A random number in a range.
     *
     * @param random the random
     * @param min the smallest value
     * @param max the largest value
     * @return the number
     */
    static double between(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
