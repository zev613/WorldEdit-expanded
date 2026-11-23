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

import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.noise.PerlinNoise;

/**
 * Repeatable smooth noise for natural shapes, built on {@link PerlinNoise}.
 */
final class SeededNoise {

    private final PerlinNoise perlin = new PerlinNoise();

    /**
     * Create noise.
     *
     * @param seed the generator's seed
     * @param salt a number that makes different uses of the same seed differ
     * @param frequency features per unit of the coordinates passed in
     * @param octaves the number of layers of detail
     */
    SeededNoise(long seed, int salt, double frequency, int octaves) {
        perlin.setSeed((int) (seed ^ (seed >>> 32)) * 31 + salt * 1013);
        perlin.setFrequency(frequency);
        perlin.setOctaveCount(octaves);
    }

    /**
     * Noise from -1 to 1.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return the value
     */
    double signed(double x, double y, double z) {
        // Perlin noise is zero on whole numbers, so shift off the grid
        return perlin.noise(Vector3.at(x + 0.3183, y + 0.5772, z + 0.1415)) * 2.0 - 1.0;
    }

    /**
     * Noise from 0 to 1.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return the value
     */
    double unit(double x, double y, double z) {
        return (signed(x, y, z) + 1) / 2;
    }
}
