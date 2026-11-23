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

package com.sk89q.worldedit.function.generator.model;

import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The built-in cross-sections for {@link Sweeper}, sized by their width in blocks.
 */
public enum SweepProfile {
    CIRCLE("a round tube, centred on the path", false),
    SQUARE("a square or rectangular beam, centred on the path", false),
    ARCH("a tunnel or aqueduct arch standing on the path, open at the bottom", true),
    RING("a pipe: a hollow tube centred on the path", true),
    RAIL("two rails on sleepers, sleepers on the path", true);

    private final String description;
    private final boolean openAlready;

    SweepProfile(String description, boolean openAlready) {
        this.description = description;
        this.openAlready = openAlready;
    }

    /**
     * Find a profile by name.
     *
     * @param name the name
     * @return the profile, if there is one
     */
    public static Optional<SweepProfile> find(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.label().equals(lower)).findFirst();
    }

    /**
     * The names of all profiles.
     *
     * @return the names, comma separated
     */
    public static String names() {
        return Arrays.stream(values()).map(SweepProfile::label).collect(Collectors.joining(", "));
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getDescription() {
        return description;
    }

    /**
     * The settings of this profile, besides its width.
     *
     * @return the settings
     */
    public List<ShapeParameter> getParameters() {
        ShapeParameter height = new ShapeParameter("height", 0, 0, 256, "height in blocks (0 = same as the width)");
        ShapeParameter thickness = new ShapeParameter("thickness", 1, 1, 64, "wall thickness in blocks");
        return switch (this) {
            case CIRCLE -> List.of();
            case SQUARE -> List.of(height);
            case ARCH -> List.of(height, thickness);
            case RING -> List.of(thickness);
            case RAIL -> List.of(new ShapeParameter("spacing", 2, 1, 64, "a sleeper every this many blocks"));
        };
    }

    /**
     * Whether the profile is already open inside, so hollow makes no difference.
     *
     * @return true for pipes, arches and rails
     */
    public boolean isOpenAlready() {
        return openAlready;
    }

    /**
     * Make the cross-section.
     *
     * @param width the width in blocks
     * @param settings the settings, from {@link #getParameters()}
     * @return the cross-section; filled spots are {@code true}
     */
    public CrossSection<Boolean> create(double width, ShapeParameters settings) {
        if (!(width >= 1) || width > 256) {
            throw new IllegalArgumentException("The width must be between 1 and 256 blocks.");
        }
        double half = width / 2;
        return switch (this) {
            case CIRCLE -> section(half + 0.5, (right, up, _) -> right * right + up * up <= half * half + 1e-9);
            case SQUARE -> {
                double height = settings.get("height") == 0 ? width : settings.get("height");
                double halfHeight = height / 2;
                yield section(Math.hypot(half, halfHeight) + 0.5,
                    (right, up, _) -> Math.abs(right) <= half + 1e-9 && Math.abs(up) <= halfHeight + 1e-9);
            }
            case RING -> {
                double inner = half - settings.get("thickness");
                yield section(half + 0.5, (right, up, _) -> {
                    double distanceSq = right * right + up * up;
                    return distanceSq <= half * half + 1e-9 && (inner <= 0 || distanceSq > inner * inner + 1e-9);
                });
            }
            case ARCH -> {
                double height = settings.get("height") == 0 ? width : settings.get("height");
                double thickness = settings.get("thickness");
                if (height < half) {
                    throw new IllegalArgumentException("An arch must be at least half as tall as it is wide.");
                }
                double top = height - 0.5;
                double centre = top - half;
                double opening = half - thickness;
                yield section(Math.hypot(half, height) + 0.5, (right, up, _) -> {
                    if (up < -0.5 - 1e-9 || up > top + 1e-9) {
                        return false;
                    }
                    boolean outer = Math.abs(right) <= half + 1e-9
                        && (up <= centre || right * right + (up - centre) * (up - centre) <= half * half + 1e-9);
                    boolean inside = opening > 0 && Math.abs(right) <= opening + 1e-9
                        && (up <= centre || right * right + (up - centre) * (up - centre) <= opening * opening + 1e-9);
                    return outer && !inside;
                });
            }
            case RAIL -> {
                int gauge = Math.max(1, (int) Math.floor(half));
                double spacing = settings.get("spacing");
                yield section(Math.hypot(gauge + 1.5, 2), (right, up, along) -> {
                    long row = Math.round(up);
                    long side = Math.round(Math.abs(right));
                    if (row == 1) {
                        return side == gauge;
                    }
                    if (row == 0) {
                        return side <= gauge + 1 && Math.floorMod((long) Math.floor(along + 0.5), (long) spacing) == 0;
                    }
                    return false;
                });
            }
        };
    }

    private interface Inside {
        boolean test(double right, double up, double along);
    }

    private static CrossSection<Boolean> section(double reach, Inside inside) {
        return new CrossSection<>() {
            @Override
            public Boolean at(double right, double up, double along) {
                return inside.test(right, up, along) ? Boolean.TRUE : null;
            }

            @Override
            public double reach() {
                return reach;
            }
        };
    }
}
