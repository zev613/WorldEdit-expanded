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

import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.Vector3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The built-in warps for {@code //warp}. Every setting is a plain number (angles in degrees).
 */
public final class BuiltinWarps {

    private static final List<NamedWarp> ALL = List.of(
        twist(), bend(), taper(), wave(), shear(), spherize(), inflate(), melt()
    );

    private BuiltinWarps() {
    }

    /**
     * All built-in warps.
     *
     * @return the warps
     */
    public static List<NamedWarp> all() {
        return ALL;
    }

    /**
     * Find a warp by name or alias, ignoring case.
     *
     * @param name the name
     * @return the warp, if found
     */
    public static Optional<NamedWarp> find(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(w -> w.getName().equals(lower) || w.getAliases().contains(lower)).findFirst();
    }

    // coordinates are handled as {x, y, z} arrays, indexed by ShapeAxis constant - 1

    private static double center(WarpBounds bounds, int axis) {
        Vector3 center = bounds.center();
        return switch (axis) {
            case ShapeAxis.X -> center.x();
            case ShapeAxis.Z -> center.z();
            default -> center.y();
        };
    }

    private static double min(WarpBounds bounds, int axis) {
        return switch (axis) {
            case ShapeAxis.X -> bounds.min().x();
            case ShapeAxis.Z -> bounds.min().z();
            default -> bounds.min().y();
        };
    }

    private static Vector3 vector(double[] p) {
        return Vector3.at(p[0], p[1], p[2]);
    }

    private static int horizontalAxis(ShapeParameters values, WarpBounds bounds, String warp) {
        int axis = values.getInt(ShapeAxis.NAME);
        if (axis == ShapeAxis.AUTO) {
            return bounds.longHorizontalAxis();
        }
        if (axis == ShapeAxis.Y) {
            throw new IllegalArgumentException(warp + " works along x or z, not y");
        }
        return axis;
    }

    private static NamedWarp twist() {
        return new NamedWarp("twist", List.of("spin", "corkscrew"),
            "Twists the selection around its upright middle line; the bottom stays put and the top turns the most",
            List.of(new ShapeParameter("angle", 90, -3600, 3600,
                "Degrees the top turns, clockwise seen from above (negative turns the other way)")),
            (values, bounds) -> {
                double angle = values.get("angle");
                Vector3 c = bounds.center();
                return (x, y, z) -> {
                    double turn = -Math.toRadians(angle * bounds.heightFraction(y));
                    double cos = Math.cos(turn);
                    double sin = Math.sin(turn);
                    double dx = x - c.x();
                    double dz = z - c.z();
                    return Vector3.at(c.x() + dx * cos - dz * sin, y, c.z() + dx * sin + dz * cos);
                };
            });
    }

    private static NamedWarp bend() {
        return new NamedWarp("bend", List.of("curve", "arc"),
            "Curves the selection along its long side, like bending a wall, bridge or road",
            List.of(
                new ShapeParameter("angle", 90, -360, 360,
                    "Degrees between the two ends after bending (negative bends the other way)"),
                ShapeAxis.parameter(ShapeAxis.AUTO, "The long side that gets curved: x or z (auto = the longer one)"),
                new ShapeParameter("vertical", 0, 0, 1,
                    "1 = bend up and down instead of sideways (positive angle lifts the ends, negative makes an arch)")),
            (values, bounds) -> {
                double angle = values.get("angle");
                int along = horizontalAxis(values, bounds, "bend");
                int across = values.getInt("vertical") == 1 ? ShapeAxis.Y
                    : along == ShapeAxis.X ? ShapeAxis.Z : ShapeAxis.X;
                double theta = Math.toRadians(Math.abs(angle));
                if (theta < 1e-9) {
                    return Vector3::at;
                }
                double sign = Math.signum(angle);
                double radius = bounds.size(along) / theta;
                double centerAlong = center(bounds, along);
                double centerAcross = center(bounds, across);
                return (x, y, z) -> {
                    double[] p = {x, y, z};
                    double u = p[along - 1] - centerAlong;
                    double v = sign * (p[across - 1] - centerAcross);
                    // the middle line becomes a circle of this radius around (0, radius)
                    double dv = v - radius;
                    double phi = Math.atan2(u, -dv);
                    p[along - 1] = centerAlong + phi * radius;
                    p[across - 1] = centerAcross + sign * (radius - Math.hypot(u, dv));
                    return vector(p);
                };
            });
    }

    private static NamedWarp taper() {
        return new NamedWarp("taper", List.of("narrow", "pinch"),
            "Narrows the selection towards the top, like a spire or a tree trunk",
            List.of(
                new ShapeParameter("top", 0.5, 0, 1, "Width at the top, as a share of the original (0 = a point)"),
                new ShapeParameter("bottom", 1, 0, 1, "Width at the bottom, as a share of the original")),
            (values, bounds) -> {
                double top = values.get("top");
                double bottom = values.get("bottom");
                Vector3 c = bounds.center();
                return (x, y, z) -> {
                    double scale = bottom + (top - bottom) * bounds.heightFraction(y);
                    double dx = x - c.x();
                    double dz = z - c.z();
                    if (scale < 1e-3) {
                        return Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5 ? Vector3.at(x, y, z) : null;
                    }
                    return Vector3.at(c.x() + dx / scale, y, c.z() + dz / scale);
                };
            });
    }

    private static NamedWarp wave() {
        return new NamedWarp("wave", List.of("waves", "ripple"),
            "Makes the selection wavy: blocks move up and down in waves along one direction",
            List.of(
                new ShapeParameter("amplitude", 3, -64, 64, "How far blocks move, in blocks"),
                new ShapeParameter("wavelength", 16, 2, 1024, "Blocks from one wave top to the next"),
                ShapeAxis.parameter(ShapeAxis.AUTO,
                    "The direction the waves run along: x or z (auto = the longer side); y makes it wiggle sideways going up"),
                new ShapeParameter("phase", 0, 0, 360, "Where along the wave to start, in degrees")),
            (values, bounds) -> {
                double amplitude = values.get("amplitude");
                double wavelength = values.get("wavelength");
                double phase = Math.toRadians(values.get("phase"));
                int axis = values.getInt(ShapeAxis.NAME);
                int along = axis == ShapeAxis.AUTO ? bounds.longHorizontalAxis() : axis;
                int moved = along == ShapeAxis.Y ? bounds.longHorizontalAxis() : ShapeAxis.Y;
                double start = min(bounds, along);
                return (x, y, z) -> {
                    double[] p = {x, y, z};
                    double offset = amplitude * Math.sin(2 * Math.PI * (p[along - 1] - start) / wavelength + phase);
                    p[moved - 1] -= offset;
                    return vector(p);
                };
            });
    }

    private static NamedWarp shear() {
        return new NamedWarp("shear", List.of("lean", "slant"),
            "Leans the selection over; the bottom stays put and each layer above slides a bit further",
            List.of(
                new ShapeParameter("angle", 30, -80, 80, "Degrees to lean from upright (negative leans the other way)"),
                ShapeAxis.parameter(ShapeAxis.AUTO, "The direction to lean towards: x or z (auto = the longer side)")),
            (values, bounds) -> {
                double slope = Math.tan(Math.toRadians(values.get("angle")));
                int axis = horizontalAxis(values, bounds, "shear");
                double bottom = bounds.min().y();
                return (x, y, z) -> {
                    double[] p = {x, y, z};
                    p[axis - 1] -= slope * (y - bottom);
                    return vector(p);
                };
            });
    }

    private static NamedWarp spherize() {
        return new NamedWarp("spherize", List.of("round", "ball"),
            "Pulls the selection towards a ball shape that fits inside it (negative pushes round things towards a box)",
            List.of(new ShapeParameter("amount", 1, -1, 1, "How far to go: 1 = a full ball, 0 = no change, -1 = boxy")),
            (values, bounds) -> {
                double amount = values.get("amount");
                Vector3 c = bounds.center();
                double hx = Math.max(0.5, (bounds.size(ShapeAxis.X) - 1) / 2.0 + 0.49);
                double hy = Math.max(0.5, (bounds.size(ShapeAxis.Y) - 1) / 2.0 + 0.49);
                double hz = Math.max(0.5, (bounds.size(ShapeAxis.Z) - 1) / 2.0 + 0.49);
                return (x, y, z) -> {
                    double nx = (x - c.x()) / hx;
                    double ny = (y - c.y()) / hy;
                    double nz = (z - c.z()) / hz;
                    double round = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double boxy = Math.max(Math.abs(nx), Math.max(Math.abs(ny), Math.abs(nz)));
                    if (round < 1e-9) {
                        return Vector3.at(x, y, z);
                    }
                    double ratio = amount >= 0 ? round / boxy : boxy / round;
                    double factor = 1 + Math.abs(amount) * (ratio - 1);
                    return Vector3.at(c.x() + nx * factor * hx, c.y() + ny * factor * hy, c.z() + nz * factor * hz);
                };
            });
    }

    private static NamedWarp inflate() {
        return new NamedWarp("inflate", List.of("puff", "bulge"),
            "Pushes everything outwards from the middle of the selection, like blowing up a balloon",
            List.of(new ShapeParameter("amount", 3, -64, 64, "Blocks to push outwards (negative pulls inwards)")),
            (values, bounds) -> {
                double amount = values.get("amount");
                Vector3 c = bounds.center();
                return (x, y, z) -> {
                    double dx = x - c.x();
                    double dy = y - c.y();
                    double dz = z - c.z();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance < 1e-9) {
                        return c;
                    }
                    double scale = Math.max(0, distance - amount) / distance;
                    return Vector3.at(c.x() + dx * scale, c.y() + dy * scale, c.z() + dz * scale);
                };
            });
    }

    private static NamedWarp melt() {
        return new NamedWarp("melt", List.of("droop", "sag"),
            "Makes the selection slump down unevenly like melting wax, spreading out at the bottom",
            List.of(
                new ShapeParameter("amount", 0.5, 0, 1, "How far it slumps (1 = down to a third of its height)"),
                new ShapeParameter("noise", 0.5, 0, 1, "How uneven the drips are (0 = even)"),
                new ShapeParameter("spread", 0.5, 0, 1, "How much the bottom spreads out into a puddle"),
                new ShapeParameter("seed", 0, 0, 1000000, "Change this for a different random look")),
            (values, bounds) -> {
                double amount = values.get("amount");
                double noise = values.get("noise");
                double spread = values.get("spread");
                int seed = values.getInt("seed");
                Vector3 c = bounds.center();
                double bottom = bounds.min().y();
                return (x, y, z) -> {
                    double low = 1 - bounds.heightFraction(y);
                    double widen = 1 + spread * amount * low * low;
                    double sx = c.x() + (x - c.x()) / widen;
                    double sz = c.z() + (z - c.z()) / widen;
                    double bumps = 0.6 * WarpNoise.smooth(sx / 5, sz / 5, seed)
                        + 0.4 * WarpNoise.hash((int) Math.floor(sx + 0.5), (int) Math.floor(sz + 0.5), seed + 1);
                    double sag = (1 - noise) + noise * bumps;
                    double squash = 1 + 2 * amount * sag;
                    return Vector3.at(sx, bottom + (y - bottom) * squash, sz);
                };
            });
    }
}
