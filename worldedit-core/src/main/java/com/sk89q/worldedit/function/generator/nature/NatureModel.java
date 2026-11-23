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

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A box of voxels for a natural object, each empty or holding a part number (stem, cap...).
 *
 * <p>Positions are offsets from the placement position. Anything set outside the box is ignored, so
 * an object never grows past its size.</p>
 */
public final class NatureModel {

    /**
     * The biggest number of voxels a model may hold.
     */
    public static final long MAX_VOLUME = 8L * 1024 * 1024;

    /**
     * Receives the voxels of a model.
     */
    @FunctionalInterface
    public interface Visitor {

        /**
         * Visit one voxel.
         *
         * @param x the x offset
         * @param y the y offset
         * @param z the z offset
         * @param part the part number, at least 1
         */
        void visit(int x, int y, int z, int part);
    }

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] cells;

    /**
     * Create an empty model spanning the given offsets (inclusive).
     *
     * @param minX lowest x
     * @param minY lowest y
     * @param minZ lowest z
     * @param maxX highest x
     * @param maxY highest y
     * @param maxZ highest z
     */
    public NatureModel(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        checkArgument(maxX >= minX && maxY >= minY && maxZ >= minZ, "empty model");
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        checkArgument(volume <= MAX_VOLUME, "That is too big to generate");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.cells = new byte[(int) volume];
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    /**
     * Whether a position is inside the box.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return true if inside
     */
    public boolean inBounds(int x, int y, int z) {
        return x >= minX && y >= minY && z >= minZ && x < minX + sizeX && y < minY + sizeY && z < minZ + sizeZ;
    }

    /**
     * Set a voxel's part; ignored outside the box.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param part the part number (1 to 127), or 0 to clear
     */
    public void set(int x, int y, int z, int part) {
        if (inBounds(x, y, z)) {
            cells[index(x, y, z)] = (byte) part;
        }
    }

    /**
     * Set a voxel's part only if it is empty.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param part the part number
     */
    public void setIfEmpty(int x, int y, int z, int part) {
        if (inBounds(x, y, z) && cells[index(x, y, z)] == 0) {
            cells[index(x, y, z)] = (byte) part;
        }
    }

    /**
     * Get a voxel's part.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return the part, or 0 if empty or outside
     */
    public int get(int x, int y, int z) {
        return inBounds(x, y, z) ? cells[index(x, y, z)] : 0;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return minX + sizeX - 1;
    }

    public int getMaxY() {
        return minY + sizeY - 1;
    }

    public int getMaxZ() {
        return minZ + sizeZ - 1;
    }

    /**
     * The number of filled voxels.
     *
     * @return the count
     */
    public int count() {
        int count = 0;
        for (byte cell : cells) {
            if (cell != 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * The number of voxels of one part.
     *
     * @param part the part
     * @return the count
     */
    public int count(int part) {
        int count = 0;
        for (byte cell : cells) {
            if (cell == part) {
                count++;
            }
        }
        return count;
    }

    /**
     * Visit every filled voxel, bottom layer first.
     *
     * @param visitor the visitor
     */
    public void forEach(Visitor visitor) {
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    if (cells[i] != 0) {
                        visitor.visit(x + minX, y + minY, z + minZ, cells[i]);
                    }
                }
            }
        }
    }

    /**
     * Label the face-connected groups of filled voxels.
     *
     * @param labels receives a group number (from 1) for every voxel, 0 for empty ones
     * @return the size of each group, indexed by group number
     */
    private int[] label(int[] labels) {
        int[] queue = new int[cells.length];
        int[] neighbours = new int[6];
        int[] sizes = new int[16];
        int groups = 0;
        int layer = sizeX * sizeZ;
        for (int start = 0; start < cells.length; start++) {
            if (cells[start] == 0 || labels[start] != 0) {
                continue;
            }
            groups++;
            if (groups >= sizes.length) {
                sizes = Arrays.copyOf(sizes, sizes.length * 2);
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            labels[start] = groups;
            while (head < tail) {
                int current = queue[head++];
                int x = current % sizeX;
                int z = (current / sizeX) % sizeZ;
                int y = current / layer;
                neighbours[0] = x > 0 ? current - 1 : -1;
                neighbours[1] = x < sizeX - 1 ? current + 1 : -1;
                neighbours[2] = z > 0 ? current - sizeX : -1;
                neighbours[3] = z < sizeZ - 1 ? current + sizeX : -1;
                neighbours[4] = y > 0 ? current - layer : -1;
                neighbours[5] = y < sizeY - 1 ? current + layer : -1;
                for (int neighbour : neighbours) {
                    if (neighbour >= 0 && cells[neighbour] != 0 && labels[neighbour] == 0) {
                        labels[neighbour] = groups;
                        queue[tail++] = neighbour;
                    }
                }
            }
            sizes[groups] = tail;
        }
        return Arrays.copyOf(sizes, groups + 1);
    }

    /**
     * The number of separate (face-connected) pieces.
     *
     * @return the number of pieces
     */
    public int pieces() {
        return label(new int[cells.length]).length - 1;
    }

    /**
     * Remove every piece except the biggest, so nothing floats on its own.
     *
     * @return the number of voxels removed
     */
    public int keepLargestPiece() {
        int[] labels = new int[cells.length];
        int[] sizes = label(labels);
        int best = 0;
        for (int group = 1; group < sizes.length; group++) {
            if (best == 0 || sizes[group] > sizes[best]) {
                best = group;
            }
        }
        int removed = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != 0 && labels[i] != best) {
                cells[i] = 0;
                removed++;
            }
        }
        return removed;
    }

    /**
     * The lowest y with a filled voxel.
     *
     * @return the y, or {@code getMaxY() + 1} if the model is empty
     */
    public int lowestY() {
        int layer = sizeX * sizeZ;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != 0) {
                return i / layer + minY;
            }
        }
        return getMaxY() + 1;
    }

    /**
     * The highest y with a filled voxel.
     *
     * @return the y, or {@code getMinY() - 1} if the model is empty
     */
    public int highestY() {
        int layer = sizeX * sizeZ;
        for (int i = cells.length - 1; i >= 0; i--) {
            if (cells[i] != 0) {
                return i / layer + minY;
            }
        }
        return getMinY() - 1;
    }
}
