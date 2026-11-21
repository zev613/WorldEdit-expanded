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

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Builds named shapes into an {@link EditSession}.
 */
public final class ShapeGenerator {

    private ShapeGenerator() {
    }

    /**
     * Compute the voxels of a shape.
     *
     * @param shape the shape
     * @param parameters its settings
     * @param radii the size in blocks along x, y and z
     * @param orientation the rotation
     * @param hollow whether to keep only the outer layer
     * @param maxVolume the largest bounding box allowed
     * @return the voxels, as offsets from the centre
     */
    public static VoxelGrid voxelize(NamedShape shape, ShapeParameters parameters, Vector3 radii,
                                     ShapeOrientation orientation, boolean hollow, long maxVolume) {
        ShapeInstance instance = shape.create(parameters, radii);
        VoxelGrid grid = new ShapeRasterizer(radii, orientation, maxVolume).rasterize(instance);
        return hollow ? grid.shell(1) : grid;
    }

    /**
     * Build a shape.
     *
     * @param editSession the edit session
     * @param position where to build it
     * @param pattern the blocks to use
     * @param voxels the voxels from {@link #voxelize}
     * @param bottom if true, the lowest layer of the shape goes at the position instead of its middle
     * @return the number of blocks changed
     * @throws MaxChangedBlocksException if too many blocks would be changed
     */
    public static int build(EditSession editSession, BlockVector3 position, Pattern pattern, VoxelGrid voxels,
                            boolean bottom) throws MaxChangedBlocksException {
        return build(editSession, List.of(position), pattern, voxels, bottom, null);
    }

    /**
     * Build copies of a shape, all in the same edit.
     *
     * @param editSession the edit session
     * @param positions where to build each copy
     * @param pattern the blocks to use
     * @param voxels the voxels from {@link #voxelize}
     * @param bottom if true, the lowest layer of the shape goes at the position instead of its middle
     * @param mask if not null, only blocks matching this mask are changed
     * @return the number of blocks changed
     * @throws MaxChangedBlocksException if too many blocks would be changed
     */
    public static int build(EditSession editSession, List<BlockVector3> positions, Pattern pattern, VoxelGrid voxels,
                            boolean bottom, @Nullable Mask mask) throws MaxChangedBlocksException {
        int changed = 0;
        int lift = bottom && voxels.count() > 0 ? -voxels.lowestY() : 0;
        for (BlockVector3 position : positions) {
            BlockVector3 origin = position.add(0, lift, 0);
            Iterator<BlockVector3> offsets = voxels.offsets();
            while (offsets.hasNext()) {
                BlockVector3 target = origin.add(offsets.next());
                if (mask != null && !mask.test(target)) {
                    continue;
                }
                if (editSession.setBlock(target, pattern)) {
                    changed++;
                }
            }
        }
        return changed;
    }
}
