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

package com.sk89q.worldedit.util;

import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongRangeTraceTest {

    private World world;
    private final Set<BlockVector3> solid = new HashSet<>();
    private final Set<BlockVector3> tested = new HashSet<>();
    private Mask mask;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        when(world.estimateSurfaceHeight(anyInt(), anyInt())).thenReturn(OptionalInt.empty());
        mask = position -> {
            tested.add(position);
            return solid.contains(position);
        };
    }

    @Test
    void findsAFarAwayBlock() {
        solid.add(BlockVector3.at(1500, 64, 0));
        LongRangeTrace.Hit hit = new LongRangeTrace(world, mask)
            .trace(Vector3.at(0.5, 64.5, 0.5), Vector3.at(1, 0, 0), 2048);
        assertEquals(BlockVector3.at(1500, 64, 0), hit.position());
        // the space just in front of the face that was hit, where -c builds
        assertEquals(BlockVector3.at(1499, 64, 0), hit.before());
        assertFalse(hit.estimated());
        assertEquals(1499.5, hit.distance(), 1e-6);
    }

    @Test
    void respectsMaxDistance() {
        solid.add(BlockVector3.at(1500, 64, 0));
        assertNull(new LongRangeTrace(world, mask).trace(Vector3.at(0.5, 64.5, 0.5), Vector3.at(1, 0, 0), 1000));
    }

    @Test
    void neverSkipsBlocksOnADiagonal() {
        // a one-block-thick wall along x = z diagonal would be skipped by coarse sampling at corners
        new LongRangeTrace(world, mask).trace(Vector3.at(0.5, 64.5, 0.5), Vector3.at(1, 0.3, 1), 50);
        // every visited block is face-adjacent to another visited block, i.e. the walk is gap-free
        for (BlockVector3 block : tested) {
            if (block.equals(BlockVector3.at(0, 64, 0))) {
                continue;
            }
            boolean connected = false;
            for (BlockVector3 other : tested) {
                if (other.subtract(block).abs().x() + other.subtract(block).abs().y() + other.subtract(block).abs().z() == 1) {
                    connected = true;
                    break;
                }
            }
            assertTrue(connected, block.toString());
        }
        assertTrue(tested.size() > 70);
    }

    @Test
    void usesGeneratorHeightForUngeneratedChunks() {
        when(world.isChunkGenerated(anyInt(), anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) < 10);
        when(world.estimateSurfaceHeight(anyInt(), anyInt())).thenReturn(OptionalInt.of(70));
        // looking slightly down from y=100: passes over generated (empty) chunks, then meets estimated terrain
        LongRangeTrace.Hit hit = new LongRangeTrace(world, mask)
            .trace(Vector3.at(0.5, 100.5, 0.5), Vector3.at(1, -0.05, 0), 2048);
        assertTrue(hit.estimated());
        assertEquals(69, hit.position().y());
        // y reaches 70 at x = 610
        assertTrue(Math.abs(hit.position().x() - 610) <= 1, hit.toString());
        // never asks the world for blocks in ungenerated chunks
        assertTrue(tested.stream().allMatch(b -> (b.x() >> 4) < 10));
    }

    @Test
    void ungeneratedChunksWithoutAnEstimateAreSeenThrough() {
        when(world.isChunkGenerated(anyInt(), anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) != 5);
        solid.add(BlockVector3.at(85, 64, 0));
        solid.add(BlockVector3.at(200, 64, 0));
        LongRangeTrace.Hit hit = new LongRangeTrace(world, mask)
            .trace(Vector3.at(0.5, 64.5, 0.5), Vector3.at(1, 0, 0), 2048);
        assertEquals(200, hit.position().x());
    }

    @Test
    void stopsWhenLeavingTheWorld() {
        assertNull(new LongRangeTrace(world, mask).trace(Vector3.at(0.5, 64.5, 0.5), Vector3.at(0, 1, 0), 2048));
        assertTrue(tested.size() <= 260);
    }
}
