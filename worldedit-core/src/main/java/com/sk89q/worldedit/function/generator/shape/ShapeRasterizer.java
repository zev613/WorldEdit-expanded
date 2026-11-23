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

import com.sk89q.worldedit.math.Vector3;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Turns a {@link ShapeInstance} into voxels at a given size and orientation.
 */
public final class ShapeRasterizer {

    /**
     * The default limit on the number of voxels examined.
     */
    public static final long DEFAULT_MAX_VOLUME = 64L * 1024 * 1024;

    private static final double MAX_SAMPLE_SPACING = 0.4;
    private static final int COARSE_CURVE_SAMPLES = 512;
    private static final int COARSE_SURFACE_SAMPLES = 96;
    private static final long MAX_SAMPLES = 40_000_000L;

    private final Vector3 radii;
    private final ShapeOrientation orientation;
    private final long maxVolume;

    /**
     * Create a rasterizer.
     *
     * @param radii the half-size of the shape in blocks along its local x, y and z
     * @param orientation the rotation to apply
     * @param maxVolume the largest number of voxels the bounding box may contain
     */
    public ShapeRasterizer(Vector3 radii, ShapeOrientation orientation, long maxVolume) {
        checkNotNull(radii);
        checkArgument(radii.x() > 0 && radii.y() > 0 && radii.z() > 0, "radii must be positive");
        this.radii = radii;
        this.orientation = checkNotNull(orientation);
        this.maxVolume = maxVolume;
    }

    /**
     * Rasterize a shape.
     *
     * @param shape the shape
     * @return the voxels, as offsets from the centre block
     * @throws IllegalArgumentException if the shape is too large
     */
    public VoxelGrid rasterize(ShapeInstance shape) {
        return switch (shape) {
            case ShapeInstance.Implicit implicit -> rasterizeImplicit(implicit);
            case ShapeInstance.Swept swept -> rasterizeSwept(swept);
        };
    }

    private VoxelGrid createGrid(double margin) {
        double[] half = orientation.rotatedHalfExtents(radii.x(), radii.y(), radii.z());
        int hx = (int) Math.ceil(half[0] + margin - 1e-9);
        int hy = (int) Math.ceil(half[1] + margin - 1e-9);
        int hz = (int) Math.ceil(half[2] + margin - 1e-9);
        long volume = (2L * hx + 1) * (2L * hy + 1) * (2L * hz + 1);
        if (volume > maxVolume) {
            throw new IllegalArgumentException("That shape is too large (" + volume + " blocks in its bounding box, the limit is "
                + maxVolume + ")");
        }
        return new VoxelGrid(-hx, -hy, -hz, hx, hy, hz);
    }

    private VoxelGrid rasterizeImplicit(ShapeInstance.Implicit shape) {
        VoxelGrid grid = createGrid(0);
        double[] v = new double[3];
        double rx = radii.x();
        double ry = radii.y();
        double rz = radii.z();
        // a tiny tolerance so that e.g. a radius-5 shape includes offset 5 exactly
        double tolerance = 1e-9;
        for (int y = grid.getMinY(); y <= grid.getMaxY(); y++) {
            for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
                for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
                    v[0] = x;
                    v[1] = y;
                    v[2] = z;
                    orientation.toLocal(v, v);
                    double nx = v[0] / rx;
                    double ny = v[1] / ry;
                    double nz = v[2] / rz;
                    if (Math.abs(nx) > 1 + tolerance || Math.abs(ny) > 1 + tolerance || Math.abs(nz) > 1 + tolerance) {
                        continue;
                    }
                    if (shape.contains(clamp(nx), clamp(ny), clamp(nz))) {
                        grid.set(x, y, z);
                    }
                }
            }
        }
        return grid;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    private VoxelGrid rasterizeSwept(ShapeInstance.Swept shape) {
        double[] fit = {0, 0, 0, 1};
        double maxBrush;
        if (shape.autoFit()) {
            BoundsTracer bounds = new BoundsTracer();
            shape.trace(bounds);
            if (!bounds.any) {
                throw new IllegalArgumentException("The shape is empty");
            }
            double half = 0;
            for (int i = 0; i < 3; i++) {
                fit[i] = (bounds.min[i] + bounds.max[i]) / 2;
                half = Math.max(half, (bounds.max[i] - bounds.min[i]) / 2);
            }
            fit[3] = half > 0 ? 1 / half : 1;
            maxBrush = bounds.maxRadius;
        } else {
            MaxRadiusTracer brushes = new MaxRadiusTracer();
            shape.trace(brushes);
            maxBrush = brushes.maxRadius;
        }
        VoxelGrid grid = createGrid(maxBrush + 1);
        shape.trace(new GridTracer(grid, fit));
        return grid;
    }

    /**
     * Records the bounds of the traced geometry using coarse sampling.
     */
    private static final class BoundsTracer implements ShapeTracer {
        final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double maxRadius;
        boolean any;
        private final double[] point = new double[3];

        @Override
        public void point(double x, double y, double z, double radius) {
            any = true;
            maxRadius = Math.max(maxRadius, radius);
            point[0] = x;
            point[1] = y;
            point[2] = z;
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], point[i]);
                max[i] = Math.max(max[i], point[i]);
            }
        }

        @Override
        public void curve(Curve curve, double t0, double t1, double radius) {
            double[] out = new double[3];
            for (int i = 0; i <= COARSE_CURVE_SAMPLES; i++) {
                curve.at(t0 + (t1 - t0) * i / COARSE_CURVE_SAMPLES, out);
                point(out[0], out[1], out[2], radius);
            }
        }

        @Override
        public void surface(Surface surface, double u0, double u1, double v0, double v1, double radius) {
            double[] out = new double[3];
            for (int i = 0; i <= COARSE_SURFACE_SAMPLES; i++) {
                double u = u0 + (u1 - u0) * i / COARSE_SURFACE_SAMPLES;
                for (int j = 0; j <= COARSE_SURFACE_SAMPLES; j++) {
                    surface.at(u, v0 + (v1 - v0) * j / COARSE_SURFACE_SAMPLES, out);
                    point(out[0], out[1], out[2], radius);
                }
            }
        }
    }

    /**
     * Records only the largest brush radius.
     */
    private static final class MaxRadiusTracer implements ShapeTracer {
        double maxRadius;

        @Override
        public void point(double x, double y, double z, double radius) {
            maxRadius = Math.max(maxRadius, radius);
        }

        @Override
        public void curve(Curve curve, double t0, double t1, double radius) {
            maxRadius = Math.max(maxRadius, radius);
        }

        @Override
        public void surface(Surface surface, double u0, double u1, double v0, double v1, double radius) {
            maxRadius = Math.max(maxRadius, radius);
        }
    }

    /**
     * Stamps balls into the grid.
     */
    private final class GridTracer implements ShapeTracer {
        private final VoxelGrid grid;
        private final double[] fit;
        private final double[] tmp = new double[3];
        private long samples;

        GridTracer(VoxelGrid grid, double[] fit) {
            this.grid = grid;
            this.fit = fit;
        }

        /** Convert a normalized point into a world offset, in place. */
        private void toWorld(double[] p) {
            p[0] = (p[0] - fit[0]) * fit[3] * radii.x();
            p[1] = (p[1] - fit[1]) * fit[3] * radii.y();
            p[2] = (p[2] - fit[2]) * fit[3] * radii.z();
            orientation.toWorld(p, p);
        }

        private void countSamples(long n) {
            samples += n;
            if (samples > MAX_SAMPLES) {
                throw new IllegalArgumentException("That shape is too detailed to generate at this size");
            }
        }

        private void stamp(double[] w, double radius) {
            grid.set((int) Math.round(w[0]), (int) Math.round(w[1]), (int) Math.round(w[2]));
            if (radius <= 0.5) {
                return;
            }
            double r2 = radius * radius;
            int x0 = (int) Math.ceil(w[0] - radius);
            int x1 = (int) Math.floor(w[0] + radius);
            int y0 = (int) Math.ceil(w[1] - radius);
            int y1 = (int) Math.floor(w[1] + radius);
            int z0 = (int) Math.ceil(w[2] - radius);
            int z1 = (int) Math.floor(w[2] + radius);
            for (int y = y0; y <= y1; y++) {
                double dy = y - w[1];
                for (int z = z0; z <= z1; z++) {
                    double dz = z - w[2];
                    double dyz = dy * dy + dz * dz;
                    if (dyz > r2) {
                        continue;
                    }
                    for (int x = x0; x <= x1; x++) {
                        double dx = x - w[0];
                        if (dx * dx + dyz <= r2) {
                            grid.set(x, y, z);
                        }
                    }
                }
            }
        }

        @Override
        public void point(double x, double y, double z, double radius) {
            countSamples(1);
            tmp[0] = x;
            tmp[1] = y;
            tmp[2] = z;
            toWorld(tmp);
            stamp(tmp, radius);
        }

        private double worldDistance(double[] a, double[] b) {
            double dx = a[0] - b[0];
            double dy = a[1] - b[1];
            double dz = a[2] - b[2];
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override
        public void curve(Curve curve, double t0, double t1, double radius) {
            double[] prev = new double[3];
            double[] next = new double[3];
            double[] w = new double[3];
            curve.at(t0, prev);
            toWorld(prev);
            stamp(prev, radius);
            for (int i = 1; i <= COARSE_CURVE_SAMPLES; i++) {
                double ta = t0 + (t1 - t0) * (i - 1) / COARSE_CURVE_SAMPLES;
                double tb = t0 + (t1 - t0) * i / COARSE_CURVE_SAMPLES;
                curve.at(tb, next);
                toWorld(next);
                int steps = Math.max(1, (int) Math.ceil(worldDistance(prev, next) / MAX_SAMPLE_SPACING));
                countSamples(steps);
                for (int s = 1; s < steps; s++) {
                    curve.at(ta + (tb - ta) * s / steps, w);
                    toWorld(w);
                    stamp(w, radius);
                }
                stamp(next, radius);
                double[] swap = prev;
                prev = next;
                next = swap;
            }
        }

        @Override
        public void surface(Surface surface, double u0, double u1, double v0, double v1, double radius) {
            int n = COARSE_SURFACE_SAMPLES;
            double[][] coarse = new double[(n + 1) * (n + 1)][];
            for (int i = 0; i <= n; i++) {
                for (int j = 0; j <= n; j++) {
                    double[] p = new double[3];
                    surface.at(u0 + (u1 - u0) * i / n, v0 + (v1 - v0) * j / n, p);
                    toWorld(p);
                    coarse[i * (n + 1) + j] = p;
                }
            }
            double[] w = new double[3];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double[] a = coarse[i * (n + 1) + j];
                    double[] b = coarse[(i + 1) * (n + 1) + j];
                    double[] c = coarse[i * (n + 1) + j + 1];
                    double[] d = coarse[(i + 1) * (n + 1) + j + 1];
                    int su = Math.max(1, (int) Math.ceil(Math.max(worldDistance(a, b), worldDistance(c, d)) / MAX_SAMPLE_SPACING));
                    int sv = Math.max(1, (int) Math.ceil(Math.max(worldDistance(a, c), worldDistance(b, d)) / MAX_SAMPLE_SPACING));
                    countSamples((long) (su + 1) * (sv + 1));
                    for (int a1 = 0; a1 <= su; a1++) {
                        double u = u0 + (u1 - u0) * (i + (double) a1 / su) / n;
                        for (int b1 = 0; b1 <= sv; b1++) {
                            double v = v0 + (v1 - v0) * (j + (double) b1 / sv) / n;
                            surface.at(u, v, w);
                            toWorld(w);
                            stamp(w, radius);
                        }
                    }
                }
            }
        }
    }
}
