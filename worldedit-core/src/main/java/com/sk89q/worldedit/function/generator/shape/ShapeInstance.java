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
 * A shape with its parameters filled in, ready to be rasterized.
 *
 * <p>Shapes work in normalized coordinates, where the shape fits in the cube
 * from -1 to 1 on each axis; y is up. The rasterizer stretches that cube to the
 * requested size and rotation.</p>
 */
public sealed interface ShapeInstance permits ShapeInstance.Implicit, ShapeInstance.Swept {

    /**
     * A shape defined by an inside test.
     */
    non-sealed interface Implicit extends ShapeInstance {

        /**
         * Whether a point is inside the shape.
         *
         * @param x normalized x
         * @param y normalized y
         * @param z normalized z
         * @return true if inside
         */
        boolean contains(double x, double y, double z);
    }

    /**
     * A shape drawn by sweeping a brush along curves and surfaces.
     */
    non-sealed interface Swept extends ShapeInstance {

        /**
         * Draw the shape.
         *
         * @param tracer the tracer to draw with
         */
        void trace(ShapeTracer tracer);

        /**
         * Whether the traced geometry should be centred and scaled to fit the
         * normalized cube automatically.
         *
         * @return true to fit automatically
         */
        default boolean autoFit() {
            return false;
        }
    }
}
