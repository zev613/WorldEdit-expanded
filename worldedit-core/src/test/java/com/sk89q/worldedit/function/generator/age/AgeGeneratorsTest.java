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

package com.sk89q.worldedit.function.generator.age;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgeGeneratorsTest {

    private static final AgeBox BOX = new AgeBox(0, 0, 0, 27, 16, 23);
    private static final long[] SEEDS = { 1, 7, 42, 1234, 99999 };

    private static AgeBlock stairs(String id, String facing) {
        return AgeBlock.of(id).with("facing", facing).with("half", "bottom").with("shape", "straight")
            .with("waterlogged", "false");
    }

    /**
     * A two-storey stone house with a stair roof, windows, a door, torches,
     * a copper roof ridge, timber corners and a porch roof, on grass.
     */
    private static MemoryAgeWorld house() {
        MemoryAgeWorld world = new MemoryAgeWorld(BOX);
        world.fill(0, 0, 0, 27, 0, 23, AgeBlock.of("dirt"));
        world.fill(0, 1, 0, 27, 1, 23, AgeBlock.of("grass_block"));
        // walls
        world.fill(6, 2, 6, 19, 10, 15, AgeBlock.of("stone_bricks"));
        world.fill(7, 3, 7, 18, 10, 14, AgeBlock.AIR);
        world.fill(7, 2, 7, 18, 2, 14, AgeBlock.of("oak_planks"));
        world.fill(7, 6, 7, 18, 6, 14, AgeBlock.of("oak_planks"));
        world.fill(6, 2, 6, 6, 10, 6, AgeBlock.of("oak_log").with("axis", "y"));
        world.fill(19, 2, 6, 19, 10, 6, AgeBlock.of("oak_log").with("axis", "y"));
        world.fill(6, 3, 10, 6, 9, 11, AgeBlock.of("cobblestone"));
        // windows
        world.fill(9, 4, 6, 10, 4, 6, AgeBlock.of("glass"));
        world.fill(15, 8, 15, 16, 8, 15, AgeBlock.of("glass_pane"));
        // door
        world.put(12, 3, 6, AgeBlock.of("oak_door").with("half", "lower").with("facing", "north"));
        world.put(12, 4, 6, AgeBlock.of("oak_door").with("half", "upper").with("facing", "north"));
        world.put(11, 4, 5, AgeBlock.of("wall_torch").with("facing", "north"));
        world.put(10, 3, 10, AgeBlock.of("torch"));
        world.put(14, 7, 10, AgeBlock.of("red_carpet"));
        // roof: a stepped stair roof with a copper ridge
        for (int i = 0; i < 5; i++) {
            int y = 11 + i;
            world.fill(5, y, 5 + i, 20, y, 5 + i, stairs("stone_brick_stairs", "south"));
            world.fill(5, y, 16 - i, 20, y, 16 - i, stairs("stone_brick_stairs", "north"));
            world.fill(5, y, 6 + i, 20, y, 15 - i, AgeBlock.of("stone_bricks"));
        }
        world.fill(5, 16, 10, 20, 16, 11, AgeBlock.of("cut_copper"));
        world.fill(9, 3, 15, 16, 3, 15, AgeBlock.of("cut_copper"));
        // porch roof (an overhang) in front of the door
        world.fill(10, 6, 3, 14, 6, 5, AgeBlock.of("cobblestone_slab").with("type", "top"));
        world.fill(10, 6, 3, 14, 6, 3, AgeBlock.of("cobblestone"));
        world.fill(10, 2, 3, 10, 5, 3, AgeBlock.of("cobblestone_wall"));
        world.fill(14, 2, 3, 14, 5, 3, AgeBlock.of("cobblestone_wall"));
        // a dirt ledge
        world.fill(22, 2, 16, 26, 5, 22, AgeBlock.of("dirt"));
        world.fill(22, 6, 16, 26, 6, 22, AgeBlock.of("grass_block"));
        world.fill(22, 2, 16, 23, 4, 22, AgeBlock.AIR);
        world.fill(24, 2, 16, 24, 4, 22, AgeBlock.of("stone"));
        return world;
    }

    private static Map<String, AgeBlock> ruined(long seed, double amount, boolean rubble, boolean greenery) {
        MemoryAgeWorld world = house();
        RuinGenerator.ruin(world, BOX, new RuinGenerator.Options(amount, seed, rubble, greenery));
        return world.snapshot();
    }

    private static Map<String, AgeBlock> overgrown(long seed, double amount, boolean flowers) {
        MemoryAgeWorld world = house();
        OvergrowGenerator.overgrow(world, BOX, new OvergrowGenerator.Options(amount, seed, flowers, true));
        return world.snapshot();
    }

    private static int[] parse(String key) {
        String[] parts = key.split(",", -1);
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static AgeBlock at(Map<String, AgeBlock> blocks, int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), AgeBlock.AIR);
    }

    @Test
    void amountZeroChangesNothing() {
        MemoryAgeWorld ruin = house();
        RuinGenerator.Result r = RuinGenerator.ruin(ruin, BOX, new RuinGenerator.Options(0, 5, true, true));
        assertEquals(0, r.changed());
        assertEquals(0, ruin.writes());
        assertEquals(house().snapshot(), ruin.snapshot());

        MemoryAgeWorld grow = house();
        OvergrowGenerator.Result g = OvergrowGenerator.overgrow(grow, BOX,
            new OvergrowGenerator.Options(0, 5, true, true));
        assertEquals(0, g.changed());
        assertEquals(0, grow.writes());
        assertEquals(house().snapshot(), grow.snapshot());
    }

    @Test
    void sameSeedSameResult() {
        for (long seed : SEEDS) {
            assertEquals(ruined(seed, 0.6, true, true), ruined(seed, 0.6, true, true));
            assertEquals(overgrown(seed, 0.6, true), overgrown(seed, 0.6, true));
        }
        assertNotEquals(ruined(1, 0.6, true, false), ruined(2, 0.6, true, false));
        assertNotEquals(overgrown(1, 0.6, true), overgrown(2, 0.6, true));
    }

    @Test
    void ruinChangesSomethingAndMoreWithAmount() {
        Map<String, AgeBlock> before = house().snapshot();
        int previous = -1;
        for (double amount : new double[] { 0.2, 0.5, 0.9 }) {
            Map<String, AgeBlock> after = ruined(3, amount, false, false);
            int lost = 0;
            for (String key : before.keySet()) {
                if (!after.containsKey(key)) {
                    lost++;
                }
            }
            assertTrue(lost > previous, "amount " + amount + " lost " + lost);
            previous = lost;
        }
    }

    @Test
    void ruinLeavesNothingFloating() {
        for (long seed : SEEDS) {
            for (double amount : new double[] { 0.3, 0.6, 1.0 }) {
                for (boolean rubble : new boolean[] { true, false }) {
                    Map<String, AgeBlock> blocks = ruined(seed, amount, rubble, true);
                    Set<String> grounded = new HashSet<>();
                    ArrayDeque<int[]> queue = new ArrayDeque<>();
                    blocks.forEach((k, b) -> {
                        int[] p = parse(k);
                        if (p[1] == BOX.minY() && AgeMaterials.isStructural(b)) {
                            grounded.add(k);
                            queue.add(p);
                        }
                    });
                    int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
                        { 0, 0, -1 } };
                    while (!queue.isEmpty()) {
                        int[] p = queue.poll();
                        for (int[] d : dirs) {
                            String k = key(p[0] + d[0], p[1] + d[1], p[2] + d[2]);
                            AgeBlock b = blocks.get(k);
                            if (b != null && AgeMaterials.isStructural(b) && grounded.add(k)) {
                                queue.add(new int[] { p[0] + d[0], p[1] + d[1], p[2] + d[2] });
                            }
                        }
                    }
                    blocks.forEach((k, b) -> {
                        if (AgeMaterials.isStructural(b)) {
                            assertTrue(grounded.contains(k), "floating " + b + " at " + k + " seed " + seed
                                + " amount " + amount);
                        }
                    });
                }
            }
        }
    }

    @Test
    void rubbleOnlyAtGroundLevel() {
        Map<String, AgeBlock> before = house().snapshot();
        for (long seed : SEEDS) {
            Map<String, AgeBlock> after = ruined(seed, 0.8, true, false);
            int rubble = 0;
            for (Map.Entry<String, AgeBlock> entry : after.entrySet()) {
                if (before.containsKey(entry.getKey())) {
                    continue;
                }
                rubble++;
                int[] p = parse(entry.getKey());
                // ground: the top of the natural blocks of the column, as it was before
                int ground = 0;
                while (AgeMaterials.isTerrain(at(before, p[0], ground, p[2]))) {
                    ground++;
                }
                assertTrue(p[1] <= ground + 1, "rubble " + entry.getValue() + " at " + entry.getKey()
                    + " above ground " + ground);
                assertTrue(AgeMaterials.isStructural(at(after, p[0], p[1] - 1, p[2])),
                    "rubble resting on nothing at " + entry.getKey());
            }
            assertTrue(rubble > 0, "no rubble for seed " + seed);
            Map<String, AgeBlock> none = ruined(seed, 0.8, false, false);
            for (String key : none.keySet()) {
                assertTrue(before.containsKey(key), "-r still placed " + none.get(key) + " at " + key);
            }
        }
    }

    @Test
    void ruinWeathersAndKeepsFacing() {
        Map<String, AgeBlock> before = house().snapshot();
        Map<String, AgeBlock> after = ruined(11, 0.7, false, false);
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, AgeBlock> entry : after.entrySet()) {
            AgeBlock old = before.get(entry.getKey());
            AgeBlock now = entry.getValue();
            if (old != null && !old.id().equals(now.id())) {
                seen.add(now.path());
                assertEquals(old.properties(), now.properties(), "state lost: " + old + " -> " + now);
            }
            assertTrue(!now.path().endsWith("_door") && !now.path().equals("wall_torch"),
                "decor left at amount 0.7: " + now);
        }
        assertTrue(seen.contains("cracked_stone_bricks"), seen.toString());
        assertTrue(seen.contains("mossy_stone_bricks") || seen.contains("mossy_stone_brick_stairs"), seen.toString());
        assertTrue(seen.stream().anyMatch(s -> s.contains("_cut_copper")), seen.toString());
        assertTrue(seen.contains("stripped_oak_log"), seen.toString());
    }

    @Test
    void missingVariantsAreSkipped() {
        MemoryAgeWorld world = house();
        world.setExists(id -> !id.contains("mossy") && !id.contains("cracked"));
        RuinGenerator.ruin(world, BOX, new RuinGenerator.Options(1, 4, true, true));
        OvergrowGenerator.overgrow(world, BOX, new OvergrowGenerator.Options(1, 4, true, true));
        for (AgeBlock block : world.snapshot().values()) {
            assertTrue(!block.id().contains("mossy") && !block.id().contains("cracked"), block.toString());
        }
    }

    @Test
    void vinesAreAttached() {
        String[] names = { "north", "south", "east", "west" };
        int[][] dirs = { { 0, -1 }, { 0, 1 }, { 1, 0 }, { -1, 0 } };
        for (long seed : SEEDS) {
            for (double amount : new double[] { 0.3, 0.7, 1.0 }) {
                Map<String, AgeBlock> blocks = overgrown(seed, amount, true);
                int vines = 0;
                for (Map.Entry<String, AgeBlock> entry : blocks.entrySet()) {
                    AgeBlock block = entry.getValue();
                    if (!block.path().equals("vine")) {
                        continue;
                    }
                    vines++;
                    int[] p = parse(entry.getKey());
                    int faces = 0;
                    for (int i = 0; i < 4; i++) {
                        boolean solid = AgeMaterials.isFullCube(at(blocks, p[0] + dirs[i][0], p[1], p[2] + dirs[i][1]));
                        assertEquals(String.valueOf(solid), block.get(names[i]),
                            names[i] + " of " + block + " at " + entry.getKey());
                        if (solid) {
                            faces++;
                        }
                    }
                    assertTrue(faces > 0, "vine on nothing at " + entry.getKey());
                    boolean up = AgeMaterials.isFullCube(at(blocks, p[0], p[1] + 1, p[2]));
                    assertEquals(String.valueOf(up), block.get("up"), "up of vine at " + entry.getKey());
                }
                if (amount >= 0.7) {
                    assertTrue(vines > 0, "no vines, seed " + seed + " amount " + amount);
                }
            }
        }
    }

    @Test
    void overgrowNeverRemovesBuildBlocks() {
        Map<String, AgeBlock> before = house().snapshot();
        for (long seed : SEEDS) {
            for (double amount : new double[] { 0.4, 1.0 }) {
                Map<String, AgeBlock> after = overgrown(seed, amount, true);
                for (Map.Entry<String, AgeBlock> entry : before.entrySet()) {
                    AgeBlock now = after.get(entry.getKey());
                    AgeBlock old = entry.getValue();
                    if (old.equals(now)) {
                        continue;
                    }
                    assertTrue(amount > 0.6 && AgeMaterials.isMossableTop(old) && now != null
                        && now.path().equals("moss_block"), old + " became " + now + " at " + entry.getKey());
                }
                // everything new grows on, next to or under something
                for (Map.Entry<String, AgeBlock> entry : after.entrySet()) {
                    if (before.containsKey(entry.getKey())) {
                        continue;
                    }
                    int[] p = parse(entry.getKey());
                    boolean touching = false;
                    for (int[] d : new int[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 },
                        { 0, 0, 1 }, { 0, 0, -1 } }) {
                        if (after.containsKey(key(p[0] + d[0], p[1] + d[1], p[2] + d[2]))) {
                            touching = true;
                        }
                    }
                    assertTrue(touching, "floating plant " + entry.getValue() + " at " + entry.getKey());
                }
            }
        }
    }

    @Test
    void overgrowGrowsEverything() {
        Map<String, AgeBlock> after = overgrown(5, 0.9, true);
        Set<String> kinds = new HashSet<>();
        after.values().forEach(b -> kinds.add(b.path()));
        for (String kind : List.of("vine", "oak_leaves", "moss_carpet", "short_grass", "hanging_roots")) {
            assertTrue(kinds.contains(kind), kind + " missing from " + kinds);
        }
        Map<String, AgeBlock> green = overgrown(5, 0.9, false);
        for (AgeBlock block : green.values()) {
            assertTrue(!AgeMaterials.FLOWERS.contains(block.path()), "flower with -n: " + block);
        }
        assertTrue(after.values().stream().anyMatch(b -> AgeMaterials.FLOWERS.contains(b.path())), "no flowers");
    }

    @Test
    void settings() {
        AgeSettings settings = AgeSettings.parse(List.of("0.7", "-r", "seed=12"), "rg");
        assertEquals(0.7, settings.amount());
        assertEquals(12, settings.seed());
        assertTrue(settings.has('r'));
        assertEquals(0.5, AgeSettings.parse(List.of(), "rg").amount());
        assertThrows(IllegalArgumentException.class, () -> AgeSettings.parse(List.of("2"), "rg"));
        assertThrows(IllegalArgumentException.class, () -> AgeSettings.parse(List.of("-x"), "rg"));
    }

    @Test
    void blockText() {
        AgeBlock block = AgeBlock.parse("vine[west=true,north=false]");
        assertEquals("minecraft:vine[north=false,west=true]", block.toString());
        assertEquals("true", block.get("west"));
        assertEquals("minecraft:mossy_stone_brick_stairs[facing=east]",
            AgeBlock.parse("stone_brick_stairs[facing=east]").withId("mossy_stone_brick_stairs").toString());
    }
}
