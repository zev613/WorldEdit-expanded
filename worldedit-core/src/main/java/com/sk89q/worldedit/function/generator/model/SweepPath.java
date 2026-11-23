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

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * A smooth path through some points, sampled evenly, with an upright frame at every sample.
 *
 * <p>Each frame has a forward direction along the path, a right direction that is always level, and an up
 * direction at right angles to both, so a cross-section carried along the path stays upright.</p>
 */
public final class SweepPath {

    /**
     * The distance between samples, in blocks.
     */
    public static final double SPACING = 0.25;

    /**
     * The longest path allowed, in blocks.
     */
    public static final double MAX_LENGTH = 4096;

    private final Vector3[] positions;
    private final Vector3[] forwards;
    private final Vector3[] rights;
    private final Vector3[] ups;
    private final double length;

    private SweepPath(List<Vector3> points) {
        int count = points.size();
        positions = points.toArray(new Vector3[0]);
        forwards = new Vector3[count];
        rights = new Vector3[count];
        ups = new Vector3[count];
        length = (count - 1) * SPACING;
        for (int i = 0; i < count; i++) {
            Vector3 ahead = positions[Math.min(count - 1, i + 1)];
            Vector3 behind = positions[Math.max(0, i - 1)];
            Vector3 forward = ahead.subtract(behind);
            forwards[i] = forward.lengthSq() < 1e-12 ? (i > 0 ? forwards[i - 1] : Vector3.UNIT_X) : forward.normalize();
        }
        for (int i = 0; i < count; i++) {
            Vector3 forward = forwards[i];
            Vector3 right = forward.cross(Vector3.UNIT_Y);
            if (right.length() < 0.1) {
                // going (nearly) straight up or down: keep the previous right-hand side so the profile does not spin
                Vector3 previous = i > 0 ? rights[i - 1] : Vector3.UNIT_Z;
                right = previous.subtract(forward.multiply(previous.dot(forward)));
                if (right.lengthSq() < 1e-9) {
                    right = Vector3.UNIT_X.subtract(forward.multiply(forward.x()));
                }
            }
            right = right.normalize();
            rights[i] = right;
            ups[i] = right.cross(forward).normalize();
        }
    }

    /**
     * Make a smooth path through block positions (through the middle of each block).
     *
     * @param waypoints the points, in order; repeated points next to each other are ignored
     * @return the path
     * @throws IllegalArgumentException if there are fewer than two different points or the path is too long
     */
    public static SweepPath through(List<BlockVector3> waypoints) {
        List<Vector3> nodes = new ArrayList<>();
        for (BlockVector3 waypoint : waypoints) {
            Vector3 centre = waypoint.toVector3().add(0.5, 0.5, 0.5);
            if (nodes.isEmpty() || !nodes.getLast().equals(centre)) {
                nodes.add(centre);
            }
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("The path needs at least two different points: select pos1 and pos2, "
                + "or use //sel poly or //sel convex and click the points in order.");
        }
        double rough = 0;
        for (int i = 1; i < nodes.size(); i++) {
            rough += nodes.get(i).distance(nodes.get(i - 1));
        }
        if (rough > MAX_LENGTH) {
            throw new IllegalArgumentException("That path is too long (" + Math.round(rough) + " blocks; at most "
                + (int) MAX_LENGTH + ").");
        }
        List<Vector3> dense = new ArrayList<>();
        if (nodes.size() == 2) {
            dense.addAll(nodes);
        } else {
            Interpolation spline = new KochanekBartelsInterpolation();
            List<Node> splineNodes = new ArrayList<>();
            for (Vector3 node : nodes) {
                splineNodes.add(new Node(node));
            }
            spline.setNodes(splineNodes);
            int steps = (int) Math.max(64, Math.ceil(rough * 16));
            for (int i = 0; i <= steps; i++) {
                dense.add(spline.getPosition((double) i / steps));
            }
        }
        return new SweepPath(resample(dense));
    }

    private static List<Vector3> resample(List<Vector3> dense) {
        List<Vector3> result = new ArrayList<>();
        result.add(dense.getFirst());
        double carried = 0;
        for (int i = 1; i < dense.size(); i++) {
            Vector3 from = dense.get(i - 1);
            Vector3 to = dense.get(i);
            double segment = from.distance(to);
            if (segment < 1e-12) {
                continue;
            }
            double position = SPACING - carried;
            while (position <= segment + 1e-12) {
                result.add(from.add(to.subtract(from).multiply(position / segment)));
                position += SPACING;
            }
            carried = segment - (position - SPACING);
        }
        if (result.getLast().distance(dense.getLast()) > 1e-6) {
            result.add(dense.getLast());
        }
        return result;
    }

    /**
     * The number of samples.
     *
     * @return the count
     */
    public int size() {
        return positions.length;
    }

    public Vector3 position(int index) {
        return positions[index];
    }

    public Vector3 forward(int index) {
        return forwards[index];
    }

    public Vector3 right(int index) {
        return rights[index];
    }

    public Vector3 up(int index) {
        return ups[index];
    }

    /**
     * The distance along the path to a sample.
     *
     * @param index the sample
     * @return the distance in blocks
     */
    public double along(int index) {
        return index * SPACING;
    }

    /**
     * The length of the path.
     *
     * @return the length in blocks
     */
    public double length() {
        return length;
    }
}
