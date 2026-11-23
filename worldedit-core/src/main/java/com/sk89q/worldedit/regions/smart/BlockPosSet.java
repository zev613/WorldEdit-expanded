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

package com.sk89q.worldedit.regions.smart;

import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A memory-efficient set of block positions.
 *
 * <p>Positions are grouped into 16x16x16 sections; each section that holds at least one position
 * costs one 512-byte bit array, so a dense set of a million blocks takes well under a megabyte,
 * and sparse sets only pay for the sections they touch.</p>
 */
public final class BlockPosSet {

    /**
     * Receives positions.
     */
    @FunctionalInterface
    public interface PositionConsumer {
        void accept(int x, int y, int z);
    }

    /** The largest bounding box {@link #toVoxelGrid(BlockVector3)} accepts. */
    public static final long MAX_GRID_VOLUME = 1L << 29;

    private static final int SECTION_BITS = 4;
    private static final int SECTION_MASK = 15;
    private static final int KEY_BITS = 21;
    private static final long KEY_MASK = (1L << KEY_BITS) - 1;

    private final Map<Long, long[]> sections = new HashMap<>();
    private long size;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    private static long key(int sectionX, int sectionY, int sectionZ) {
        return ((sectionX & KEY_MASK) << (2 * KEY_BITS)) | ((sectionY & KEY_MASK) << KEY_BITS) | (sectionZ & KEY_MASK);
    }

    private static int bit(int x, int y, int z) {
        return ((y & SECTION_MASK) << 8) | ((z & SECTION_MASK) << 4) | (x & SECTION_MASK);
    }

    /**
     * Add a position.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return true if it was not in the set before
     */
    public boolean add(int x, int y, int z) {
        long[] words = sections.computeIfAbsent(key(x >> SECTION_BITS, y >> SECTION_BITS, z >> SECTION_BITS),
            _ -> new long[64]);
        int bit = bit(x, y, z);
        long mask = 1L << (bit & 63);
        if ((words[bit >>> 6] & mask) != 0) {
            return false;
        }
        words[bit >>> 6] |= mask;
        size++;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
        return true;
    }

    /**
     * Whether a position is in the set.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return true if present
     */
    public boolean contains(int x, int y, int z) {
        long[] words = sections.get(key(x >> SECTION_BITS, y >> SECTION_BITS, z >> SECTION_BITS));
        if (words == null) {
            return false;
        }
        int bit = bit(x, y, z);
        return (words[bit >>> 6] & (1L << (bit & 63))) != 0;
    }

    public boolean contains(BlockVector3 position) {
        return contains(position.x(), position.y(), position.z());
    }

    public long size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * The lowest corner of the positions' bounding box.
     *
     * @return the corner
     */
    public BlockVector3 getMinimum() {
        checkArgument(size > 0, "empty set");
        return BlockVector3.at(minX, minY, minZ);
    }

    /**
     * The highest corner of the positions' bounding box.
     *
     * @return the corner
     */
    public BlockVector3 getMaximum() {
        checkArgument(size > 0, "empty set");
        return BlockVector3.at(maxX, maxY, maxZ);
    }

    /**
     * Visit every position (in no particular order).
     *
     * @param consumer receives the positions
     */
    public void forEach(PositionConsumer consumer) {
        for (Map.Entry<Long, long[]> entry : sections.entrySet()) {
            long key = entry.getKey();
            int baseX = signExtend(key >>> (2 * KEY_BITS)) << SECTION_BITS;
            int baseY = signExtend(key >>> KEY_BITS) << SECTION_BITS;
            int baseZ = signExtend(key) << SECTION_BITS;
            long[] words = entry.getValue();
            for (int w = 0; w < words.length; w++) {
                long word = words[w];
                while (word != 0) {
                    int bit = (w << 6) | Long.numberOfTrailingZeros(word);
                    word &= word - 1;
                    consumer.accept(baseX + (bit & SECTION_MASK), baseY + (bit >>> 8),
                        baseZ + ((bit >>> 4) & SECTION_MASK));
                }
            }
        }
    }

    private static int signExtend(long value) {
        int masked = (int) (value & KEY_MASK);
        return (masked << (32 - KEY_BITS)) >> (32 - KEY_BITS);
    }

    /**
     * A copy of this set.
     *
     * @return the copy
     */
    public BlockPosSet copy() {
        BlockPosSet copy = new BlockPosSet();
        sections.forEach((key, words) -> copy.sections.put(key, words.clone()));
        copy.size = size;
        copy.minX = minX;
        copy.minY = minY;
        copy.minZ = minZ;
        copy.maxX = maxX;
        copy.maxY = maxY;
        copy.maxZ = maxZ;
        return copy;
    }

    /**
     * Whether {@link #toVoxelGrid(BlockVector3)} can hold these positions: their bounding box may have at most
     * {@value #MAX_GRID_VOLUME} blocks (a 64 MB bit grid).
     *
     * @return true if it fits
     */
    public boolean fitsVoxelGrid() {
        return size > 0
            && (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) <= MAX_GRID_VOLUME;
    }

    /**
     * Copy the positions into a voxel grid, as offsets from an origin.
     *
     * @param origin the position that becomes offset (0, 0, 0)
     * @return the grid
     */
    public VoxelGrid toVoxelGrid(BlockVector3 origin) {
        checkArgument(fitsVoxelGrid(), "the positions span too large a box");
        VoxelGrid grid = new VoxelGrid(minX - origin.x(), minY - origin.y(), minZ - origin.z(),
            maxX - origin.x(), maxY - origin.y(), maxZ - origin.z());
        forEach((x, y, z) -> grid.set(x - origin.x(), y - origin.y(), z - origin.z()));
        return grid;
    }
}
