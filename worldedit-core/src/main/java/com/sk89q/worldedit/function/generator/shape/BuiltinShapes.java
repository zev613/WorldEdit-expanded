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

package com.sk89q.worldedit.function.generator.shape;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.noise.PerlinNoise;
import com.sk89q.worldedit.math.noise.RidgedMultiFractalNoise;

import java.util.List;

/**
 * The shapes that come with WorldEdit. Every option is a plain number, so
 * players never have to write a formula.
 */
public final class BuiltinShapes {

    public static final String SOLIDS = "Solids";
    public static final String BUILDING = "Building";
    public static final String FRACTALS = "Fractals";
    public static final String LATTICES = "Lattices";
    public static final String NATURE = "Nature";
    public static final String CURVES = "Knots and curves";
    public static final String SURFACES = "Surfaces";

    private static final double TAU = Math.PI * 2;
    private static final double PHI = (1 + Math.sqrt(5)) / 2;

    private BuiltinShapes() {
    }

    /**
     * Creates shape instances.
     */
    @FunctionalInterface
    private interface Factory {
        ShapeInstance create(ShapeParameters p, Vector3 radii);
    }

    private static final class Simple extends NamedShape {
        private final Factory factory;

        Simple(String name, List<String> aliases, String category, String description,
               List<ShapeParameter> parameters, Factory factory) {
            super(name, aliases, category, description, parameters);
            this.factory = factory;
        }

        @Override
        public ShapeInstance create(ShapeParameters parameters, Vector3 radii) {
            return factory.create(parameters, radii);
        }
    }

    private static NamedShape shape(String name, List<String> aliases, String category, String description,
                                    List<ShapeParameter> parameters, Factory factory) {
        return new Simple(name, aliases, category, description, parameters, factory);
    }

    private static ShapeParameter param(String name, double def, double min, double max, String description) {
        return new ShapeParameter(name, def, min, max, description);
    }

    private static ShapeParameter tube(String what) {
        return param("tube", 0, 0, 64, "Radius of the " + what + " in blocks (0 = automatic)");
    }

    private static ShapeParameter thickness() {
        return param("thickness", 1, 1, 32, "Thickness of the surface in blocks");
    }

    /** Automatic tube radius: a tenth of the average size, at least one block. */
    private static double autoTube(double value, Vector3 radii, double fraction) {
        if (value > 0) {
            return value;
        }
        double average = (radii.x() + radii.y() + radii.z()) / 3;
        return Math.max(0.5, average * fraction);
    }

    private static double surfaceRadius(double thickness) {
        // 0.75 keeps one-block-thick surfaces free of pinholes
        return Math.max(0.75, thickness / 2);
    }

    /**
     * Create the list of built-in shapes.
     *
     * @return the shapes
     */
    public static List<NamedShape> create() {
        ImmutableList.Builder<NamedShape> shapes = ImmutableList.builder();
        addSolids(shapes);
        addRings(shapes);
        addBuilding(shapes);
        addFractals(shapes);
        addLattices(shapes);
        addNature(shapes);
        addCurves(shapes);
        addSurfaces(shapes);
        return shapes.build();
    }

    // ------------------------------------------------------------------ solids

    private static void addSolids(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("sphere", List.of("ball"), SOLIDS,
            "A ball (give three sizes such as 8,5,8 for an egg-like ellipsoid)",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> x * x + y * y + z * z <= 1));
        shapes.add(shape("cube", List.of("box", "cuboid"), SOLIDS,
            "A box (give three sizes such as 6,3,4 for any cuboid)",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (_, _, _) -> true));
        shapes.add(shape("cylinder", List.of("cyl"), SOLIDS,
            "An upright cylinder (give sizes such as 4,10,4 for a tall one; tilt it with -t)",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, _, z) -> x * x + z * z <= 1));
        shapes.add(shape("cone", List.of(), SOLIDS,
            "A cone standing on its round base, point up",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> {
                double radius = (1 - y) / 2;
                return x * x + z * z <= radius * radius;
            }));
        shapes.add(shape("pyramid", List.of(), SOLIDS,
            "A pyramid with a square base, point up",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> {
                double half = (1 - y) / 2;
                return Math.abs(x) <= half && Math.abs(z) <= half;
            }));

        shapes.add(shape("heart", List.of("love"), SOLIDS,
            "A 3D heart, facing north/south (turn it with -r, or use -f to face you)",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> {
                double s = 1.136;
                double hx = x * s;
                double hd = z * s;
                double hu = y * s + 0.116;
                double a = hx * hx + 2.25 * hd * hd + hu * hu - 1;
                double hu3 = hu * hu * hu;
                return a * a * a - hx * hx * hu3 - 0.1125 * hd * hd * hu3 <= 0;
            }));

        shapes.add(shape("star", List.of(), SOLIDS,
            "A star standing upright, facing north/south",
            List.of(
                param("points", 5, 3, 32, "Number of points"),
                param("inner", 0.45, 0.05, 0.95, "How deep the gaps between the points go (smaller = spikier)"),
                param("depth", 0.25, 0.02, 1, "Thickness, as a fraction of the size"),
                param("puff", 0, 0, 1, "0 = flat slab, 1 = rounded like a cushion")),
            (p, r) -> {
                int points = p.getInt("points");
                double inner = p.get("inner");
                double depth = p.get("depth");
                double puff = p.get("puff");
                double sector = TAU / points;
                double half = sector / 2;
                // the edge from the outer tip (1, 0) to the inner corner
                double ax = 1;
                double ay = 0;
                double bx = inner * Math.cos(half);
                double by = inner * Math.sin(half);
                double ex = bx - ax;
                double ey = by - ay;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double radius = Math.hypot(x, y);
                    if (radius > 1) {
                        return false;
                    }
                    double boundary;
                    if (radius < 1e-9) {
                        boundary = 1;
                    } else {
                        double angle = Math.atan2(y, x) - Math.PI / 2;
                        double phi = angle - sector * Math.floor(angle / sector);
                        if (phi > half) {
                            phi = sector - phi;
                        }
                        double dx = Math.cos(phi);
                        double dy = Math.sin(phi);
                        // ray t*d meets segment a + s*e
                        double cross = dx * ey - dy * ex;
                        boundary = cross == 0 ? 1 : (ax * ey - ay * ex) / cross;
                    }
                    if (radius > boundary) {
                        return false;
                    }
                    double allowed = depth * (1 - puff * radius / boundary);
                    return Math.abs(z) <= Math.max(allowed, 0.5 / Math.max(1, r.z()));
                };
            }));

        shapes.add(polyhedron("tetrahedron", List.of("tetra"),
            "A pyramid with four triangular faces",
            tetrahedronVertices(), 1.0 / 3));
        shapes.add(shape("octahedron", List.of("octa"), SOLIDS,
            "Two square pyramids joined at the base (a 3D diamond)",
            List.of(), (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> Math.abs(x) + Math.abs(y) + Math.abs(z) <= 1));
        shapes.add(polyhedron("dodecahedron", List.of("dodeca", "d12"),
            "A solid with 12 pentagon faces",
            icosahedronVertices(), 0.7946544722917661));
        shapes.add(polyhedron("icosahedron", List.of("icosa", "d20"),
            "A solid with 20 triangle faces",
            dodecahedronVertices(), 0.7946544722917661));

        shapes.add(shape("superellipsoid", List.of("roundcube", "superball", "squircle"), SOLIDS,
            "Anything from a spiky star (power below 1) through a sphere (2) to a rounded cube (4+)",
            List.of(
                param("power", 4, 0.3, 30, "Roundness: 1 = octahedron, 2 = sphere, higher = boxier"),
                param("vpower", 0, 0, 30, "Roundness of the top and bottom (0 = same as power)")),
            (p, _) -> {
                double e = p.get("power");
                double ev = p.get("vpower") > 0 ? p.get("vpower") : e;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double horizontal = Math.pow(Math.pow(Math.abs(x), e) + Math.pow(Math.abs(z), e), ev / e);
                    return horizontal + Math.pow(Math.abs(y), ev) <= 1;
                };
            }));

        shapes.add(shape("egg", List.of(), SOLIDS,
            "An egg, narrower at the top",
            List.of(param("pointy", 0.25, 0, 0.6, "How much narrower the top is")),
            (p, _) -> {
                double k = p.get("pointy");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double profile = (1 - y * y) * (1 - k * y) * (1 - k * y) * 0.72;
                    return x * x + z * z <= profile;
                };
            }));

        shapes.add(shape("crescent", List.of("moon"), SOLIDS,
            "A crescent moon: a ball with a bite taken out of the side",
            List.of(param("bite", 0.5, 0.1, 1.5, "How far the bite is from the centre (smaller = thinner crescent)")),
            (p, _) -> {
                double offset = p.get("bite");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    if (x * x + y * y + z * z > 1) {
                        return false;
                    }
                    double bx = x - offset;
                    return bx * bx + y * y + z * z > 0.9;
                };
            }));
    }

    private static void addRings(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("torus", List.of("donut", "ring"), SOLIDS,
            "A donut lying flat (tilt it with -t)",
            List.of(param("ring", 0.25, 0.02, 0.5, "Radius of the tube compared to the whole donut")),
            (p, _) -> {
                double minor = p.get("ring");
                double major = 1 - minor;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double d = major - Math.hypot(x, z);
                    return d * d + (y * y) <= minor * minor;
                };
            }));

        shapes.add(shape("helix", List.of("spring", "corkscrew"), CURVES,
            "A spring or spiral going up. Make it taller than wide, e.g. 10,30,10",
            List.of(
                param("turns", 3, 0.25, 200, "Number of turns"),
                param("strands", 1, 1, 12, "Number of strands twisted together"),
                tube("strand")),
            (p, r) -> {
                double turns = p.get("turns");
                int strands = p.getInt("strands");
                double radius = autoTube(p.get("tube"), Vector3.at(r.x(), Math.min(r.x(), r.z()), r.z()), 0.12);
                return (ShapeInstance.Swept) tracer -> {
                    for (int i = 0; i < strands; i++) {
                        double offset = TAU * i / strands;
                        tracer.curve((t, out) -> {
                            out[0] = Math.cos(TAU * turns * t + offset);
                            out[1] = t * 2 - 1;
                            out[2] = Math.sin(TAU * turns * t + offset);
                        }, 0, 1, radius);
                    }
                };
            }));
    }

    private static NamedShape polyhedron(String name, List<String> aliases, String description,
                                         double[][] faceNormals, double inradius) {
        double[][] normals = new double[faceNormals.length][];
        for (int i = 0; i < faceNormals.length; i++) {
            double[] n = faceNormals[i];
            double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            normals[i] = new double[] {n[0] / length, n[1] / length, n[2] / length};
        }
        return shape(name, aliases, SOLIDS, description, List.of(),
            (_, _) -> (ShapeInstance.Implicit) (x, y, z) -> {
                for (double[] n : normals) {
                    if (n[0] * x + n[1] * y + n[2] * z > inradius + 1e-9) {
                        return false;
                    }
                }
                return true;
            });
    }

    /** Outward face normals of a tetrahedron with one vertex pointing up and a flat base. */
    private static double[][] tetrahedronVertices() {
        // face opposite vertex v has outward normal -v
        double base = Math.sqrt(8.0 / 9);
        double[][] vertices = {
            {0, 1, 0},
            {base, -1.0 / 3, 0},
            {base * Math.cos(TAU / 3), -1.0 / 3, base * Math.sin(TAU / 3)},
            {base * Math.cos(2 * TAU / 3), -1.0 / 3, base * Math.sin(2 * TAU / 3)},
        };
        double[][] normals = new double[4][];
        for (int i = 0; i < 4; i++) {
            normals[i] = new double[] {-vertices[i][0], -vertices[i][1], -vertices[i][2]};
        }
        return normals;
    }

    private static double[][] icosahedronVertices() {
        return new double[][] {
            {0, 1, PHI}, {0, 1, -PHI}, {0, -1, PHI}, {0, -1, -PHI},
            {1, PHI, 0}, {1, -PHI, 0}, {-1, PHI, 0}, {-1, -PHI, 0},
            {PHI, 0, 1}, {PHI, 0, -1}, {-PHI, 0, 1}, {-PHI, 0, -1},
        };
    }

    private static double[][] dodecahedronVertices() {
        double inv = 1 / PHI;
        ImmutableList.Builder<double[]> list = ImmutableList.builder();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                list.add(new double[] {sx, sy, 1});
                list.add(new double[] {sx, sy, -1});
                list.add(new double[] {0, sx * inv, sy * PHI});
                list.add(new double[] {sx * inv, sy * PHI, 0});
                list.add(new double[] {sx * PHI, 0, sy * inv});
            }
        }
        return list.build().toArray(new double[0][]);
    }

    // ---------------------------------------------------------------- building

    private static void addBuilding(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("arch", List.of(), BUILDING,
            "A round arch standing on the ground, running east-west, as deep as the north-south size",
            List.of(param("thickness", 0.3, 0.05, 1, "Thickness of the arch, as a fraction of its width")),
            (p, _) -> {
                double inner = 1 - p.get("thickness");
                return (ShapeInstance.Implicit) (x, y, _) -> {
                    double h = (y + 1) / 2;
                    double d = x * x + h * h;
                    return d <= 1 && d > inner * inner;
                };
            }));

        shapes.add(shape("tower", List.of("twist", "prism"), BUILDING,
            "A tower with flat sides that can twist and narrow as it goes up",
            List.of(
                param("sides", 4, 3, 64, "Number of sides"),
                param("twist", 90, -3600, 3600, "How many degrees it turns from bottom to top (0 = straight)"),
                param("taper", 0.3, 0, 1, "How much narrower the top is (0 = same width, 1 = comes to a point)")),
            (p, _) -> {
                int sides = p.getInt("sides");
                double twist = Math.toRadians(p.get("twist"));
                double taper = p.get("taper");
                double sector = TAU / sides;
                double apothem = Math.cos(Math.PI / sides);
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double h = (y + 1) / 2;
                    double scale = 1 - taper * h;
                    double radius = Math.hypot(x, z);
                    if (radius > scale) {
                        return false;
                    }
                    double angle = Math.atan2(z, x) - twist * h + Math.PI / sides;
                    double phi = angle - sector * Math.floor(angle / sector);
                    return radius * Math.cos(phi - Math.PI / sides) <= scale * apothem;
                };
            }));

        shapes.add(shape("hourglass", List.of(), BUILDING,
            "An hourglass: wide at the top and bottom with straight sides meeting at a narrow neck",
            List.of(param("neck", 0.15, 0, 1, "Width of the neck compared to the ends"),
                param("sides", 0, 0, 64, "Number of flat sides (0 = round)")),
            (p, _) -> {
                double neck = p.get("neck");
                int sides = p.getInt("sides");
                double sector = sides >= 3 ? TAU / sides : 0;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double width = neck + (1 - neck) * Math.abs(y);
                    double radius = Math.hypot(x, z);
                    if (sides < 3) {
                        return radius <= width;
                    }
                    double angle = Math.atan2(z, x) + Math.PI / sides;
                    double phi = angle - sector * Math.floor(angle / sector);
                    return radius * Math.cos(phi - Math.PI / sides) <= width * Math.cos(Math.PI / sides);
                };
            }));

        shapes.add(shape("hyperboloid", List.of("coolingtower"), BUILDING,
            "A cooling-tower shape: wide at the top and bottom, pinched in the middle",
            List.of(
                param("waist", 0.6, 0.05, 1, "Width of the middle compared to the ends"),
                param("height", 0.5, 0, 1, "Where the waist is: 0 = bottom, 0.5 = middle, 1 = top")),
            (p, _) -> {
                double waist = p.get("waist");
                double at = p.get("height") * 2 - 1;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double span = y >= at ? 1 - at : 1 + at;
                    double t = span <= 0 ? 0 : (y - at) / span;
                    return x * x + z * z <= waist * waist + (1 - waist * waist) * t * t;
                };
            }));
    }

    // ---------------------------------------------------------------- fractals

    private static void addFractals(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("mandelbulb", List.of("bulb"), FRACTALS,
            "The 3D Mandelbrot fractal. Looks best at size 30 or more",
            List.of(
                param("power", 8, 2, 16, "Number of lobes (8 is the classic one)"),
                param("detail", 8, 1, 30, "How much fine detail")),
            (p, _) -> {
                double power = p.get("power");
                int iterations = p.getInt("detail");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double cx = x * 1.2;
                    double cy = z * 1.2;
                    double cz = y * 1.2;
                    double zx = cx;
                    double zy = cy;
                    double zz = cz;
                    for (int i = 0; i < iterations; i++) {
                        double rad = Math.sqrt(zx * zx + zy * zy + zz * zz);
                        if (rad > 2) {
                            return false;
                        }
                        double theta = Math.acos(rad == 0 ? 0 : zz / rad) * power;
                        double phi = Math.atan2(zy, zx) * power;
                        double rp = Math.pow(rad, power);
                        zx = rp * Math.sin(theta) * Math.cos(phi) + cx;
                        zy = rp * Math.sin(theta) * Math.sin(phi) + cy;
                        zz = rp * Math.cos(theta) + cz;
                    }
                    return zx * zx + zy * zy + zz * zz <= 4;
                };
            }));

        shapes.add(shape("menger", List.of("sponge", "mengersponge"), FRACTALS,
            "A cube with holes punched through it, and smaller holes in the rest, and so on",
            List.of(param("level", 3, 0, 6, "How many times to punch holes")),
            (p, _) -> {
                int level = p.getInt("level");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double ux = Math.min((x + 1) / 2, 0.999999);
                    double uy = Math.min((y + 1) / 2, 0.999999);
                    double uz = Math.min((z + 1) / 2, 0.999999);
                    for (int i = 0; i < level; i++) {
                        ux *= 3;
                        uy *= 3;
                        uz *= 3;
                        int dx = (int) ux;
                        int dy = (int) uy;
                        int dz = (int) uz;
                        int middles = (dx == 1 ? 1 : 0) + (dy == 1 ? 1 : 0) + (dz == 1 ? 1 : 0);
                        if (middles >= 2) {
                            return false;
                        }
                        ux -= dx;
                        uy -= dy;
                        uz -= dz;
                    }
                    return true;
                };
            }));

        double[][] vertices = {
            {0, 1, 0},
            {Math.sqrt(8.0 / 9), -1.0 / 3, 0},
            {Math.sqrt(8.0 / 9) * Math.cos(TAU / 3), -1.0 / 3, Math.sqrt(8.0 / 9) * Math.sin(TAU / 3)},
            {Math.sqrt(8.0 / 9) * Math.cos(2 * TAU / 3), -1.0 / 3, Math.sqrt(8.0 / 9) * Math.sin(2 * TAU / 3)},
        };
        shapes.add(shape("sierpinski", List.of("sierpinskipyramid", "triforce"), FRACTALS,
            "A pyramid made of smaller pyramids, made of smaller pyramids...",
            List.of(param("level", 3, 0, 10, "How many times to divide (use bigger sizes for higher levels)")),
            (p, _) -> {
                int level = p.getInt("level");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double px = x;
                    double py = y;
                    double pz = z;
                    for (int i = 0; i < level; i++) {
                        double best = Double.MAX_VALUE;
                        double[] nearest = vertices[0];
                        for (double[] v : vertices) {
                            double dx = px - v[0];
                            double dy = py - v[1];
                            double dz = pz - v[2];
                            double d = dx * dx + dy * dy + dz * dz;
                            if (d < best) {
                                best = d;
                                nearest = v;
                            }
                        }
                        px = 2 * px - nearest[0];
                        py = 2 * py - nearest[1];
                        pz = 2 * pz - nearest[2];
                    }
                    for (double[] v : vertices) {
                        if (-(v[0] * px + v[1] * py + v[2] * pz) > 1.0 / 3 + 1e-9) {
                            return false;
                        }
                    }
                    return true;
                };
            }));
    }

    // ---------------------------------------------------------------- lattices

    /** A triply periodic surface such as the gyroid. */
    @FunctionalInterface
    private interface Periodic {
        double value(double x, double y, double z);
    }

    private static NamedShape lattice(String name, List<String> aliases, String description, double defaultWall,
                                      Periodic function) {
        return shape(name, aliases, LATTICES, description,
            List.of(
                param("cell", 16, 3, 512, "Size of one repeating cell in blocks"),
                param("wall", defaultWall, 0.02, 2, "Wall thickness (bigger = thicker walls, smaller holes)"),
                param("ball", 0, 0, 1, "1 = trim to a ball instead of a box")),
            (p, r) -> {
                double scale = TAU / p.get("cell");
                double wall = p.get("wall");
                boolean ball = p.get("ball") >= 0.5;
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    if (ball && x * x + y * y + z * z > 1) {
                        return false;
                    }
                    double v = function.value(x * r.x() * scale, y * r.y() * scale, z * r.z() * scale);
                    return Math.abs(v) <= wall;
                };
            });
    }

    private static void addLattices(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(lattice("gyroid", List.of(), "A twisting, cave-like lattice of connected tunnels", 0.3,
            (x, y, z) -> Math.sin(x) * Math.cos(y) + Math.sin(y) * Math.cos(z) + Math.sin(z) * Math.cos(x)));
        shapes.add(lattice("schwarzp", List.of("schwarz", "primitive"),
            "A lattice of round chambers joined by tubes in all six directions", 0.3,
            (x, y, z) -> Math.cos(x) + Math.cos(y) + Math.cos(z)));
        shapes.add(lattice("schwarzd", List.of("diamondlattice"),
            "A lattice with the structure of a diamond crystal", 0.25,
            (x, y, z) -> Math.sin(x) * Math.sin(y) * Math.sin(z) + Math.sin(x) * Math.cos(y) * Math.cos(z)
                + Math.cos(x) * Math.sin(y) * Math.cos(z) + Math.cos(x) * Math.cos(y) * Math.sin(z)));
    }

    // ------------------------------------------------------------------ nature

    private static void addNature(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("mountain", List.of("peak", "hill"), NATURE,
            "A rugged mountain with ridges. The size is its radius; make it wider than tall, e.g. 60,40,60",
            List.of(
                param("roughness", 0.5, 0, 1, "How rugged the ridges are"),
                param("steepness", 1.5, 0.5, 4, "Shape of the slopes: 1 = dome, higher = steeper peak"),
                param("seed", 0, 0, 1_000_000, "Change this for a different mountain")),
            (p, _) -> {
                double roughness = p.get("roughness");
                double steepness = p.get("steepness");
                RidgedMultiFractalNoise ridges = new RidgedMultiFractalNoise();
                ridges.setSeed(p.getInt("seed"));
                ridges.setOctaveCount(5);
                PerlinNoise outline = new PerlinNoise();
                outline.setSeed(p.getInt("seed") + 1);
                outline.setOctaveCount(3);
                return heightField((x, z) -> {
                    double wobble = 1 + 0.35 * roughness * (outline.noise(Vector3.at(x * 1.5, 0, z * 1.5)) - 0.5) * 2;
                    double distance = Math.hypot(x, z) / Math.max(0.3, wobble);
                    double profile = Math.pow(Math.max(0, 1 - distance * distance), steepness);
                    double ridge = ridges.noise(Vector3.at(x * 2.2, 0.5, z * 2.2));
                    return profile * ((1 - roughness) + roughness * (0.45 + 0.75 * ridge));
                });
            }));

        shapes.add(shape("volcano", List.of(), NATURE,
            "A volcano with a crater at the top. Make it wider than tall, e.g. 50,30,50",
            List.of(
                param("crater", 0.3, 0.05, 0.8, "Width of the crater compared to the volcano"),
                param("depth", 0.4, 0, 1, "Depth of the crater compared to the height"),
                param("roughness", 0.3, 0, 1, "How bumpy the slopes are"),
                param("seed", 0, 0, 1_000_000, "Change this for a different volcano")),
            (p, _) -> {
                double crater = p.get("crater");
                double depth = p.get("depth");
                double roughness = p.get("roughness");
                PerlinNoise noise = new PerlinNoise();
                noise.setSeed(p.getInt("seed"));
                noise.setOctaveCount(4);
                return heightField((x, z) -> {
                    double distance = Math.hypot(x, z);
                    double bump = 1 + roughness * 0.3 * (noise.noise(Vector3.at(x * 3, 0, z * 3)) - 0.5) * 2;
                    double height;
                    if (distance >= crater) {
                        height = Math.pow(Math.max(0, (1 - distance) / (1 - crater)), 1.6);
                    } else {
                        double t = distance / crater;
                        height = 1 - depth * (1 - t * t);
                    }
                    return height * bump;
                });
            }));

        shapes.add(shape("galaxy", List.of("spiralgalaxy"), NATURE,
            "A flat spiral galaxy with a bulge in the middle",
            List.of(
                param("arms", 2, 1, 12, "Number of spiral arms"),
                param("swirl", 3, 0, 12, "How tightly the arms wind"),
                param("width", 0.3, 0.05, 1, "Width of the arms compared to the gaps"),
                param("thickness", 0.25, 0.02, 1, "Thickness of the disc at the middle, as a fraction of the height")),
            (p, _) -> {
                int arms = p.getInt("arms");
                double swirl = p.get("swirl");
                double threshold = Math.cos(Math.PI * p.get("width"));
                double thick = p.get("thickness");
                return (ShapeInstance.Implicit) (x, y, z) -> {
                    double radius = Math.hypot(x, z);
                    if (radius > 1) {
                        return false;
                    }
                    double core = 0.25;
                    double cy = y / 0.6;
                    if (radius * radius + cy * cy <= core * core) {
                        return true;
                    }
                    double halfHeight = thick * (1 - radius);
                    if (Math.abs(y) > halfHeight) {
                        return false;
                    }
                    double phase = arms * (Math.atan2(z, x) - swirl * radius);
                    return Math.cos(phase) >= threshold;
                };
            }));
    }

    @FunctionalInterface
    private interface Height {
        double at(double x, double z);
    }

    private static ShapeInstance.Implicit heightField(Height height) {
        return (x, y, z) -> (y + 1) / 2 <= Math.min(1, height.at(x, z)) && height.at(x, z) > 0;
    }

    // ------------------------------------------------------------------ curves

    private static void addCurves(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("knot", List.of("torusknot", "trefoil"), CURVES,
            "A tube tied in a knot. The default is a trefoil (the simplest knot)",
            List.of(
                param("loops", 2, 1, 20, "How many times it goes around the middle"),
                param("winds", 3, 1, 20, "How many times it winds through the hole (pick a number with no common factor with loops)"),
                tube("tube")),
            (p, r) -> {
                int loops = p.getInt("loops");
                int winds = p.getInt("winds");
                double radius = autoTube(p.get("tube"), r, 0.1);
                return (ShapeInstance.Swept) tracer -> tracer.curve((t, out) -> {
                    double ring = 0.62 + 0.38 * Math.cos(winds * t);
                    out[0] = ring * Math.cos(loops * t);
                    out[1] = 0.38 * Math.sin(winds * t) * 1.6;
                    out[2] = ring * Math.sin(loops * t);
                }, 0, TAU, radius);
            }));

        shapes.add(shape("dna", List.of("doublehelix"), CURVES,
            "Two twisting strands joined by rungs, like DNA. Make it taller than wide, e.g. 5,30,5",
            List.of(
                param("turns", 2, 0.25, 100, "Number of full twists"),
                param("rungs", 10, 0, 100, "Rungs per twist (0 = none)"),
                tube("strands")),
            (p, r) -> {
                double turns = p.get("turns");
                int rungs = p.getInt("rungs");
                double radius = autoTube(p.get("tube"), Vector3.at(r.x(), Math.min(r.x(), r.z()), r.z()), 0.15);
                return (ShapeInstance.Swept) tracer -> {
                    for (int strand = 0; strand < 2; strand++) {
                        double offset = strand * Math.PI;
                        tracer.curve((t, out) -> {
                            out[0] = Math.cos(TAU * turns * t + offset);
                            out[1] = t * 2 - 1;
                            out[2] = Math.sin(TAU * turns * t + offset);
                        }, 0, 1, radius);
                    }
                    int count = (int) Math.floor(rungs * turns);
                    for (int i = 0; i < count; i++) {
                        double t = (i + 0.5) / count;
                        double angle = TAU * turns * t;
                        double height = t * 2 - 1;
                        tracer.curve((s, out) -> {
                            double v = s * 2 - 1;
                            out[0] = v * Math.cos(angle);
                            out[1] = height;
                            out[2] = v * Math.sin(angle);
                        }, 0, 1, Math.max(0.5, radius * 0.6));
                    }
                };
            }));

        shapes.add(shape("coil", List.of("toroidalcoil", "slinky"), CURVES,
            "A spring bent round into a ring",
            List.of(
                param("coils", 12, 1, 200, "Number of coils around the ring"),
                param("ring", 0.3, 0.05, 0.9, "Thickness of the ring compared to its size"),
                tube("wire")),
            (p, r) -> {
                int coils = p.getInt("coils");
                double minor = p.get("ring") / (1 + p.get("ring"));
                double major = 1 - minor;
                double radius = autoTube(p.get("tube"), r, 0.04);
                return (ShapeInstance.Swept) tracer -> tracer.curve((t, out) -> {
                    double ring = major + minor * Math.cos(coils * t);
                    out[0] = ring * Math.cos(t);
                    out[1] = minor * Math.sin(coils * t);
                    out[2] = ring * Math.sin(t);
                }, 0, TAU, radius);
            }));

        shapes.add(shape("lissajous", List.of("lissajousknot"), CURVES,
            "A looping tube that weaves back and forth through a box",
            List.of(
                param("a", 3, 1, 20, "Loops across east-west"),
                param("b", 2, 1, 20, "Loops up and down"),
                param("c", 7, 1, 20, "Loops across north-south"),
                tube("tube")),
            (p, r) -> {
                int a = p.getInt("a");
                int b = p.getInt("b");
                int c = p.getInt("c");
                double radius = autoTube(p.get("tube"), r, 0.06);
                return (ShapeInstance.Swept) tracer -> tracer.curve((t, out) -> {
                    out[0] = Math.cos(a * t + 0.7);
                    out[1] = Math.cos(b * t + 0.2);
                    out[2] = Math.cos(c * t);
                }, 0, TAU, radius);
            }));

        shapes.add(shape("lorenz", List.of("butterfly", "attractor"), CURVES,
            "The Lorenz attractor, a chaotic path that loops around two 'wings' like a butterfly",
            List.of(
                param("loops", 30, 2, 200, "Roughly how many loops to draw"),
                tube("path")),
            (p, r) -> {
                int steps = Math.max(200, p.getInt("loops") * 160);
                double radius = autoTube(p.get("tube"), r, 0.025);
                double[][] path = lorenzPath(steps);
                return new ShapeInstance.Swept() {
                    @Override
                    public void trace(ShapeTracer tracer) {
                        tracer.curve((t, out) -> {
                            double position = Math.max(0, Math.min(path.length - 1.000001, t));
                            int i = (int) position;
                            double f = position - i;
                            for (int k = 0; k < 3; k++) {
                                out[k] = path[i][k] + (path[i + 1][k] - path[i][k]) * f;
                            }
                        }, 0, path.length - 1, radius);
                    }

                    @Override
                    public boolean autoFit() {
                        return true;
                    }
                };
            }));
    }

    private static double[][] lorenzPath(int steps) {
        double sigma = 10;
        double rho = 28;
        double beta = 8.0 / 3;
        double dt = 0.005;
        double x = 0.1;
        double y = 0;
        double z = 0;
        int skip = 400;
        double[][] path = new double[steps][];
        for (int i = 0; i < steps + skip; i++) {
            // midpoint method
            double dx1 = sigma * (y - x);
            double dy1 = x * (rho - z) - y;
            double dz1 = x * y - beta * z;
            double mx = x + dx1 * dt / 2;
            double my = y + dy1 * dt / 2;
            double mz = z + dz1 * dt / 2;
            x += sigma * (my - mx) * dt;
            y += (mx * (rho - mz) - my) * dt;
            z += (mx * my - beta * mz) * dt;
            if (i >= skip) {
                // the wings spread along x and y; z is the height
                path[i - skip] = new double[] {x, z, y};
            }
        }
        return path;
    }

    // ---------------------------------------------------------------- surfaces

    private static ShapeInstance.Swept fitted(ShapeTracer.Surface surface, double u0, double u1,
                                             double v0, double v1, double radius) {
        return new ShapeInstance.Swept() {
            @Override
            public void trace(ShapeTracer tracer) {
                tracer.surface(surface, u0, u1, v0, v1, radius);
            }

            @Override
            public boolean autoFit() {
                return true;
            }
        };
    }

    private static void addSurfaces(ImmutableList.Builder<NamedShape> shapes) {
        shapes.add(shape("mobius", List.of("moebius", "mobiusstrip"), SURFACES,
            "A Möbius strip: a ring of ribbon with a twist, so it only has one side",
            List.of(
                param("width", 0.5, 0.1, 1.5, "Width of the ribbon compared to the ring"),
                param("twists", 1, 1, 15, "Number of half twists (odd numbers give one side)"),
                thickness()),
            (p, _) -> {
                double halfWidth = p.get("width") / 2;
                double twists = p.getInt("twists");
                double scale = 1 / (1 + halfWidth);
                double radius = surfaceRadius(p.get("thickness"));
                return (ShapeInstance.Swept) tracer -> tracer.surface((u, v, out) -> {
                    double ring = 1 + halfWidth * v * Math.cos(twists * u / 2);
                    out[0] = scale * ring * Math.cos(u);
                    out[1] = halfWidth * v * Math.sin(twists * u / 2);
                    out[2] = scale * ring * Math.sin(u);
                }, 0, TAU, -1, 1, radius);
            }));

        shapes.add(shape("klein", List.of("kleinbottle"), SURFACES,
            "A Klein bottle: a bottle whose neck passes through its side and joins the bottom",
            List.of(thickness()),
            (p, _) -> fitted((u, v, out) -> {
                double c = Math.cos(u);
                double s = Math.sin(u);
                double cv = Math.cos(v);
                double sv = Math.sin(v);
                double c2 = c * c;
                double c3 = c2 * c;
                double c4 = c2 * c2;
                double c5 = c4 * c;
                double c6 = c4 * c2;
                double c7 = c6 * c;
                double a = -2.0 / 15 * c * (3 * cv - 30 * s + 90 * c4 * s - 60 * c6 * s + 5 * c * cv * s);
                double b = -1.0 / 15 * s * (3 * cv - 3 * c2 * cv - 48 * c4 * cv + 48 * c6 * cv - 60 * s
                    + 5 * c * cv * s - 5 * c3 * cv * s - 80 * c5 * cv * s + 80 * c7 * cv * s);
                double d = 2.0 / 15 * (3 + 5 * c * s) * sv;
                out[0] = a;
                out[1] = b;
                out[2] = d;
            }, 0, Math.PI, 0, TAU, surfaceRadius(p.get("thickness")))));

        shapes.add(shape("seashell", List.of("shell", "conch"), SURFACES,
            "A spiral seashell, tip at the top",
            List.of(
                param("turns", 3, 1, 8, "Number of turns of the spiral"),
                thickness()),
            (p, _) -> {
                double turns = p.get("turns");
                return fitted((u, v, out) -> {
                    double grow = Math.exp(u / (TAU * turns));
                    double bulge = Math.cos(v / 2);
                    bulge *= bulge;
                    out[0] = 2 * (1 - grow) * Math.cos(u) * bulge;
                    out[2] = 2 * (grow - 1) * Math.sin(u) * bulge;
                    out[1] = 1 - grow * grow - Math.sin(v) + grow * Math.sin(v);
                }, 0, TAU * turns, 0, TAU, surfaceRadius(p.get("thickness")));
            }));

        shapes.add(shape("bowl", List.of("dish", "paraboloid"), SURFACES,
            "An open bowl (turn it upside down with -t 180 for a dome)",
            List.of(
                param("flat", 0, 0, 1, "How flat the bottom is (0 = round, 1 = flat)"),
                thickness()),
            (p, _) -> {
                double flat = p.get("flat");
                double radius = surfaceRadius(p.get("thickness"));
                return (ShapeInstance.Swept) tracer -> tracer.surface((u, v, out) -> {
                    double edge = Math.max(0, (u - flat) / (1 - flat + 1e-9));
                    out[0] = u * Math.cos(v);
                    out[1] = -1 + 2 * Math.pow(edge, 2);
                    out[2] = u * Math.sin(v);
                }, 0, 1, 0, TAU, radius);
            }));

        shapes.add(shape("saddle", List.of("pringle", "hypar"), SURFACES,
            "A saddle-shaped sheet that curves up on two sides and down on the other two, great for roofs",
            List.of(
                param("round", 0, 0, 1, "1 = round edge (like a crisp) instead of square"),
                param("solid", 0, 0, 1, "1 = fill everything underneath (a solid block with a saddle top)"),
                thickness()),
            (p, _) -> {
                boolean round = p.get("round") >= 0.5;
                if (p.get("solid") >= 0.5) {
                    return (ShapeInstance.Implicit) (x, y, z) -> (!round || x * x + z * z <= 1) && y <= x * x - z * z;
                }
                double radius = surfaceRadius(p.get("thickness"));
                if (round) {
                    return (ShapeInstance.Swept) tracer -> tracer.surface((u, v, out) -> {
                        double x = u * Math.cos(v);
                        double z = u * Math.sin(v);
                        out[0] = x;
                        out[1] = x * x - z * z;
                        out[2] = z;
                    }, 0, 1, 0, TAU, radius);
                }
                return (ShapeInstance.Swept) tracer -> tracer.surface((u, v, out) -> {
                    out[0] = u;
                    out[1] = u * u - v * v;
                    out[2] = v;
                }, -1, 1, -1, 1, radius);
            }));

        shapes.add(shape("ripple", List.of("waves"), SURFACES,
            "A round sheet with circular waves, like a drop falling into water",
            List.of(
                param("waves", 3, 0.5, 30, "Number of waves from the middle to the edge"),
                param("fade", 0.5, 0, 1, "How much the waves shrink towards the edge"),
                param("solid", 0, 0, 1, "1 = fill everything underneath the waves"),
                thickness()),
            (p, _) -> {
                double waves = p.get("waves");
                double fade = p.get("fade");
                if (p.get("solid") >= 0.5) {
                    return (ShapeInstance.Implicit) (x, y, z) -> {
                        double u = Math.hypot(x, z);
                        return u <= 1 && y <= Math.cos(TAU * waves * u) * (1 - fade * u);
                    };
                }
                double radius = surfaceRadius(p.get("thickness"));
                return (ShapeInstance.Swept) tracer -> tracer.surface((u, v, out) -> {
                    out[0] = u * Math.cos(v);
                    out[1] = Math.cos(TAU * waves * u) * (1 - fade * u);
                    out[2] = u * Math.sin(v);
                }, 0, 1, 0, TAU, radius);
            }));
    }
}
