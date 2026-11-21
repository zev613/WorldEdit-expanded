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

package com.sk89q.worldedit.function.generator.shape;

/**
 * Receives the geometry of a {@link ShapeInstance.Swept} shape.
 *
 * <p>Positions are in normalized coordinates; brush radii are in blocks.</p>
 */
public interface ShapeTracer {

    /**
     * A parametric curve.
     */
    @FunctionalInterface
    interface Curve {
        /**
         * Evaluate the curve.
         *
         * @param t the parameter
         * @param out receives x, y and z
         */
        void at(double t, double[] out);
    }

    /**
     * A parametric surface.
     */
    @FunctionalInterface
    interface Surface {
        /**
         * Evaluate the surface.
         *
         * @param u the first parameter
         * @param v the second parameter
         * @param out receives x, y and z
         */
        void at(double u, double v, double[] out);
    }

    /**
     * Draw a ball.
     *
     * @param x normalized x
     * @param y normalized y
     * @param z normalized z
     * @param radius the radius in blocks
     */
    void point(double x, double y, double z, double radius);

    /**
     * Draw a tube along a curve.
     *
     * @param curve the curve
     * @param t0 the start parameter
     * @param t1 the end parameter
     * @param radius the tube radius in blocks
     */
    void curve(Curve curve, double t0, double t1, double radius);

    /**
     * Draw a thickened surface.
     *
     * @param surface the surface
     * @param u0 the start of the first parameter
     * @param u1 the end of the first parameter
     * @param v0 the start of the second parameter
     * @param v1 the end of the second parameter
     * @param radius half the thickness, in blocks
     */
    void surface(Surface surface, double u0, double u1, double v0, double v1, double radius);
}
