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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Boulders and rock formations: a few overlapping lumps, deformed by noise, with a flat base.
 */
public final class RockGenerator {

    /**
     * The part number of the rock.
     */
    public static final int ROCK = 1;

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("roughness", 0.5, 0, 1, "0 smooth pebble to 1 jagged crag"),
        NatureMath.SEED
    );

    private RockGenerator() {
    }

    private record Lobe(double x, double y, double z, double radius) {
    }

    /**
     * Generate a rock.
     *
     * @param size the size; the rock stands on the placement position
     * @param parameters the settings
     * @param seed the seed
     * @return the model
     */
    public static NatureModel generate(NatureSize size, ShapeParameters parameters, long seed) {
        double roughness = parameters.get("roughness");
        Random random = new Random(seed);
        NatureModel model = size.standingModel();

        // lumps in "unit" space, where the rock's bounding ellipsoid is the unit ball
        List<Lobe> lobes = new ArrayList<>();
        lobes.add(new Lobe(0, 0, 0, 0.75));
        int extra = 1 + random.nextInt(3);
        for (int i = 0; i < extra; i++) {
            double radius = NatureMath.between(random, 0.3, 0.5);
            double distance = NatureMath.between(random, 0.15, 0.8 - radius);
            double azimuth = random.nextDouble() * Math.PI * 2;
            double elevation = NatureMath.between(random, -0.3, 0.6);
            lobes.add(new Lobe(Math.cos(azimuth) * Math.cos(elevation) * distance, Math.sin(elevation) * distance,
                Math.sin(azimuth) * Math.cos(elevation) * distance, radius));
        }

        SeededNoise noise = new SeededNoise(seed, 1, 1.7, 4);
        SeededNoise strata = new SeededNoise(seed, 2, 0.9, 1);
        // the middle sits low so the bottom is cut flat by the ground
        double centerY = size.y() * 0.6;
        for (int x = model.getMinX(); x <= model.getMaxX(); x++) {
            double ux = x / size.x();
            for (int y = model.getMinY(); y <= model.getMaxY(); y++) {
                double uy = (y - centerY) / size.y();
                for (int z = model.getMinZ(); z <= model.getMaxZ(); z++) {
                    double uz = z / size.z();
                    double nearest = Double.MAX_VALUE;
                    for (Lobe lobe : lobes) {
                        double dx = ux - lobe.x();
                        double dy = uy - lobe.y();
                        double dz = uz - lobe.z();
                        nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz) / lobe.radius());
                    }
                    if (nearest > 1.4) {
                        continue;
                    }
                    double bump = noise.signed(ux, uy, uz);
                    // faint horizontal ledges, like weathered layers
                    double ledge = Math.abs(strata.signed(ux * 0.3, uy * 4, uz * 0.3)) < 0.08 ? 0.06 : 0;
                    if (nearest + roughness * 0.25 * bump + roughness * ledge < 1.0) {
                        model.set(x, y, z, ROCK);
                    }
                }
            }
        }
        model.keepLargestPiece();
        return model;
    }
}
