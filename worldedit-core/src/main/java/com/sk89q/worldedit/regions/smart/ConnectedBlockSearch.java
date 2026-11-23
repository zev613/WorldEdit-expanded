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

import com.sk89q.worldedit.math.BlockVector3;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A "magic wand" flood fill: finds the blocks connected to a start block that pass a test.
 *
 * <p>Blocks are connected through their faces (6 neighbours), or with {@code diagonal} also through
 * their edges and corners (26 neighbours), so zig-zag lines and diagonal stairs that only touch at
 * edges are followed too. The search never goes further than {@code maxDistance} blocks from the start
 * along any axis, and stops once {@code maxBlocks} blocks were found.</p>
 */
public final class ConnectedBlockSearch {

    /**
     * Decides whether a block belongs to the selection.
     */
    @FunctionalInterface
    public interface BlockTest {
        boolean test(int x, int y, int z);
    }

    /**
     * The result of a search.
     *
     * @param blocks the blocks found (including the start)
     * @param reachedDistanceLimit true if matching blocks continued beyond the distance limit
     * @param reachedBlockLimit true if the search stopped because it found the most blocks allowed
     */
    public record Result(BlockPosSet blocks, boolean reachedDistanceLimit, boolean reachedBlockLimit) {
    }

    /** The largest distance limit; positions are packed into 21 bits per axis. */
    public static final int MAX_DISTANCE = 1_000_000;

    private static final int[][] FACES = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };
    private static final int[][] ALL;

    static {
        ALL = new int[26][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        ALL[i++] = new int[] {dx, dy, dz};
                    }
                }
            }
        }
    }

    private ConnectedBlockSearch() {
    }

    /**
     * The neighbour offsets used.
     *
     * @param diagonal true for 26 neighbours, false for 6
     * @return the offsets
     */
    static int[][] neighbours(boolean diagonal) {
        return diagonal ? ALL : FACES;
    }

    /**
     * Find the blocks connected to a start block.
     *
     * @param start the start block; it is included if it passes the test
     * @param test which blocks belong to the selection
     * @param diagonal true to also connect through edges and corners
     * @param maxDistance how far from the start to look, per axis
     * @param maxBlocks the most blocks to find
     * @return the result (empty if the start block fails the test)
     */
    public static Result search(BlockVector3 start, BlockTest test, boolean diagonal, int maxDistance, long maxBlocks) {
        checkNotNull(start);
        checkNotNull(test);
        checkArgument(maxDistance >= 0 && maxDistance <= MAX_DISTANCE, "maxDistance out of range");
        checkArgument(maxBlocks >= 1, "maxBlocks must be at least 1");
        BlockPosSet found = new BlockPosSet();
        if (!test.test(start.x(), start.y(), start.z())) {
            return new Result(found, false, false);
        }
        // Rejected blocks are remembered too, so each block is tested at most once
        BlockPosSet rejected = new BlockPosSet();
        int[][] offsets = neighbours(diagonal);
        LongQueue queue = new LongQueue();
        found.add(start.x(), start.y(), start.z());
        queue.add(pack(0, 0, 0));
        boolean distanceLimited = false;
        boolean blockLimited = false;
        outer:
        while (!queue.isEmpty()) {
            long packed = queue.poll();
            int ox = unpack(packed, 2);
            int oy = unpack(packed, 1);
            int oz = unpack(packed, 0);
            for (int[] offset : offsets) {
                int nx = ox + offset[0];
                int ny = oy + offset[1];
                int nz = oz + offset[2];
                int x = start.x() + nx;
                int y = start.y() + ny;
                int z = start.z() + nz;
                if (found.contains(x, y, z) || rejected.contains(x, y, z)) {
                    continue;
                }
                boolean outside = Math.abs(nx) > maxDistance || Math.abs(ny) > maxDistance || Math.abs(nz) > maxDistance;
                if (!test.test(x, y, z)) {
                    rejected.add(x, y, z);
                    continue;
                }
                if (outside) {
                    distanceLimited = true;
                    rejected.add(x, y, z);
                    continue;
                }
                if (found.size() >= maxBlocks) {
                    blockLimited = true;
                    break outer;
                }
                found.add(x, y, z);
                queue.add(pack(nx, ny, nz));
            }
        }
        return new Result(found, distanceLimited, blockLimited);
    }

    /**
     * Add the blocks that touch a set of blocks through a face and pass a test, such as the layer of
     * ground a build stands on.
     *
     * @param blocks the blocks
     * @param test which touching blocks to add
     * @param maxBlocks the most blocks the result may have
     * @return a new set with the blocks and the touching layer
     */
    public static BlockPosSet withTouchingLayer(BlockPosSet blocks, BlockTest test, long maxBlocks) {
        BlockPosSet result = blocks.copy();
        blocks.forEach((x, y, z) -> {
            for (int[] offset : FACES) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (result.size() < maxBlocks && !result.contains(nx, ny, nz) && test.test(nx, ny, nz)) {
                    result.add(nx, ny, nz);
                }
            }
        });
        return result;
    }

    private static long pack(int x, int y, int z) {
        long mask = (1L << 21) - 1;
        return ((x & mask) << 42) | ((y & mask) << 21) | (z & mask);
    }

    private static int unpack(long packed, int slot) {
        int value = (int) ((packed >>> (21 * slot)) & ((1L << 21) - 1));
        return (value << 11) >> 11;
    }

    /**
     * A growable ring buffer of longs, so the queue does not box every position.
     */
    private static final class LongQueue {
        private long[] items = new long[1024];
        private int head;
        private int count;

        void add(long value) {
            if (count == items.length) {
                long[] bigger = new long[items.length * 2];
                for (int i = 0; i < count; i++) {
                    bigger[i] = items[(head + i) % items.length];
                }
                items = bigger;
                head = 0;
            }
            items[(head + count) % items.length] = value;
            count++;
        }

        long poll() {
            long value = items[head];
            head = (head + 1) % items.length;
            count--;
            return value;
        }

        boolean isEmpty() {
            return count == 0;
        }
    }
}
