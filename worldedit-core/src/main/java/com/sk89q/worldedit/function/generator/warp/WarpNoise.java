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

package com.sk89q.worldedit.function.generator.warp;

/**
 * Small, repeatable 2D noise for warps.
 */
final class WarpNoise {

    private WarpNoise() {
    }

    /**
     * A repeatable random number for a grid point.
     *
     * @param x the x
     * @param z the z
     * @param seed the seed
     * @return a number from 0 to 1
     */
    static double hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    /**
     * Smoothly varying noise: random values on a grid, blended between grid points.
     *
     * @param x the x
     * @param z the z
     * @param seed the seed
     * @return a number from 0 to 1
     */
    static double smooth(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = fade(x - x0);
        double fz = fade(z - z0);
        double a = lerp(hash(x0, z0, seed), hash(x0 + 1, z0, seed), fx);
        double b = lerp(hash(x0, z0 + 1, seed), hash(x0 + 1, z0 + 1, seed), fx);
        return lerp(a, b, fz);
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
