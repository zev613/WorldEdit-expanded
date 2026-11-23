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

package com.sk89q.worldedit.function.generator.nature;

import com.sk89q.worldedit.function.generator.shape.ShapeParameter;

import java.util.List;

/**
 * The size of a natural object: the x, y and z "radius", like {@code //shape}.
 *
 * <p>Standing objects (rocks, crystals, mushrooms, coral) are up to {@code 2x+1} by {@code 2z+1} blocks
 * across, centred on the placement position, and up to {@code 2y} blocks tall from it. Roots hang down
 * from the placement position, up to {@code 2y} blocks deep.</p>
 *
 * @param x the east-west radius
 * @param y the up-down radius (half the height)
 * @param z the north-south radius
 */
public record NatureSize(double x, double y, double z) {

    /**
     * The largest radius allowed.
     */
    public static final double MAX_RADIUS = 64;

    public NatureSize {
        if (!(x >= 1 && y >= 1 && z >= 1)) {
            throw new IllegalArgumentException("Sizes must be at least 1");
        }
        if (x > MAX_RADIUS || y > MAX_RADIUS || z > MAX_RADIUS) {
            throw new IllegalArgumentException("Sizes can be at most " + (int) MAX_RADIUS);
        }
    }

    /**
     * Make a size from one number or three (x,y,z).
     *
     * @param values the numbers
     * @return the size
     */
    public static NatureSize of(List<Double> values) {
        return switch (values.size()) {
            case 1 -> new NatureSize(values.get(0), values.get(0), values.get(0));
            case 3 -> new NatureSize(values.get(0), values.get(1), values.get(2));
            default -> throw new IllegalArgumentException("Give one size, or three sizes as x,y,z");
        };
    }

    /**
     * Make a size with the same radius every way.
     *
     * @param radius the radius
     * @return the size
     */
    public static NatureSize of(double radius) {
        return new NatureSize(radius, radius, radius);
    }

    /**
     * The height in blocks.
     *
     * @return the height
     */
    public int height() {
        return Math.max(1, (int) Math.ceil(2 * y - 1e-9));
    }

    /**
     * The largest radius.
     *
     * @return the radius
     */
    public double max() {
        return Math.max(x, Math.max(y, z));
    }

    /**
     * The smallest horizontal radius.
     *
     * @return the radius
     */
    public double minHorizontal() {
        return Math.min(x, z);
    }

    /**
     * An empty model for an object standing on the placement position.
     *
     * @return the model
     */
    NatureModel standingModel() {
        return new NatureModel(-(int) x, 0, -(int) z, (int) x, height() - 1, (int) z);
    }

    /**
     * An empty model for an object hanging down from the placement position.
     *
     * @return the model
     */
    NatureModel hangingModel() {
        return new NatureModel(-(int) x, -(height() - 1), -(int) z, (int) x, 0, (int) z);
    }

    @Override
    public String toString() {
        return x == y && y == z ? ShapeParameter.format(x)
            : ShapeParameter.format(x) + "," + ShapeParameter.format(y) + "," + ShapeParameter.format(z);
    }
}
