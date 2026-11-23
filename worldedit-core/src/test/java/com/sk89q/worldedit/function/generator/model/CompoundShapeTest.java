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

package com.sk89q.worldedit.function.generator.model;

import com.sk89q.worldedit.function.generator.model.CompoundShape.Operation;
import com.sk89q.worldedit.function.generator.model.CompoundShape.Step;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompoundShapeTest {

    /**
     * A solid box from -rx..rx, -ry..ry, -rz..rz.
     */
    private static VoxelGrid box(int rx, int ry, int rz) {
        VoxelGrid grid = new VoxelGrid(-rx, -ry, -rz, rx, ry, rz);
        for (int x = -rx; x <= rx; x++) {
            for (int y = -ry; y <= ry; y++) {
                for (int z = -rz; z <= rz; z++) {
                    grid.set(x, y, z);
                }
            }
        }
        return grid;
    }

    private static Step step(Operation operation, VoxelGrid grid, int x, int y, int z) {
        return new Step(operation, grid, BlockVector3.at(x, y, z), operation.label());
    }

    @Test
    void setLogic() {
        VoxelGrid cube = box(2, 2, 2);
        Step first = step(Operation.ADD, cube, 0, 0, 0);
        // a second cube shifted 2 along x: overlap is 3 x 5 x 5
        assertEquals(125 + 125 - 75, CompoundShape.evaluate(List.of(first, step(Operation.ADD, cube, 2, 0, 0)), 0).count());
        assertEquals(125 - 75, CompoundShape.evaluate(List.of(first, step(Operation.CUT, cube, 2, 0, 0)), 0).count());
        VoxelGrid keep = CompoundShape.evaluate(List.of(first, step(Operation.KEEP, cube, 2, 0, 0)), 0);
        assertEquals(75, keep.count());
        assertTrue(keep.get(0, 0, 0));
        assertFalse(keep.get(-1, 0, 0));
        // steps apply in order: cutting a hole and then adding a plug fills it again
        VoxelGrid plugged = CompoundShape.evaluate(List.of(first, step(Operation.CUT, box(0, 0, 0), 0, 0, 0),
            step(Operation.ADD, box(0, 0, 0), 0, 0, 0)), 0);
        assertEquals(125, plugged.count());
    }

    @Test
    void zeroSmoothnessIsTheSameAsSharp() {
        List<Step> steps = List.of(step(Operation.ADD, box(3, 1, 1), 0, 0, 0), step(Operation.ADD, box(1, 3, 1), 3, 2, 0),
            step(Operation.CUT, box(1, 1, 3), 0, 0, 0));
        VoxelGrid sharp = CompoundShape.evaluate(steps, 0);
        VoxelGrid tiny = CompoundShape.evaluate(steps, 1e-6);
        assertEquals(sharp.count(), tiny.count());
        sharp.forEach(block -> assertTrue(tiny.get(block.x(), block.y(), block.z())));
    }

    @Test
    void smoothUnionAddsFilletsAtTheJoin() {
        // an L: a bar along x and a post going up from its end
        List<Step> steps = List.of(step(Operation.ADD, box(6, 1, 1), 0, 0, 0), step(Operation.ADD, box(1, 6, 1), -5, 7, 0));
        VoxelGrid sharp = CompoundShape.evaluate(steps, 0);
        VoxelGrid smooth = CompoundShape.evaluate(steps, 4);
        assertTrue(smooth.count() > sharp.count(), smooth.count() + " vs " + sharp.count());
        // everything that was there is still there
        sharp.forEach(block -> assertTrue(smooth.get(block.x(), block.y(), block.z()), "lost " + block));
        // the inside corner of the L is filled in
        assertFalse(sharp.get(-3, 2, 0));
        assertTrue(smooth.get(-3, 2, 0));
        // far from the join nothing changes
        assertFalse(smooth.get(5, 3, 0));
        assertFalse(smooth.get(-3, 12, 0));
    }

    @Test
    void distanceFieldIsSigned() {
        boolean[] inside = new boolean[5 * 5 * 5];
        inside[(2 * 5 + 2) * 5 + 2] = true;
        float[] field = DistanceField.signed(inside, 5, 5, 5);
        assertEquals(-0.5, field[(2 * 5 + 2) * 5 + 2], 1e-6);
        assertEquals(0.5, field[(2 * 5 + 2) * 5 + 3], 1e-6);
        assertEquals(Math.sqrt(8) - 0.5, field[(0 * 5 + 0) * 5 + 2], 1e-5);
        assertEquals(1, CompoundShape.smoothMin(1, 2, 0));
        assertTrue(CompoundShape.smoothMin(1, 1, 2) < 1);
    }

    @Test
    void farOffsetsAreRefusedWhenAdded() {
        CompoundShape shape = CompoundShape.of(UUID.randomUUID());
        shape.add(step(Operation.ADD, box(5, 5, 5), 0, 0, 0), BlockVector3.ZERO);
        IllegalArgumentException far = assertThrows(IllegalArgumentException.class,
            () -> shape.add(step(Operation.ADD, box(5, 5, 5), 100000, 0, 0), BlockVector3.ZERO));
        assertTrue(far.getMessage().contains("too far"), far.getMessage());
        assertEquals(1, shape.getSteps().size());
        // within the offset limit, but the box around both would be far too big
        IllegalArgumentException big = assertThrows(IllegalArgumentException.class,
            () -> shape.add(step(Operation.ADD, box(5, 5, 5), 500, 500, 500), BlockVector3.ZERO));
        assertTrue(big.getMessage().contains("too big"), big.getMessage());
        assertEquals(1, shape.getSteps().size());
        shape.add(step(Operation.ADD, box(5, 5, 5), 512, 0, 0), BlockVector3.ZERO);
        assertEquals(2, shape.getSteps().size());
    }

    @Test
    void tooBigToBlendSaysSo() {
        List<Step> steps = List.of(step(Operation.ADD, box(5, 5, 5), 0, 0, 0),
            step(Operation.ADD, box(5, 5, 5), 500, 500, 100));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CompoundShape.evaluate(steps, 4));
        assertTrue(e.getMessage().startsWith("The combined shape is too big to blend ("), e.getMessage());
    }

    @Test
    void historyPerPlayer() {
        UUID player = UUID.randomUUID();
        CompoundShape shape = CompoundShape.of(player);
        assertSame(shape, CompoundShape.of(player));
        assertThrows(IllegalArgumentException.class, shape::evaluate);
        assertThrows(IllegalArgumentException.class,
            () -> shape.add(step(Operation.CUT, box(1, 1, 1), 0, 0, 0), BlockVector3.ZERO));
        shape.add(step(Operation.ADD, box(1, 1, 1), 0, 0, 0), BlockVector3.at(5, 6, 7));
        shape.add(step(Operation.CUT, box(0, 0, 0), 0, 0, 0), BlockVector3.at(50, 60, 70));
        assertEquals(BlockVector3.at(5, 6, 7), shape.getAnchor());
        assertEquals(26, shape.evaluate().count());
        assertEquals(Operation.CUT, shape.undo().operation());
        assertEquals(27, shape.evaluate().count());
        shape.undo();
        assertNull(shape.getAnchor());
        assertNull(shape.undo());
        shape.setSmoothness(2);
        shape.clear();
        assertEquals(0, shape.getSmoothness());
    }
}
