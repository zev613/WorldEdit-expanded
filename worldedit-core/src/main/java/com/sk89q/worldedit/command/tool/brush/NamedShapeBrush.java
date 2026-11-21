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

package com.sk89q.worldedit.command.tool.brush;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.ShapeGenerator;
import com.sk89q.worldedit.function.generator.shape.ShapeOrientation;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.shape.ShapeRasterizer;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.block.BlockTypes;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stamps a named shape (heart, star, knot...) where you click.
 */
public class NamedShapeBrush implements Brush {

    private final NamedShape shape;
    private final ShapeParameters parameters;
    private final ShapeOrientation orientation;
    private final boolean hollow;
    private double cachedSize = -1;
    private VoxelGrid cached;

    public NamedShapeBrush(NamedShape shape, ShapeParameters parameters, ShapeOrientation orientation, boolean hollow) {
        this.shape = checkNotNull(shape);
        this.parameters = checkNotNull(parameters);
        this.orientation = checkNotNull(orientation);
        this.hollow = hollow;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size)
            throws MaxChangedBlocksException {
        if (pattern == null) {
            pattern = BlockTypes.COBBLESTONE.getDefaultState();
        }
        double radius = Math.max(1, size);
        VoxelGrid voxels;
        synchronized (this) {
            // the voxels only depend on the size, so reuse them between clicks
            if (cached == null || cachedSize != radius) {
                cached = ShapeGenerator.voxelize(shape, parameters, Vector3.at(radius, radius, radius),
                    orientation, hollow, ShapeRasterizer.DEFAULT_MAX_VOLUME);
                cachedSize = radius;
            }
            voxels = cached;
        }
        ShapeGenerator.build(editSession, position, pattern, voxels, false);
    }
}
