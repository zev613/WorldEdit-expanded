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

package com.sk89q.worldedit.function.generator.age;

/**
 * Repeatable randomness for the ageing generators: per-block hashes and smooth
 * value noise for clumped (rather than speckled) results. Everything depends
 * only on the seed and the position, never on the order blocks are visited.
 */
final class AgeNoise {

    private final long seed;
    private final double scale;

    /**
     * Create smooth noise.
     *
     * @param seed the seed
     * @param salt makes different uses of one seed differ
     * @param scale the feature size in blocks
     */
    AgeNoise(long seed, int salt, double scale) {
        this.seed = mix(seed * 0x9E3779B97F4A7C15L + salt);
        this.scale = scale;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * A random number from 0 (inclusive) to 1 (exclusive) for a position.
     *
     * @param seed the seed
     * @param salt makes different uses differ
     * @param x x
     * @param y y
     * @param z z
     * @return the number
     */
    static double hash(long seed, int salt, int x, int y, int z) {
        long h = mix(seed ^ mix(salt * 0x632BE59BD9B4E019L));
        h = mix(h ^ x * 0x8CB92BA72F3D8DD7L);
        h = mix(h ^ y * 0xD1B54A32D192ED03L);
        h = mix(h ^ z * 0xABC98388FB8FAC03L);
        return (h >>> 11) * 0x1.0p-53;
    }

    private double lattice(int x, int y, int z) {
        return hash(seed, 7, x, y, z);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double octave(double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double fz = smooth(z - z0);
        double c00 = lerp(lattice(x0, y0, z0), lattice(x0 + 1, y0, z0), fx);
        double c10 = lerp(lattice(x0, y0 + 1, z0), lattice(x0 + 1, y0 + 1, z0), fx);
        double c01 = lerp(lattice(x0, y0, z0 + 1), lattice(x0 + 1, y0, z0 + 1), fx);
        double c11 = lerp(lattice(x0, y0 + 1, z0 + 1), lattice(x0 + 1, y0 + 1, z0 + 1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * Smooth noise from 0 to 1, stretched so that values near 0 and 1 are
     * about as common as values in the middle.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return the value
     */
    double value(double x, double y, double z) {
        double v = 0.7 * octave(x / scale, y / scale, z / scale)
            + 0.3 * octave(x * 2.3 / scale + 17.1, y * 2.3 / scale + 5.3, z * 2.3 / scale + 9.7);
        // the sum of interpolated values bunches up around 0.5; stretch it back out
        return Math.clamp((v - 0.5) * 2.2 + 0.5, 0.0, 1.0);
    }
}
