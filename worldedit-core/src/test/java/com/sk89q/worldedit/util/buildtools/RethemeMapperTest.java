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

package com.sk89q.worldedit.util.buildtools;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RethemeMapperTest {

    /**
     * A small subset of vanilla block ids, standing in for the registry.
     */
    private static final Set<String> EXISTING = ImmutableSet.of(
        "oak_planks", "oak_stairs", "oak_slab", "oak_fence", "oak_fence_gate", "oak_door", "oak_trapdoor",
        "oak_log", "stripped_oak_log", "oak_wood", "stripped_oak_wood", "oak_sign", "oak_wall_sign",
        "oak_button", "oak_pressure_plate", "oak_leaves", "oak_sapling", "potted_oak_sapling",
        "spruce_planks", "spruce_stairs", "spruce_slab", "spruce_fence", "spruce_fence_gate", "spruce_door",
        "spruce_trapdoor", "spruce_log", "stripped_spruce_log", "spruce_wood", "stripped_spruce_wood",
        "spruce_sign", "spruce_wall_sign", "spruce_button", "spruce_pressure_plate", "spruce_leaves",
        "spruce_sapling",
        "dark_oak_planks", "dark_oak_log",
        "crimson_planks", "crimson_stairs", "crimson_stem", "stripped_crimson_stem", "crimson_hyphae",
        "stripped_crimson_hyphae", "crimson_fungus", "nether_wart_block", "potted_crimson_fungus",
        "warped_stem", "warped_wart_block", "warped_planks",
        "bamboo_planks", "bamboo_block", "stripped_bamboo_block",
        "cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall",
        "mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall",
        "stone", "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall",
        "cracked_stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks",
        "deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall",
        "cracked_deepslate_bricks", "deepslate_tiles", "deepslate_tile_stairs",
        "red_nether_bricks", "nether_bricks", "nether_brick_stairs", "end_stone_bricks", "andesite",
        "quartz_block", "quartz_stairs", "purpur_block", "purpur_stairs"
    );

    private static String map(String id, String from, String to) {
        for (String candidate : RethemeMapper.candidates(id, from, to)) {
            String path = candidate.substring("minecraft:".length());
            if (EXISTING.contains(path)) {
                return path;
            }
        }
        return null;
    }

    @Test
    void woodFamilyStraightReplacement() {
        for (String suffix : List.of("planks", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "log",
            "wood", "sign", "wall_sign", "button", "pressure_plate", "leaves", "sapling")) {
            assertEquals("spruce_" + suffix, map("minecraft:oak_" + suffix, "oak", "spruce"), suffix);
        }
        assertEquals("stripped_spruce_log", map("minecraft:stripped_oak_log", "oak", "spruce"));
        assertEquals("stripped_spruce_wood", map("minecraft:stripped_oak_wood", "oak", "spruce"));
    }

    @Test
    void namespaceIsKept() {
        assertEquals("minecraft:spruce_planks", RethemeMapper.candidates("minecraft:oak_planks", "oak", "spruce").getFirst());
        assertEquals("mymod:spruce_thing", RethemeMapper.candidates("mymod:oak_thing", "oak", "spruce").getFirst());
    }

    @Test
    void nonMembersAreIgnored() {
        assertTrue(RethemeMapper.candidates("minecraft:stone", "oak", "spruce").isEmpty());
        // whole segments only
        assertTrue(RethemeMapper.candidates("minecraft:oaky_planks", "oak", "spruce").isEmpty());
        // dark_oak is its own family
        assertTrue(RethemeMapper.candidates("minecraft:dark_oak_planks", "oak", "spruce").isEmpty());
        // cobblestone is a single segment, not "stone"
        assertTrue(RethemeMapper.candidates("minecraft:cobblestone", "stone", "andesite").isEmpty());
        // red_nether_bricks is not nether_bricks
        assertTrue(RethemeMapper.candidates("minecraft:red_nether_bricks", "nether_brick", "stone_brick").isEmpty());
        assertTrue(RethemeMapper.candidates("minecraft:end_stone_bricks", "stone", "andesite").isEmpty());
        assertTrue(RethemeMapper.candidates("minecraft:oak_planks", "oak", "oak").isEmpty());
    }

    @Test
    void crimsonQuirks() {
        assertEquals("crimson_planks", map("minecraft:oak_planks", "oak", "crimson"));
        assertEquals("crimson_stem", map("minecraft:oak_log", "oak", "crimson"));
        assertEquals("stripped_crimson_stem", map("minecraft:stripped_oak_log", "oak", "crimson"));
        assertEquals("crimson_hyphae", map("minecraft:oak_wood", "oak", "crimson"));
        assertEquals("stripped_crimson_hyphae", map("minecraft:stripped_oak_wood", "oak", "crimson"));
        assertEquals("nether_wart_block", map("minecraft:oak_leaves", "oak", "crimson"));
        assertEquals("crimson_fungus", map("minecraft:oak_sapling", "oak", "crimson"));
        assertEquals("potted_crimson_fungus", map("minecraft:potted_oak_sapling", "oak", "crimson"));
        assertEquals("warped_wart_block", map("minecraft:oak_leaves", "oak", "warped"));
        // and back again
        assertEquals("oak_log", map("minecraft:crimson_stem", "crimson", "oak"));
        assertEquals("oak_wood", map("minecraft:crimson_hyphae", "crimson", "oak"));
        assertEquals("oak_leaves", map("minecraft:nether_wart_block", "crimson", "oak"));
        assertEquals("spruce_leaves", map("minecraft:warped_wart_block", "warped", "spruce"));
    }

    @Test
    void bambooQuirks() {
        assertEquals("bamboo_block", map("minecraft:oak_log", "oak", "bamboo"));
        assertEquals("stripped_bamboo_block", map("minecraft:stripped_oak_log", "oak", "bamboo"));
        assertEquals("oak_log", map("minecraft:bamboo_block", "bamboo", "oak"));
        // no bamboo wood: candidates exist but none match
        assertFalse(RethemeMapper.candidates("minecraft:oak_wood", "oak", "bamboo").isEmpty());
        assertNull(map("minecraft:oak_wood", "oak", "bamboo"));
    }

    @Test
    void mossyCobblestone() {
        assertEquals("mossy_cobblestone", map("minecraft:cobblestone", "cobblestone", "mossy_cobblestone"));
        assertEquals("mossy_cobblestone_stairs", map("minecraft:cobblestone_stairs", "cobblestone", "mossy_cobblestone"));
        assertEquals("mossy_cobblestone_wall", map("minecraft:cobblestone_wall", "cobblestone", "mossy_cobblestone"));
        // already mossy: left alone instead of becoming mossy_mossy_cobblestone
        assertTrue(RethemeMapper.candidates("minecraft:mossy_cobblestone", "cobblestone", "mossy_cobblestone").isEmpty());
        // and back
        assertEquals("cobblestone_slab", map("minecraft:mossy_cobblestone_slab", "mossy_cobblestone", "cobblestone"));
    }

    @Test
    void brickPlurals() {
        assertEquals("deepslate_bricks", map("minecraft:stone_bricks", "stone_brick", "deepslate_brick"));
        assertEquals("deepslate_brick_stairs", map("minecraft:stone_brick_stairs", "stone_brick", "deepslate_brick"));
        assertEquals("deepslate_brick_wall", map("minecraft:stone_brick_wall", "stone_brick", "deepslate_brick"));
        assertEquals("cracked_deepslate_bricks", map("minecraft:cracked_stone_bricks", "stone_brick", "deepslate_brick"));
        // plural tokens given by the user
        assertEquals("deepslate_bricks", map("minecraft:stone_bricks", "stone_bricks", "deepslate_bricks"));
        assertEquals("deepslate_brick_slab", map("minecraft:stone_brick_slab", "stone_bricks", "deepslate_bricks"));
        // to a tile family
        assertEquals("deepslate_tiles", map("minecraft:stone_bricks", "stone_brick", "deepslate_tile"));
        assertEquals("deepslate_tile_stairs", map("minecraft:stone_brick_stairs", "stone_brick", "deepslate_tile"));
        // no mossy deepslate bricks: skipped
        assertNull(map("minecraft:mossy_stone_bricks", "stone_brick", "deepslate_brick"));
        assertEquals("minecraft:mossy_deepslate_bricks",
            RethemeMapper.candidates("minecraft:mossy_stone_bricks", "stone_brick", "deepslate_brick").getFirst());
    }

    @Test
    void blockSuffixQuirk() {
        assertEquals("purpur_block", map("minecraft:quartz_block", "quartz", "purpur"));
        assertEquals("purpur_stairs", map("minecraft:quartz_stairs", "quartz", "purpur"));
        assertEquals("andesite", map("minecraft:quartz_block", "quartz", "andesite"));
        assertEquals("quartz_block", map("minecraft:andesite", "andesite", "quartz"));
    }

    @Test
    void tokenValidation() {
        assertTrue(RethemeMapper.isValidToken("oak"));
        assertTrue(RethemeMapper.isValidToken("stone_brick"));
        assertFalse(RethemeMapper.isValidToken("minecraft:oak"));
        assertFalse(RethemeMapper.isValidToken("_oak"));
        assertFalse(RethemeMapper.isValidToken("oak__log"));
        assertFalse(RethemeMapper.isValidToken(""));
        assertFalse(RethemeMapper.isValidToken(null));
    }

    @Test
    void unknownMaterialsAreDetected() {
        assertTrue(RethemeMapper.isKnownMaterial(EXISTING, "oak"));
        assertTrue(RethemeMapper.isKnownMaterial(EXISTING, "spruce"));
        assertFalse(RethemeMapper.isKnownMaterial(EXISTING, "nosuch"));
        assertFalse(RethemeMapper.isKnownMaterial(EXISTING, "spruse"));
        assertTrue(RethemeMapper.isMember("minecraft:stripped_oak_log", "oak"));
        assertFalse(RethemeMapper.isMember("minecraft:dark_oak_log", "oak"));
    }
}
