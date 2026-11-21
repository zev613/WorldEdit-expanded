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

package com.sk89q.worldedit.function.generator.warp;

import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The box a warp works in: the bounding box of the selection.
 *
 * @param min the smallest corner
 * @param max the largest corner
 */
public record WarpBounds(BlockVector3 min, BlockVector3 max) {

    public WarpBounds {
        checkNotNull(min);
        checkNotNull(max);
        BlockVector3 low = min.getMinimum(max);
        BlockVector3 high = min.getMaximum(max);
        min = low;
        max = high;
    }

    /**
     * The middle of the box, in block coordinates (halfway between blocks for even sizes).
     *
     * @return the centre
     */
    public Vector3 center() {
        return Vector3.at((min.x() + max.x()) / 2.0, (min.y() + max.y()) / 2.0, (min.z() + max.z()) / 2.0);
    }

    /**
     * The number of blocks along an axis.
     *
     * @param axis {@link ShapeAxis#X}, {@link ShapeAxis#Y} or {@link ShapeAxis#Z}
     * @return the size
     */
    public int size(int axis) {
        return switch (axis) {
            case ShapeAxis.X -> max.x() - min.x() + 1;
            case ShapeAxis.Z -> max.z() - min.z() + 1;
            default -> max.y() - min.y() + 1;
        };
    }

    /**
     * The longer of the two horizontal axes (x when they are equal).
     *
     * @return {@link ShapeAxis#X} or {@link ShapeAxis#Z}
     */
    public int longHorizontalAxis() {
        return size(ShapeAxis.X) >= size(ShapeAxis.Z) ? ShapeAxis.X : ShapeAxis.Z;
    }

    /**
     * How far up the box a height is, from 0 at the bottom layer to 1 at the top layer.
     *
     * @param y the height
     * @return the fraction, 0 for a box one block high
     */
    public double heightFraction(double y) {
        int height = size(ShapeAxis.Y);
        return height <= 1 ? 0 : (y - min.y()) / (height - 1);
    }
}
