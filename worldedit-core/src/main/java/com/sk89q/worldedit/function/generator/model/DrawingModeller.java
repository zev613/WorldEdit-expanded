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

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a {@link FlatDrawing} into a 3D model by spinning it ({@link #revolve}) or pulling it
 * ({@link #extrude}).
 *
 * <p>Both work backwards: every block of the result looks up which cell of the drawing it came from,
 * so the result has no holes however it is scaled or turned, and each block keeps the drawn material.</p>
 */
public final class DrawingModeller {

    /**
     * The largest number of blocks a model may have.
     */
    public static final long MAX_BLOCKS = 8L * 1024 * 1024;

    private DrawingModeller() {
    }

    /**
     * Spin an upright drawing around a vertical axis, like a potter's wheel or a lathe.
     *
     * <p>The axis is the vertical line through the middle of the drawing column {@code axisU}, in the
     * drawing's own plane. Each part of the drawing sweeps around the axis clockwise (seen from above) by
     * {@code degrees}; parts on the other side of the axis sweep the opposite half, so a whole drawn vase
     * and half a drawn vase give the same result at 360 degrees.</p>
     *
     * @param drawing the drawing, which must stand upright (thin along x or z)
     * @param axisU the u coordinate (x for a drawing thin along z, z for a drawing thin along x) of the axis
     * @param degrees how far to spin it, 1 to 360
     * @param <T> the kind of block
     * @return the blocks of the model, by world position
     */
    public static <T> Map<BlockVector3, T> revolve(FlatDrawing<T> drawing, int axisU, double degrees) {
        if (drawing.getThinAxis() == FlatDrawing.Axis.Y) {
            throw new IllegalArgumentException("The drawing has to stand upright to be spun around.");
        }
        if (!(degrees > 0) || degrees > 360) {
            throw new IllegalArgumentException("The angle must be more than 0 and at most 360 degrees.");
        }
        int radius = Math.max(Math.abs(drawing.getMaxU() - axisU), Math.abs(drawing.getMinU() - axisU));
        int height = drawing.getMaxV() - drawing.getMinV() + 1;
        long volume = (2L * radius + 1) * (2L * radius + 1) * height;
        if (volume > MAX_BLOCKS * 4) {
            throw new IllegalArgumentException("That would be far too big: the drawing reaches " + radius
                + " blocks from the axis. Stand closer to the drawing, or use -c.");
        }
        int axisW = drawing.getMinW();
        // clockwise seen from above: east, south, west, north
        int turn = drawing.getThinAxis() == FlatDrawing.Axis.Z ? 1 : -1;
        boolean full = degrees >= 360;
        Map<BlockVector3, T> result = new HashMap<>();
        for (int du = -radius; du <= radius; du++) {
            for (int dw = -radius; dw <= radius; dw++) {
                double distance = Math.sqrt((double) du * du + (double) dw * dw);
                int cell = (int) Math.round(distance);
                if (cell > radius) {
                    continue;
                }
                boolean rightSide = full || cell == 0;
                boolean leftSide = full || cell == 0;
                if (!full && cell > 0) {
                    double angle = Math.toDegrees(Math.atan2(turn * dw, du));
                    rightSide = within(angle, degrees);
                    leftSide = within(angle - 180, degrees);
                }
                for (int v = drawing.getMinV(); v <= drawing.getMaxV(); v++) {
                    T block = rightSide ? drawing.get(axisU + cell, v) : null;
                    if (block == null && leftSide) {
                        block = drawing.get(axisU - cell, v);
                    }
                    if (block != null) {
                        result.put(drawing.toWorld(axisU + du, v, axisW + dw), block);
                    }
                }
            }
        }
        return result;
    }

    private static boolean within(double angle, double degrees) {
        double normalized = ((angle % 360) + 360) % 360;
        if (normalized > 360 - 1e-9) {
            normalized = 0;
        }
        return normalized <= degrees + 1e-9;
    }

    /**
     * Pull a drawing straight out along its thin axis.
     *
     * <p>The first new layer is next to the drawing; the last is {@code length} layers away. Each layer is a
     * copy of the drawing turned by a share of {@code twist} and scaled by a share of the {@code taper}, both
     * around the middle of the drawing.</p>
     *
     * @param drawing the drawing
     * @param direction +1 to pull towards higher coordinates on the thin axis, -1 towards lower ones
     * @param length how many layers to add
     * @param twist how far the last layer is turned, in degrees
     * @param taper how big the last layer is compared to the drawing (1 = the same size)
     * @param <T> the kind of block
     * @return the new blocks (not including the drawing itself), by world position
     */
    public static <T> Map<BlockVector3, T> extrude(FlatDrawing<T> drawing, int direction, int length,
                                                   double twist, double taper) {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("direction must be 1 or -1");
        }
        if (length < 1) {
            throw new IllegalArgumentException("The length must be at least 1.");
        }
        if (!(taper > 0) || taper > 16 || !Double.isFinite(twist)) {
            throw new IllegalArgumentException("The taper must be more than 0 and at most 16 (1 keeps the size, "
                + "0.5 halves it, 2 doubles it).");
        }
        double centreU = (drawing.getMinU() + drawing.getMaxU()) / 2.0;
        double centreV = (drawing.getMinV() + drawing.getMaxV()) / 2.0;
        double halfU = (drawing.getMaxU() - drawing.getMinU()) / 2.0 + 0.5;
        double halfV = (drawing.getMaxV() - drawing.getMinV()) / 2.0 + 0.5;
        double maxScale = Math.max(1, taper);
        double reachU = twist == 0 ? halfU * maxScale : Math.hypot(halfU, halfV) * maxScale;
        double reachV = twist == 0 ? halfV * maxScale : Math.hypot(halfU, halfV) * maxScale;
        double volume = (2 * reachU + 2) * (2 * reachV + 2) * length;
        if (volume > MAX_BLOCKS) {
            throw new IllegalArgumentException("That would be too big; use a shorter length or a smaller taper.");
        }
        int start = direction > 0 ? drawing.getMaxW() : drawing.getMinW();
        Map<BlockVector3, T> result = new HashMap<>();
        for (int layer = 1; layer <= length; layer++) {
            double share = (double) layer / length;
            double scale = 1 + (taper - 1) * share;
            double angle = Math.toRadians(twist * share);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int w = start + direction * layer;
            int minU = (int) Math.floor(centreU - reachU);
            int maxU = (int) Math.ceil(centreU + reachU);
            int minV = (int) Math.floor(centreV - reachV);
            int maxV = (int) Math.ceil(centreV + reachV);
            for (int u = minU; u <= maxU; u++) {
                for (int v = minV; v <= maxV; v++) {
                    double du = u - centreU;
                    double dv = v - centreV;
                    // turn back and shrink back to find the drawn cell this block comes from
                    double sourceU = (du * cos + dv * sin) / scale + centreU;
                    double sourceV = (-du * sin + dv * cos) / scale + centreV;
                    T block = drawing.get(roundCell(sourceU), roundCell(sourceV));
                    if (block != null) {
                        result.put(drawing.toWorld(u, v, w), block);
                    }
                }
            }
        }
        return result;
    }

    private static int roundCell(double value) {
        // values within a hair of a whole number are treated as exact, so an untransformed layer is a perfect copy
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1e-7) {
            return (int) rounded;
        }
        return (int) Math.floor(value + 0.5);
    }
}
