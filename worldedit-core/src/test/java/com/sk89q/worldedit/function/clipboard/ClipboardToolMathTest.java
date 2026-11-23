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

import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardToolMathTest {

    private static Map<List<Integer>, Integer> run(ScaleResampler resampler, ScaleResampler.Source source,
                                                   double fraction) {
        Map<List<Integer>, Integer> out = new HashMap<>();
        resampler.resample(source, fraction, (dx, dy, dz, sx, sy, sz, _) ->
            out.put(List.of(dx, dy, dz), source.key(sx, sy, sz)));
        return out;
    }

    @Test
    void scaleUpByTwoAndAHalfHasNoHoles() {
        ScaleResampler resampler = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 9, 9, 9 },
            new double[] { 2.5, 2.5, 2.5 });
        assertArrayEquals(new int[] { 0, 0, 0 }, resampler.getDestinationMin());
        assertArrayEquals(new int[] { 24, 24, 24 }, resampler.getDestinationMax());
        Map<List<Integer>, Integer> out = run(resampler, (_, _, _) -> 1, 0.25);
        assertEquals(25 * 25 * 25, out.size());
    }

    @Test
    void scaleUpKeepsLayoutAroundTheOrigin() {
        // origin in the middle: cells -2..1 become -5..4 (rounded) at 2.5
        ScaleResampler resampler = new ScaleResampler(new int[] { -2, 0, -2 }, new int[] { 1, 0, 1 },
            new double[] { 2.5, 1, 2.5 });
        assertArrayEquals(new int[] { -5, 0, -5 }, resampler.getDestinationMin());
        assertArrayEquals(new int[] { 4, 0, 4 }, resampler.getDestinationMax());
        // a marker at x >= 0 stays at x >= 0
        Map<List<Integer>, Integer> out = run(resampler, (x, _, _) -> x >= 0 ? 2 : 1, 0.25);
        assertEquals(100, out.size());
        out.forEach((pos, key) -> assertEquals(pos.get(0) >= 0 ? 2 : 1, key));
    }

    @Test
    void scaleOnlyTaller() {
        ScaleResampler resampler = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 2, 3, 2 },
            new double[] { 1, 2, 1 });
        assertArrayEquals(new int[] { 2, 7, 2 }, resampler.getDestinationMax());
        Map<List<Integer>, Integer> out = run(resampler, (_, y, _) -> y + 1, 0.25);
        assertEquals(3 * 8 * 3, out.size());
        assertEquals(1, out.get(List.of(0, 1, 0)));
        assertEquals(4, out.get(List.of(0, 7, 0)));
    }

    @Test
    void scaleHalfKeepsOneBlockWall() {
        ScaleResampler resampler = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 7, 7, 7 },
            new double[] { 0.5, 0.5, 0.5 });
        assertArrayEquals(new int[] { 3, 3, 3 }, resampler.getDestinationMax());
        Map<List<Integer>, Integer> out = run(resampler, (x, _, _) -> x == 3 ? 5 : 0, 0.25);
        assertEquals(16, out.size());
        for (List<Integer> pos : out.keySet()) {
            assertEquals(1, pos.get(0));
        }
        // a 1x1 post fills a quarter of each cell and survives at the default
        Map<List<Integer>, Integer> post = run(resampler, (x, _, z) -> x == 3 && z == 3 ? 5 : 0, 0.25);
        assertEquals(4, post.size());
        // but not when asking for half
        assertTrue(run(resampler, (x, _, z) -> x == 3 && z == 3 ? 5 : 0, 0.5).isEmpty());
    }

    @Test
    void scaleDownPicksMostCommonSolidBlock() {
        ScaleResampler resampler = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 1, 1, 1 },
            new double[] { 0.5, 0.5, 0.5 });
        // 3 stone (1), 2 dirt (2), rest air
        Map<List<Integer>, Integer> out = run(resampler, (x, y, z) -> {
            int index = x + 2 * y + 4 * z;
            return index < 3 ? 1 : index < 5 ? 2 : 0;
        }, 0.25);
        assertEquals(Map.of(List.of(0, 0, 0), 1), out);
    }

    @Test
    void scaleUpMarksOnePrimaryCopyPerBlock() {
        ScaleResampler resampler = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 0, 0, 0 },
            new double[] { 3, 3, 3 });
        int[] primaries = new int[1];
        resampler.resample((_, _, _) -> 1, 0.25, (_, _, _, _, _, _, primary) -> {
            if (primary) {
                primaries[0]++;
            }
        });
        assertEquals(1, primaries[0]);
    }

    @Test
    void scaleLimitAndParsing() {
        ScaleResampler big = new ScaleResampler(new int[] { 0, 0, 0 }, new int[] { 99, 99, 99 },
            new double[] { 10, 10, 10 });
        assertThrows(IllegalArgumentException.class, () -> big.checkVolume(ScaleResampler.MAX_VOLUME));
        assertArrayEquals(new double[] { 2.5, 2.5, 2.5 }, ScaleResampler.parseFactor("2.5"));
        assertArrayEquals(new double[] { 1, 2, 1 }, ScaleResampler.parseFactor("1,2,1"));
        assertThrows(IllegalArgumentException.class, () -> ScaleResampler.parseFactor("0"));
        assertThrows(IllegalArgumentException.class, () -> ScaleResampler.parseFactor("big"));
        assertThrows(IllegalArgumentException.class, () -> ScaleResampler.parseFactor("1,2"));
    }

    private static void assertNear(Vector3 expected, Vector3 actual) {
        assertTrue(expected.distance(actual) < 1e-6, () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void radialTurnsClockwiseAroundThePlayer() {
        RadialLayout layout = new RadialLayout(4, Vector3.at(0, 0, -5), null, 0);
        assertEquals(90, layout.angle(1), 1e-9);
        // north -> east -> south -> west
        assertNear(Vector3.at(0, 0, -5), layout.transform(0).apply(Vector3.at(0, 0, -5)));
        assertNear(Vector3.at(5, 0, 0), layout.transform(1).apply(Vector3.at(0, 0, -5)));
        assertNear(Vector3.at(0, 0, 5), layout.transform(2).apply(Vector3.at(0, 0, -5)));
        assertNear(Vector3.at(-5, 0, 0), layout.transform(3).apply(Vector3.at(0, 0, -5)));
    }

    @Test
    void radialRadiusMovesCentresOntoACircle() {
        Vector3 center = Vector3.at(3, 2, 4);
        RadialLayout layout = new RadialLayout(6, center, 10.0, 0);
        Set<Long> angles = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Vector3 moved = layout.transform(i).apply(center);
            assertEquals(10, Math.hypot(moved.x(), moved.z()), 1e-6);
            assertEquals(2, moved.y(), 1e-9);
            angles.add(Math.round(Math.toDegrees(Math.atan2(moved.x(), -moved.z())) + 360) % 360);
        }
        assertEquals(6, angles.size());
        // copy 0 keeps the clipboard's own direction from the player
        Vector3 first = layout.transform(0).apply(center);
        assertNear(Vector3.at(6, 2, 8), first);
    }

    @Test
    void radialRadiusShiftIsWholeBlocks() {
        RadialLayout layout = new RadialLayout(4, Vector3.at(0, 0, -3.5), 15.0, 0);
        assertNear(Vector3.at(0, 0, -12), layout.getShift());
        for (int i = 0; i < 4; i++) {
            Vector3 moved = layout.transform(i).apply(Vector3.at(0, 0, -3.5));
            assertEquals(15.5, Math.hypot(moved.x(), moved.z()), 1e-6);
        }
    }

    @Test
    void radialCentredClipboardUsesFacing() {
        // yaw 0 is south (+z)
        RadialLayout layout = new RadialLayout(2, Vector3.ZERO, 7.0, 0);
        assertNear(Vector3.at(0, 0, 7), layout.transform(0).apply(Vector3.ZERO));
        assertNear(Vector3.at(0, 0, -7), layout.transform(1).apply(Vector3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(0, Vector3.ZERO, null, 0));
    }

    @Test
    void clockwiseRotationOfDirections() {
        AffineTransform turn = RadialLayout.clockwise(90);
        assertNear(Vector3.at(1, 0, 0), turn.apply(Vector3.at(0, 0, -1)));
        assertEquals(90, PathSampler.heading(1, 0), 1e-9);
        assertEquals(0, PathSampler.heading(0, -1), 1e-9);
        assertEquals(180, Math.abs(PathSampler.heading(0, 1)), 1e-9);
    }

    @Test
    void pathStraightLineSpacing() {
        List<PathSampler.PathPoint> points = PathSampler.sample(
            List.of(Vector3.at(0, 5, 0), Vector3.at(20, 5, 0)), 5, 100);
        assertEquals(5, points.size());
        for (int i = 0; i < 5; i++) {
            assertNear(Vector3.at(5 * i, 5, 0), points.get(i).position());
            assertEquals(90, points.get(i).heading(), 1e-6);
        }
    }

    @Test
    void pathCurveSpacingIsEven() {
        List<Vector3> nodes = List.of(Vector3.at(0, 0, 0), Vector3.at(20, 0, 10), Vector3.at(0, 0, 30),
            Vector3.at(25, 0, 40));
        List<PathSampler.PathPoint> points = PathSampler.sample(nodes, 3, 1000);
        assertTrue(points.size() > 20);
        for (int i = 1; i < points.size(); i++) {
            assertEquals(3, points.get(i).distance() - points.get(i - 1).distance(), 1e-9);
            double chord = points.get(i).position().distance(points.get(i - 1).position());
            // straight-line gap is at most the path gap and close to it on a gentle curve
            assertTrue(chord <= 3 + 1e-6 && chord > 2.6, "chord " + chord);
        }
        assertNear(nodes.getFirst(), points.getFirst().position());
        // the path turns, so the heading changes
        double minHeading = points.stream().mapToDouble(PathSampler.PathPoint::heading).min().orElseThrow();
        double maxHeading = points.stream().mapToDouble(PathSampler.PathPoint::heading).max().orElseThrow();
        assertTrue(maxHeading - minHeading > 90);
        assertThrows(IllegalArgumentException.class, () -> PathSampler.sample(nodes, 0.001, 1000));
        assertFalse(PathSampler.sample(List.of(Vector3.ZERO), 2, 10).isEmpty());
    }
}
