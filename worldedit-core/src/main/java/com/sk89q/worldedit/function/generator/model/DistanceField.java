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

package com.sk89q.worldedit.function.generator.model;

/**
 * Exact Euclidean distance transforms on a box of voxels (Felzenszwalb and Huttenlocher), used to give
 * block shapes a signed distance so they can be blended smoothly.
 */
public final class DistanceField {

    private static final double FAR = 1e20;

    private DistanceField() {
    }

    /**
     * The signed distance of each voxel to the edge of a shape.
     *
     * <p>Voxels inside get {@code -(distance to the nearest outside voxel) + 0.5}, voxels outside get
     * {@code (distance to the nearest inside voxel) - 0.5}, so the surface sits at 0, every inside voxel is
     * at most -0.5 and every outside voxel at least 0.5. Voxels beyond the box count as outside.</p>
     *
     * @param inside which voxels are in the shape, indexed {@code (y * sizeZ + z) * sizeX + x}
     * @param sizeX the size of the box along x
     * @param sizeY the size of the box along y
     * @param sizeZ the size of the box along z
     * @return the signed distances, indexed the same way
     */
    public static float[] signed(boolean[] inside, int sizeX, int sizeY, int sizeZ) {
        int volume = sizeX * sizeY * sizeZ;
        boolean any = false;
        for (boolean b : inside) {
            if (b) {
                any = true;
                break;
            }
        }
        double[] toInside = new double[volume];
        double[] toOutside = new double[volume];
        for (int i = 0; i < volume; i++) {
            toInside[i] = inside[i] ? 0 : FAR;
            toOutside[i] = inside[i] ? FAR : 0;
        }
        transform(toInside, sizeX, sizeY, sizeZ);
        transform(toOutside, sizeX, sizeY, sizeZ);
        float[] result = new float[volume];
        for (int i = 0; i < volume; i++) {
            if (inside[i]) {
                // the box edge is outside too
                result[i] = (float) (0.5 - Math.sqrt(Math.min(toOutside[i], edgeSq(i, sizeX, sizeY, sizeZ))));
            } else {
                result[i] = any ? (float) (Math.sqrt(toInside[i]) - 0.5) : (float) FAR;
            }
        }
        return result;
    }

    private static double edgeSq(int index, int sizeX, int sizeY, int sizeZ) {
        int x = index % sizeX;
        int z = (index / sizeX) % sizeZ;
        int y = index / (sizeX * sizeZ);
        int nearest = Math.min(Math.min(Math.min(x + 1, sizeX - x), Math.min(y + 1, sizeY - y)),
            Math.min(z + 1, sizeZ - z));
        return (double) nearest * nearest;
    }

    /**
     * Turn a field of 0 (feature) and {@link #FAR} (no feature) into squared distances to the nearest feature.
     */
    private static void transform(double[] field, int sizeX, int sizeY, int sizeZ) {
        int longest = Math.max(sizeX, Math.max(sizeY, sizeZ));
        double[] line = new double[longest];
        double[] out = new double[longest];
        int[] hull = new int[longest];
        double[] bounds = new double[longest + 1];
        // along x
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                int base = (y * sizeZ + z) * sizeX;
                for (int x = 0; x < sizeX; x++) {
                    line[x] = field[base + x];
                }
                pass(line, sizeX, out, hull, bounds);
                for (int x = 0; x < sizeX; x++) {
                    field[base + x] = out[x];
                }
            }
        }
        // along z
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    line[z] = field[(y * sizeZ + z) * sizeX + x];
                }
                pass(line, sizeZ, out, hull, bounds);
                for (int z = 0; z < sizeZ; z++) {
                    field[(y * sizeZ + z) * sizeX + x] = out[z];
                }
            }
        }
        // along y
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    line[y] = field[(y * sizeZ + z) * sizeX + x];
                }
                pass(line, sizeY, out, hull, bounds);
                for (int y = 0; y < sizeY; y++) {
                    field[(y * sizeZ + z) * sizeX + x] = out[y];
                }
            }
        }
    }

    /**
     * One-dimensional squared distance transform of a sampled function (lower envelope of parabolas).
     */
    private static void pass(double[] f, int n, double[] out, int[] hull, double[] bounds) {
        int k = 0;
        hull[0] = 0;
        bounds[0] = Double.NEGATIVE_INFINITY;
        bounds[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = intersection(f, q, hull[k]);
            while (s <= bounds[k]) {
                k--;
                s = intersection(f, q, hull[k]);
            }
            k++;
            hull[k] = q;
            bounds[k] = s;
            bounds[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (bounds[k + 1] < q) {
                k++;
            }
            double d = q - hull[k];
            out[q] = d * d + f[hull[k]];
        }
    }

    private static double intersection(double[] f, int q, int p) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
    }
}
