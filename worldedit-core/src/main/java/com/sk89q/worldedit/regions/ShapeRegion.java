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

package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.storage.ChunkStore;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A region made of exactly the voxels of a generated shape (a heart, a star, a torus, ...),
 * so that region commands such as {@code //set}, {@code //replace} or {@code //count} work inside it.
 *
 * <p>The shape can be moved with {@link #shift(BlockVector3)}, but not expanded or contracted:
 * a different size means generating the shape again.</p>
 */
public class ShapeRegion extends AbstractRegion {

    private final VoxelGrid voxels;
    private final long volume;
    // bounds of the voxels that are on, as offsets from the origin
    private final BlockVector3 minOffset;
    private final BlockVector3 maxOffset;
    private BlockVector3 origin;

    /**
     * Create a region from the voxels of a shape.
     *
     * @param world the world, or null
     * @param voxels the voxels, as offsets from {@code origin}; must not be changed afterwards
     * @param origin where offset (0, 0, 0) of the voxels is
     */
    public ShapeRegion(@Nullable World world, VoxelGrid voxels, BlockVector3 origin) {
        super(world);
        checkNotNull(voxels);
        checkNotNull(origin);
        checkArgument(voxels.count() > 0, "the shape has no blocks");
        this.voxels = voxels;
        this.origin = origin;
        this.volume = voxels.count();
        int[] bounds = {
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
        };
        voxels.forEach(offset -> {
            bounds[0] = Math.min(bounds[0], offset.x());
            bounds[1] = Math.min(bounds[1], offset.y());
            bounds[2] = Math.min(bounds[2], offset.z());
            bounds[3] = Math.max(bounds[3], offset.x());
            bounds[4] = Math.max(bounds[4], offset.y());
            bounds[5] = Math.max(bounds[5], offset.z());
        });
        this.minOffset = BlockVector3.at(bounds[0], bounds[1], bounds[2]);
        this.maxOffset = BlockVector3.at(bounds[3], bounds[4], bounds[5]);
    }

    /**
     * Where offset (0, 0, 0) of the shape is in the world; normally the centre of the shape.
     *
     * @return the origin
     */
    public BlockVector3 getOrigin() {
        return origin;
    }

    /**
     * The voxels of the shape, as offsets from {@link #getOrigin()}.
     *
     * @return the voxels
     */
    public VoxelGrid getVoxels() {
        return voxels;
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return origin.add(minOffset);
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return origin.add(maxOffset);
    }

    @Override
    public long getVolume() {
        return volume;
    }

    @Override
    public void expand(BlockVector3... changes) throws RegionOperationException {
        throw unsupported();
    }

    @Override
    public void contract(BlockVector3... changes) throws RegionOperationException {
        throw unsupported();
    }

    private static RegionOperationException unsupported() {
        return new RegionOperationException(TextComponent.of(
            "A shape selection can't be expanded, contracted or outset. Select the shape again with a "
                + "different size (//selshape), or use //sel cuboid to turn it into its bounding box."));
    }

    @Override
    public void shift(BlockVector3 change) {
        origin = origin.add(change);
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return voxels.get(position.x() - origin.x(), position.y() - origin.y(), position.z() - origin.z());
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        BlockVector3 base = origin;
        Iterator<BlockVector3> offsets = voxels.offsets();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return offsets.hasNext();
            }

            @Override
            public BlockVector3 next() {
                return base.add(offsets.next());
            }
        };
    }

    @Override
    public Set<BlockVector2> getChunks() {
        Set<BlockVector2> chunks = new HashSet<>();
        for (BlockVector3 position : this) {
            chunks.add(BlockVector2.at(position.x() >> ChunkStore.CHUNK_SHIFTS, position.z() >> ChunkStore.CHUNK_SHIFTS));
        }
        return chunks;
    }

    @Override
    public Set<BlockVector3> getChunkCubes() {
        Set<BlockVector3> chunks = new HashSet<>();
        for (BlockVector3 position : this) {
            chunks.add(BlockVector3.at(position.x() >> ChunkStore.CHUNK_SHIFTS,
                position.y() >> ChunkStore.CHUNK_SHIFTS, position.z() >> ChunkStore.CHUNK_SHIFTS));
        }
        return chunks;
    }

    @Override
    public ShapeRegion clone() {
        return (ShapeRegion) super.clone();
    }
}
