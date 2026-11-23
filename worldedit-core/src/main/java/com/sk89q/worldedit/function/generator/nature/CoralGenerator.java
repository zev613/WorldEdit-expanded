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
import com.sk89q.worldedit.math.Vector3;

import java.util.List;
import java.util.Random;

/**
 * Coral: branching (tree-like), brain (a wrinkly ball), fan (a flat lattice) or tubes.
 */
public final class CoralGenerator {

    /**
     * The part number of the coral.
     */
    public static final int CORAL = 1;

    /**
     * The coral types, by number.
     */
    public static final List<String> TYPES = List.of("branching|branch|tree|staghorn", "brain|ball",
        "fan|sea_fan", "tubes|tube|pipe|pipes");

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("type", 0, 0, 3, NamedSettings.describe(TYPES)),
        NatureMath.SEED
    );

    private static final double MIN_RADIUS = 0.87;

    private CoralGenerator() {
    }

    /**
     * Generate coral.
     *
     * @param size the size: x and z are the spread, it is up to {@code 2y} blocks tall
     * @param parameters the settings
     * @param seed the seed
     * @return the model
     */
    public static NatureModel generate(NatureSize size, ShapeParameters parameters, long seed) {
        NatureModel model = size.standingModel();
        Random random = new Random(seed);
        switch (parameters.getInt("type")) {
            case 1 -> brain(model, size, seed);
            case 2 -> fan(model, size, random, seed);
            case 3 -> tubes(model, size, random);
            default -> branching(model, size, random, seed);
        }
        model.keepLargestPiece();
        return model;
    }

    // ---- branching -------------------------------------------------------------------------------

    private static void branching(NatureModel model, NatureSize size, Random random, long seed) {
        double radius = Math.max(MIN_RADIUS, Math.min(1.6, size.minHorizontal() * 0.12));
        SeededNoise[] wander = {
            new SeededNoise(seed, 9, 0.2, 1), new SeededNoise(seed, 10, 0.2, 1), new SeededNoise(seed, 11, 0.2, 1)
        };
        NatureMath.ball(model, Vector3.at(0, 0, 0), radius * 1.6, CORAL);
        Vector3 up = NatureMath.direction(random.nextDouble() * Math.PI * 2, Math.toRadians(80));
        branch(model, size, wander, random, Vector3.ZERO, up, size.height() * 0.3, radius, 0);
    }

    private static void branch(NatureModel model, NatureSize size, SeededNoise[] wander, Random random,
                               Vector3 start, Vector3 direction, double length, double radius, int depth) {
        Vector3 position = start;
        Vector3 heading = direction;
        for (double travelled = 0; travelled < length; travelled += 0.5) {
            NatureMath.ball(model, position, radius, CORAL);
            Vector3 push = Vector3.at(wander[0].signed(position.x(), position.y(), position.z()),
                wander[1].signed(position.x(), position.y(), position.z()),
                wander[2].signed(position.x(), position.y(), position.z()));
            heading = heading.add(push.multiply(0.12)).add(steerInside(position, size)).normalize();
            position = position.add(heading.multiply(0.5));
        }
        if (depth >= 4 || length < 1.5) {
            NatureMath.ball(model, position, radius, CORAL);
            return;
        }
        int children = random.nextDouble() < 0.35 ? 3 : 2;
        double azimuth = random.nextDouble() * Math.PI * 2;
        Vector3[] sides = NatureMath.perpendiculars(heading);
        for (int i = 0; i < children; i++) {
            double around = azimuth + Math.PI * 2 * i / children;
            double spreadAngle = Math.toRadians(NatureMath.between(random, 25, 45));
            Vector3 sideways = sides[0].multiply(Math.cos(around)).add(sides[1].multiply(Math.sin(around)));
            Vector3 child = heading.multiply(Math.cos(spreadAngle)).add(sideways.multiply(Math.sin(spreadAngle)))
                .add(0, 0.15, 0).normalize();
            branch(model, size, wander, random, position, child, length * NatureMath.between(random, 0.65, 0.85),
                Math.max(MIN_RADIUS, radius * 0.85), depth + 1);
        }
    }

    /**
     * A nudge back towards the middle when a branch gets near the edge of the size.
     */
    private static Vector3 steerInside(Vector3 position, NatureSize size) {
        double ux = position.x() / size.x();
        double uz = position.z() / size.z();
        double uy = position.y() / size.height();
        Vector3 nudge = Vector3.ZERO;
        double sideways = Math.sqrt(ux * ux + uz * uz);
        if (sideways > 0.7) {
            nudge = nudge.add(-ux * 0.4, 0, -uz * 0.4);
        }
        if (uy > 0.85) {
            nudge = nudge.add(0, -0.3, 0);
        }
        return nudge;
    }

    // ---- brain -----------------------------------------------------------------------------------

    private static void brain(NatureModel model, NatureSize size, long seed) {
        SeededNoise grooves = new SeededNoise(seed, 12, 0.3, 1);
        SeededNoise lumps = new SeededNoise(seed, 13, 0.15, 2);
        double centerY = size.y() * 0.9;
        double radiusY = size.y() * 0.95;
        for (int x = model.getMinX(); x <= model.getMaxX(); x++) {
            for (int y = model.getMinY(); y <= model.getMaxY(); y++) {
                for (int z = model.getMinZ(); z <= model.getMaxZ(); z++) {
                    double ux = x / (size.x() + 0.5);
                    double uy = (y - centerY) / radiusY;
                    double uz = z / (size.z() + 0.5);
                    double distance = Math.sqrt(ux * ux + uy * uy + uz * uz);
                    double surface = 0.94 + 0.06 * lumps.signed(x, y, z);
                    if (distance > surface) {
                        continue;
                    }
                    // winding grooves near the surface, where the noise crosses zero
                    if (distance > surface - 0.2 && Math.abs(grooves.signed(x, y, z)) < 0.09) {
                        continue;
                    }
                    model.set(x, y, z, CORAL);
                }
            }
        }
    }

    // ---- fan -------------------------------------------------------------------------------------

    private static void fan(NatureModel model, NatureSize size, Random random, long seed) {
        boolean alongX = random.nextBoolean();
        double across = alongX ? size.x() : size.z();
        double tall = size.height() - 0.5;
        SeededNoise ribs = new SeededNoise(seed, 14, 0.25, 1);
        SeededNoise wobble = new SeededNoise(seed, 15, 0.15, 1);
        int baseRays = 5;
        double spacing = 3;
        int reach = (int) Math.ceil(across);
        for (int u = -reach; u <= reach; u++) {
            for (int v = 0; v < size.height(); v++) {
                double eu = u / (across + 0.5);
                double ev = (v + 0.5) / tall;
                if (eu * eu + ev * ev > 1) {
                    continue;
                }
                double rho = Math.hypot(u, v);
                boolean on = rho < 1.6;
                if (!on) {
                    double theta = Math.atan2(v, u) + 0.12 * wobble.signed(u, v, 0);
                    // more rays further out, each level splitting every ray of the one below
                    double ratio = rho * Math.PI / (spacing * baseRays);
                    int level = Math.max(0, (int) Math.floor(Math.log(ratio) / Math.log(2)));
                    int rays = baseRays << Math.min(level, 6);
                    double slot = theta / Math.PI * rays;
                    double nearest = Math.rint(slot);
                    if (nearest > 0 && nearest < rays && Math.abs(slot - nearest) * Math.PI / rays * rho < 0.75) {
                        on = true;
                    }
                    // complete ribs where rays split, broken ribs elsewhere
                    double splitRadius = spacing * baseRays * (1 << Math.max(0, level)) / Math.PI;
                    if (level > 0 && Math.abs(rho - splitRadius) < 0.75) {
                        on = true;
                    }
                    if (Math.abs(rho / spacing - Math.rint(rho / spacing)) * spacing < 0.5
                        && ribs.signed(u, v, 0) > -0.2) {
                        on = true;
                    }
                }
                if (on) {
                    if (alongX) {
                        model.set(u, v, 0, CORAL);
                    } else {
                        model.set(0, v, u, CORAL);
                    }
                }
            }
        }
    }

    // ---- tubes -----------------------------------------------------------------------------------

    private static void tubes(NatureModel model, NatureSize size, Random random) {
        double spread = size.minHorizontal();
        int count = (int) Math.max(3, Math.min(25, Math.round(size.x() * size.z() / 5)));
        double maxRadius = Math.max(1, Math.min(3, spread / 3));
        double furthest = 0;
        for (int i = 0; i < count; i++) {
            double tubeRadius = NatureMath.between(random, 0.9, maxRadius);
            double azimuth = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * Math.max(0, spread * 0.75 - tubeRadius);
            double bx = Math.cos(azimuth) * distance * size.x() / spread;
            double bz = Math.sin(azimuth) * distance * size.z() / spread;
            double height = size.height() * NatureMath.between(random, 0.35, 1.0);
            double leanX = NatureMath.between(random, -0.12, 0.12) * height;
            double leanZ = NatureMath.between(random, -0.12, 0.12) * height;
            furthest = Math.max(furthest, Math.hypot(bx, bz));
            for (int y = 0; y < height; y++) {
                double t = y / height;
                double cx = bx + leanX * t;
                double cz = bz + leanZ * t;
                double r = tubeRadius + (y >= height - 1 ? 0.4 : 0);
                for (int x = (int) Math.floor(cx - r - 1); x <= Math.ceil(cx + r + 1); x++) {
                    for (int z = (int) Math.floor(cz - r - 1); z <= Math.ceil(cz + r + 1); z++) {
                        double d = Math.hypot(x - cx, z - cz);
                        boolean wall = d <= r + 0.5 && (r < 1.5 || d >= r - 0.5 || y == 0);
                        if (wall) {
                            model.set(x, y, z, CORAL);
                        }
                    }
                }
            }
        }
        // a crust joining the tubes at the bottom
        for (int x = model.getMinX(); x <= model.getMaxX(); x++) {
            for (int z = model.getMinZ(); z <= model.getMaxZ(); z++) {
                if (Math.hypot(x, z) <= furthest + 1) {
                    model.set(x, 0, z, CORAL);
                }
            }
        }
    }
}
