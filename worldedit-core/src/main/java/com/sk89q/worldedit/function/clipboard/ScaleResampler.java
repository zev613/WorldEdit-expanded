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

import java.util.Arrays;

/**
 * Resamples a block grid to a new size by inverse mapping: every destination cell looks up the
 * source cell(s) it covers, so enlarging leaves no holes.
 *
 * <p>Coordinates are relative to the scale centre (the clipboard origin); the centre is the
 * <em>corner</em> of the origin block, so whatever was at or above the origin stays at or above
 * it. Along an axis that grows (factor &ge; 1) a destination cell copies the source cell under its
 * centre. Along an axis that shrinks, all source cells overlapping the destination cell vote,
 * weighted by how much of them falls inside: the cell becomes solid when at least
 * {@code solidFraction} of it is solid, taking the most common solid block. That keeps
 * 1-block walls and posts when halving.</p>
 */
public final class ScaleResampler {

    /** The largest result allowed, in blocks. */
    public static final long MAX_VOLUME = 64L * 1024 * 1024;

    /**
     * Gives the block kind at a source cell; 0 means air, any other number a block kind.
     */
    @FunctionalInterface
    public interface Source {
        int key(int x, int y, int z);
    }

    /**
     * Receives every solid destination cell.
     */
    @FunctionalInterface
    public interface Sink {
        /**
         * Called for a solid destination cell.
         *
         * @param dx destination x
         * @param dy destination y
         * @param dz destination z
         * @param sx the source x to copy the block from
         * @param sy the source y
         * @param sz the source z
         * @param primary true if this is the one destination cell holding the centre of the
         *     source cell (only that copy should carry block entity data such as chest contents)
         */
        void accept(int dx, int dy, int dz, int sx, int sy, int sz, boolean primary);
    }

    private final int[] srcMin;
    private final int[] srcMax;
    private final double[] factor;
    private final int[] dstMin = new int[3];
    private final int[] dstMax = new int[3];

    /**
     * Create a resampler.
     *
     * @param srcMin the minimum source cell (relative to the scale centre)
     * @param srcMax the maximum source cell, inclusive
     * @param factor the scale factor along x, y and z, each above 0
     */
    public ScaleResampler(int[] srcMin, int[] srcMax, double[] factor) {
        if (srcMin.length != 3 || srcMax.length != 3 || factor.length != 3) {
            throw new IllegalArgumentException("Need three axes");
        }
        for (int a = 0; a < 3; a++) {
            if (!(factor[a] > 0) || Double.isInfinite(factor[a])) {
                throw new IllegalArgumentException("Scale factors must be above 0.");
            }
            if (srcMax[a] < srcMin[a]) {
                throw new IllegalArgumentException("Empty source");
            }
        }
        this.srcMin = srcMin.clone();
        this.srcMax = srcMax.clone();
        this.factor = factor.clone();
        for (int a = 0; a < 3; a++) {
            dstMin[a] = round(srcMin[a] * factor[a]);
            int endExclusive = round((srcMax[a] + 1.0) * factor[a]);
            dstMax[a] = Math.max(dstMin[a], endExclusive - 1);
        }
    }

    private static int round(double value) {
        return (int) Math.floor(value + 0.5);
    }

    /**
     * Parse a factor such as {@code 2.5} or {@code 1,2,1}.
     *
     * @param text the text
     * @return factors for x, y and z
     */
    public static double[] parseFactor(String text) {
        String[] parts = text.trim().split("\\s*[,;]\\s*", -1);
        double[] result = new double[3];
        try {
            if (parts.length == 1) {
                Arrays.fill(result, Double.parseDouble(parts[0]));
            } else if (parts.length == 3) {
                for (int a = 0; a < 3; a++) {
                    result[a] = Double.parseDouble(parts[a]);
                }
            } else {
                throw new IllegalArgumentException("Give one factor (2.5) or one per axis (1,2,1).");
            }
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("'" + text + "' is not a scale factor; use e.g. 2.5, 0.5 or 1,2,1.");
        }
        for (double f : result) {
            if (!(f > 0) || Double.isInfinite(f)) {
                throw new IllegalArgumentException("Scale factors must be above 0.");
            }
        }
        return result;
    }

    public int[] getDestinationMin() {
        return dstMin.clone();
    }

    public int[] getDestinationMax() {
        return dstMax.clone();
    }

    /**
     * Get the number of cells in the result.
     *
     * @return the volume
     */
    public long getDestinationVolume() {
        long volume = 1;
        for (int a = 0; a < 3; a++) {
            volume *= dstMax[a] - dstMin[a] + 1L;
        }
        return volume;
    }

    /**
     * Throws a friendly error when the result would be too large.
     *
     * @param maxVolume the largest allowed volume
     */
    public void checkVolume(long maxVolume) {
        long volume = getDestinationVolume();
        if (volume > maxVolume) {
            throw new IllegalArgumentException("The scaled clipboard would have " + volume
                + " blocks, more than the limit of " + maxVolume + ". Use a smaller factor.");
        }
    }

    /**
     * Source cells (and their weights) that a destination cell covers along one axis.
     */
    private static final class AxisSamples {
        private final int[] cells;
        private final double[] weights;

        AxisSamples(int[] cells, double[] weights) {
            this.cells = cells;
            this.weights = weights;
        }
    }

    private AxisSamples[] samplesFor(int axis) {
        int count = dstMax[axis] - dstMin[axis] + 1;
        AxisSamples[] result = new AxisSamples[count];
        double f = factor[axis];
        for (int i = 0; i < count; i++) {
            int d = dstMin[axis] + i;
            if (f >= 1) {
                int s = clamp((int) Math.floor((d + 0.5) / f), srcMin[axis], srcMax[axis]);
                result[i] = new AxisSamples(new int[] { s }, new double[] { 1 });
                continue;
            }
            double lo = Math.max(d / f, srcMin[axis]);
            double hi = Math.min((d + 1) / f, srcMax[axis] + 1.0);
            if (hi <= lo) {
                int s = clamp((int) Math.floor((d + 0.5) / f), srcMin[axis], srcMax[axis]);
                result[i] = new AxisSamples(new int[] { s }, new double[] { 1 });
                continue;
            }
            int first = (int) Math.floor(lo);
            int last = (int) Math.ceil(hi) - 1;
            int[] cells = new int[last - first + 1];
            double[] weights = new double[cells.length];
            for (int s = first; s <= last; s++) {
                cells[s - first] = s;
                weights[s - first] = Math.min(hi, s + 1) - Math.max(lo, s);
            }
            result[i] = new AxisSamples(cells, weights);
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isPrimary(int axis, int dest, int src) {
        return (int) Math.floor((src + 0.5) * factor[axis]) == dest;
    }

    /**
     * Resample.
     *
     * @param source the source grid
     * @param solidFraction the share of a destination cell that must be solid for it to be solid
     *     (only matters when shrinking), from just above 0 to 1
     * @param sink receives the solid destination cells
     */
    public void resample(Source source, double solidFraction, Sink sink) {
        if (!(solidFraction > 0) || solidFraction > 1) {
            throw new IllegalArgumentException("The solid fraction must be above 0 and at most 1.");
        }
        AxisSamples[] xs = samplesFor(0);
        AxisSamples[] ys = samplesFor(1);
        AxisSamples[] zs = samplesFor(2);
        // small reusable vote table: keys and weights in first-seen order
        int[] keys = new int[8];
        double[] votes = new double[8];
        int[] firstX = new int[8];
        int[] firstY = new int[8];
        int[] firstZ = new int[8];
        // tiny tolerance so exactly a quarter counts as a quarter despite rounding
        double threshold = solidFraction - 1e-9;

        for (int ix = 0; ix < xs.length; ix++) {
            AxisSamples sx = xs[ix];
            for (int iy = 0; iy < ys.length; iy++) {
                AxisSamples sy = ys[iy];
                for (int iz = 0; iz < zs.length; iz++) {
                    AxisSamples sz = zs[iz];
                    int used = 0;
                    double total = 0;
                    double solid = 0;
                    for (int a = 0; a < sx.cells.length; a++) {
                        for (int b = 0; b < sy.cells.length; b++) {
                            double wab = sx.weights[a] * sy.weights[b];
                            for (int c = 0; c < sz.cells.length; c++) {
                                double w = wab * sz.weights[c];
                                total += w;
                                int key = source.key(sx.cells[a], sy.cells[b], sz.cells[c]);
                                if (key == 0) {
                                    continue;
                                }
                                solid += w;
                                int slot = -1;
                                for (int k = 0; k < used; k++) {
                                    if (keys[k] == key) {
                                        slot = k;
                                        break;
                                    }
                                }
                                if (slot < 0) {
                                    if (used == keys.length) {
                                        keys = Arrays.copyOf(keys, used * 2);
                                        votes = Arrays.copyOf(votes, used * 2);
                                        firstX = Arrays.copyOf(firstX, used * 2);
                                        firstY = Arrays.copyOf(firstY, used * 2);
                                        firstZ = Arrays.copyOf(firstZ, used * 2);
                                    }
                                    slot = used++;
                                    keys[slot] = key;
                                    votes[slot] = 0;
                                    firstX[slot] = sx.cells[a];
                                    firstY[slot] = sy.cells[b];
                                    firstZ[slot] = sz.cells[c];
                                }
                                votes[slot] += w;
                            }
                        }
                    }
                    if (used == 0 || solid < threshold * total) {
                        continue;
                    }
                    int best = 0;
                    for (int k = 1; k < used; k++) {
                        if (votes[k] > votes[best] + 1e-12) {
                            best = k;
                        }
                    }
                    int dx = dstMin[0] + ix;
                    int dy = dstMin[1] + iy;
                    int dz = dstMin[2] + iz;
                    boolean primary = isPrimary(0, dx, firstX[best])
                        && isPrimary(1, dy, firstY[best])
                        && isPrimary(2, dz, firstZ[best]);
                    sink.accept(dx, dy, dz, firstX[best], firstY[best], firstZ[best], primary);
                }
            }
        }
    }
}
