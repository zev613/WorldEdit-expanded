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

import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedShapesTest {

    private static final ShapeLibrary LIBRARY = new ShapeLibrary(null);

    static Stream<NamedShape> builtins() {
        return LIBRARY.getBuiltins().stream();
    }

    private static VoxelGrid build(String name, double size, String... settings) {
        NamedShape shape = LIBRARY.find(name).orElseThrow();
        return ShapeGenerator.voxelize(shape, ShapeParameters.parse(shape.getParameters(), List.of(settings)),
            Vector3.at(size, size, size), ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
    }

    @ParameterizedTest
    @MethodSource("builtins")
    void everyShapeGeneratesSomethingReasonable(NamedShape shape) {
        double size = 12;
        VoxelGrid grid = ShapeGenerator.voxelize(shape, ShapeParameters.defaults(shape.getParameters()),
            Vector3.at(size, size, size), ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
        int count = grid.count();
        double box = Math.pow(2 * size + 1, 3);
        assertTrue(count > 30, shape.getName() + " produced only " + count + " blocks");
        if (shape.getName().equals("cube")) {
            assertEquals(box, count, "cube must fill its whole box");
        } else {
            assertTrue(count < box, shape.getName() + " filled its whole box");
        }
        // hollow is never bigger than filled
        VoxelGrid shell = grid.shell(1);
        assertTrue(shell.count() <= count);
        assertTrue(shell.count() > 0);
    }

    @ParameterizedTest
    @MethodSource("builtins")
    void everyParameterDescribedAndDefaultsValid(NamedShape shape) {
        assertFalse(shape.getDescription().isBlank());
        for (ShapeParameter parameter : shape.getParameters()) {
            assertFalse(parameter.description().isBlank(), shape.getName() + "." + parameter.name());
        }
        ShapeParameters.defaults(shape.getParameters());
    }

    @Test
    void torusMatchesTheTorusCommandFormula() {
        // same as (0.75 - sqrt(x^2 + z^2))^2 + y^2 < 0.25^2
        VoxelGrid grid = build("torus", 20, "ring=0.25");
        assertTrue(grid.get(15, 0, 0));
        assertTrue(grid.get(0, 0, -15));
        assertTrue(grid.get(15, 4, 0));
        assertFalse(grid.get(15, 6, 0));
        assertFalse(grid.get(0, 0, 0));
        assertFalse(grid.get(9, 0, 0));
    }

    @Test
    void basicSolidsHaveTheExpectedBlocks() {
        VoxelGrid sphere = build("ball", 5);
        assertTrue(sphere.get(5, 0, 0));
        assertTrue(sphere.get(0, -5, 0));
        assertFalse(sphere.get(4, 4, 0));
        assertEquals(1331, build("box", 5).count());

        NamedShape cylinder = LIBRARY.find("cyl").orElseThrow();
        VoxelGrid tall = ShapeGenerator.voxelize(cylinder, ShapeParameters.defaults(cylinder.getParameters()),
            Vector3.at(3, 8, 3), ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
        assertTrue(tall.get(3, 8, 0));
        assertTrue(tall.get(0, -8, -3));
        assertFalse(tall.get(3, 0, 3));
        assertFalse(tall.get(0, 9, 0));

        VoxelGrid cone = build("cone", 6);
        assertTrue(cone.get(6, -6, 0));
        assertTrue(cone.get(0, 6, 0));
        assertFalse(cone.get(1, 6, 0));
        assertFalse(cone.get(6, 0, 0));

        VoxelGrid pyramid = build("pyramid", 6);
        assertTrue(pyramid.get(6, -6, 6));
        assertTrue(pyramid.get(0, 6, 0));
        assertFalse(pyramid.get(1, 6, 1));
        assertTrue(pyramid.get(3, 0, 3));
        assertFalse(pyramid.get(4, 0, 4));
        assertFalse(cone.get(5, -6, 5));
    }

    @Test
    void builtinsAreListedOnce() {
        List<NamedShape> shapes = LIBRARY.getBuiltins();
        assertEquals(shapes.size(), shapes.stream().map(NamedShape::getName).distinct().count());
        assertTrue(shapes.size() >= 35);
    }

    @Test
    void namesAndAliasesAreCaseInsensitive() {
        assertEquals("torus", LIBRARY.find("DONUT").orElseThrow().getName());
        assertEquals("knot", LIBRARY.find("Trefoil").orElseThrow().getName());
        assertTrue(LIBRARY.find("nope").isEmpty());
    }

    @Test
    void parametersAcceptPositionalAndNamedValues() {
        NamedShape star = LIBRARY.find("star").orElseThrow();
        ShapeParameters p = ShapeParameters.parse(star.getParameters(), List.of("6", "depth=0.5"));
        assertEquals(6, p.getInt("points"));
        assertEquals(0.5, p.get("depth"));
        assertEquals(0.45, p.get("inner"));
        assertThrows(IllegalArgumentException.class,
            () -> ShapeParameters.parse(star.getParameters(), List.of("points=2")));
        assertThrows(IllegalArgumentException.class,
            () -> ShapeParameters.parse(star.getParameters(), List.of("colour=3")));
        assertThrows(IllegalArgumentException.class,
            () -> ShapeParameters.parse(star.getParameters(), List.of("pi")));
        assertThrows(IllegalArgumentException.class,
            () -> ShapeParameters.parse(star.getParameters(), List.of("1", "2", "3", "4", "5")));
    }

    @Test
    void settingsChangeTheShape() {
        assertTrue(build("star", 12, "points=8").count() != build("star", 12).count());
        assertTrue(build("menger", 13, "level=1").count() > build("menger", 13, "level=2").count());
    }

    @Test
    void symmetricShapesAreSymmetric() {
        for (String name : List.of("torus", "octahedron", "superellipsoid", "schwarzp", "menger", "hourglass")) {
            VoxelGrid grid = build(name, 10);
            for (int x = -10; x <= 10; x++) {
                for (int y = -10; y <= 10; y++) {
                    for (int z = -10; z <= 10; z++) {
                        assertEquals(grid.get(x, y, z), grid.get(-x, y, z), name + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void stretchedSizeStretchesTheShape() {
        NamedShape shape = LIBRARY.find("egg").orElseThrow();
        VoxelGrid grid = ShapeGenerator.voxelize(shape, ShapeParameters.defaults(shape.getParameters()),
            Vector3.at(5, 20, 5), ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
        assertTrue(grid.get(0, 19, 0));
        assertFalse(grid.get(8, 0, 0));
    }

    @Test
    void tiltLaysTheShapeOnItsSide() {
        NamedShape shape = LIBRARY.find("hourglass").orElseThrow();
        ShapeParameters defaults = ShapeParameters.defaults(shape.getParameters());
        VoxelGrid upright = ShapeGenerator.voxelize(shape, defaults, Vector3.at(3, 10, 3),
            ShapeOrientation.NONE, false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
        VoxelGrid tilted = ShapeGenerator.voxelize(shape, defaults, Vector3.at(3, 10, 3),
            new ShapeOrientation(0, 90), false, ShapeRasterizer.DEFAULT_MAX_VOLUME);
        assertTrue(upright.get(0, 10, 0));
        assertFalse(upright.get(0, 0, 10));
        assertTrue(tilted.get(0, 0, 10));
        assertFalse(tilted.get(0, 10, 0));
        assertEquals(upright.count(), tilted.count());
    }

    @Test
    void tooLargeShapesAreRejected() {
        NamedShape shape = LIBRARY.find("torus").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> new ShapeRasterizer(Vector3.at(500, 500, 500),
            ShapeOrientation.NONE, 1_000_000).rasterize(shape.create(ShapeParameters.defaults(shape.getParameters()),
            Vector3.at(500, 500, 500))));
    }

    @Test
    void hollowShellIsOneLayer() {
        VoxelGrid filled = build("superellipsoid", 10, "power=2");
        VoxelGrid shell = filled.shell(1);
        assertTrue(shell.get(10, 0, 0));
        assertFalse(shell.get(0, 0, 0));
        assertFalse(shell.get(8, 0, 0));
    }

    @Test
    void presetsSaveAndReload(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("presets.txt");
        ShapeLibrary library = new ShapeLibrary(file);
        PresetShape preset = library.savePreset("BigKnot", "trefoil", List.of("loops=3", "5"));
        assertEquals("bigknot", preset.getName());
        assertEquals(3, preset.getSettings().get("loops"));
        assertEquals(5, preset.getSettings().get("winds"));
        assertTrue(Files.readString(file).contains("bigknot = knot loops=3 winds=5 tube=0"));

        ShapeLibrary reloaded = new ShapeLibrary(file);
        NamedShape loaded = reloaded.find("bigknot").orElseThrow();
        assertEquals(3, ShapeParameters.defaults(loaded.getParameters()).getInt("loops"));
        // settings can still be overridden when used
        assertEquals(4, ShapeParameters.parse(loaded.getParameters(), List.of("loops=4")).getInt("loops"));

        // a preset of a preset starts from its settings
        PresetShape derived = reloaded.savePreset("bigknot2", "bigknot", List.of("tube=2"));
        assertEquals(3, derived.getSettings().get("loops"));
        assertEquals(2, derived.getSettings().get("tube"));
        assertEquals("knot", derived.getBase().getName());

        assertThrows(IllegalArgumentException.class, () -> reloaded.savePreset("heart", "star", List.of()));
        assertThrows(IllegalArgumentException.class, () -> reloaded.savePreset("bad name!", "star", List.of()));
        assertThrows(IllegalArgumentException.class, () -> reloaded.savePreset("x", "star", List.of("points=999")));
        assertTrue(reloaded.deletePreset("bigknot"));
        assertTrue(new ShapeLibrary(file).find("bigknot").isEmpty());
    }

    @Test
    void bottomAlignmentUsesLowestBlock() {
        VoxelGrid grid = build("superellipsoid", 6, "power=2");
        assertEquals(-6, grid.lowestY());
    }
}
