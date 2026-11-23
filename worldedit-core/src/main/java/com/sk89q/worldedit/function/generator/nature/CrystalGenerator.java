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
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.Vector3;

import java.util.List;
import java.util.Random;

/**
 * A cluster of pointed hexagonal crystals growing out of a small base, at different lengths and tilts.
 */
public final class CrystalGenerator {

    /**
     * The part number of the crystals.
     */
    public static final int CRYSTAL = 1;

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("count", 7, 1, 40, "how many crystals"),
        NatureMath.SEED
    );

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double SIN60 = Math.sqrt(3) / 2;

    private CrystalGenerator() {
    }

    /**
     * Generate a crystal cluster.
     *
     * @param size the size: x and z are the spread, the tallest crystal reaches {@code 2y} blocks up
     * @param parameters the settings
     * @param seed the seed
     * @return the model
     */
    public static NatureModel generate(NatureSize size, ShapeParameters parameters, long seed) {
        int count = parameters.getInt("count");
        Random random = new Random(seed);
        NatureModel model = size.standingModel();
        int height = size.height();
        double spread = size.minHorizontal();

        // the base the crystals grow out of
        double baseRadius = Math.max(1.2, spread * 0.3);
        SeededNoise noise = new SeededNoise(seed, 3, 0.35, 2);
        for (int x = model.getMinX(); x <= model.getMaxX(); x++) {
            for (int z = model.getMinZ(); z <= model.getMaxZ(); z++) {
                double distance = Math.sqrt(x * x + z * z) / baseRadius;
                double top = (1 - distance * distance) * Math.max(1, baseRadius * 0.5) + noise.signed(x, 0, z);
                for (int y = 0; y < top; y++) {
                    model.set(x, y, z, CRYSTAL);
                }
            }
        }

        double azimuthStart = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            boolean main = i == 0;
            double azimuth = azimuthStart + i * GOLDEN_ANGLE + NatureMath.between(random, -0.3, 0.3);
            double tilt = main ? NatureMath.between(random, 0, 0.15)
                : NatureMath.between(random, Math.toRadians(15), Math.toRadians(count <= 3 ? 40 : 60));
            Vector3 direction = NatureMath.direction(azimuth, Math.PI / 2 - tilt);
            double start = main ? 0 : NatureMath.between(random, 0.1, 0.6) * baseRadius;
            Vector3 origin = Vector3.at(Math.cos(azimuth) * start, 0, Math.sin(azimuth) * start);
            double length = (main ? 0.97 : NatureMath.between(random, 0.35, 0.8)) * height;
            double radius = Math.max(0.8, Math.min(spread * 0.35, length * NatureMath.between(random, 0.1, 0.16)));
            if (main) {
                radius = Math.max(radius, Math.min(spread * 0.4, length * 0.14));
            }
            length = fit(origin, direction, length, radius, size, height);
            prism(model, origin, direction, length, radius, random.nextDouble() * Math.PI);
        }
        model.keepLargestPiece();
        return model;
    }

    /**
     * Shorten a crystal so its tip stays inside the size.
     */
    private static double fit(Vector3 origin, Vector3 direction, double length, double radius, NatureSize size,
                              int height) {
        double limit = length;
        double[] start = {origin.x(), origin.y(), origin.z()};
        double[] step = {direction.x(), direction.y(), direction.z()};
        double[] bound = {size.x() - radius * 0.5, height - 0.5, size.z() - radius * 0.5};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(step[axis]) > 1e-6) {
                double room = (Math.signum(step[axis]) * bound[axis] - start[axis]) / step[axis];
                limit = Math.min(limit, Math.max(1, room));
            }
        }
        return limit;
    }

    private static void prism(NatureModel model, Vector3 origin, Vector3 direction, double length, double radius,
                              double twist) {
        Vector3[] axes = NatureMath.perpendiculars(direction);
        Vector3 u = axes[0].multiply(Math.cos(twist)).add(axes[1].multiply(Math.sin(twist)));
        Vector3 v = direction.cross(u);
        double tip = Math.min(length * 0.5, radius * 2.2);
        Vector3 end = origin.add(direction.multiply(length));
        int x0 = (int) Math.floor(Math.min(origin.x(), end.x()) - radius - 1);
        int x1 = (int) Math.ceil(Math.max(origin.x(), end.x()) + radius + 1);
        int y0 = (int) Math.floor(Math.min(origin.y(), end.y()) - radius - 1);
        int y1 = (int) Math.ceil(Math.max(origin.y(), end.y()) + radius + 1);
        int z0 = (int) Math.floor(Math.min(origin.z(), end.z()) - radius - 1);
        int z1 = (int) Math.ceil(Math.max(origin.z(), end.z()) + radius + 1);
        for (int x = x0; x <= x1; x++) {
            for (int y = Math.max(0, y0); y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Vector3 offset = Vector3.at(x - origin.x(), y - origin.y(), z - origin.z());
                    double along = offset.dot(direction);
                    if (along < -radius || along > length) {
                        continue;
                    }
                    double a = offset.dot(u);
                    double b = offset.dot(v);
                    double hex = Math.max(Math.abs(a), Math.max(Math.abs(a * 0.5 + b * SIN60),
                        Math.abs(a * 0.5 - b * SIN60)));
                    double allowed = along > length - tip ? radius * (length - along) / tip : radius;
                    if (hex <= allowed + 0.15) {
                        model.set(x, y, z, CRYSTAL);
                    }
                }
            }
        }
    }
}
