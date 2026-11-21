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

package com.sk89q.worldedit.function.generator.parts;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The building parts available to {@code //build}.
 *
 * <p>Every part is placed relative to an origin: the placement position, or the selection's first position
 * for parts that run between two points. Angles start at the direction the player is looking, so the first
 * step of a staircase and the first window of a dome or tower are in front of the player.</p>
 */
public final class BuildingParts {

    private static final double TAU = Math.PI * 2;

    /**
     * A spiral staircase around a central pole.
     */
    public static final BuildingPart SPIRAL_STAIR = new BuildingPart("spiralstair",
        List.of("spiralstairs", "spiral"),
        "A spiral staircase around a pole. Add -s <stair block> for stairs on the front edge of each step",
        List.of(
            new ShapeParameter("radius", 4, 2, 32, "how far the steps reach from the pole"),
            new ShapeParameter("height", 16, 2, 384, "the number of steps (one block up each)"),
            new ShapeParameter("turns", 1, 0.25, 32, "how many times it goes around"),
            new ShapeParameter("pole", 1, 0, 1, "1 for a central pole, 0 for none"),
            new ShapeParameter("clockwise", 1, 0, 1, "1 to climb clockwise (seen from above), 0 for anticlockwise")),
        false, "", "radius=5 height=24 turns=1.5", BuildingParts::spiralStair);

    /**
     * A dome with optional windows.
     */
    public static final BuildingPart DOME = new BuildingPart("dome", List.of("cupola"),
        "A half-sphere dome, with optional windows round the bottom (filled with -w, default glass)",
        List.of(
            new ShapeParameter("radius", 10, 2, 64, "the radius in blocks"),
            new ShapeParameter("thickness", 1, 1, 16, "how thick the shell is"),
            new ShapeParameter("windows", 0, 0, 64, "the number of windows round the bottom")),
        false, "glass", "radius=12 windows=8", BuildingParts::dome);

    /**
     * A round tower.
     */
    public static final BuildingPart TOWER = new BuildingPart("tower", List.of("turret"),
        "A round tower with floors, windows (filled with -w, default glass) and battlements",
        List.of(
            new ShapeParameter("radius", 4, 2, 48, "the outside radius"),
            new ShapeParameter("height", 20, 3, 384, "the height of the walls"),
            new ShapeParameter("battlements", 1, 0, 1, "1 for a roof with battlements, 0 for an open top"),
            new ShapeParameter("windows", 4, 0, 32, "windows per floor"),
            new ShapeParameter("floors", 5, 0, 64, "blocks between floors (0 for no floors)"),
            new ShapeParameter("thickness", 1, 1, 8, "how thick the wall is")),
        false, "glass", "radius=5 height=24 windows=6", BuildingParts::tower);

    /**
     * A castle wall between the selection's two positions.
     */
    public static final BuildingPart WALL = new BuildingPart("wall", List.of("castlewall"),
        "A wall from your first to your second position (//pos1, //pos2), following the slope between them",
        List.of(
            new ShapeParameter("height", 6, 1, 256, "the height above the ground line"),
            new ShapeParameter("thickness", 2, 1, 16, "how thick it is"),
            new ShapeParameter("crenellations", 1, 0, 1, "1 for crenellations along the top, 0 for a flat top")),
        true, "", "height=8 thickness=3", BuildingParts::wall);

    /**
     * A column with a base and capital.
     */
    public static final BuildingPart COLUMN = new BuildingPart("column", List.of("pillar"),
        "A round column with a wider base and capital",
        List.of(
            new ShapeParameter("radius", 1, 1, 16, "the radius of the shaft"),
            new ShapeParameter("height", 10, 5, 384, "the total height")),
        false, "", "radius=2 height=14", BuildingParts::column);

    /**
     * A rose window.
     */
    public static final BuildingPart ROUND_WINDOW = new BuildingPart("roundwindow", List.of("rosewindow", "rose"),
        "A round rose window standing upright and facing you, with spokes and panes (filled with -w, default glass)",
        List.of(
            new ShapeParameter("radius", 6, 2, 64, "the radius in blocks"),
            new ShapeParameter("spokes", 8, 0, 48, "the number of spokes"),
            new ShapeParameter("depth", 1, 1, 8, "how thick it is"),
            new ShapeParameter("ring", 1, 0, 1, "1 for an inner ring of tracery (radius 6 and up), 0 for none")),
        false, "glass", "radius=8 spokes=12", BuildingParts::roundWindow);

    /**
     * An arched bridge between the selection's two positions.
     */
    public static final BuildingPart BRIDGE = new BuildingPart("bridge", List.of("archbridge"),
        "An arched bridge from your first to your second position (//pos1, //pos2). Railings use -w if given",
        List.of(
            new ShapeParameter("width", 3, 1, 32, "the width of the deck"),
            new ShapeParameter("arch", 4, 0, 128, "how much higher the middle is than the ends"),
            new ShapeParameter("railings", 1, 0, 1, "1 for railings, 0 for none"),
            new ShapeParameter("thickness", 1, 1, 8, "how thick the deck is")),
        true, "", "width=5 arch=6", BuildingParts::bridge);

    private static final List<BuildingPart> ALL = ImmutableList.of(
        SPIRAL_STAIR, DOME, TOWER, WALL, COLUMN, ROUND_WINDOW, BRIDGE);

    private BuildingParts() {
    }

    /**
     * All building parts.
     *
     * @return the parts
     */
    public static List<BuildingPart> all() {
        return ALL;
    }

    /**
     * Find a part by name or alias.
     *
     * @param name the name
     * @return the part, if any
     */
    public static Optional<BuildingPart> find(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return ALL.stream().filter(part -> part.name().equals(key) || part.aliases().contains(key)).findFirst();
    }

    private static int round(double value) {
        return (int) Math.floor(value + 0.5);
    }

    private static double mod(double value, double modulus) {
        double result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    /**
     * The angle (radians, from east towards south) of a horizontal direction.
     */
    private static double angleOf(Direction facing) {
        BlockVector3 vector = facing.toBlockVector();
        return Math.atan2(vector.z(), vector.x());
    }

    /**
     * The cardinal direction nearest to a horizontal vector.
     *
     * @param x the east part
     * @param z the south part
     * @return the direction
     */
    static Direction cardinal(double x, double z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0 ? Direction.EAST : Direction.WEST;
        }
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean inside(double x, double y, double z, double radius) {
        return x * x + y * y + z * z <= radius * radius;
    }

    /**
     * Whether a block and its six neighbours are all inside a sphere.
     */
    private static boolean deepInside(int x, int y, int z, double radius) {
        return inside(x, y, z, radius) && inside(x + 1, y, z, radius) && inside(x - 1, y, z, radius)
            && inside(x, y + 1, z, radius) && inside(x, y - 1, z, radius)
            && inside(x, y, z + 1, radius) && inside(x, y, z - 1, radius);
    }

    /**
     * Whether a column and its four neighbours are all inside a circle.
     */
    private static boolean deepInside(int x, int z, double radius) {
        return inside(x, 0, z, radius) && inside(x + 1, 0, z, radius) && inside(x - 1, 0, z, radius)
            && inside(x, 0, z + 1, radius) && inside(x, 0, z - 1, radius);
    }

    private static void spiralStair(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int radius = settings.getInt("radius");
        int height = settings.getInt("height");
        double turns = settings.get("turns");
        boolean pole = settings.getInt("pole") != 0;
        boolean clockwise = settings.getInt("clockwise") != 0;
        double perStep = turns * TAU / height;
        double start = angleOf(context.facing());
        int maxTurns = (int) Math.ceil(turns) + 1;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.hypot(x, z);
                if (distance > radius + 0.5 || (x == 0 && z == 0)) {
                    continue;
                }
                double world = Math.atan2(z, x);
                // angles grow east -> south, which is clockwise seen from above
                double along = mod(clockwise ? world - start : start - world, TAU);
                // the way up, as a horizontal vector
                double tangentX = clockwise ? -Math.sin(world) : Math.sin(world);
                double tangentZ = clockwise ? Math.cos(world) : -Math.cos(world);
                for (int turn = 0; turn <= maxTurns; turn++) {
                    double position = (along + turn * TAU) / perStep;
                    int step = (int) Math.floor(position);
                    if (step >= height) {
                        break;
                    }
                    // the front half of each step (where you come up from the step below) is the tread edge
                    boolean edge = position - step < 0.5 && distance > 1.2;
                    plan.put(x, step, z, edge ? BuildBlock.stair(cardinal(tangentX, tangentZ)) : BuildBlock.MAIN);
                }
            }
        }
        if (pole) {
            for (int y = 0; y <= height; y++) {
                plan.put(0, y, 0, BuildBlock.MAIN);
            }
        }
    }

    private static void dome(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int radius = settings.getInt("radius");
        int thickness = settings.getInt("thickness");
        int windows = settings.getInt("windows");
        double outer = radius + 0.5;
        // a block is part of the shell unless it and its neighbours are all deeper than the thickness
        double inner = outer - (thickness - 1);
        int windowWidth = Math.max(1, round(radius * 0.2));
        int windowHeight = Math.max(2, round(radius * 0.35));
        double start = angleOf(context.facing());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= radius; y++) {
                    if (!inside(x, y, z, outer) || deepInside(x, y, z, inner)) {
                        continue;
                    }
                    boolean window = false;
                    if (windows > 0 && y >= 1 && y <= windowHeight && y < radius) {
                        double spacing = TAU / windows;
                        double offset = mod(Math.atan2(z, x) - start + spacing / 2, spacing) - spacing / 2;
                        window = Math.abs(offset) * Math.hypot(x, z) <= windowWidth / 2.0 + 0.1;
                    }
                    plan.put(x, y, z, window ? BuildBlock.DETAIL : BuildBlock.MAIN);
                }
            }
        }
    }

    private static void tower(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int radius = settings.getInt("radius");
        int height = settings.getInt("height");
        boolean battlements = settings.getInt("battlements") != 0;
        int windows = settings.getInt("windows");
        int floors = settings.getInt("floors");
        int thickness = settings.getInt("thickness");
        if (thickness >= radius) {
            throw new IllegalArgumentException("The wall thickness must be less than the radius");
        }
        double outer = radius + 0.5;
        // a block is part of the shell unless it and its neighbours are all deeper than the thickness
        double inner = outer - (thickness - 1);
        double start = angleOf(context.facing());
        int roofY = battlements ? height - 1 : height;
        int windowSpacing = floors > 0 ? floors : 5;
        int hatch = radius - thickness;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!inside(x, 0, z, outer)) {
                    continue;
                }
                boolean wall = !deepInside(x, z, inner);
                double angle = Math.atan2(z, x) - start;
                if (wall) {
                    for (int y = 0; y < height; y++) {
                        boolean window = false;
                        if (windows > 0 && y < roofY) {
                            int base = (y / windowSpacing) * windowSpacing;
                            int low = base + Math.min(2, Math.max(1, windowSpacing - 1));
                            int high = Math.max(low, Math.min(base + 3, base + windowSpacing - 2));
                            if (y >= low && y <= high && y <= height - 2) {
                                double spacing = TAU / windows;
                                double offset = mod(angle + spacing / 2, spacing) - spacing / 2;
                                window = Math.abs(offset) * radius <= 0.6;
                            }
                        }
                        plan.put(x, y, z, window ? BuildBlock.DETAIL : BuildBlock.MAIN);
                    }
                    if (battlements) {
                        int segment = (int) Math.floor(mod(angle, TAU) * radius / 2);
                        if (segment % 2 == 0) {
                            plan.put(x, height, z, BuildBlock.MAIN);
                        }
                    }
                } else {
                    for (int y = 0; y < height; y++) {
                        boolean roof = y == roofY;
                        boolean floor = floors > 0 && y % floors == 0 && y < roofY;
                        if (!roof && !floor) {
                            continue;
                        }
                        int level = floors > 0 ? y / floors : 1;
                        // leave a hole by the wall to climb through, alternating sides
                        boolean isHatch = y > 0 && hatch >= 1 && z == 0 && x == (level % 2 == 1 ? hatch : -hatch);
                        if (!isHatch) {
                            plan.put(x, y, z, BuildBlock.MAIN);
                        }
                    }
                }
            }
        }
    }

    private static BlockVector3 requireLength(BuildingPart.Context context, String what) {
        BlockVector3 end = context.end();
        if (Math.hypot(end.x(), end.z()) < 1) {
            throw new IllegalArgumentException("Select the two ends of the " + what
                + " with //pos1 and //pos2 (at least one block apart, not straight above each other)");
        }
        return end;
    }

    private static void wall(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int height = settings.getInt("height");
        int thickness = settings.getInt("thickness");
        boolean crenellations = settings.getInt("crenellations") != 0;
        BlockVector3 end = requireLength(context, "wall");
        double length = Math.hypot(end.x(), end.z());
        double normalX = -end.z() / length;
        double normalZ = end.x() / length;
        int steps = (int) Math.ceil(length * 2);
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double centreX = end.x() * t;
            double centreZ = end.z() * t;
            int base = round(end.y() * t);
            boolean merlon = crenellations && Math.floorMod(round(t * length), 2) == 0;
            for (int k = 0; k < thickness * 2; k++) {
                double offset = -thickness / 2.0 + 0.25 + k * 0.5;
                int x = round(centreX + normalX * offset);
                int z = round(centreZ + normalZ * offset);
                for (int y = 0; y < height; y++) {
                    plan.put(x, base + y, z, BuildBlock.MAIN);
                }
                if (merlon) {
                    plan.put(x, base + height, z, BuildBlock.MAIN);
                }
            }
        }
    }

    private static void column(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int radius = settings.getInt("radius");
        int height = settings.getInt("height");
        int wide = radius + 1;
        for (int y = 0; y < height; y++) {
            boolean slab = y == 0 || y == height - 1;
            boolean ring = y == 1 || y == height - 2;
            for (int x = -wide; x <= wide; x++) {
                for (int z = -wide; z <= wide; z++) {
                    boolean in;
                    if (slab) {
                        in = true;
                    } else if (ring) {
                        in = inside(x, 0, z, wide + 0.5);
                    } else {
                        in = inside(x, 0, z, radius + 0.5);
                    }
                    if (in) {
                        plan.put(x, y, z, BuildBlock.MAIN);
                    }
                }
            }
        }
    }

    private static void roundWindow(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int radius = settings.getInt("radius");
        int spokes = settings.getInt("spokes");
        int depth = settings.getInt("depth");
        boolean ring = settings.getInt("ring") != 0 && radius >= 6;
        FacingPlane plane = new FacingPlane(context.facing(), false);
        double hub = Math.max(0.5, radius / 6.0);
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                double distance = Math.hypot(u, v);
                if (distance > radius + 0.5) {
                    continue;
                }
                boolean frame = distance > radius - 0.5 || distance <= hub
                    || (ring && Math.abs(distance - radius * 0.55) < 0.5);
                for (int k = 0; k < spokes && !frame; k++) {
                    double angle = Math.PI / 2 + k * TAU / spokes;
                    double along = u * Math.cos(angle) + v * Math.sin(angle);
                    double across = -u * Math.sin(angle) + v * Math.cos(angle);
                    frame = along > 0 && Math.abs(across) <= 0.5;
                }
                for (int w = 0; w < depth; w++) {
                    plan.put(plane.map(u, v, w), frame ? BuildBlock.MAIN : BuildBlock.DETAIL);
                }
            }
        }
    }

    private static void bridge(ShapeParameters settings, BuildingPart.Context context, BuildPlan plan) {
        int width = settings.getInt("width");
        double arch = settings.get("arch");
        boolean railings = settings.getInt("railings") != 0;
        int thickness = settings.getInt("thickness");
        BlockVector3 end = requireLength(context, "bridge");
        double length = Math.hypot(end.x(), end.z());
        double normalX = -end.z() / length;
        double normalZ = end.x() / length;
        int steps = (int) Math.ceil(length * 2);
        int previous = 0;
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double centreX = end.x() * t;
            double centreZ = end.z() * t;
            int deck = round(end.y() * t + arch * 4 * t * (1 - t));
            for (int k = 0; k < width * 2; k++) {
                double offset = -width / 2.0 + 0.25 + k * 0.5;
                int x = round(centreX + normalX * offset);
                int z = round(centreZ + normalZ * offset);
                for (int th = 0; th < thickness; th++) {
                    plan.put(x, deck - th, z, BuildBlock.MAIN);
                }
                // fill steep jumps so the deck has no gaps
                if (s > 0 && Math.abs(deck - previous) > 1) {
                    for (int y = Math.min(deck, previous) + 1; y < Math.max(deck, previous); y++) {
                        plan.put(x, y, z, BuildBlock.MAIN);
                    }
                }
            }
            if (railings) {
                for (int side = -1; side <= 1; side += 2) {
                    double offset = side * (width / 2.0 + 0.5);
                    int x = round(centreX + normalX * offset);
                    int z = round(centreZ + normalZ * offset);
                    plan.putIfAbsent(x, deck, z, BuildBlock.MAIN);
                    plan.putIfAbsent(x, deck + 1, z, BuildBlock.DETAIL);
                }
            }
            previous = deck;
        }
    }
}
