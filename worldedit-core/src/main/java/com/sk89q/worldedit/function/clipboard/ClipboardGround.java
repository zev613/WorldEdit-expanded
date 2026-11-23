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

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.diff.WorldDiff;
import com.sk89q.worldedit.function.generator.landscape.ExtentTerrainSampler;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.registry.BlockMaterial;

/**
 * Ground finding for placing clipboard copies on the terrain.
 */
public final class ClipboardGround {

    private ClipboardGround() {
    }

    /**
     * Whether a block is ground for dropping whole copies (//stackpath -g, //grid -g): anything
     * that is not air and not a plant that gets replaced when building.
     *
     * @param state the block
     * @return true if ground
     */
    public static boolean isGround(BlockState state) {
        if (WorldDiff.isAir(state)) {
            return false;
        }
        BlockMaterial material = state.getBlockType().getMaterial();
        // grass and other plants that get replaced when building are not ground
        return material.isSolid() || material.isLiquid() || !material.isReplacedDuringPlacement();
    }

    /**
     * Whether a block is terrain for conform paste: a solid block that is not leaves, logs or
     * other tree parts, and not a snow layer; water only when asked.
     *
     * @param state the block
     * @param waterIsGround true to count water (and other liquids) as the surface
     * @return true if terrain
     */
    public static boolean isTerrain(BlockState state, boolean waterIsGround) {
        if (state.getBlockType() == BlockTypes.SNOW) {
            return false;
        }
        if (state.getBlockType().getMaterial().isLiquid()) {
            return waterIsGround;
        }
        return ExtentTerrainSampler.isGround(state);
    }

    /**
     * Find the terrain surface nearest to a height in one column: if the block at {@code startY}
     * is terrain, go up to the last terrain block; otherwise go down to the first one.
     *
     * @param extent the extent
     * @param x the column x
     * @param z the column z
     * @param startY the height to start at
     * @param minY the lowest height to look at
     * @param maxY the highest height to look at
     * @param waterIsGround true to count water as the surface
     * @return the y of the surface block, or {@link Integer#MIN_VALUE} if there is none
     */
    public static int surfaceNear(Extent extent, int x, int z, int startY, int minY, int maxY,
                                  boolean waterIsGround) {
        int y = Math.max(minY, Math.min(maxY, startY));
        if (isTerrain(extent.getBlock(BlockVector3.at(x, y, z)), waterIsGround)) {
            while (y < maxY && isTerrain(extent.getBlock(BlockVector3.at(x, y + 1, z)), waterIsGround)) {
                y++;
            }
            return y;
        }
        for (y--; y >= minY; y--) {
            if (isTerrain(extent.getBlock(BlockVector3.at(x, y, z)), waterIsGround)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Move a copy up or down so its lowest solid layer rests on the highest ground under its
     * footprint. The search starts at the top of the copy, so a copy that is partly buried is lifted.
     *
     * @param extent the extent to read
     * @param minY the world's lowest y
     * @param maxY the world's highest y
     * @param placer the copy
     * @param to where the copy's origin would go
     * @param lowestOffset the copy's lowest solid offset from its origin
     * @return where the copy's origin goes
     */
    public static BlockVector3 onGround(Extent extent, int minY, int maxY, TransformedClipboardPlacer placer,
                                        BlockVector3 to, int lowestOffset) {
        BlockVector3 min = to.add(placer.getMinimum());
        BlockVector3 max = to.add(placer.getMaximum());
        int top = Math.min(max.y(), maxY);
        int ground = Integer.MIN_VALUE;
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int y = top; y >= minY && y > ground; y--) {
                    if (isGround(extent.getBlock(BlockVector3.at(x, y, z)))) {
                        ground = y;
                        break;
                    }
                }
            }
        }
        if (ground == Integer.MIN_VALUE) {
            return to;
        }
        return to.withY(ground + 1 - lowestOffset);
    }
}
