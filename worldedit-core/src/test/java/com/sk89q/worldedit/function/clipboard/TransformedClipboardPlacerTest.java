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

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformedClipboardPlacerTest extends BaseWorldEditTest {

    private static BlockState state(String id) {
        BlockType type = BlockType.REGISTRY.get(id);
        if (type == null) {
            type = new BlockType(id);
            BlockType.REGISTRY.register(id, type);
        }
        return type.getDefaultState();
    }

    @Test
    void everyCopyOfAThinPostIsPlaced() throws WorldEditException {
        BlockState stone = state("worldedit:placer_test_stone");
        // (air is never registered here: other tests register it themselves)
        // a one-block-wide post, 6 high, with the origin at its base
        CuboidRegion postRegion = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(0, 5, 0));
        BlockArrayClipboard post = new BlockArrayClipboard(postRegion);
        post.setOrigin(BlockVector3.ZERO);
        for (BlockVector3 pos : postRegion) {
            post.setBlock(pos, stone);
        }

        CuboidRegion worldRegion = new CuboidRegion(BlockVector3.at(-20, 0, -20), BlockVector3.at(20, 10, 20));
        int[] counts = {7, 10, 12, 13};
        for (int count : counts) {
            BlockArrayClipboard world = new BlockArrayClipboard(worldRegion);
            RadialLayout layout = new RadialLayout(count, Vector3.ZERO, 11.0, 0);
            int placed = 0;
            for (int i = 0; i < count; i++) {
                placed += new TransformedClipboardPlacer(post, layout.transform(i), true).place(world, BlockVector3.ZERO);
            }
            Set<BlockVector3> columns = new HashSet<>();
            for (BlockVector3 pos : worldRegion) {
                if (world.getBlock(pos).getBlockType() == stone.getBlockType()) {
                    columns.add(pos.withY(0));
                }
            }
            assertEquals(count, columns.size(), "posts for " + count + " copies");
            assertEquals(count * 6, placed, "blocks for " + count + " copies");
        }
    }

    @Test
    void turnedSolidSquareHasNoHoles() throws WorldEditException {
        BlockState stone = state("worldedit:placer_test_stone");
        CuboidRegion squareRegion = new CuboidRegion(BlockVector3.at(-6, 0, -6), BlockVector3.at(6, 0, 6));
        BlockArrayClipboard square = new BlockArrayClipboard(squareRegion);
        square.setOrigin(BlockVector3.ZERO);
        for (BlockVector3 pos : squareRegion) {
            square.setBlock(pos, stone);
        }
        CuboidRegion worldRegion = new CuboidRegion(BlockVector3.at(-12, 0, -12), BlockVector3.at(12, 0, 12));
        for (double degrees : new double[] {17, 30, 45, 63}) {
            BlockArrayClipboard world = new BlockArrayClipboard(worldRegion);
            new TransformedClipboardPlacer(square, RadialLayout.clockwise(degrees), true).place(world, BlockVector3.ZERO);
            // everything within the square's inscribed circle (radius 6) must be filled
            for (BlockVector3 pos : worldRegion) {
                if (Math.hypot(pos.x(), pos.z()) <= 5.5) {
                    assertEquals(stone.getBlockType(), world.getBlock(pos).getBlockType(), "hole at " + pos + " for " + degrees);
                }
            }
        }
    }
}
