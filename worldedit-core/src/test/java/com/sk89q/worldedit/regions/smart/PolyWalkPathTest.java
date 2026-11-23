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

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.session.job.TickListeners;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolyWalkPathTest {

    /** Walk along a straight line from a to b in small steps, as a player would. */
    private static void walk(PolyWalkPath path, double x0, double z0, double x1, double z1, double y) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / 0.2);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            path.offer(Vector3.at(x0 + (x1 - x0) * t, y, z0 + (z1 - z0) * t));
        }
    }

    @Test
    void spacing() {
        PolyWalkPath path = new PolyWalkPath(4);
        assertTrue(path.offer(Vector3.at(0.5, 64, 0.5)));
        assertFalse(path.offer(Vector3.at(3.0, 64, 0.5)));
        assertFalse(path.offer(Vector3.at(0.5, 70, 0.5)), "vertical movement does not count");
        assertTrue(path.offer(Vector3.at(4.6, 64, 0.5)));
        assertEquals(2, path.size());
        walk(path, 4.6, 0.5, 40.6, 0.5, 64);
        assertEquals(11, path.size());
        assertEquals(64, path.getLowestY());
        assertEquals(70, path.getHighestY());
    }

    @Test
    void squareWalkSimplifiesToCorners() {
        PolyWalkPath path = new PolyWalkPath(4);
        path.offer(Vector3.at(0.5, 64, 0.5));
        walk(path, 0.5, 0.5, 30.5, 0.5, 64);
        walk(path, 30.5, 0.5, 30.5, 20.5, 64);
        walk(path, 30.5, 20.5, 0.5, 20.5, 64);
        walk(path, 0.5, 20.5, 0.5, 0.5, 64);
        assertTrue(path.size() > 20);
        List<BlockVector2> outline = path.outline(0);
        // the straight stretches collapse; corners are kept (the corner points may be off by a block,
        // since automatic points are not placed exactly on corners)
        assertTrue(outline.size() >= 4 && outline.size() <= 6, "got " + outline);
        assertFalse(outline.getFirst().equals(outline.getLast()), "the loop is closed without repeating a point");
    }

    @Test
    void manualCornersAreKept() {
        PolyWalkPath path = new PolyWalkPath(4);
        path.addManualPoint(Vector3.at(0, 64, 0));
        walk(path, 0, 0, 20, 0, 64);
        path.addManualPoint(Vector3.at(20, 64, 0));
        walk(path, 20, 0, 20, 20, 64);
        path.addManualPoint(Vector3.at(20, 64, 20));
        walk(path, 20, 20, 0, 20, 64);
        path.addManualPoint(Vector3.at(0, 64, 20));
        walk(path, 0, 20, 0, 0, 64);
        List<BlockVector2> outline = path.outline(0);
        assertEquals(List.of(BlockVector2.at(0, 0), BlockVector2.at(20, 0), BlockVector2.at(20, 20),
            BlockVector2.at(0, 20)), outline);
    }

    @Test
    void limitDropsLeastImportantPoints() {
        PolyWalkPath path = new PolyWalkPath(1);
        // a rough circle of radius 30
        for (int i = 0; i < 360; i += 2) {
            double angle = Math.toRadians(i);
            path.offer(Vector3.at(30 * Math.cos(angle), 64, 30 * Math.sin(angle)));
        }
        assertTrue(path.size() > 50);
        List<BlockVector2> outline = path.outline(12);
        assertEquals(12, outline.size());
        assertTrue(path.outline(0).size() > 12);
    }

    @Test
    void undo() {
        PolyWalkPath path = new PolyWalkPath(4);
        assertNull(path.undo());
        path.offer(Vector3.at(0, 64, 0));
        path.offer(Vector3.at(5, 64, 0));
        assertEquals(BlockVector2.at(5, 0), path.undo().position());
        assertEquals(1, path.size());
        // spacing is measured from the remaining last point again
        assertFalse(path.offer(Vector3.at(3, 64, 0)));
        assertTrue(path.offer(Vector3.at(4, 64, 0)));
    }

    @Test
    void repeatedManualPointIsNotAddedTwice() {
        PolyWalkPath path = new PolyWalkPath(4);
        assertTrue(path.addManualPoint(Vector3.at(1.2, 64, 1.7)));
        assertFalse(path.addManualPoint(Vector3.at(1.9, 64, 1.1)));
        assertEquals(1, path.size());
    }

    @Test
    void highestBlockInside() {
        List<BlockVector2> outline = List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(10, 10),
            BlockVector2.at(0, 10));
        // a tower at 5,5 up to y 80; something outside the outline up to y 100
        ConnectedBlockSearch.BlockTest world = (x, y, z) -> y <= 63
            || (x == 5 && z == 5 && y <= 80) || (x == 20 && z == 20 && y <= 100);
        assertEquals(80, PolyWalkPath.highestBlockInside(outline, 64, 319, world, Long.MAX_VALUE));
        assertEquals(63, PolyWalkPath.highestBlockInside(outline, 64, 319, (_, y, _) -> y <= 63, Long.MAX_VALUE));
        // with a tiny budget columns are sampled, but the result stays in range
        int sampled = PolyWalkPath.highestBlockInside(outline, 64, 319, world, 1000);
        assertTrue(sampled == 63 || sampled == 80);
    }

    @Test
    void tickListenersDoNothingWhenEmpty() {
        TickListeners listeners = new TickListeners();
        assertTrue(listeners.isEmpty());
        listeners.tick();
        int[] calls = {0};
        Runnable task = () -> calls[0]++;
        listeners.add(task);
        listeners.add(task);
        listeners.tick();
        assertEquals(1, calls[0]);
        listeners.remove(task);
        listeners.tick();
        assertEquals(1, calls[0]);
        Runnable failing = () -> {
            throw new IllegalStateException("boom");
        };
        listeners.add(failing);
        listeners.tick();
        assertFalse(listeners.contains(failing));
    }
}
