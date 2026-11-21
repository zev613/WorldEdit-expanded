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

import com.sk89q.worldedit.math.Vector3;

import javax.annotation.Nullable;

/**
 * Says, for each block after a warp, where in the original it comes from.
 */
@FunctionalInterface
public interface WarpMap {

    /**
     * Find the position in the original that ends up at a block.
     *
     * @param x the x coordinate of the block
     * @param y the y coordinate of the block
     * @param z the z coordinate of the block
     * @return the position to copy from, or {@code null} for nothing (air)
     */
    @Nullable
    Vector3 source(double x, double y, double z);
}
