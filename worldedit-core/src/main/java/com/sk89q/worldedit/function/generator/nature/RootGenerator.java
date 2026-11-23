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
 * Twisting, tapering roots that spread out and down from a point, as under a tree or a floating island.
 */
public final class RootGenerator {

    /**
     * The part number of the roots.
     */
    public static final int ROOT = 1;

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("count", 6, 1, 24, "how many main roots"),
        NatureMath.SEED
    );

    private static final double STEP = 0.5;
    private static final double MIN_RADIUS = 0.87;

    private RootGenerator() {
    }

    /**
     * Generate roots.
     *
     * @param size the size: x and z are how far they spread, they reach down up to {@code 2y} blocks
     * @param parameters the settings
     * @param seed the seed
     * @return the model, hanging down from y = 0
     */
    public static NatureModel generate(NatureSize size, ShapeParameters parameters, long seed) {
        int count = parameters.getInt("count");
        Random random = new Random(seed);
        NatureModel model = size.hangingModel();
        double spread = size.minHorizontal();
        double thickness = Math.max(MIN_RADIUS, spread * 0.14);
        SeededNoise[] wander = {
            new SeededNoise(seed, 6, 0.12, 2), new SeededNoise(seed, 7, 0.12, 2), new SeededNoise(seed, 8, 0.12, 2)
        };

        // the root collar everything grows from
        NatureMath.ball(model, Vector3.ZERO, Math.max(MIN_RADIUS, thickness * 1.3), ROOT);

        double azimuthStart = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double azimuth = azimuthStart + Math.PI * 2 * i / count + NatureMath.between(random, -0.35, 0.35);
            double pitch = Math.toRadians(NatureMath.between(random, 12, 35));
            Vector3 direction = NatureMath.direction(azimuth, -pitch);
            double reach = Math.hypot(Math.max(size.x(), size.z()), size.height() * 0.5);
            double length = reach * NatureMath.between(random, 0.85, 1.25);
            double radius = thickness * NatureMath.between(random, 0.75, 1.15);
            grow(model, size, wander, random, Vector3.ZERO, direction, length, radius, 0, i * 97);
        }
        model.keepLargestPiece();
        return model;
    }

    private static void grow(NatureModel model, NatureSize size, SeededNoise[] wander, Random random,
                             Vector3 start, Vector3 direction, double length, double radius, int depth, int salt) {
        Vector3 position = start;
        Vector3 heading = direction;
        double branchAt = depth < 2 && random.nextDouble() < 0.8 ? NatureMath.between(random, 0.3, 0.6) : 2;
        double height = size.height() - 1;
        for (double travelled = 0; travelled < length; travelled += STEP) {
            double progress = travelled / length;
            double r = Math.max(MIN_RADIUS, radius * Math.pow(1 - progress, 0.8));
            NatureMath.ball(model, position, r, ROOT);

            if (progress >= branchAt) {
                branchAt = 2;
                double turn = (random.nextBoolean() ? 1 : -1) * Math.toRadians(NatureMath.between(random, 30, 60));
                double cos = Math.cos(turn);
                double sin = Math.sin(turn);
                Vector3 side = Vector3.at(heading.x() * cos - heading.z() * sin, heading.y() - 0.15,
                    heading.x() * sin + heading.z() * cos).normalize();
                grow(model, size, wander, random, position, side, (length - travelled) * 0.6, r * 0.7, depth + 1,
                    salt + 31);
            }

            // twist with the noise, and droop more the further out the root gets
            double nx = position.x() + salt;
            Vector3 push = Vector3.at(wander[0].signed(nx, position.y(), position.z()),
                wander[1].signed(nx, position.y(), position.z()) * 0.6,
                wander[2].signed(nx, position.y(), position.z()));
            heading = heading.add(push.multiply(0.22)).add(0, -0.05 - 0.08 * progress, 0).normalize();
            if (heading.y() > 0.1) {
                heading = Vector3.at(heading.x(), 0.1, heading.z()).normalize();
            }
            Vector3 next = position.add(heading.multiply(STEP));
            // keep inside the size: turn back from the sides, flatten out at the bottom
            if (Math.abs(next.x()) > size.x() - r || Math.abs(next.z()) > size.z() - r) {
                Vector3 inward = Vector3.at(-next.x() / size.x(), 0, -next.z() / size.z());
                heading = heading.add(inward.multiply(0.6)).normalize();
                if (Math.abs(next.x()) > size.x() || Math.abs(next.z()) > size.z()) {
                    break;
                }
            }
            if (next.y() < -height + r) {
                heading = Vector3.at(heading.x(), 0, heading.z());
                if (heading.lengthSq() < 1e-6) {
                    break;
                }
                heading = heading.normalize();
            }
            position = position.add(heading.multiply(STEP));
        }
    }
}
