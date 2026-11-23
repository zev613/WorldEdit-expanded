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
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Polygonal2DRegion;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The points of a {@code //polywalk}: a polygon recorded by walking around a build.
 *
 * <p>While recording, {@link #offer(Vector3)} is given the walker's position every tick and adds a point
 * whenever they have moved at least {@code spacing} blocks horizontally from the last point.
 * Points can also be added by hand ({@link #addManualPoint(Vector3)}), for corners; those are never
 * dropped when the outline is simplified.</p>
 */
public final class PolyWalkPath {

    /**
     * A recorded point.
     *
     * @param position the column
     * @param manual true if it was added by hand
     */
    public record Point(BlockVector2 position, boolean manual) {
    }

    /** How far (in blocks) a point may be off the straight line between its neighbours to be dropped. */
    public static final double STRAIGHT_TOLERANCE = 1.0;

    private final double spacing;
    private final List<Point> points = new ArrayList<>();
    private final List<Vector3> exactPositions = new ArrayList<>();
    private int lowestY = Integer.MAX_VALUE;
    private int highestY = Integer.MIN_VALUE;

    /**
     * Start a path.
     *
     * @param spacing the horizontal distance between automatic points, in blocks
     */
    public PolyWalkPath(double spacing) {
        checkArgument(spacing >= 1, "spacing must be at least 1");
        this.spacing = spacing;
    }

    public double getSpacing() {
        return spacing;
    }

    /**
     * Record the walker's position; adds a point if they moved far enough from the last one.
     *
     * @param position the walker's position (their feet)
     * @return true if a point was added
     */
    public boolean offer(Vector3 position) {
        checkNotNull(position);
        trackHeight(position);
        if (!exactPositions.isEmpty()) {
            Vector3 last = exactPositions.getLast();
            double dx = position.x() - last.x();
            double dz = position.z() - last.z();
            if (dx * dx + dz * dz < spacing * spacing) {
                return false;
            }
        }
        add(position, false);
        return true;
    }

    /**
     * Add a point at a position, whatever the distance from the last one.
     *
     * @param position the position
     * @return false if the last point is already on that column
     */
    public boolean addManualPoint(Vector3 position) {
        checkNotNull(position);
        trackHeight(position);
        BlockVector2 column = column(position);
        if (!points.isEmpty() && points.getLast().position().equals(column)) {
            // promote it to a manual point so it is kept
            points.set(points.size() - 1, new Point(column, true));
            return false;
        }
        add(position, true);
        return true;
    }

    private void add(Vector3 position, boolean manual) {
        points.add(new Point(column(position), manual));
        exactPositions.add(position);
    }

    private void trackHeight(Vector3 position) {
        int y = (int) Math.floor(position.y());
        lowestY = Math.min(lowestY, y);
        highestY = Math.max(highestY, y);
    }

    private static BlockVector2 column(Vector3 position) {
        return BlockVector2.at(Math.floor(position.x()), Math.floor(position.z()));
    }

    /**
     * Remove the last point.
     *
     * @return the removed point, or null if there were none
     */
    public Point undo() {
        if (points.isEmpty()) {
            return null;
        }
        exactPositions.removeLast();
        return points.removeLast();
    }

    /**
     * The recorded points, oldest first.
     *
     * @return the points
     */
    public List<Point> getPoints() {
        return List.copyOf(points);
    }

    /**
     * The recorded columns, oldest first (for showing the selection while walking).
     *
     * @return the columns
     */
    public List<BlockVector2> getColumns() {
        return points.stream().map(Point::position).toList();
    }

    public int size() {
        return points.size();
    }

    /**
     * The lowest y the walker's feet were at.
     *
     * @return the y, or {@link Integer#MAX_VALUE} if nothing was recorded
     */
    public int getLowestY() {
        return lowestY;
    }

    /**
     * The highest y the walker's feet were at.
     *
     * @return the y, or {@link Integer#MIN_VALUE} if nothing was recorded
     */
    public int getHighestY() {
        return highestY;
    }

    /**
     * The finished outline: repeated points removed, the loop closed (a last point on the first column is
     * dropped), points that are nearly on a straight line dropped, and then the least important points
     * dropped until at most {@code limit} remain. Manual points are dropped last.
     *
     * @param limit the most points, or 0 or less for no limit
     * @return the outline
     */
    public List<BlockVector2> outline(int limit) {
        List<Point> result = new ArrayList<>();
        for (Point point : points) {
            if (!result.isEmpty() && result.getLast().position().equals(point.position())) {
                if (point.manual()) {
                    result.set(result.size() - 1, point);
                }
                continue;
            }
            result.add(point);
        }
        while (result.size() > 1 && result.getLast().position().equals(result.getFirst().position())) {
            Point last = result.removeLast();
            if (last.manual()) {
                result.set(0, new Point(last.position(), true));
            }
        }
        // drop nearly straight points, least bent first
        while (result.size() > 3) {
            int index = leastImportant(result, false);
            if (index < 0 || deviation(result, index) >= STRAIGHT_TOLERANCE) {
                break;
            }
            result.remove(index);
        }
        int effectiveLimit = limit <= 0 ? Integer.MAX_VALUE : Math.max(3, limit);
        while (result.size() > effectiveLimit) {
            int index = leastImportant(result, false);
            if (index < 0) {
                index = leastImportant(result, true);
            }
            result.remove(index);
        }
        return result.stream().map(Point::position).toList();
    }

    private static int leastImportant(List<Point> points, boolean includeManual) {
        int best = -1;
        double bestDeviation = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            if (!includeManual && points.get(i).manual()) {
                continue;
            }
            double deviation = deviation(points, i);
            if (deviation < bestDeviation) {
                bestDeviation = deviation;
                best = i;
            }
        }
        return best;
    }

    private static double deviation(List<Point> points, int index) {
        int n = points.size();
        BlockVector2 a = points.get((index + n - 1) % n).position();
        BlockVector2 p = points.get(index).position();
        BlockVector2 b = points.get((index + 1) % n).position();
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        double px = p.x() - a.x();
        double pz = p.z() - a.z();
        if (length < 1e-9) {
            return Math.sqrt(px * px + pz * pz);
        }
        return Math.abs(dx * pz - dz * px) / length;
    }

    /**
     * Find the highest block inside an outline, for the top of the selection.
     *
     * <p>Each column inside the outline is scanned from {@code toY} down to {@code fromY}. To keep this
     * quick on huge outlines, at most about {@code maxChecks} blocks are tested: beyond that, only every
     * n-th column (in both directions) is scanned.</p>
     *
     * @param outline the outline
     * @param fromY the lowest y to scan
     * @param toY the highest y to scan
     * @param solid which blocks count
     * @param maxChecks roughly how many blocks to test at most
     * @return the highest y with a block, or {@code fromY - 1} if there is none
     */
    public static int highestBlockInside(List<BlockVector2> outline, int fromY, int toY,
                                         ConnectedBlockSearch.BlockTest solid, long maxChecks) {
        checkArgument(!outline.isEmpty(), "empty outline");
        List<BlockVector2> polygon = FootprintOutline.atLeastThreePoints(outline);
        if (toY < fromY) {
            return fromY - 1;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockVector2 point : outline) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        long checks = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (toY - fromY + 1);
        int stride = checks <= maxChecks ? 1 : (int) Math.ceil(Math.sqrt((double) checks / Math.max(1, maxChecks)));
        int highest = fromY - 1;
        for (int x = minX; x <= maxX; x += stride) {
            for (int z = minZ; z <= maxZ; z += stride) {
                if (!Polygonal2DRegion.contains(polygon, 0, 0, BlockVector3.at(x, 0, z))) {
                    continue;
                }
                for (int y = toY; y > highest; y--) {
                    if (solid.test(x, y, z)) {
                        highest = y;
                        break;
                    }
                }
            }
        }
        return highest;
    }
}
