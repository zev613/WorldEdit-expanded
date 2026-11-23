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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureGeneratorsTest {

    private static final List<NatureSize> SIZES = List.of(NatureSize.of(1), NatureSize.of(3), NatureSize.of(8),
        new NatureSize(12, 5, 6), new NatureSize(4, 14, 4));

    private static ShapeParameters settings(List<ShapeParameter> parameters, String... arguments) {
        return ShapeParameters.parse(parameters, List.of(arguments));
    }

    /**
     * Every voxel as x,y,z,part, to compare models.
     */
    private static List<String> voxels(NatureModel model) {
        List<String> list = new ArrayList<>();
        model.forEach((x, y, z, part) -> list.add(x + "," + y + "," + z + "," + part));
        return list;
    }

    private static void assertWithin(NatureModel model, NatureSize size, boolean hanging, String what) {
        assertTrue(model.count() > 0, what + " is empty");
        model.forEach((x, y, z, _) -> {
            String where = what + " at " + x + "," + y + "," + z;
            assertTrue(Math.abs(x) <= size.x() && Math.abs(z) <= size.z(), where);
            if (hanging) {
                assertTrue(y <= 0 && y > -size.height(), where);
            } else {
                assertTrue(y >= 0 && y < size.height(), where);
            }
        });
    }

    @Test
    void rocksStayInSizeSitOnTheGroundAndHoldTogether() {
        for (NatureSize size : SIZES) {
            for (double roughness : new double[] {0, 0.5, 1}) {
                for (long seed = 1; seed <= 4; seed++) {
                    NatureModel rock = RockGenerator.generate(size,
                        settings(RockGenerator.PARAMETERS, "roughness=" + roughness), seed);
                    String what = "rock " + size + " roughness " + roughness + " seed " + seed;
                    assertWithin(rock, size, false, what);
                    assertEquals(1, rock.pieces(), what + " has floating bits");
                    assertEquals(0, rock.lowestY(), what + " sits on the ground");
                }
            }
        }
        // it fills a good part of its size
        NatureModel big = RockGenerator.generate(NatureSize.of(10), settings(RockGenerator.PARAMETERS), 5);
        assertTrue(big.count() > 21 * 21 * 20 * 0.15, "count " + big.count());
        assertTrue(big.highestY() >= 10);
    }

    @Test
    void sameSeedSameResultOtherSeedOtherResult() {
        NatureSize size = NatureSize.of(6);
        assertEquals(voxels(RockGenerator.generate(size, settings(RockGenerator.PARAMETERS), 9)),
            voxels(RockGenerator.generate(size, settings(RockGenerator.PARAMETERS), 9)));
        assertNotEquals(voxels(RockGenerator.generate(size, settings(RockGenerator.PARAMETERS), 9)),
            voxels(RockGenerator.generate(size, settings(RockGenerator.PARAMETERS), 10)));
        assertEquals(voxels(CrystalGenerator.generate(size, settings(CrystalGenerator.PARAMETERS), 3)),
            voxels(CrystalGenerator.generate(size, settings(CrystalGenerator.PARAMETERS), 3)));
        assertEquals(voxels(RootGenerator.generate(size, settings(RootGenerator.PARAMETERS), 3)),
            voxels(RootGenerator.generate(size, settings(RootGenerator.PARAMETERS), 3)));
        for (int type = 0; type < 4; type++) {
            assertEquals(voxels(CoralGenerator.generate(size, settings(CoralGenerator.PARAMETERS, "type=" + type), 3)),
                voxels(CoralGenerator.generate(size, settings(CoralGenerator.PARAMETERS, "type=" + type), 3)));
        }
        assertEquals(voxels(GiantMushroomGenerator.generate(size, settings(GiantMushroomGenerator.PARAMETERS), true, 3)),
            voxels(GiantMushroomGenerator.generate(size, settings(GiantMushroomGenerator.PARAMETERS), true, 3)));
    }

    @Test
    void mushroomsHaveAStemACapAndNoLooseBits() {
        for (NatureSize size : SIZES) {
            for (int type = 0; type < 3; type++) {
                for (long seed = 1; seed <= 3; seed++) {
                    String what = "mushroom " + size + " type " + type + " seed " + seed;
                    NatureModel mushroom = GiantMushroomGenerator.generate(size,
                        settings(GiantMushroomGenerator.PARAMETERS, "type=" + type), seed % 2 == 0, seed);
                    assertWithin(mushroom, size, false, what);
                    assertEquals(1, mushroom.pieces(), what + " has floating bits");
                    assertEquals(0, mushroom.lowestY(), what);
                    assertTrue(mushroom.count(GiantMushroomGenerator.STEM) > 0, what + " has no stem");
                    assertTrue(mushroom.count(GiantMushroomGenerator.CAP) > 0, what + " has no cap");
                    assertEquals(size.height() - 1, mushroom.highestY(), what + " reaches its height");
                }
            }
        }
        NatureModel gills = GiantMushroomGenerator.generate(NatureSize.of(8),
            settings(GiantMushroomGenerator.PARAMETERS, "type=1"), true, 1);
        assertTrue(gills.count(GiantMushroomGenerator.UNDERSIDE) > 20);
        NatureModel noGills = GiantMushroomGenerator.generate(NatureSize.of(8),
            settings(GiantMushroomGenerator.PARAMETERS, "type=1"), false, 1);
        assertEquals(0, noGills.count(GiantMushroomGenerator.UNDERSIDE));
        // a flat cap is wider and lower than a bell
        NatureModel round = GiantMushroomGenerator.generate(NatureSize.of(8),
            settings(GiantMushroomGenerator.PARAMETERS, "type=0"), false, 1);
        assertTrue(round.count(GiantMushroomGenerator.CAP) > 100);
    }

    @Test
    void crystalsRootsAndCoralStayInSize() {
        for (NatureSize size : SIZES) {
            for (long seed = 1; seed <= 3; seed++) {
                String suffix = " " + size + " seed " + seed;
                NatureModel crystals = CrystalGenerator.generate(size,
                    settings(CrystalGenerator.PARAMETERS, "count=" + (seed * 4)), seed);
                assertWithin(crystals, size, false, "crystals" + suffix);
                assertEquals(1, crystals.pieces(), "crystals" + suffix);

                NatureModel roots = RootGenerator.generate(size, settings(RootGenerator.PARAMETERS), seed);
                assertWithin(roots, size, true, "roots" + suffix);
                assertEquals(1, roots.pieces(), "roots" + suffix);
                assertEquals(0, roots.highestY());

                for (int type = 0; type < 4; type++) {
                    NatureModel coral = CoralGenerator.generate(size,
                        settings(CoralGenerator.PARAMETERS, "type=" + type), seed);
                    assertWithin(coral, size, false, "coral type " + type + suffix);
                    assertEquals(1, coral.pieces(), "coral type " + type + suffix);
                }
            }
        }
    }

    @Test
    void shapesLookTheirPart() {
        NatureSize size = NatureSize.of(10);
        // the tallest crystal nearly reaches the top
        NatureModel crystals = CrystalGenerator.generate(size, settings(CrystalGenerator.PARAMETERS), 2);
        assertTrue(crystals.highestY() >= 15, "crystal height " + crystals.highestY());
        // roots spread out and go down
        NatureModel roots = RootGenerator.generate(size, settings(RootGenerator.PARAMETERS), 2);
        assertTrue(roots.lowestY() <= -5, "roots depth " + roots.lowestY());
        int[] reach = new int[1];
        roots.forEach((x, _, z, _) -> reach[0] = Math.max(reach[0], Math.abs(x) + Math.abs(z)));
        assertTrue(reach[0] >= 7, "roots reach " + reach[0]);
        // a fan is one block thick
        NatureModel fan = CoralGenerator.generate(size, settings(CoralGenerator.PARAMETERS, "type=2"), 2);
        int[] thickness = new int[2];
        fan.forEach((x, _, z, _) -> {
            thickness[0] |= x != 0 ? 1 : 0;
            thickness[1] |= z != 0 ? 1 : 0;
        });
        assertEquals(1, thickness[0] + thickness[1], "fan is flat");
        assertTrue(fan.count() > 40);
        // brain coral is a wrinkled, mostly solid ball
        NatureModel brain = CoralGenerator.generate(size, settings(CoralGenerator.PARAMETERS, "type=1"), 2);
        assertTrue(brain.count() > 1500, "brain count " + brain.count());
    }

    @Test
    void namesWorkForTypes() {
        List<String> words = NamedSettings.replaceNames(List.of("flat", "seed=4"), "type", GiantMushroomGenerator.TYPES);
        assertEquals(1, settings(GiantMushroomGenerator.PARAMETERS, words.toArray(String[]::new)).getInt("type"));
        assertEquals(List.of("type=3"), NamedSettings.replaceNames(List.of("type=tubes"), "type", CoralGenerator.TYPES));
        assertThrows(IllegalArgumentException.class, () -> NatureSize.of(List.of(1.0, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> NatureSize.of(65));
        assertThrows(IllegalArgumentException.class, () -> NatureSize.of(0.5));
    }
}
