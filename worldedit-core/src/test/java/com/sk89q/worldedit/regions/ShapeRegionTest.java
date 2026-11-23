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

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.ShapeGenerator;
import com.sk89q.worldedit.function.generator.shape.ShapeLibrary;
import com.sk89q.worldedit.function.generator.shape.ShapeOrientation;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.shape.ShapeRasterizer;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.selector.ShapeRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeRegionTest {

    private static VoxelGrid heart(double size) {
        NamedShape shape = new ShapeLibrary(null).find("heart").orElseThrow();
        return ShapeGenerator.voxelize(shape, ShapeParameters.defaults(shape.getParameters()),
            Vector3.at(size, size, size), ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
    }

    @Test
    void containsExactlyTheVoxels() {
        VoxelGrid voxels = heart(8);
        BlockVector3 origin = BlockVector3.at(100, 64, -40);
        ShapeRegion region = new ShapeRegion(null, voxels, origin);

        Set<BlockVector3> visited = new HashSet<>();
        for (BlockVector3 position : region) {
            assertTrue(region.contains(position));
            assertTrue(visited.add(position));
        }
        assertEquals(voxels.count(), visited.size());
        assertEquals(voxels.count(), region.getVolume());

        // everything in the bounding box but outside the shape is not contained
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        long inBox = 0;
        for (BlockVector3 position : new CuboidRegion(min, max)) {
            if (region.contains(position)) {
                inBox++;
            }
        }
        assertEquals(voxels.count(), inBox);
        assertTrue(region.getBoundingBox().getVolume() > region.getVolume());
        // the bounds are tight
        assertTrue(visited.stream().anyMatch(p -> p.x() == min.x()));
        assertTrue(visited.stream().anyMatch(p -> p.y() == max.y()));
        assertFalse(region.contains(min.subtract(1, 1, 1)));
    }

    @Test
    void shiftMovesAndExpandIsRefused() {
        ShapeRegion region = new ShapeRegion(null, heart(6), BlockVector3.ZERO);
        BlockVector3 min = region.getMinimumPoint();
        ShapeRegion copy = region.clone();
        assertNotSame(region, copy);
        region.shift(BlockVector3.at(10, 5, 0));
        assertEquals(min.add(10, 5, 0), region.getMinimumPoint());
        assertEquals(min, copy.getMinimumPoint());
        // a RegionOperationException; without a platform, building its message fails first, so accept any exception
        BlockVector3 before = region.getMaximumPoint();
        assertThrows(Exception.class, () -> region.expand(BlockVector3.UNIT_Y));
        assertThrows(Exception.class, () -> region.contract(BlockVector3.UNIT_Y));
        assertEquals(before, region.getMaximumPoint());
    }

    @Test
    void chunksCoverEveryVoxel() {
        ShapeRegion region = new ShapeRegion(null, heart(12), BlockVector3.at(8, 64, 8));
        Set<BlockVector2> chunks = region.getChunks();
        for (BlockVector3 position : region) {
            assertTrue(chunks.contains(BlockVector2.at(position.x() >> 4, position.z() >> 4)));
        }
    }

    @Test
    void selectorMovesWithClicksAndClears() throws IncompleteRegionException {
        VoxelGrid voxels = heart(5);
        ShapeRegionSelector selector = new ShapeRegionSelector(null, voxels, BlockVector3.at(0, 10, 0),
            BlockVector3.at(0, -voxels.lowestY(), 0), "heart 5");
        assertTrue(selector.isDefined());
        assertEquals(voxels.count(), selector.getVolume());
        assertEquals(10, selector.getRegion().getMinimumPoint().y());
        assertEquals(BlockVector3.at(0, 10, 0), selector.getPrimaryPosition());

        assertFalse(selector.selectPrimary(BlockVector3.at(0, 10, 0), PermissiveSelectorLimits.getInstance()));
        assertTrue(selector.selectSecondary(BlockVector3.at(50, 20, 50), PermissiveSelectorLimits.getInstance()));
        assertEquals(BlockVector3.at(50, 20, 50), selector.getPrimaryPosition());
        assertEquals(20, selector.getRegion().getMinimumPoint().y());

        selector.clear();
        assertFalse(selector.isDefined());
        assertThrows(IncompleteRegionException.class, selector::getRegion);
        assertTrue(selector.selectPrimary(BlockVector3.at(50, 20, 50), PermissiveSelectorLimits.getInstance()));
        assertTrue(selector.isDefined());
        assertEquals("shape", selector.getTypeName());
    }
}
