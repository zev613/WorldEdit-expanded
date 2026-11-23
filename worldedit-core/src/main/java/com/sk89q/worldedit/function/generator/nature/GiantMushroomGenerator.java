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

import com.sk89q.worldedit.function.generator.NamedSettings;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;

import java.util.List;
import java.util.Random;

/**
 * Giant mushrooms: a slightly curved stem under a round, flat or bell-shaped cap.
 */
public final class GiantMushroomGenerator {

    /**
     * The part number of the stem.
     */
    public static final int STEM = 1;

    /**
     * The part number of the cap.
     */
    public static final int CAP = 2;

    /**
     * The part number of the underside of the cap (only made when asked for).
     */
    public static final int UNDERSIDE = 3;

    /**
     * The cap types, by number.
     */
    public static final List<String> TYPES = List.of("round|red|dome", "flat|brown|disc", "bell|tall|cone");

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("type", 0, 0, 2, "cap: " + NamedSettings.describe(TYPES)),
        NatureMath.SEED
    );

    private GiantMushroomGenerator() {
    }

    /**
     * Generate a giant mushroom.
     *
     * @param size the size: x and z are the cap's radius, the mushroom is {@code 2y} blocks tall
     * @param parameters the settings
     * @param underside whether to mark the underside of the cap as its own part
     * @param seed the seed
     * @return the model
     */
    public static NatureModel generate(NatureSize size, ShapeParameters parameters, boolean underside, long seed) {
        int type = parameters.getInt("type");
        Random random = new Random(seed);
        NatureModel model = size.standingModel();
        double height = size.height();
        double radius = Math.max(size.x(), size.z());
        double thickness = Math.max(1, Math.min(2, size.minHorizontal() * 0.12));
        double capHeight = switch (type) {
            case 1 -> Math.max(2, Math.min(radius * 0.3, height * 0.3));
            case 2 -> Math.max(3, Math.min(radius * 1.3, height * 0.65));
            default -> Math.max(2, Math.min(radius * 0.8, height * 0.55));
        };
        double capBottom = height - capHeight;
        SeededNoise rimNoise = new SeededNoise(seed, 4, 1.3, 2);

        // stem: thicker at the foot, bowing gently to one side
        double stemRadius = Math.max(0.9, size.minHorizontal() * 0.18);
        double bend = Math.min(size.minHorizontal() * 0.25, stemRadius * 1.5) * NatureMath.between(random, 0.5, 1);
        double bendAzimuth = random.nextDouble() * Math.PI * 2;
        double stemTop = type == 1 ? capBottom + 0.5 : height - 1;
        SeededNoise stemNoise = new SeededNoise(seed, 5, 0.25, 1);
        for (int y = 0; y <= stemTop; y++) {
            double t = y / Math.max(1, stemTop);
            double offset = bend * Math.sin(Math.PI * t);
            double cx = Math.cos(bendAzimuth) * offset;
            double cz = Math.sin(bendAzimuth) * offset;
            double r = stemRadius * (1 + 0.45 * Math.pow(1 - t, 3)) * (0.92 + 0.16 * stemNoise.unit(0, y, 0));
            for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
                for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                    double dx = x - cx;
                    double dz = z - cz;
                    if (dx * dx + dz * dz <= r * r + 0.25) {
                        model.set(x, y, z, STEM);
                    }
                }
            }
        }

        // cap: a solid of revolution, hollow for the round and bell types
        boolean hollow = type != 1;
        int firstY = Math.max(0, (int) Math.ceil(capBottom - 0.5));
        for (int y = firstY; y < height; y++) {
            double f = (y + 0.5 - capBottom) / capHeight;
            boolean bottomLayer = y == firstY;
            double outer = profile(type, Math.min(1, f));
            double innerF = (y + 0.5 - capBottom) / Math.max(0.5, capHeight - thickness);
            double inner = hollow && innerF < 1 ? profile(type, innerF) - thickness / radius : -1;
            for (int x = model.getMinX(); x <= model.getMaxX(); x++) {
                for (int z = model.getMinZ(); z <= model.getMaxZ(); z++) {
                    double ux = x / (size.x() + 0.5);
                    double uz = z / (size.z() + 0.5);
                    double rho = Math.sqrt(ux * ux + uz * uz);
                    double angle = Math.atan2(uz, ux);
                    double wobble = 0.9 + 0.1 * rimNoise.unit(Math.cos(angle) * 2, f, Math.sin(angle) * 2);
                    if (rho > outer * wobble) {
                        continue;
                    }
                    if (rho <= inner * wobble) {
                        // inside a hollow cap: only the gills on the bottom layer
                        if (underside && bottomLayer) {
                            model.setIfEmpty(x, y, z, UNDERSIDE);
                        }
                        continue;
                    }
                    boolean rim = rho > outer * wobble - thickness / radius;
                    if (underside && bottomLayer && !rim && model.get(x, y, z) != STEM) {
                        model.set(x, y, z, UNDERSIDE);
                    } else if (model.get(x, y, z) != STEM || !bottomLayer) {
                        model.set(x, y, z, CAP);
                    }
                }
            }
        }
        model.keepLargestPiece();
        return model;
    }

    /**
     * The cap's radius (as a fraction of the full radius) at a height through the cap.
     *
     * @param type the cap type
     * @param f 0 at the rim to 1 at the top
     * @return the radius fraction
     */
    private static double profile(int type, double f) {
        return switch (type) {
            case 1 -> 1 - 0.3 * f * f * f;
            case 2 -> Math.pow(Math.max(0, 1 - Math.pow(f, 1.8)), 0.55) * (0.8 + 0.2 * Math.pow(1 - f, 3));
            default -> Math.sqrt(Math.max(0, 1 - f * f));
        };
    }
}
