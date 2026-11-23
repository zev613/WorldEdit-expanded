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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lays out copies of a box in a grid on the horizontal plane: columns go along a "right"
 * direction and rows along a "forward" direction, each a world axis (+x, -x, +z or -z).
 *
 * <p>Each copy gets a cell. Cells are as big as the copy's footprint (or, with random rotations
 * of a footprint that is not square, a square as wide as its longest side), with the spacing in
 * blocks between cells. A turned copy is centred in its cell. Positions are offsets of each
 * copy's minimum corner from the minimum corner of cell (0, 0).</p>
 */
public final class GridLayout {

    /**
     * The most copies allowed.
     */
    public static final int MAX_COPIES = 10_000;

    /**
     * The most blocks all copies together may cover.
     */
    public static final long MAX_VOLUME = CopyLimit.MAX_BLOCKS;

    /**
     * The largest spacing allowed.
     */
    public static final int MAX_SPACING = 1000;

    /**
     * The largest rise per row allowed (up or down).
     */
    public static final int MAX_RISE = 64;

    /**
     * One copy.
     *
     * @param column the index along the right direction, from 0
     * @param row the index along the forward direction, from 0
     * @param quarterTurns clockwise quarter turns (0-3) of this copy
     * @param dx offset of the copy's minimum corner from cell (0, 0)'s minimum corner
     * @param dy vertical offset
     * @param dz offset of the copy's minimum corner from cell (0, 0)'s minimum corner
     */
    public record Cell(int column, int row, int quarterTurns, int dx, int dy, int dz) {
    }

    private GridLayout() {
    }

    /**
     * Get the forward direction for a yaw, snapped to the nearest world axis (Minecraft yaw:
     * 0 is +z, 90 is -x).
     *
     * @param yaw the yaw in degrees
     * @return {dx, dz}
     */
    public static int[] forwardFromYaw(double yaw) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        if (Math.abs(fx) > Math.abs(fz)) {
            return new int[] { fx > 0 ? 1 : -1, 0 };
        }
        return new int[] { 0, fz >= 0 ? 1 : -1 };
    }

    /**
     * Get the direction to the right of someone facing {@code forward} (seen from above, with
     * Minecraft's axes: facing +z, the right hand points to -x).
     *
     * @param forward {dx, dz}
     * @return {dx, dz}
     */
    public static int[] rightOf(int[] forward) {
        return new int[] { -forward[1], forward[0] };
    }

    /**
     * Check the grid settings, throwing a friendly message when they are not allowed.
     *
     * @param columns copies along the right direction
     * @param rows copies along the forward direction
     * @param spacingRight gap between columns
     * @param spacingForward gap between rows
     * @param rise blocks each row is raised
     * @param copyVolume blocks in one copy
     */
    public static void check(int columns, int rows, int spacingRight, int spacingForward, int rise,
                             long copyVolume) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Both counts must be at least 1.");
        }
        long copies = (long) columns * rows;
        if (copies > MAX_COPIES) {
            throw new IllegalArgumentException("That is " + CopyLimit.format(copies) + " copies; at most "
                + CopyLimit.format(MAX_COPIES) + " are allowed.");
        }
        if (spacingRight < 0 || spacingForward < 0 || spacingRight > MAX_SPACING || spacingForward > MAX_SPACING) {
            throw new IllegalArgumentException("The spacing must be between 0 and " + MAX_SPACING + ".");
        }
        if (Math.abs(rise) > MAX_RISE) {
            throw new IllegalArgumentException("The rise per row must be between -" + MAX_RISE + " and "
                + MAX_RISE + ".");
        }
        CopyLimit.check(copies, copyVolume);
    }

    /**
     * Lay out the grid.
     *
     * @param columns copies along the right direction
     * @param rows copies along the forward direction
     * @param spacingRight gap in blocks between columns
     * @param spacingForward gap in blocks between rows
     * @param sizeX the copy's size along x (unturned)
     * @param sizeZ the copy's size along z (unturned)
     * @param right the right direction {dx, dz}, a unit world axis
     * @param forward the forward direction {dx, dz}, a unit world axis at right angles to right
     * @param rise blocks each row is raised above the previous one
     * @param random random source for rotations, or null to keep every copy unturned
     * @return the copies, row by row
     */
    public static List<Cell> layout(int columns, int rows, int spacingRight, int spacingForward,
                                    int sizeX, int sizeZ, int[] right, int[] forward, int rise, Random random) {
        boolean square = random != null && sizeX != sizeZ;
        int cellX = square ? Math.max(sizeX, sizeZ) : sizeX;
        int cellZ = square ? Math.max(sizeX, sizeZ) : sizeZ;
        boolean rightIsX = right[0] != 0;
        int pitchRight = (rightIsX ? cellX : cellZ) + spacingRight;
        int pitchForward = (rightIsX ? cellZ : cellX) + spacingForward;
        List<Cell> cells = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int turns = random == null ? 0 : random.nextInt(4);
                int footX = turns % 2 == 0 ? sizeX : sizeZ;
                int footZ = turns % 2 == 0 ? sizeZ : sizeX;
                int dx = right[0] * column * pitchRight + forward[0] * row * pitchForward + (cellX - footX) / 2;
                int dz = right[1] * column * pitchRight + forward[1] * row * pitchForward + (cellZ - footZ) / 2;
                cells.add(new Cell(column, row, turns, dx, row * rise, dz));
            }
        }
        return cells;
    }
}
