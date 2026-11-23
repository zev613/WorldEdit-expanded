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
import com.sk89q.worldedit.regions.Polygonal2DRegion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The 2D footprint (x/z columns) of a set of blocks, and a polygon outline that hugs it.
 *
 * <p>The outline follows the outer boundary of the footprint (Moore-neighbour contour tracing), so it is
 * concave where the footprint is (an L-shaped house gets an L-shaped outline). Polygon points are block
 * positions and, as in WorldEdit's polygon selection, a block is selected when it lies inside or on the
 * outline, so the traced outline contains every block of the footprint. Holes (courtyards) are filled.
 * The outline is then simplified to fit a point limit with {@link #simplify(List, int)}.</p>
 */
public final class FootprintOutline {

    // Moore neighbourhood, clockwise when z points "down": W, NW, N, NE, E, SE, S, SW
    private static final int[] DX = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] DZ = {0, -1, -1, -1, 0, 1, 1, 1};

    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final BitSet cells;

    private FootprintOutline(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeZ = maxZ - minZ + 1;
        checkArgument((long) sizeX * sizeZ <= Integer.MAX_VALUE, "footprint too large");
        this.cells = new BitSet(sizeX * sizeZ);
    }

    /**
     * The footprint of a set of blocks.
     *
     * @param blocks the blocks (not empty)
     * @return the footprint
     */
    public static FootprintOutline of(BlockPosSet blocks) {
        BlockVector3 min = blocks.getMinimum();
        BlockVector3 max = blocks.getMaximum();
        FootprintOutline footprint = new FootprintOutline(min.x(), min.z(), max.x(), max.z());
        blocks.forEach((x, _, z) -> footprint.set(x, z));
        return footprint;
    }

    /**
     * A footprint made of the given columns.
     *
     * @param columns the columns (not empty)
     * @return the footprint
     */
    public static FootprintOutline of(Collection<BlockVector2> columns) {
        checkArgument(!columns.isEmpty(), "no columns");
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockVector2 column : columns) {
            minX = Math.min(minX, column.x());
            minZ = Math.min(minZ, column.z());
            maxX = Math.max(maxX, column.x());
            maxZ = Math.max(maxZ, column.z());
        }
        FootprintOutline footprint = new FootprintOutline(minX, minZ, maxX, maxZ);
        for (BlockVector2 column : columns) {
            footprint.set(column.x(), column.z());
        }
        return footprint;
    }

    private void set(int x, int z) {
        cells.set((z - minZ) * sizeX + (x - minX));
    }

    /**
     * Whether a column is part of the footprint.
     *
     * @param x x
     * @param z z
     * @return true if it is
     */
    public boolean contains(int x, int z) {
        int dx = x - minX;
        int dz = z - minZ;
        return dx >= 0 && dz >= 0 && dx < sizeX && dz < sizeZ && cells.get(dz * sizeX + dx);
    }

    /**
     * The number of columns in the footprint.
     *
     * @return the count
     */
    public int count() {
        return cells.cardinality();
    }

    /**
     * Trace the outer boundary of the footprint. Only the part connected (through edges or corners) to the
     * first column in z/x order is traced, which for a connected build is the whole footprint.
     *
     * @return the boundary columns in order, without repeating the first one at the end
     */
    public List<BlockVector2> trace() {
        int first = cells.nextSetBit(0);
        checkArgument(first >= 0, "empty footprint");
        int startX = first % sizeX + minX;
        int startZ = first / sizeX + minZ;
        List<BlockVector2> contour = new ArrayList<>();
        contour.add(BlockVector2.at(startX, startZ));
        int x = startX;
        int z = startZ;
        // the cell to the west of the start is empty (the start is the first cell of its row)
        int backtrack = 0;
        int firstMove = -1;
        long safety = 8L * cells.cardinality() + 16;
        while (safety-- > 0) {
            int found = -1;
            for (int k = 1; k <= 8; k++) {
                int direction = (backtrack + k) % 8;
                if (contains(x + DX[direction], z + DZ[direction])) {
                    found = direction;
                    break;
                }
            }
            if (found < 0) {
                // a single column
                return contour;
            }
            if (x == startX && z == startZ) {
                if (firstMove == found) {
                    break;
                }
                if (firstMove < 0) {
                    firstMove = found;
                }
            }
            int previous = (found + 7) % 8;
            int nextX = x + DX[found];
            int nextZ = z + DZ[found];
            // the last empty neighbour checked, seen from the new cell
            int emptyX = x + DX[previous] - nextX;
            int emptyZ = z + DZ[previous] - nextZ;
            backtrack = directionOf(emptyX, emptyZ);
            x = nextX;
            z = nextZ;
            contour.add(BlockVector2.at(x, z));
        }
        // the loop ends after arriving back at the start
        if (contour.size() > 1 && contour.getLast().equals(contour.getFirst())) {
            contour.removeLast();
        }
        return removeStraightPoints(contour);
    }

    private static int directionOf(int dx, int dz) {
        for (int i = 0; i < 8; i++) {
            if (DX[i] == dx && DZ[i] == dz) {
                return i;
            }
        }
        throw new IllegalStateException("not a neighbour: " + dx + "," + dz);
    }

    /**
     * Remove points that lie exactly on the straight line between their neighbours (and repeated points).
     * Points where the outline turns back on itself are kept.
     *
     * @param points a closed outline
     * @return the outline without straight points
     */
    static List<BlockVector2> removeStraightPoints(List<BlockVector2> points) {
        List<BlockVector2> result = new ArrayList<>(points);
        boolean changed = true;
        while (changed && result.size() > 2) {
            changed = false;
            for (int i = 0; i < result.size() && result.size() > 2; i++) {
                BlockVector2 previous = result.get((i + result.size() - 1) % result.size());
                BlockVector2 point = result.get(i);
                BlockVector2 next = result.get((i + 1) % result.size());
                if (point.equals(previous) || isBetween(previous, point, next)) {
                    result.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return result;
    }

    private static boolean isBetween(BlockVector2 a, BlockVector2 point, BlockVector2 b) {
        long cross = (long) (point.x() - a.x()) * (b.z() - a.z()) - (long) (point.z() - a.z()) * (b.x() - a.x());
        if (cross != 0) {
            return false;
        }
        long dot = (long) (point.x() - a.x()) * (b.x() - a.x()) + (long) (point.z() - a.z()) * (b.z() - a.z());
        long lengthSquared = (long) (b.x() - a.x()) * (b.x() - a.x()) + (long) (b.z() - a.z()) * (b.z() - a.z());
        return dot > 0 && dot < lengthSquared;
    }

    /**
     * Simplify a closed outline (Douglas-Peucker) until it has at most {@code limit} points.
     *
     * <p>Cutting a corner off an outline would leave blocks outside it, so points are first dropped only
     * where the shorter outline still goes around the original (any tolerance inwards, none outwards);
     * only if that still leaves too many points is the outline allowed to cut across the footprint,
     * a little more each round. Use {@link #countOutside(List)} to see how many columns that left out.</p>
     *
     * @param outline the closed outline
     * @param limit the most points allowed, or 0 or less for no limit
     * @return the simplified outline
     */
    public static List<BlockVector2> simplify(List<BlockVector2> outline, int limit) {
        List<BlockVector2> points = removeStraightPoints(outline);
        if (limit <= 0 || points.size() <= limit) {
            return points;
        }
        int effectiveLimit = Math.max(3, limit);
        int orientation = orientation(points);
        for (double inward = 0.5; inward <= 1 << 20; inward *= 2) {
            List<BlockVector2> result = douglasPeucker(points, inward, 0.01, orientation);
            if (result.size() <= effectiveLimit) {
                return result;
            }
        }
        for (double tolerance = 0.25; ; tolerance *= 1.5) {
            List<BlockVector2> result = douglasPeucker(points, Math.max(tolerance * 4, 1 << 20), tolerance, orientation);
            if (result.size() <= effectiveLimit || tolerance > 1 << 24) {
                return result;
            }
        }
    }

    /**
     * The number of footprint columns that are outside an outline.
     *
     * @param outline the outline
     * @return the number of columns outside
     */
    public long countOutside(List<BlockVector2> outline) {
        long outside = 0;
        for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1)) {
            BlockVector3 point = BlockVector3.at(i % sizeX + minX, 0, i / sizeX + minZ);
            if (!Polygonal2DRegion.contains(outline, 0, 0, point)) {
                outside++;
            }
        }
        return outside;
    }

    private static int orientation(List<BlockVector2> points) {
        long area = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            area += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return area >= 0 ? 1 : -1;
    }

    private static List<BlockVector2> douglasPeucker(List<BlockVector2> points, double inward, double outward,
                                                     int orientation) {
        int n = points.size();
        // split the closed loop at the first point and the point farthest from it
        BlockVector2 first = points.getFirst();
        int far = 0;
        long farDistance = -1;
        for (int i = 1; i < n; i++) {
            long distance = (long) (points.get(i).x() - first.x()) * (points.get(i).x() - first.x())
                + (long) (points.get(i).z() - first.z()) * (points.get(i).z() - first.z());
            if (distance > farDistance) {
                farDistance = distance;
                far = i;
            }
        }
        boolean[] keep = new boolean[n + 1];
        keep[0] = true;
        keep[far] = true;
        keep[n] = true;
        simplifyChain(points, 0, far, inward, outward, orientation, keep);
        simplifyChain(points, far, n, inward, outward, orientation, keep);
        List<BlockVector2> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void simplifyChain(List<BlockVector2> points, int from, int to, double inward, double outward,
                                      int orientation, boolean[] keep) {
        if (to - from < 2) {
            return;
        }
        int n = points.size();
        BlockVector2 a = points.get(from % n);
        BlockVector2 b = points.get(to % n);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        int worst = -1;
        double worstScore = 1;
        for (int i = from + 1; i < to; i++) {
            BlockVector2 p = points.get(i % n);
            double px = p.x() - a.x();
            double pz = p.z() - a.z();
            double score;
            if (length < 1e-9) {
                // the chain comes back to where it started: keep its farthest point
                score = Math.sqrt(px * px + pz * pz) / Math.min(inward, outward);
            } else {
                // positive: the point is on the outside of the chord, so dropping it would cut it off
                double side = -(dx * pz - dz * px) / length * orientation;
                score = side > 0 ? side / outward : -side / inward;
            }
            if (score > worstScore) {
                worstScore = score;
                worst = i;
            }
        }
        if (worst >= 0) {
            keep[worst] = true;
            simplifyChain(points, from, worst, inward, outward, orientation, keep);
            simplifyChain(points, worst, to, inward, outward, orientation, keep);
        }
    }

    /**
     * Make an outline usable as a polygon selection, which needs at least 3 points: a line or a single
     * column is repeated so that it still selects exactly its columns.
     *
     * @param outline the outline
     * @return an outline with at least 3 points
     */
    public static List<BlockVector2> atLeastThreePoints(List<BlockVector2> outline) {
        List<BlockVector2> result = new ArrayList<>(outline);
        while (result.size() < 3) {
            result.add(result.getFirst());
        }
        return result;
    }
}
