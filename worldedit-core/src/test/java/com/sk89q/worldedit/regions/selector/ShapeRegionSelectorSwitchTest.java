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

package com.sk89q.worldedit.regions.selector;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.ShapeRegion;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ShapeRegionSelectorSwitchTest {

    private static VoxelGrid grid() {
        VoxelGrid grid = new VoxelGrid(-1, -1, -1, 1, 1, 1);
        grid.set(0, 0, 0);
        grid.set(1, 0, 0);
        return grid;
    }

    @Test
    void shapeSelectionsMoveOnClick() throws IncompleteRegionException {
        ShapeRegionSelector selector = new ShapeRegionSelector(null, grid(), BlockVector3.ZERO, BlockVector3.ZERO, "heart");
        selector.selectPrimary(BlockVector3.at(10, 0, 0), PermissiveSelectorLimits.getInstance());
        assertInstanceOf(ShapeRegion.class, selector.getRegion());
        assertEquals(2, selector.getVolume());
        assertEquals(BlockVector3.at(10, 0, 0), selector.getAnchor());
    }

    @Test
    void connectedSelectionsStartANewSelectionOnClick() throws IncompleteRegionException {
        ShapeRegionSelector selector = new ShapeRegionSelector(null, grid(), BlockVector3.ZERO, BlockVector3.ZERO,
            "Connected blocks", "gravel").switchOnClick(RegionSelectorType.CUBOID);
        selector.selectPrimary(BlockVector3.at(0, 0, 0), PermissiveSelectorLimits.getInstance());
        selector.selectSecondary(BlockVector3.at(4, 4, 4), PermissiveSelectorLimits.getInstance());
        assertInstanceOf(CuboidRegion.class, selector.getRegion());
        assertEquals(125, selector.getVolume());
        assertEquals("cuboid", selector.getTypeID());
        assertEquals(RegionSelectorType.CUBOID, ShapeRegionSelector.typeOf(selector));
    }

    @Test
    void switchesBackToPolygonSelections() throws IncompleteRegionException {
        assertEquals(RegionSelectorType.POLYGON, ShapeRegionSelector.typeOf(new Polygonal2DRegionSelector()));
        ShapeRegionSelector selector = new ShapeRegionSelector(null, grid(), BlockVector3.ZERO, BlockVector3.ZERO,
            "Build", "build").switchOnClick(RegionSelectorType.POLYGON);
        selector.selectPrimary(BlockVector3.at(0, 0, 0), PermissiveSelectorLimits.getInstance());
        selector.selectSecondary(BlockVector3.at(4, 0, 0), PermissiveSelectorLimits.getInstance());
        selector.selectSecondary(BlockVector3.at(4, 0, 4), PermissiveSelectorLimits.getInstance());
        assertInstanceOf(Polygonal2DRegion.class, selector.getRegion());
        assertEquals(RegionSelectorType.POLYGON, ShapeRegionSelector.typeOf(selector));
    }
}
