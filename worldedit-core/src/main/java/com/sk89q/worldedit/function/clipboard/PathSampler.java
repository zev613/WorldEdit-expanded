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
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Points spaced evenly along a smooth path through some nodes, using the same spline as
 * {@code //curve}.
 */
public final class PathSampler {

    /**
     * A point on the path.
     *
     * @param position the position
     * @param heading the horizontal direction of travel, in degrees clockwise from north (-z)
     * @param distance the path length from the start
     */
    public record PathPoint(Vector3 position, double heading, double distance) {
    }

    private PathSampler() {
    }

    /**
     * Get the heading of a direction, in degrees clockwise from north (-z).
     *
     * @param dx the x part
     * @param dz the z part
     * @return the heading, from -180 to 180
     */
    public static double heading(double dx, double dz) {
        return Math.toDegrees(Math.atan2(dx, -dz));
    }

    /**
     * Get points every {@code spacing} blocks of path length, starting at the first node.
     *
     * @param nodes the nodes, in order
     * @param spacing the distance between points, above 0
     * @param maxPoints the most points allowed
     * @return the points
     */
    public static List<PathPoint> sample(List<Vector3> nodes, double spacing, int maxPoints) {
        if (!(spacing > 0) || Double.isInfinite(spacing)) {
            throw new IllegalArgumentException("The spacing must be above 0.");
        }
        List<Vector3> unique = new ArrayList<>();
        for (Vector3 node : nodes) {
            if (unique.isEmpty() || unique.getLast().distanceSq(node) > 1e-9) {
                unique.add(node);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("The path needs at least one point.");
        }
        List<PathPoint> result = new ArrayList<>();
        if (unique.size() == 1) {
            result.add(new PathPoint(unique.getFirst(), 0, 0));
            return result;
        }

        List<Node> splineNodes = new ArrayList<>();
        double rough = 0;
        for (int i = 0; i < unique.size(); i++) {
            splineNodes.add(new Node(unique.get(i)));
            if (i > 0) {
                rough += unique.get(i).distance(unique.get(i - 1));
            }
        }
        if (rough / spacing > maxPoints * 4.0) {
            throw new IllegalArgumentException("That would make more than " + maxPoints
                + " copies; use a larger spacing.");
        }
        Interpolation spline = new KochanekBartelsInterpolation();
        spline.setNodes(splineNodes);

        // walk the spline in small steps, measuring the length as we go
        int steps = (int) Math.min(4_000_000, Math.max(256, Math.ceil(rough * 16)));
        Vector3 previous = spline.getPosition(0);
        double travelled = 0;
        double next = 0;
        double lastHeading = Double.NaN;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vector3 position = spline.getPosition(t);
            double segment = i == 0 ? 0 : position.distance(previous);
            double before = travelled;
            travelled += segment;
            while (next <= travelled + 1e-9) {
                double share = segment > 0 ? (next - before) / segment : 0;
                Vector3 at = i == 0 ? position : previous.add(position.subtract(previous).multiply(share));
                Vector3 direction = spline.get1stDerivative(t);
                double heading;
                if (Math.hypot(direction.x(), direction.z()) > 1e-6) {
                    heading = heading(direction.x(), direction.z());
                } else {
                    heading = Double.isNaN(lastHeading) ? 0 : lastHeading;
                }
                lastHeading = heading;
                result.add(new PathPoint(at, heading, next));
                if (result.size() > maxPoints) {
                    throw new IllegalArgumentException("That would make more than " + maxPoints
                        + " copies; use a larger spacing.");
                }
                next += spacing;
            }
            previous = position;
        }
        return result;
    }
}
