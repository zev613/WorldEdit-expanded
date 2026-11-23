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

/**
 * A rotation applied to a generated shape: first a tilt around the x axis,
 * then a turn around the vertical axis.
 */
public final class ShapeOrientation {

    public static final ShapeOrientation NONE = new ShapeOrientation(0, 0);

    // row-major matrix mapping shape-local to world offsets
    private final double[] matrix = new double[9];
    private final double yaw;
    private final double tilt;

    /**
     * Create an orientation.
     *
     * @param yawDegrees turn around the vertical axis, clockwise seen from above (east towards south)
     * @param tiltDegrees tilt around the east-west axis; positive tips the top towards the south
     */
    public ShapeOrientation(double yawDegrees, double tiltDegrees) {
        this.yaw = yawDegrees;
        this.tilt = tiltDegrees;
        double cy = cosDeg(yawDegrees);
        double sy = sinDeg(yawDegrees);
        double ct = cosDeg(tiltDegrees);
        double st = sinDeg(tiltDegrees);
        // tilt: (x, y, z) -> (x, y ct - z st, y st + z ct)
        // yaw:  (x, y, z) -> (x cy - z sy, y, x sy + z cy)
        // combined = yaw * tilt
        matrix[0] = cy;
        matrix[1] = -sy * st;
        matrix[2] = -sy * ct;
        matrix[3] = 0;
        matrix[4] = ct;
        matrix[5] = -st;
        matrix[6] = sy;
        matrix[7] = cy * st;
        matrix[8] = cy * ct;
    }

    private static double cosDeg(double degrees) {
        double r = Math.IEEEremainder(degrees, 360);
        if (r == 0) {
            return 1;
        } else if (r == 90 || r == -270) {
            return 0;
        } else if (r == 180 || r == -180) {
            return -1;
        } else if (r == -90 || r == 270) {
            return 0;
        }
        return Math.cos(Math.toRadians(degrees));
    }

    private static double sinDeg(double degrees) {
        return cosDeg(degrees - 90);
    }

    public double getYaw() {
        return yaw;
    }

    public double getTilt() {
        return tilt;
    }

    public boolean isIdentity() {
        return matrix[0] == 1 && matrix[4] == 1 && matrix[8] == 1;
    }

    /**
     * Rotate a shape-local offset into a world offset.
     *
     * @param in x, y, z
     * @param out receives the result; may be the same array as {@code in}
     */
    public void toWorld(double[] in, double[] out) {
        double x = in[0];
        double y = in[1];
        double z = in[2];
        out[0] = matrix[0] * x + matrix[1] * y + matrix[2] * z;
        out[1] = matrix[3] * x + matrix[4] * y + matrix[5] * z;
        out[2] = matrix[6] * x + matrix[7] * y + matrix[8] * z;
    }

    /**
     * Rotate a world offset into a shape-local offset.
     *
     * @param in x, y, z
     * @param out receives the result; may be the same array as {@code in}
     */
    public void toLocal(double[] in, double[] out) {
        double x = in[0];
        double y = in[1];
        double z = in[2];
        out[0] = matrix[0] * x + matrix[3] * y + matrix[6] * z;
        out[1] = matrix[1] * x + matrix[4] * y + matrix[7] * z;
        out[2] = matrix[2] * x + matrix[5] * y + matrix[8] * z;
    }

    /**
     * The half-size along each world axis of a box with the given local half-size after rotation.
     *
     * @param hx local half-size along x
     * @param hy local half-size along y
     * @param hz local half-size along z
     * @return the world half-sizes
     */
    public double[] rotatedHalfExtents(double hx, double hy, double hz) {
        return new double[] {
            Math.abs(matrix[0]) * hx + Math.abs(matrix[1]) * hy + Math.abs(matrix[2]) * hz,
            Math.abs(matrix[3]) * hx + Math.abs(matrix[4]) * hy + Math.abs(matrix[5]) * hz,
            Math.abs(matrix[6]) * hx + Math.abs(matrix[7]) * hy + Math.abs(matrix[8]) * hz,
        };
    }
}
