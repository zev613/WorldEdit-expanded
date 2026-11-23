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

import com.sk89q.worldedit.math.BlockVector3;

import java.util.BitSet;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A box of on/off voxels, indexed by offsets from a centre block.
 */
public final class VoxelGrid {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet bits;

    /**
     * Create an empty grid spanning the given offsets (inclusive).
     *
     * @param minX the lowest x offset
     * @param minY the lowest y offset
     * @param minZ the lowest z offset
     * @param maxX the highest x offset
     * @param maxY the highest y offset
     * @param maxZ the highest z offset
     */
    public VoxelGrid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        checkArgument(maxX >= minX && maxY >= minY && maxZ >= minZ, "empty grid");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        checkArgument(volume <= Integer.MAX_VALUE, "grid too large");
        this.bits = new BitSet((int) volume);
    }

    private VoxelGrid(VoxelGrid other) {
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.sizeX = other.sizeX;
        this.sizeY = other.sizeY;
        this.sizeZ = other.sizeZ;
        this.bits = new BitSet(sizeX * sizeY * sizeZ);
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= minX && y >= minY && z >= minZ
            && x < minX + sizeX && y < minY + sizeY && z < minZ + sizeZ;
    }

    public boolean get(int x, int y, int z) {
        return inBounds(x, y, z) && bits.get(index(x, y, z));
    }

    /**
     * Turn on a voxel. Voxels outside the grid are ignored.
     *
     * @param x x offset
     * @param y y offset
     * @param z z offset
     */
    public void set(int x, int y, int z) {
        if (inBounds(x, y, z)) {
            bits.set(index(x, y, z));
        }
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
     * The number of voxels that are on.
     *
     * @return the count
     */
    public int count() {
        return bits.cardinality();
    }

    /**
     * The lowest y offset of any voxel that is on.
     *
     * @return the lowest y, or {@link Integer#MAX_VALUE} if the grid is empty
     */
    public int lowestY() {
        int first = bits.nextSetBit(0);
        return first < 0 ? Integer.MAX_VALUE : first / (sizeX * sizeZ) + minY;
    }

    /**
     * A copy containing only voxels with at least one face exposed.
     *
     * @param thickness how many layers of the shell to keep (1 or more)
     * @return the shell
     */
    public VoxelGrid shell(int thickness) {
        checkArgument(thickness >= 1, "thickness must be at least 1");
        VoxelGrid current = this;
        VoxelGrid result = new VoxelGrid(this);
        for (int layer = 0; layer < thickness; layer++) {
            VoxelGrid next = new VoxelGrid(this);
            final VoxelGrid source = current;
            for (int i = source.bits.nextSetBit(0); i >= 0; i = source.bits.nextSetBit(i + 1)) {
                int x = i % sizeX + minX;
                int z = (i / sizeX) % sizeZ + minZ;
                int y = i / (sizeX * sizeZ) + minY;
                boolean exposed = !source.get(x + 1, y, z) || !source.get(x - 1, y, z)
                    || !source.get(x, y + 1, z) || !source.get(x, y - 1, z)
                    || !source.get(x, y, z + 1) || !source.get(x, y, z - 1);
                if (exposed) {
                    result.bits.set(i);
                } else {
                    next.bits.set(i);
                }
            }
            current = next;
        }
        return result;
    }

    /**
     * Visit every voxel that is on, from the bottom up.
     *
     * @param consumer receives the offsets
     */
    public void forEach(Consumer<BlockVector3> consumer) {
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            int x = i % sizeX + minX;
            int z = (i / sizeX) % sizeZ + minZ;
            int y = i / (sizeX * sizeZ) + minY;
            consumer.accept(BlockVector3.at(x, y, z));
        }
    }
}
