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

import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A shape put together from other shapes, step by step: add one, cut one away, keep only the overlap.
 *
 * <p>Offsets are in blocks from the centre of the first shape. The joins can be blended with
 * {@link #setSmoothness(double)}, which turns every step into a distance field and combines them with a
 * smooth minimum, so joins get rounded fillets instead of sharp creases.</p>
 */
public final class CompoundShape {

    /**
     * The most voxels the box around a smoothed compound may hold.
     */
    public static final long MAX_SMOOTH_VOLUME = 8L * 1024 * 1024;

    /**
     * The most voxels the box around a compound may hold.
     */
    public static final long MAX_VOLUME = 64L * 1024 * 1024;

    private static final Map<UUID, CompoundShape> BY_PLAYER = new ConcurrentHashMap<>();

    /**
     * How a step changes the shape so far.
     */
    public enum Operation {
        /**
         * Union: add the step's blocks.
         */
        ADD,
        /**
         * Subtract: carve the step's blocks away.
         */
        CUT,
        /**
         * Intersect: keep only blocks in both.
         */
        KEEP;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One step.
     *
     * @param operation what it does
     * @param voxels the voxels of the shape, as offsets from its own centre
     * @param offset where its centre is, from the centre of the first shape
     * @param description what the player typed, for listing
     */
    public record Step(Operation operation, VoxelGrid voxels, BlockVector3 offset, String description) {
        public Step {
            checkNotNull(operation);
            checkNotNull(voxels);
            checkNotNull(offset);
            checkNotNull(description);
        }
    }

    private final List<Step> steps = new ArrayList<>();
    @Nullable
    private BlockVector3 anchor;
    private double smoothness;

    /**
     * The compound shape a player is working on (kept in memory only; it is lost on a restart).
     *
     * @param player the player's id
     * @return their compound shape, created empty if needed
     */
    public static CompoundShape of(UUID player) {
        return BY_PLAYER.computeIfAbsent(player, _ -> new CompoundShape());
    }

    /**
     * Add a step.
     *
     * @param step the step
     * @param position the placement position, remembered if this is the first step
     * @throws IllegalArgumentException if the first step is not {@link Operation#ADD}
     */
    public synchronized void add(Step step, BlockVector3 position) {
        if (steps.isEmpty()) {
            if (step.operation() != Operation.ADD) {
                throw new IllegalArgumentException("Start with //combine add: there is nothing yet to "
                    + step.operation().label() + " from.");
            }
            anchor = position;
        }
        steps.add(step);
    }

    /**
     * Remove the last step.
     *
     * @return the removed step, or null if there were none
     */
    @Nullable
    public synchronized Step undo() {
        if (steps.isEmpty()) {
            return null;
        }
        Step removed = steps.removeLast();
        if (steps.isEmpty()) {
            anchor = null;
        }
        return removed;
    }

    /**
     * Remove every step and the smoothing.
     */
    public synchronized void clear() {
        steps.clear();
        anchor = null;
        smoothness = 0;
    }

    public synchronized List<Step> getSteps() {
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * Where the first shape was added (its centre).
     *
     * @return the position, or null if there are no steps
     */
    @Nullable
    public synchronized BlockVector3 getAnchor() {
        return anchor;
    }

    public synchronized double getSmoothness() {
        return smoothness;
    }

    /**
     * Set how many blocks wide the blends at joins are (0 = sharp).
     *
     * @param smoothness the blend size in blocks
     */
    public synchronized void setSmoothness(double smoothness) {
        if (!(smoothness >= 0) || smoothness > 64) {
            throw new IllegalArgumentException("Smoothness must be between 0 (sharp) and 64 blocks.");
        }
        this.smoothness = smoothness;
    }

    /**
     * Work out the blocks of the compound shape.
     *
     * @return the voxels, as offsets from the centre of the first shape
     * @throws IllegalArgumentException if there are no steps, the result is empty or it is too big
     */
    public synchronized VoxelGrid evaluate() {
        return evaluate(steps, smoothness);
    }

    /**
     * Work out the blocks of a list of steps.
     *
     * @param steps the steps, the first of which must add
     * @param smoothness the blend size in blocks (0 = sharp)
     * @return the voxels, as offsets from the centre of the first shape
     */
    public static VoxelGrid evaluate(List<Step> steps, double smoothness) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("There are no shapes yet: start with //combine add <shape> <size>.");
        }
        int margin = smoothness > 0 ? (int) Math.ceil(smoothness) + 1 : 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Step step : steps) {
            if (step.operation() != Operation.ADD) {
                continue;
            }
            VoxelGrid grid = step.voxels();
            BlockVector3 offset = step.offset();
            minX = Math.min(minX, grid.getMinX() + offset.x() - margin - 1);
            minY = Math.min(minY, grid.getMinY() + offset.y() - margin - 1);
            minZ = Math.min(minZ, grid.getMinZ() + offset.z() - margin - 1);
            maxX = Math.max(maxX, grid.getMaxX() + offset.x() + margin + 1);
            maxY = Math.max(maxY, grid.getMaxY() + offset.y() + margin + 1);
            maxZ = Math.max(maxZ, grid.getMaxZ() + offset.z() + margin + 1);
        }
        long sizeX = (long) maxX - minX + 1;
        long sizeY = (long) maxY - minY + 1;
        long sizeZ = (long) maxZ - minZ + 1;
        long volume = sizeX * sizeY * sizeZ;
        if (volume > (smoothness > 0 ? MAX_SMOOTH_VOLUME : MAX_VOLUME)) {
            throw new IllegalArgumentException("The combined shape is too big (" + sizeX + " x " + sizeY + " x "
                + sizeZ + ")" + (smoothness > 0 ? "to smooth; try //combine smooth 0 or smaller shapes." : "."));
        }
        VoxelGrid result = new VoxelGrid(minX, minY, minZ, maxX, maxY, maxZ);
        if (smoothness <= 0) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        boolean in = false;
                        for (Step step : steps) {
                            boolean here = contains(step, x, y, z);
                            in = switch (step.operation()) {
                                case ADD -> in || here;
                                case CUT -> in && !here;
                                case KEEP -> in && here;
                            };
                        }
                        if (in) {
                            result.set(x, y, z);
                        }
                    }
                }
            }
            return result;
        }
        int nx = (int) sizeX;
        int ny = (int) sizeY;
        int nz = (int) sizeZ;
        float[] field = null;
        boolean[] mask = new boolean[(int) volume];
        for (Step step : steps) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    for (int x = 0; x < nx; x++) {
                        mask[(y * nz + z) * nx + x] = contains(step, x + minX, y + minY, z + minZ);
                    }
                }
            }
            float[] distance = DistanceField.signed(mask, nx, ny, nz);
            if (field == null) {
                field = distance;
                continue;
            }
            for (int i = 0; i < field.length; i++) {
                field[i] = (float) switch (step.operation()) {
                    case ADD -> smoothMin(field[i], distance[i], smoothness);
                    case CUT -> -smoothMin(-field[i], distance[i], smoothness);
                    case KEEP -> -smoothMin(-field[i], -distance[i], smoothness);
                };
            }
        }
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    if (field[(y * nz + z) * nx + x] < 0) {
                        result.set(x + minX, y + minY, z + minZ);
                    }
                }
            }
        }
        return result;
    }

    private static boolean contains(Step step, int x, int y, int z) {
        BlockVector3 offset = step.offset();
        return step.voxels().get(x - offset.x(), y - offset.y(), z - offset.z());
    }

    /**
     * A smooth minimum: the smaller of two distances, rounded off where they are within {@code k} of each other.
     *
     * @param a one distance
     * @param b the other
     * @param k the blend size (0 = plain minimum)
     * @return the blended distance
     */
    public static double smoothMin(double a, double b, double k) {
        if (k <= 0) {
            return Math.min(a, b);
        }
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }
}
