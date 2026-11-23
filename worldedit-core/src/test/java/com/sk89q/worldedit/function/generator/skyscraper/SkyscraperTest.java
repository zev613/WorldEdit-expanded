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

package com.sk89q.worldedit.function.generator.skyscraper;

import com.sk89q.worldedit.function.generator.NamedSettings;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.style.BuiltinStyles;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyscraperTest {

    /**
     * Collects blocks like an edit session would: the last write wins, air is an empty optional.
     */
    private static Map<BlockVector3, Optional<StyleRole>> build(String family, int stories, String... settings) {
        List<String> arguments = NamedSettings.replaceNames(List.of(settings), "shape", Skyscraper.SHAPE_NAMES);
        arguments = NamedSettings.replaceNames(arguments, "crown", Skyscraper.CROWN_NAMES);
        Skyscraper skyscraper = new Skyscraper(family, stories,
            ShapeParameters.parse(Skyscraper.PARAMETERS, arguments), 42);
        Map<BlockVector3, Optional<StyleRole>> blocks = new HashMap<>();
        skyscraper.generate(new Skyscraper.Sink() {
            @Override
            public void block(int x, int y, int z, StyleRole role) {
                blocks.put(BlockVector3.at(x, y, z), Optional.of(role));
            }

            @Override
            public void air(int x, int y, int z) {
                blocks.put(BlockVector3.at(x, y, z), Optional.empty());
            }
        });
        return blocks;
    }

    private static Optional<StyleRole> at(Map<BlockVector3, Optional<StyleRole>> blocks, int x, int y, int z) {
        Optional<StyleRole> found = blocks.get(BlockVector3.at(x, y, z));
        return found == null ? Optional.empty() : found;
    }

    @Test
    void footprintAndHeightAreRespected() {
        for (String family : List.of("modern", "art_deco", "futuristic", "gothic", "brutalist", "glass", "brick",
            "sandstone")) {
            for (int crown = 0; crown <= 3; crown++) {
                for (int shape = 0; shape <= 3; shape++) {
                    Map<BlockVector3, Optional<StyleRole>> blocks = build(family, 12, "width=17", "depth=13",
                        "crown=" + crown, "shape=" + shape, "seed=3");
                    int roof = 5 + 11 * 4;
                    for (BlockVector3 position : blocks.keySet()) {
                        String where = family + " crown " + crown + " shape " + shape + " at " + position;
                        assertTrue(position.x() >= 0 && position.x() < 17, where);
                        // the entrance canopy sticks out two blocks at the front
                        assertTrue(position.z() >= -2 && position.z() < 13, where);
                        assertTrue(position.y() >= 0 && position.y() <= roof + 40, where);
                    }
                    assertTrue(blocks.containsKey(BlockVector3.at(8, roof, 6)) || shape == 3,
                        family + " has a roof");
                }
            }
        }
    }

    @Test
    void sameSeedBuildsTheSameTower() {
        assertEquals(build("brick", 10, "seed=7"), build("brick", 10, "seed=7"));
        assertEquals(build("art_deco", 20, "width=21", "seed=7"), build("art_deco", 20, "width=21", "seed=7"));
    }

    @Test
    void stylesLookDifferent() {
        Set<Map<BlockVector3, Optional<StyleRole>>> looks = new HashSet<>();
        for (var style : BuiltinStyles.create()) {
            looks.add(build(style.family(), 8, "seed=1", "shape=box", "crown=flat", "setbacks=0"));
        }
        assertEquals(BuiltinStyles.create().size(), looks.size(), "each style has its own facade");
    }

    @Test
    void lobbyHasAnEntranceAtTheFront() {
        Map<BlockVector3, Optional<StyleRole>> blocks = build("modern", 5);
        // 15 wide: the door is the middle three columns of the front wall, three blocks tall
        for (int x = 6; x <= 8; x++) {
            for (int y = 1; y <= 3; y++) {
                assertEquals(Optional.empty(), at(blocks, x, y, 0), "doorway at " + x + "," + y);
            }
        }
        assertEquals(Optional.of(StyleRole.ACCENT), at(blocks, 5, 1, 0), "door frame");
        assertEquals(Optional.of(StyleRole.ACCENT), at(blocks, 7, 4, -1), "canopy");
        assertEquals(Optional.of(StyleRole.WINDOW), at(blocks, 2, 2, 0), "lobby glass");
        // the back wall has no door
        assertTrue(at(blocks, 7, 2, 14).isPresent());
    }

    @Test
    void setbacksStepTheTowerIn() {
        Skyscraper tower = new Skyscraper("art_deco", 30,
            ShapeParameters.parse(Skyscraper.PARAMETERS, List.of("width=25", "depth=25")), 1);
        assertEquals(3, tower.setbacks());
        assertEquals(0, tower.inset(0));
        assertTrue(tower.inset(29) >= 6, "three setbacks of about 2-3 blocks each");
        for (int s = 1; s < 30; s++) {
            assertTrue(tower.inset(s) >= tower.inset(s - 1));
        }
        Skyscraper tapered = new Skyscraper("modern", 20,
            ShapeParameters.parse(Skyscraper.PARAMETERS, List.of("width=31", "depth=31", "taper=8")), 1);
        assertEquals(8, tapered.inset(19));
    }

    @Test
    void interiorHasAStairwellAllTheWayUp() {
        Skyscraper tower = new Skyscraper("modern", 6, ShapeParameters.parse(Skyscraper.PARAMETERS, List.of()), 1);
        assertTrue(tower.hasCore());
        Map<BlockVector3, Optional<StyleRole>> blocks = build("modern", 6);
        int roof = tower.roofY();
        // one step per block of height, going round the 3x3 shaft at the middle (7,7)
        for (int y = 1; y < roof; y++) {
            int steps = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && at(blocks, 7 + dx, y, 7 + dz).equals(Optional.of(StyleRole.FLOOR))) {
                        steps++;
                    }
                }
            }
            assertEquals(1, steps, "one step at y=" + y);
        }
        Map<BlockVector3, Optional<StyleRole>> slabsOnly = build("modern", 6, "interior=0");
        assertEquals(Optional.of(StyleRole.FLOOR), at(slabsOnly, 7, tower.floorY(2), 6));
        assertNotEquals(blocks, slabsOnly);
    }

    @Test
    void tallTowersAreQuick() {
        long start = System.nanoTime();
        Map<BlockVector3, Optional<StyleRole>> blocks = build("glass", 60, "width=40", "depth=30", "crown=spire");
        assertTrue(blocks.size() > 60 * 100);
        assertTrue(System.nanoTime() - start < 20_000_000_000L);
    }

    @Test
    void entranceFacesTheGivenDirection() {
        // the middle of the front wall is three blocks away, the building behind it
        assertEquals(BlockVector3.at(0, 0, 3), Skyscraper.toWorld(Direction.NORTH, 15, 7, 0, 0));
        assertEquals(BlockVector3.at(0, 0, 4), Skyscraper.toWorld(Direction.NORTH, 15, 7, 0, 1));
        assertEquals(BlockVector3.at(-3, 5, 0), Skyscraper.toWorld(Direction.EAST, 15, 7, 5, 0));
        assertEquals(BlockVector3.at(0, 0, -3), Skyscraper.toWorld(Direction.SOUTH, 15, 7, 0, 0));
        assertEquals(BlockVector3.at(3, 0, 0), Skyscraper.toWorld(Direction.WEST, 15, 7, 0, 0));
        // across the front runs to the entrance's right
        assertEquals(BlockVector3.at(1, 0, 3), Skyscraper.toWorld(Direction.NORTH, 15, 8, 0, 0));
    }
}
