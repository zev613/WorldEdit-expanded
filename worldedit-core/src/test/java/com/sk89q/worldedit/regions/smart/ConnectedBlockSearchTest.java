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
import com.sk89q.worldedit.regions.ShapeRegion;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedBlockSearchTest {

    private static ConnectedBlockSearch.BlockTest of(Set<BlockVector3> blocks) {
        return (x, y, z) -> blocks.contains(BlockVector3.at(x, y, z));
    }

    /** A staircase going up diagonally in x/y: each step only touches the next at an edge. */
    private static Set<BlockVector3> zigZag(int steps) {
        Set<BlockVector3> blocks = new HashSet<>();
        for (int i = 0; i < steps; i++) {
            blocks.add(BlockVector3.at(i, i, 0));
        }
        return blocks;
    }

    @Test
    void zigZagNeedsDiagonals() {
        Set<BlockVector3> blocks = zigZag(10);
        ConnectedBlockSearch.Result faces = ConnectedBlockSearch.search(BlockVector3.ZERO, of(blocks), false, 100, 1000);
        assertEquals(1, faces.blocks().size());
        ConnectedBlockSearch.Result diagonal = ConnectedBlockSearch.search(BlockVector3.ZERO, of(blocks), true, 100, 1000);
        assertEquals(10, diagonal.blocks().size());
        for (BlockVector3 block : blocks) {
            assertTrue(diagonal.blocks().contains(block));
        }
        assertFalse(diagonal.reachedDistanceLimit());
        assertFalse(diagonal.reachedBlockLimit());
    }

    @Test
    void cornerOnlyContact() {
        Set<BlockVector3> blocks = Set.of(BlockVector3.ZERO, BlockVector3.at(1, 1, 1));
        assertEquals(1, ConnectedBlockSearch.search(BlockVector3.ZERO, of(blocks), false, 10, 10).blocks().size());
        assertEquals(2, ConnectedBlockSearch.search(BlockVector3.ZERO, of(blocks), true, 10, 10).blocks().size());
    }

    @Test
    void doesNotCrossGaps() {
        Set<BlockVector3> blocks = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            blocks.add(BlockVector3.at(x, 0, 0));
            blocks.add(BlockVector3.at(x + 7, 0, 0));
        }
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(BlockVector3.ZERO, of(blocks), true, 100, 1000);
        assertEquals(5, result.blocks().size());
    }

    @Test
    void stopsAtDistance() {
        // an endless line
        ConnectedBlockSearch.BlockTest line = (_, y, z) -> y == 0 && z == 0;
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(BlockVector3.ZERO, line, false, 5, 1000);
        assertEquals(11, result.blocks().size());
        assertTrue(result.reachedDistanceLimit());
        assertFalse(result.reachedBlockLimit());
        assertEquals(BlockVector3.at(-5, 0, 0), result.blocks().getMinimum());
        assertEquals(BlockVector3.at(5, 0, 0), result.blocks().getMaximum());
    }

    @Test
    void stopsAtBlockLimit() {
        ConnectedBlockSearch.BlockTest everything = (_, _, _) -> true;
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(BlockVector3.ZERO, everything, true, 1000, 500);
        assertEquals(500, result.blocks().size());
        assertTrue(result.reachedBlockLimit());
    }

    @Test
    void startMustMatch() {
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(BlockVector3.ZERO, (_, _, _) -> false,
            true, 10, 10);
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void largeFillAcrossSections() {
        // a 40x3x40 slab with negative coordinates, spanning many 16^3 sections
        ConnectedBlockSearch.BlockTest slab = (x, y, z) -> x >= -20 && x < 20 && y >= -1 && y <= 1 && z >= -20 && z < 20;
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(BlockVector3.at(-3, 0, 7), slab, false, 100,
            100_000);
        assertEquals(40 * 3 * 40, result.blocks().size());
        long[] count = {0};
        result.blocks().forEach((x, y, z) -> {
            assertTrue(slab.test(x, y, z));
            count[0]++;
        });
        assertEquals(40 * 3 * 40, count[0]);
    }

    @Test
    void exactSelectionThroughShapeRegion() {
        Set<BlockVector3> blocks = zigZag(6);
        blocks.add(BlockVector3.at(0, 0, 1));
        BlockVector3 start = BlockVector3.at(0, 0, 0);
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(start, of(blocks), true, 100, 1000);
        VoxelGrid grid = result.blocks().toVoxelGrid(start);
        ShapeRegion region = new ShapeRegion(null, grid, start);
        assertEquals(7, region.getVolume());
        Set<BlockVector3> visited = new HashSet<>();
        region.forEach(visited::add);
        assertEquals(blocks, visited);
        assertFalse(region.contains(BlockVector3.at(1, 0, 0)));
    }

    @Test
    void touchingLayer() {
        Set<BlockVector3> wall = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            wall.add(BlockVector3.at(x, 1, 0));
            wall.add(BlockVector3.at(x, 2, 0));
        }
        BlockPosSet blocks = ConnectedBlockSearch.search(BlockVector3.at(0, 1, 0), of(wall), true, 10, 100).blocks();
        // ground is everything at y <= 0
        BlockPosSet withGround = ConnectedBlockSearch.withTouchingLayer(blocks, (_, y, _) -> y <= 0, 1000);
        assertEquals(6 + 3, withGround.size());
        assertTrue(withGround.contains(1, 0, 0));
        assertFalse(withGround.contains(1, -1, 0));
        assertFalse(withGround.contains(1, 0, 1));
    }

    @Test
    void naturalBlocks() {
        assertTrue(NaturalBlocks.isNatural("minecraft:grass_block"));
        assertTrue(NaturalBlocks.isNatural("minecraft:deepslate"));
        assertTrue(NaturalBlocks.isNatural("minecraft:deepslate_iron_ore"));
        assertTrue(NaturalBlocks.isNatural("minecraft:nether_quartz_ore"));
        assertTrue(NaturalBlocks.isNatural("minecraft:poppy"));
        assertTrue(NaturalBlocks.isNatural("minecraft:water"));
        assertTrue(NaturalBlocks.isNatural("stone"));
        assertFalse(NaturalBlocks.isNatural("minecraft:oak_leaves"));
        assertFalse(NaturalBlocks.isNatural("minecraft:sandstone"));
        assertFalse(NaturalBlocks.isNatural("minecraft:cobblestone"));
        assertFalse(NaturalBlocks.isNatural("minecraft:snow"));
        assertFalse(NaturalBlocks.isNatural("minecraft:stone_bricks"));
        assertFalse(NaturalBlocks.isNatural("minecraft:oak_planks"));
        assertFalse(NaturalBlocks.isNatural("somemod:stone"));
        assertTrue(NaturalBlocks.isFluid("minecraft:lava"));
        assertFalse(NaturalBlocks.isFluid("minecraft:dirt"));
    }
}
