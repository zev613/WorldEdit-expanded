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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Works out how far each vertical column of a pasted clipboard moves up or down so that it
 * follows the terrain ("conform paste").
 *
 * <p>The input is the raw shift of every column: how many blocks the column must move so the
 * clipboard's bottom layer sits on the ground there, or {@link #MISSING} when no ground was
 * found. Missing columns take the shift of the nearest column that has one. The shifts are then
 * smoothed by averaging over a square of the given radius, rounded, and kept within one block of
 * the raw shift, so a wide road does not get jagged steps from single-block bumps, but never
 * drifts away from the terrain.</p>
 */
public final class ConformOffsets {

    /**
     * Marks a column without ground.
     */
    public static final int MISSING = Integer.MIN_VALUE;

    /**
     * The largest smoothing radius.
     */
    public static final int MAX_RADIUS = 8;

    private ConformOffsets() {
    }

    /**
     * Fill missing columns with the shift of the nearest column that has one.
     *
     * @param raw the raw shifts, indexed [x][z]; not changed
     * @return the filled shifts, or null if no column has a shift
     */
    public static int[][] fillMissing(int[][] raw) {
        int sizeX = raw.length;
        int sizeZ = sizeX == 0 ? 0 : raw[0].length;
        int[][] out = new int[sizeX][sizeZ];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                out[x][z] = raw[x][z];
                if (raw[x][z] != MISSING) {
                    queue.add(new int[] { x, z });
                }
            }
        }
        if (queue.isEmpty()) {
            return null;
        }
        int[][] steps = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int nz = cell[1] + step[1];
                if (nx >= 0 && nz >= 0 && nx < sizeX && nz < sizeZ && out[nx][nz] == MISSING) {
                    out[nx][nz] = out[cell[0]][cell[1]];
                    queue.add(new int[] { nx, nz });
                }
            }
        }
        return out;
    }

    /**
     * Smooth shifts: each column takes the rounded average of the columns within {@code radius}
     * (a square), but stays within one block of its own shift.
     *
     * @param shifts the shifts without missing columns, indexed [x][z]
     * @param radius the smoothing radius, 0 for none
     * @return the smoothed shifts
     */
    public static int[][] smooth(int[][] shifts, int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("The smoothing radius must be between 0 and " + MAX_RADIUS + ".");
        }
        int sizeX = shifts.length;
        int sizeZ = sizeX == 0 ? 0 : shifts[0].length;
        int[][] out = new int[sizeX][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                long sum = 0;
                int count = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx >= 0 && nz >= 0 && nx < sizeX && nz < sizeZ) {
                            sum += shifts[nx][nz];
                            count++;
                        }
                    }
                }
                int mean = (int) Math.floor((double) sum / count + 0.5);
                int own = shifts[x][z];
                out[x][z] = Math.max(own - 1, Math.min(own + 1, mean));
            }
        }
        return out;
    }

    /**
     * Fill missing columns, then smooth.
     *
     * @param raw the raw shifts with {@link #MISSING} for columns without ground
     * @param radius the smoothing radius
     * @return the final shifts, or null if no column found ground
     */
    public static int[][] compute(int[][] raw, int radius) {
        int[][] filled = fillMissing(raw);
        return filled == null ? null : smooth(filled, radius);
    }
}
