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

package com.sk89q.worldedit.function.generator.warp;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Applies a {@link WarpMap} to a region.
 *
 * <p>The region is first copied into a snapshot, so every block reads the original and never a
 * block that was already warped. Each block then takes the snapshot block nearest to the position
 * the map gives. Source positions outside the selection are read with {@code outside} instead,
 * which usually gives air.</p>
 */
public final class Warper {

    /**
     * The largest bounding box (in blocks) that can be warped at once.
     */
    public static final long MAX_VOLUME = 256L * 256 * 256;

    /**
     * Writes a block.
     *
     * @param <T> the block type
     */
    @FunctionalInterface
    public interface BlockWriter<T> {

        /**
         * Write a block.
         *
         * @param position the position
         * @param block the block
         * @throws WorldEditException if the block cannot be set, e.g. too many changes
         */
        void set(BlockVector3 position, T block) throws WorldEditException;
    }

    private Warper() {
    }

    /**
     * Warp a region.
     *
     * @param region the region
     * @param map the warp map
     * @param reader reads the original blocks of the region
     * @param outside gives the block for source positions outside the region
     * @param empty the block used where the map gives no source
     * @param writer writes changed blocks
     * @param <T> the block type
     * @return the number of blocks changed
     * @throws WorldEditException if the writer fails
     * @throws IllegalArgumentException if the region is too big
     */
    public static <T> int warp(Region region, WarpMap map, Function<BlockVector3, T> reader,
                               Function<BlockVector3, T> outside, T empty,
                               BlockWriter<T> writer) throws WorldEditException {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        long sizeX = max.x() - min.x() + 1L;
        long sizeY = max.y() - min.y() + 1L;
        long sizeZ = max.z() - min.z() + 1L;
        if (sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("The selection is too big to warp (at most " + MAX_VOLUME
                + " blocks in its bounding box)");
        }
        Snapshot<T> snapshot = new Snapshot<>(min, (int) sizeX, (int) sizeY, (int) sizeZ);
        for (BlockVector3 position : region) {
            snapshot.put(position, reader.apply(position));
        }

        int changed = 0;
        for (BlockVector3 position : region) {
            Vector3 source = map.source(position.x(), position.y(), position.z());
            T block = empty;
            if (source != null && Double.isFinite(source.x()) && Double.isFinite(source.y())
                && Double.isFinite(source.z())) {
                int sx = nearest(source.x());
                int sy = nearest(source.y());
                int sz = nearest(source.z());
                T found = snapshot.get(sx, sy, sz);
                block = found != null ? found : outside.apply(BlockVector3.at(sx, sy, sz));
            }
            if (!Objects.equals(block, snapshot.get(position.x(), position.y(), position.z()))) {
                writer.set(position, block);
                changed++;
            }
        }
        return changed;
    }

    private static int nearest(double value) {
        double rounded = Math.floor(value + 0.5);
        return (int) Math.max(Integer.MIN_VALUE / 2.0, Math.min(Integer.MAX_VALUE / 2.0, rounded));
    }

    private static final class Snapshot<T> {
        private final BlockVector3 min;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Object[] blocks;

        Snapshot(BlockVector3 min, int sizeX, int sizeY, int sizeZ) {
            this.min = min;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = new Object[sizeX * sizeY * sizeZ];
        }

        private int index(int x, int y, int z) {
            int dx = x - min.x();
            int dy = y - min.y();
            int dz = z - min.z();
            if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) {
                return -1;
            }
            return (dy * sizeZ + dz) * sizeX + dx;
        }

        void put(BlockVector3 position, T block) {
            blocks[index(position.x(), position.y(), position.z())] = block;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        T get(int x, int y, int z) {
            int index = index(x, y, z);
            return index < 0 ? null : (T) blocks[index];
        }
    }
}
