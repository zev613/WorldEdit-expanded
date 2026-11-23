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

package com.sk89q.worldedit.regions.smart;

import java.util.Locale;
import java.util.Set;

/**
 * Which blocks count as natural terrain for {@code //selbuild}: the flood fill spreads through
 * every non-air block except these, so it follows a build and its decorations but stops at the ground.
 *
 * <p>The list is deliberately narrow. It holds blocks that terrain generation places in bulk and that
 * builders rarely use as building material: soil (grass, dirt, podzol, mycelium...), the stone types of
 * the underground (stone, deepslate, andesite, diorite, granite, tuff, calcite), loose ground (gravel,
 * sand, red sand, clay), snow blocks and powder snow, water and lava, ground plants (grass, ferns,
 * flowers, dead bushes, seagrass), ores, bedrock, and the bulk blocks of the Nether and the End.</p>
 *
 * <p>It deliberately does not hold leaves and logs (trees planted next to a build, hedges and timber
 * framing would be cut off), sandstone and cobblestone (common building blocks), snow layers (they sit on
 * roofs and paths), ice, moss or basalt. A tree touching a build is therefore included; trim it with
 * {@code //selconnected} or a mask afterwards.</p>
 */
public final class NaturalBlocks {

    private static final Set<String> NATURAL = Set.of(
        // soil
        "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "mud",
        // underground stone
        "stone", "deepslate", "andesite", "diorite", "granite", "tuff", "calcite", "bedrock",
        // loose ground
        "gravel", "sand", "red_sand", "clay", "suspicious_sand", "suspicious_gravel",
        // snow
        "snow_block", "powder_snow",
        // fluids
        "water", "lava", "bubble_column",
        // ground plants
        "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "short_dry_grass",
        "tall_dry_grass", "bush", "seagrass", "tall_seagrass", "kelp", "kelp_plant",
        // flowers
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
        "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
        "torchflower", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "pink_petals",
        "wildflowers", "leaf_litter", "open_eyeblossom", "closed_eyeblossom", "firefly_bush",
        // nether and end
        "netherrack", "soul_sand", "soul_soil", "crimson_nylium", "warped_nylium", "end_stone",
        "ancient_debris"
    );

    private NaturalBlocks() {
    }

    /**
     * Whether a block type is natural terrain.
     *
     * @param blockId a block id such as {@code minecraft:grass_block} (the namespace is optional)
     * @return true if natural
     */
    public static boolean isNatural(String blockId) {
        String id = blockId.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) {
            if (!id.startsWith("minecraft:")) {
                return false;
            }
            id = id.substring(colon + 1);
        }
        return NATURAL.contains(id) || id.endsWith("_ore");
    }

    /**
     * Whether a block type is a fluid, which the ground layer of {@code //selbuild -g} leaves out.
     *
     * @param blockId a block id
     * @return true for water, lava and bubble columns
     */
    public static boolean isFluid(String blockId) {
        String id = blockId.toLowerCase(Locale.ROOT);
        return id.equals("minecraft:water") || id.equals("minecraft:lava") || id.equals("minecraft:bubble_column")
            || id.equals("water") || id.equals("lava") || id.equals("bubble_column");
    }
}
