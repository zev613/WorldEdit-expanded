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

import javax.annotation.Nullable;

/**
 * The shape carried along a path by {@link Sweeper}.
 *
 * @param <T> the kind of block it is made of
 */
public interface CrossSection<T> {

    /**
     * The block at a spot of the cross-section.
     *
     * @param right blocks to the right of the path (seen looking along it)
     * @param up blocks above the path
     * @param along blocks along the path from its start (for patterns that repeat, such as rail sleepers)
     * @return the block, or null for empty
     */
    @Nullable
    T at(double right, double up, double along);

    /**
     * How far from the path the cross-section reaches, in blocks.
     *
     * @return the distance
     */
    double reach();

    /**
     * Read an upright flat drawing as a cross-section, keeping its blocks.
     *
     * @param drawing the drawing (thin along x or z)
     * @param originU the u coordinate of the drawing that sits on the path
     * @param originV the v (height) coordinate of the drawing that sits on the path
     * @param <T> the kind of block
     * @return the cross-section
     */
    static <T> CrossSection<T> of(FlatDrawing<T> drawing, int originU, int originV) {
        if (drawing.getThinAxis() == FlatDrawing.Axis.Y) {
            throw new IllegalArgumentException("The drawing has to stand upright (flat along x or z) to be "
                + "used as a cross-section.");
        }
        double reachU = Math.max(Math.abs(drawing.getMinU() - originU), Math.abs(drawing.getMaxU() - originU));
        double reachV = Math.max(Math.abs(drawing.getMinV() - originV), Math.abs(drawing.getMaxV() - originV));
        double reach = Math.hypot(reachU + 0.5, reachV + 0.5);
        return new CrossSection<>() {
            @Override
            public T at(double right, double up, double along) {
                return drawing.get(originU + (int) Math.floor(right + 0.5), originV + (int) Math.floor(up + 0.5));
            }

            @Override
            public double reach() {
                return reach;
            }
        };
    }
}
