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

import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A flat drawing made of blocks: a picture that is at most {@link #MAX_THICKNESS} blocks thick along one axis.
 *
 * <p>The drawing is described in its own 2D coordinates: {@code u} runs along the drawing horizontally and
 * {@code v} runs up it (for a drawing lying on the ground, {@code u} is x and {@code v} is z). {@code w} is the
 * thin axis. All three are plain world coordinates on the matching axis.</p>
 *
 * @param <T> the kind of block stored in each cell
 */
public final class FlatDrawing<T> {

    /**
     * How thick a drawing may be along its thin axis.
     */
    public static final int MAX_THICKNESS = 2;

    /**
     * A world axis.
     */
    public enum Axis {
        X(0), Y(1), Z(2);

        private final int index;

        Axis(int index) {
            this.index = index;
        }

        /**
         * The position of this axis in an {x, y, z} array.
         *
         * @return 0, 1 or 2
         */
        public int index() {
            return index;
        }

        /**
         * The coordinate of a position on this axis.
         *
         * @param position the position
         * @return the coordinate
         */
        public int of(BlockVector3 position) {
            return switch (this) {
                case X -> position.x();
                case Y -> position.y();
                case Z -> position.z();
            };
        }

        /**
         * A lower-case name.
         *
         * @return the name
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Axis thin;
    private final Axis uAxis;
    private final Axis vAxis;
    private final int minU;
    private final int minV;
    private final int minW;
    private final int maxW;
    private final int width;
    private final int height;
    private final Object[] cells;
    private int count;

    private FlatDrawing(Axis thin, int[] min, int[] max) {
        this.thin = thin;
        this.uAxis = thin == Axis.X ? Axis.Z : Axis.X;
        this.vAxis = thin == Axis.Y ? Axis.Z : Axis.Y;
        this.minU = min[uAxis.index()];
        this.minV = min[vAxis.index()];
        this.minW = min[thin.index()];
        this.maxW = max[thin.index()];
        this.width = max[uAxis.index()] - minU + 1;
        this.height = max[vAxis.index()] - minV + 1;
        long cellCount = (long) width * height;
        if (cellCount > 4L * 1024 * 1024) {
            throw new IllegalArgumentException("That drawing is too big (" + width + " x " + height + ").");
        }
        this.cells = new Object[(int) cellCount];
    }

    /**
     * Read a drawing from its blocks.
     *
     * @param blocks the non-air blocks of the drawing, by position
     * @param forced the thin axis to use, or null to detect it (the thinnest axis; ties prefer z, then x, then y)
     * @param allowFlatOnGround whether the drawing may lie on the ground (thin along y)
     * @param <T> the kind of block
     * @return the drawing
     * @throws IllegalArgumentException with a friendly message if there are no blocks or the drawing is not flat
     */
    public static <T> FlatDrawing<T> of(Map<BlockVector3, T> blocks, @Nullable Axis forced, boolean allowFlatOnGround) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("There is nothing drawn here: the selection has no blocks in it.");
        }
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockVector3 position : blocks.keySet()) {
            for (Axis axis : Axis.values()) {
                min[axis.index()] = Math.min(min[axis.index()], axis.of(position));
                max[axis.index()] = Math.max(max[axis.index()], axis.of(position));
            }
        }
        String size = (max[0] - min[0] + 1) + " x " + (max[1] - min[1] + 1) + " x " + (max[2] - min[2] + 1);
        Axis thin = forced;
        if (thin == null) {
            Axis[] order = allowFlatOnGround ? new Axis[] {Axis.Z, Axis.X, Axis.Y} : new Axis[] {Axis.Z, Axis.X};
            for (Axis axis : order) {
                int thickness = max[axis.index()] - min[axis.index()] + 1;
                if (thin == null || thickness < max[thin.index()] - min[thin.index()] + 1) {
                    thin = axis;
                }
            }
        } else if (thin == Axis.Y && !allowFlatOnGround) {
            throw new IllegalArgumentException("The drawing has to stand upright (flat along x or z), "
                + "not lie on the ground.");
        }
        int thickness = max[thin.index()] - min[thin.index()] + 1;
        if (thickness > MAX_THICKNESS && forced != null) {
            throw new IllegalArgumentException("The drawing is " + thickness + " blocks thick along " + thin.label()
                + " (it spans " + size + ", x by y by z), so it can only go along the axis it is flat in.");
        }
        if (thickness > MAX_THICKNESS) {
            throw new IllegalArgumentException("The drawing is not flat: its blocks span " + size
                + " (x by y by z). Draw a flat picture at most " + MAX_THICKNESS + " blocks thick"
                + (allowFlatOnGround ? "" : ", standing upright like a wall along x or z")
                + ", and select only the drawing.");
        }
        FlatDrawing<T> drawing = new FlatDrawing<>(thin, min, max);
        // the layer on the low side wins when the drawing is two thick
        for (int w = drawing.minW; w <= drawing.maxW; w++) {
            for (Map.Entry<BlockVector3, T> entry : blocks.entrySet()) {
                BlockVector3 position = entry.getKey();
                if (thin.of(position) != w || entry.getValue() == null) {
                    continue;
                }
                int index = drawing.index(drawing.uAxis.of(position), drawing.vAxis.of(position));
                if (drawing.cells[index] == null) {
                    drawing.cells[index] = entry.getValue();
                    drawing.count++;
                }
            }
        }
        return drawing;
    }

    private int index(int u, int v) {
        return (v - minV) * width + (u - minU);
    }

    /**
     * The block drawn at a spot, or null if nothing is drawn there.
     *
     * @param u the u coordinate
     * @param v the v coordinate
     * @return the block, or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T get(int u, int v) {
        if (u < minU || v < minV || u >= minU + width || v >= minV + height) {
            return null;
        }
        return (T) cells[index(u, v)];
    }

    public Axis getThinAxis() {
        return thin;
    }

    public Axis getUAxis() {
        return uAxis;
    }

    public Axis getVAxis() {
        return vAxis;
    }

    public int getMinU() {
        return minU;
    }

    public int getMaxU() {
        return minU + width - 1;
    }

    public int getMinV() {
        return minV;
    }

    public int getMaxV() {
        return minV + height - 1;
    }

    public int getMinW() {
        return minW;
    }

    public int getMaxW() {
        return maxW;
    }

    /**
     * The number of cells with something drawn in them.
     *
     * @return the count
     */
    public int count() {
        return count;
    }

    /**
     * Turn drawing coordinates back into a world position.
     *
     * @param u the u coordinate
     * @param v the v coordinate
     * @param w the coordinate on the thin axis
     * @return the position
     */
    public BlockVector3 toWorld(int u, int v, int w) {
        return switch (thin) {
            case X -> BlockVector3.at(w, v, u);
            case Y -> BlockVector3.at(u, w, v);
            case Z -> BlockVector3.at(u, v, w);
        };
    }
}
