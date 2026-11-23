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

package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.math.Vector3;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Block layouts for {@link LayoutPattern}: stripes, checkerboards, rings, spirals, diagonals,
 * bricks and dithering. Axes are {@link ShapeAxis#X}, {@link ShapeAxis#Y} or {@link ShapeAxis#Z}.
 */
public final class PatternLayouts {

    /**
     * Gives the index of the pattern to use at a block. The index is wrapped around the list of patterns.
     */
    @FunctionalInterface
    public interface Layout {

        /**
         * Get the index for a block.
         *
         * @param x the x
         * @param y the y
         * @param z the z
         * @return the index
         */
        int index(int x, int y, int z);
    }

    private static final int BAYER_SIZE = 8;
    private static final int[][] BAYER = bayerMatrix();

    private PatternLayouts() {
    }

    private static int[][] bayerMatrix() {
        int[][] matrix = {{0}};
        while (matrix.length < BAYER_SIZE) {
            int size = matrix.length;
            int[][] next = new int[size * 2][size * 2];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int base = matrix[i][j] * 4;
                    next[i][j] = base;
                    next[i + size][j + size] = base + 1;
                    next[i][j + size] = base + 2;
                    next[i + size][j] = base + 3;
                }
            }
            matrix = next;
        }
        return matrix;
    }

    private static int coordinate(int x, int y, int z, int axis) {
        return switch (axis) {
            case ShapeAxis.X -> x;
            case ShapeAxis.Z -> z;
            default -> y;
        };
    }

    private static double coordinate(Vector3 vector, int axis) {
        return switch (axis) {
            case ShapeAxis.X -> vector.x();
            case ShapeAxis.Z -> vector.z();
            default -> vector.y();
        };
    }

    /**
     * Flat layers across an axis, each {@code width} blocks thick.
     *
     * @param width the thickness of each stripe
     * @param axis the axis the stripes change along (y = horizontal layers)
     * @return the layout
     */
    public static Layout stripes(int width, int axis) {
        checkArgument(width >= 1, "width must be at least 1");
        return (x, y, z) -> Math.floorDiv(coordinate(x, y, z, axis), width);
    }

    /**
     * A 3D checkerboard of cubes.
     *
     * @param size the size of each cube
     * @return the layout
     */
    public static Layout checker(int size) {
        checkArgument(size >= 1, "size must be at least 1");
        return (x, y, z) -> Math.floorDiv(x, size) + Math.floorDiv(y, size) + Math.floorDiv(z, size);
    }

    /**
     * Diagonal stripes running across x, y and z at once, so they look diagonal on walls and floors alike.
     *
     * @param width the thickness of each stripe, measured along one axis
     * @return the layout
     */
    public static Layout diagonal(int width) {
        checkArgument(width >= 1, "width must be at least 1");
        return (x, y, z) -> Math.floorDiv(x + y + z, width);
    }

    /**
     * Rings around a centre line, each {@code width} blocks wide.
     *
     * @param center the centre
     * @param width the width of each ring
     * @param axis the axis the rings go around (y = rings seen from above)
     * @return the layout
     */
    public static Layout rings(Vector3 center, double width, int axis) {
        checkArgument(width > 0, "width must be above 0");
        return (x, y, z) -> (int) Math.floor(planeDistance(center, x, y, z, axis) / width);
    }

    /**
     * Spiral arms around a centre line.
     *
     * @param center the centre
     * @param bands the number of arms in total (each pattern gets the same number)
     * @param curl how many blocks an arm moves outwards per full turn; 0 for straight spokes
     * @param axis the axis the spiral goes around
     * @return the layout
     */
    public static Layout spiral(Vector3 center, int bands, double curl, int axis) {
        checkArgument(bands >= 1, "there must be at least one arm");
        return (x, y, z) -> {
            double[] plane = plane(center, x, y, z, axis);
            double turns = Math.atan2(plane[1], plane[0]) / (2 * Math.PI);
            if (curl > 0) {
                turns += Math.hypot(plane[0], plane[1]) / curl;
            }
            return (int) Math.floor(turns * bands);
        };
    }

    private static double[] plane(Vector3 center, int x, int y, int z, int axis) {
        return switch (axis) {
            case ShapeAxis.X -> new double[] {z - center.z(), y - center.y()};
            case ShapeAxis.Z -> new double[] {x - center.x(), y - center.y()};
            default -> new double[] {x - center.x(), z - center.z()};
        };
    }

    private static double planeDistance(Vector3 center, int x, int y, int z, int axis) {
        double[] plane = plane(center, x, y, z, axis);
        return Math.hypot(plane[0], plane[1]);
    }

    /**
     * A running-bond brick wall: courses of bricks with 1-block mortar lines between them, each
     * course shifted by half a brick. Index 0 is brick and 1 is mortar.
     *
     * @param length the length of each brick
     * @param height the height of each course
     * @param axis the axis the courses stack along (y for walls; x or z to pave a floor)
     * @return the layout
     */
    public static Layout bricks(int length, int height, int axis) {
        checkArgument(length >= 1, "length must be at least 1");
        checkArgument(height >= 1, "height must be at least 1");
        return (x, y, z) -> {
            int stack = coordinate(x, y, z, axis);
            int run = x + y + z - stack;
            if (Math.floorMod(stack, height + 1) == height) {
                return 1;
            }
            int course = Math.floorDiv(stack, height + 1);
            int shift = Math.floorMod(course, 2) == 1 ? (length + 1) / 2 : 0;
            return Math.floorMod(run + shift, length + 1) == length ? 1 : 0;
        };
    }

    /**
     * The ordered-dither threshold of a block, spread evenly so that every flat slice through
     * x, y or z gets every threshold equally often.
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return a threshold between 0 and 1 (never exactly 0 or 1)
     */
    public static double ditherThreshold(int x, int y, int z) {
        int row = Math.floorMod(x + z, BAYER_SIZE);
        int column = Math.floorMod(y + z, BAYER_SIZE);
        return (BAYER[row][column] + 0.5) / (BAYER_SIZE * BAYER_SIZE);
    }

    /**
     * An ordered dither with a fixed share of the second pattern. Index 0 is the first pattern and 1 the second.
     *
     * @param fraction the share of the second pattern, 0 to 1
     * @return the layout
     */
    public static Layout dither(double fraction) {
        return (x, y, z) -> ditherThreshold(x, y, z) < fraction ? 1 : 0;
    }

    /**
     * An ordered dither that blends from the first pattern at {@code from} to the second at {@code to}.
     *
     * @param axis the axis to blend along
     * @param from the coordinate where only the first pattern is used
     * @param to the coordinate where only the second pattern is used
     * @return the layout
     */
    public static Layout ditherBlend(int axis, double from, double to) {
        return (x, y, z) -> {
            double fraction = to == from ? 0.5 : (coordinate(x, y, z, axis) - from) / (to - from);
            return ditherThreshold(x, y, z) < Math.max(0, Math.min(1, fraction)) ? 1 : 0;
        };
    }

    /**
     * The coordinate of a vector along an axis.
     *
     * @param vector the vector
     * @param axis the axis
     * @return the coordinate
     */
    public static double along(Vector3 vector, int axis) {
        return coordinate(vector, axis);
    }
}
