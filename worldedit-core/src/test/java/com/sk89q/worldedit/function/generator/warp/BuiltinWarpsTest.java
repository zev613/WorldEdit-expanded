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

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinWarpsTest {

    private static final String AIR = "air";
    private static final String STONE = "stone";

    /**
     * A tiny world of named blocks; positions not in the map are air.
     */
    private static final class World {
        final Map<BlockVector3, String> blocks = new HashMap<>();

        String get(BlockVector3 position) {
            return blocks.getOrDefault(position, AIR);
        }

        void set(BlockVector3 position, String block) {
            if (block.equals(AIR)) {
                blocks.remove(position);
            } else {
                blocks.put(position, block);
            }
        }

        void fill(CuboidRegion region, Predicate<BlockVector3> where, String block) {
            for (BlockVector3 position : region) {
                if (where.test(position)) {
                    set(position, block);
                }
            }
        }

        int count(CuboidRegion region, Predicate<BlockVector3> where) {
            int count = 0;
            for (BlockVector3 position : region) {
                if (where.test(position) && !get(position).equals(AIR)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static NamedWarp warp(String name) {
        return BuiltinWarps.find(name).orElseThrow();
    }

    private static int apply(World world, CuboidRegion region, String name, String... settings) throws WorldEditException {
        NamedWarp warp = warp(name);
        WarpMap map = warp.create(warp.parseSettings(List.of(settings)),
            new WarpBounds(region.getMinimumPoint(), region.getMaximumPoint()));
        return Warper.warp(region, map, world::get, _ -> AIR, AIR, world::set);
    }

    private static WarpMap map(String name, BlockVector3 min, BlockVector3 max, String... settings) {
        NamedWarp warp = warp(name);
        return warp.create(warp.parseSettings(List.of(settings)), new WarpBounds(min, max));
    }

    @Test
    void everyWarpHasValidDefaultsAndWorks() {
        assertTrue(BuiltinWarps.all().size() >= 8);
        WarpBounds bounds = new WarpBounds(BlockVector3.at(0, 0, 0), BlockVector3.at(20, 10, 8));
        for (NamedWarp warp : BuiltinWarps.all()) {
            ShapeParameters values = ShapeParameters.defaults(warp.getParameters());
            WarpMap map = warp.create(values, bounds);
            assertNotNull(map, warp.getName());
            map.source(5, 5, 5);
            for (ShapeParameter parameter : warp.getParameters()) {
                assertFalse(parameter.description().isEmpty());
            }
        }
    }

    @Test
    void findsByNameAndAlias() {
        assertEquals("twist", BuiltinWarps.find("TWIST").orElseThrow().getName());
        assertEquals("shear", BuiltinWarps.find("lean").orElseThrow().getName());
        assertTrue(BuiltinWarps.find("nope").isEmpty());
    }

    @Test
    void zeroSettingsLeaveBlocksInPlace() {
        BlockVector3 min = BlockVector3.at(-3, 60, 7);
        BlockVector3 max = BlockVector3.at(9, 70, 12);
        for (String[] settings : List.of(
            new String[] {"twist", "0"}, new String[] {"bend", "0"}, new String[] {"wave", "0"},
            new String[] {"shear", "0"}, new String[] {"spherize", "0"}, new String[] {"inflate", "0"},
            new String[] {"melt", "0"}, new String[] {"taper", "1"})) {
            WarpMap map = map(settings[0], min, max, settings[1]);
            for (BlockVector3 p : new CuboidRegion(min, max)) {
                Vector3 source = map.source(p.x(), p.y(), p.z());
                assertNotNull(source, settings[0]);
                assertEquals(p, source.add(0.5, 0.5, 0.5).toBlockPoint(), settings[0] + " at " + p);
            }
        }
    }

    @Test
    void twistTurnsTheTopButNotTheBottom() {
        WarpMap map = map("twist", BlockVector3.at(-5, 0, -5), BlockVector3.at(5, 10, 5), "180");
        Vector3 bottom = map.source(3, 0, 0);
        assertNotNull(bottom);
        assertEquals(3, bottom.x(), 1e-9);
        Vector3 top = map.source(3, 10, 0);
        assertNotNull(top);
        assertEquals(-3, top.x(), 1e-9);
        assertEquals(0, top.z(), 1e-9);
        // 90 degrees at the top is clockwise seen from above: east (+x) goes to south (+z)
        WarpMap quarter = map("twist", BlockVector3.at(-5, 0, -5), BlockVector3.at(5, 10, 5), "angle=90");
        Vector3 fromEast = quarter.source(0, 10, 3);
        assertNotNull(fromEast);
        assertEquals(3, fromEast.x(), 1e-9);
        assertEquals(0, fromEast.z(), 1e-9);
    }

    @Test
    void warpReadsTheOriginalNotHalfWarpedBlocks() throws WorldEditException {
        // a 1-wide pillar on the west edge; shearing 45 degrees east should give a clean diagonal,
        // which only works if every layer reads the original pillar
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(9, 9, 0));
        world.fill(region, p -> p.x() == 0, STONE);
        int changed = apply(world, region, "shear", "45", "x");
        assertEquals(18, changed);
        for (int y = 0; y <= 9; y++) {
            for (int x = 0; x <= 9; x++) {
                assertEquals(x == y ? STONE : AIR, world.get(BlockVector3.at(x, y, 0)), "at " + x + "," + y);
            }
        }
    }

    @Test
    void shearRejectsTheVerticalAxis() {
        NamedWarp shear = warp("shear");
        WarpBounds bounds = new WarpBounds(BlockVector3.ZERO, BlockVector3.at(4, 4, 4));
        ShapeParameters values = shear.parseSettings(List.of("20", "y"));
        assertThrows(IllegalArgumentException.class, () -> shear.create(values, bounds));
    }

    @Test
    void axisWordsAndNamesAreAccepted() {
        ShapeParameters values = warp("wave").parseSettings(List.of("2", "axis=z", "phase=90"));
        assertEquals(ShapeAxis.Z, values.getInt("axis"));
        assertEquals(90, values.get("phase"));
        assertEquals(ShapeAxis.X, warp("wave").parseSettings(List.of("x")).getInt("axis"));
        assertThrows(IllegalArgumentException.class, () -> warp("twist").parseSettings(List.of("x")));
        assertThrows(IllegalArgumentException.class, () -> warp("taper").parseSettings(List.of("2")));
    }

    @Test
    void taperNarrowsTheTop() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10));
        world.fill(region, _ -> true, STONE);
        apply(world, region, "taper", "0.2");
        assertEquals(121, world.count(region, p -> p.y() == 0));
        int top = world.count(region, p -> p.y() == 10);
        assertTrue(top > 0 && top < 16, "top layer has " + top);
    }

    @Test
    void waveMovesColumnsUpAndDown() {
        WarpMap map = map("wave", BlockVector3.at(0, 0, 0), BlockVector3.at(31, 20, 3), "4", "16");
        // a quarter wavelength in, blocks are lifted by the full amplitude, so they come from 4 below
        Vector3 source = map.source(4, 10, 0);
        assertNotNull(source);
        assertEquals(6, source.y(), 1e-9);
    }

    @Test
    void spherizeRoundsOffABox() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(12, 12, 12));
        world.fill(region, _ -> true, STONE);
        apply(world, region, "spherize");
        assertEquals(AIR, world.get(BlockVector3.at(0, 0, 0)));
        assertEquals(AIR, world.get(BlockVector3.at(12, 12, 12)));
        assertEquals(STONE, world.get(BlockVector3.at(6, 6, 6)));
        assertEquals(STONE, world.get(BlockVector3.at(6, 6, 0)));
        assertEquals(STONE, world.get(BlockVector3.at(0, 6, 6)));
    }

    @Test
    void inflateGrowsABallInTheMiddle() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(20, 20, 20));
        world.fill(region, p -> p.distanceSq(BlockVector3.at(10, 10, 10)) <= 9, STONE);
        int before = world.count(region, _ -> true);
        apply(world, region, "inflate", "3");
        assertTrue(world.count(region, _ -> true) > before * 3);
        assertEquals(STONE, world.get(BlockVector3.at(10, 10, 15)));
    }

    @Test
    void meltLowersTheTopAndKeepsTheBottom() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 20, 10));
        world.fill(region, p -> p.x() >= 3 && p.x() <= 7 && p.z() >= 3 && p.z() <= 7, STONE);
        apply(world, region, "melt", "0.8", "seed=3");
        assertEquals(0, world.count(region, p -> p.y() >= 16));
        assertTrue(world.count(region, p -> p.y() == 0) >= 25);
    }

    @Test
    void bendCurvesALongWall() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(40, 2, 20));
        world.fill(region, p -> p.z() == 10, STONE);
        apply(world, region, "bend", "90");
        // the middle stays put while the ends move sideways
        assertEquals(STONE, world.get(BlockVector3.at(20, 1, 10)));
        assertEquals(AIR, world.get(BlockVector3.at(1, 1, 10)));
        assertTrue(world.count(region, p -> p.z() > 13) > 0);
    }

    @Test
    void bendCanMakeAnArch() {
        WarpMap map = map("bend", BlockVector3.at(0, 0, 0), BlockVector3.at(40, 40, 0), "-180", "vertical=1");
        // in an arch the middle of the top comes from the middle; the ends come down
        Vector3 middle = map.source(20, 20, 0);
        assertNotNull(middle);
        assertEquals(20, middle.x(), 1e-6);
        assertEquals(20, middle.y(), 1e-6);
    }

    @Test
    void noSourceMeansAir() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(2, 2, 2));
        world.fill(region, _ -> true, STONE);
        int changed = Warper.warp(region, (_, _, _) -> null, world::get, _ -> STONE, AIR, world::set);
        assertEquals(27, changed);
        assertEquals(0, world.count(region, _ -> true));
        assertNull(map("taper", BlockVector3.ZERO, BlockVector3.at(10, 10, 10), "0").source(0, 10, 0));
    }

    @Test
    void outsideBlocksCanBePulledIn() throws WorldEditException {
        World world = new World();
        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(4, 0, 0));
        world.set(BlockVector3.at(-1, 0, 0), STONE);
        // shift everything one block east: the west end reads from outside
        Warper.warp(region, (x, y, z) -> Vector3.at(x - 1, y, z), world::get, world::get, AIR, world::set);
        assertEquals(STONE, world.get(BlockVector3.at(0, 0, 0)));
    }
}
