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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the ageing generators know about materials, by block id: which blocks
 * are structure, decoration or plants, which are full cubes (for vines), and
 * the cracked / mossy / stripped / oxidised variants (the same families as
 * {@code //weather}).
 */
public final class AgeMaterials {

    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");
    private static final Set<String> LIQUIDS = Set.of("water", "lava", "bubble_column");

    /**
     * Natural ground. A column's ground is the run of these from the bottom of
     * the selection up.
     */
    private static final Set<String> TERRAIN = Set.of(
        "dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mycelium", "mud", "clay", "sand",
        "red_sand", "gravel", "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite",
        "netherrack", "end_stone", "moss_block", "snow_block", "dirt_path", "farmland", "bedrock",
        "packed_mud", "soul_sand", "soul_soil"
    );

    private static final Set<String> SOIL = Set.of(
        "dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mycelium", "mud", "moss_block"
    );

    private static final Set<String> MOSSABLE_TOPS = Set.of(
        "stone", "cobblestone", "mossy_cobblestone", "andesite", "diorite", "granite", "tuff"
    );

    private static final Set<String> PLANTS = Set.of(
        "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "vine", "glow_lichen",
        "moss_carpet", "hanging_roots", "cave_vines", "cave_vines_plant", "dandelion", "poppy", "blue_orchid",
        "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "brown_mushroom",
        "red_mushroom", "sugar_cane", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "sweet_berry_bush",
        "pink_petals", "lily_pad", "leaf_litter", "bush", "firefly_bush", "wildflowers", "short_dry_grass",
        "tall_dry_grass", "spore_blossom", "azalea", "flowering_azalea", "bamboo", "cactus", "weeping_vines",
        "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "pale_hanging_moss", "pale_moss_carpet"
    );

    private static final Set<String> DECOR_EXACT = Set.of(
        "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
        "lantern", "soul_lantern", "flower_pot", "lever", "ladder", "rail", "powered_rail", "detector_rail",
        "activator_rail", "chain", "iron_chain", "bell", "cobweb", "redstone_wire", "tripwire", "tripwire_hook",
        "end_rod", "campfire", "soul_campfire", "scaffolding", "snow", "candle", "skeleton_skull",
        "skeleton_wall_skull", "lightning_rod", "repeater", "comparator", "copper_torch", "copper_wall_torch",
        "copper_lantern"
    );

    private static final String[] DECOR_SUFFIXES = {
        "_door", "_trapdoor", "_bed", "_carpet", "_button", "_pressure_plate", "_sign", "_hanging_sign",
        "_wall_sign", "_wall_hanging_sign", "_banner", "_wall_banner", "_candle", "_head", "_wall_head",
        "_skull", "_wall_skull", "_lantern", "_torch", "_chain"
    };

    /**
     * Parts of ids of blocks that are not full cubes (vines and moss need a
     * full face to grow on).
     */
    private static final String[] PARTIAL_PARTS = {
        "stairs", "slab", "fence", "pane", "_bars", "chain", "anvil", "cake", "chest", "lectern",
        "enchanting_table", "hopper", "cauldron", "composter", "brewing_stand", "daylight_detector",
        "stonecutter", "pointed_dripstone", "amethyst_cluster", "_bud", "dirt_path", "farmland", "conduit",
        "grindstone", "bell", "candle", "head", "skull", "pot", "sniffer_egg", "turtle_egg", "dragon_egg",
        "sea_pickle", "heavy_core", "shelf"
    };

    private static final Set<String> CRACKABLE = Set.of(
        "stone_bricks", "deepslate_bricks", "deepslate_tiles", "polished_blackstone_bricks", "nether_bricks"
    );

    private static final List<String> WOODS = List.of(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo",
        "crimson", "warped"
    );

    /**
     * Flowers placed by {@code //overgrow}.
     */
    static final List<String> FLOWERS = List.of(
        "dandelion", "poppy", "azure_bluet", "oxeye_daisy", "cornflower", "allium", "blue_orchid"
    );

    private AgeMaterials() {
    }

    /**
     * Whether a block is air.
     *
     * @param block the block
     * @return true for air, cave air and void air
     */
    public static boolean isAir(AgeBlock block) {
        return AIR.contains(block.path());
    }

    /**
     * Whether a block is a liquid.
     *
     * @param block the block
     * @return true for water and lava
     */
    public static boolean isLiquid(AgeBlock block) {
        return LIQUIDS.contains(block.path());
    }

    /**
     * Whether a block is a plant (grass, flowers, vines, moss carpet...).
     *
     * @param block the block
     * @return true if it is a plant
     */
    public static boolean isPlant(AgeBlock block) {
        String path = block.path();
        return PLANTS.contains(path) || path.endsWith("_sapling") || path.endsWith("_propagule")
            || path.endsWith("_tulip") || path.endsWith("_roots") || path.endsWith("_fungus")
            || path.endsWith("_coral") || path.endsWith("_coral_fan") || path.endsWith("_coral_wall_fan");
    }

    /**
     * Whether a block is leaves.
     *
     * @param block the block
     * @return true for leaves
     */
    public static boolean isLeaves(AgeBlock block) {
        return block.path().endsWith("_leaves");
    }

    /**
     * Whether a block is a small furnishing that falls off or rots away in a
     * ruin (doors, trapdoors, torches, lanterns, beds, carpets, signs...).
     *
     * @param block the block
     * @return true if it is decoration
     */
    public static boolean isDecor(AgeBlock block) {
        String path = block.path();
        if (isPlant(block)) {
            return false;
        }
        if (path.equals("sea_lantern") || path.equals("jack_o_lantern")) {
            return false;
        }
        if (DECOR_EXACT.contains(path) || path.startsWith("potted_") || path.endsWith("rail")) {
            return true;
        }
        for (String suffix : DECOR_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a block is part of a structure: anything but air, liquids,
     * plants, leaves and decoration. Only these hold other blocks up.
     *
     * @param block the block
     * @return true if structural
     */
    public static boolean isStructural(AgeBlock block) {
        return !isAir(block) && !isLiquid(block) && !isPlant(block) && !isLeaves(block) && !isDecor(block);
    }

    /**
     * Whether a block is a solid full cube that vines, lichen and moss can grow on.
     *
     * @param block the block
     * @return true if it is a full cube
     */
    public static boolean isFullCube(AgeBlock block) {
        if (!isStructural(block)) {
            return false;
        }
        String path = block.path();
        if (path.endsWith("_wall") || path.contains("glass_pane") || path.equals("snow")) {
            return false;
        }
        for (String part : PARTIAL_PARTS) {
            if (path.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a block is natural ground.
     *
     * @param block the block
     * @return true for dirt, grass, stone and the like
     */
    public static boolean isTerrain(AgeBlock block) {
        return TERRAIN.contains(block.path());
    }

    /**
     * Whether grass and flowers grow on top of a block.
     *
     * @param block the block
     * @return true for dirt, grass and moss
     */
    public static boolean isSoil(AgeBlock block) {
        return SOIL.contains(block.path());
    }

    /**
     * Whether an exposed top of this block may turn into moss at high amounts.
     *
     * @param block the block
     * @return true for stone and cobblestone
     */
    public static boolean isMossableTop(AgeBlock block) {
        return MOSSABLE_TOPS.contains(block.path());
    }

    /**
     * Whether a block is glass or a glass pane.
     *
     * @param block the block
     * @return true for glass
     */
    public static boolean isGlass(AgeBlock block) {
        return block.path().contains("glass");
    }

    /**
     * Whether a block is wooden planks (or stairs / slabs / fences made of them).
     *
     * @param block the block
     * @return true for planks
     */
    public static boolean isPlanks(AgeBlock block) {
        String path = block.path();
        if (path.endsWith("_planks")) {
            return true;
        }
        for (String wood : WOODS) {
            if (path.startsWith(wood + "_") && (path.endsWith("_stairs") || path.endsWith("_slab")
                || path.endsWith("_fence") || path.endsWith("_fence_gate"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a block is a log, wood, stem or hyphae that is not stripped yet.
     *
     * @param block the block
     * @return true for bark-covered logs
     */
    public static boolean isLog(AgeBlock block) {
        String path = block.path();
        return !path.startsWith("stripped_")
            && (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae"))
            && !path.equals("mushroom_stem");
    }

    /**
     * Whether a block contains copper that can oxidise further.
     *
     * @param block the block
     * @return true for unwaxed, not fully oxidised copper
     */
    public static boolean isOxidisable(AgeBlock block) {
        String path = block.path();
        return path.contains("copper") && !path.contains("waxed") && !path.startsWith("oxidized_")
            && !path.contains("ore") && !path.equals("raw_copper_block");
    }

    /**
     * The cracked variant id of a block, if it has one.
     *
     * @param block the block
     * @return the id or null
     */
    static String cracked(AgeBlock block) {
        return CRACKABLE.contains(block.path()) ? "minecraft:cracked_" + block.path() : null;
    }

    /**
     * The mossy variant id of a block, if it has one.
     *
     * @param block the block
     * @return the id or null
     */
    static String mossy(AgeBlock block) {
        String path = block.path();
        if (path.startsWith("cobblestone") || path.startsWith("stone_brick")) {
            return "minecraft:mossy_" + path;
        }
        return null;
    }

    /**
     * The stripped variant id of a log.
     *
     * @param block the block
     * @return the id or null
     */
    static String stripped(AgeBlock block) {
        return isLog(block) ? "minecraft:stripped_" + block.path() : null;
    }

    /**
     * The next oxidation stage of a copper block (as {@code //weather -c}).
     *
     * @param path the id without namespace
     * @return the next id without namespace, or null if fully oxidised
     */
    static String nextOxidation(String path) {
        if (path.startsWith("exposed_")) {
            return "weathered_" + path.substring("exposed_".length());
        } else if (path.startsWith("weathered_")) {
            return "oxidized_" + path.substring("weathered_".length());
        } else if (path.equals("copper_block")) {
            return "exposed_copper";
        } else if (path.startsWith("oxidized_")) {
            return null;
        }
        return "exposed_" + path;
    }

    /**
     * The full-block form of a block: {@code stone_brick_stairs} becomes
     * {@code stone_bricks}, {@code oak_slab} becomes {@code oak_planks}.
     *
     * @param block the block
     * @param world to check which candidates exist
     * @return the id or null
     */
    static String fullBlock(AgeBlock block, AgeWorld world) {
        String path = block.path();
        String base = path;
        for (String suffix : new String[] { "_stairs", "_slab", "_wall", "_fence_gate", "_fence" }) {
            if (path.endsWith(suffix)) {
                base = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        if (base.equals(path)) {
            return block.id();
        }
        for (String candidate : new String[] { base, base + "s", base + "_planks", base + "_block" }) {
            if (world.exists("minecraft:" + candidate)) {
                return "minecraft:" + candidate;
            }
        }
        return null;
    }

    /**
     * The slab of a full block, for smooth tops of rubble piles.
     *
     * @param id the full block id
     * @param world to check which candidates exist
     * @return the slab id or null
     */
    static String slabOf(String id, AgeWorld world) {
        String path = id.substring(id.indexOf(':') + 1);
        List<String> candidates = new ArrayList<>();
        candidates.add(path + "_slab");
        if (path.endsWith("s")) {
            candidates.add(path.substring(0, path.length() - 1) + "_slab");
        }
        if (path.endsWith("_planks")) {
            candidates.add(path.substring(0, path.length() - "_planks".length()) + "_slab");
        }
        if (path.endsWith("_block")) {
            candidates.add(path.substring(0, path.length() - "_block".length()) + "_slab");
        }
        for (String candidate : candidates) {
            if (world.exists("minecraft:" + candidate)) {
                return "minecraft:" + candidate;
            }
        }
        return null;
    }

    /**
     * The rubble a fallen block leaves, or null for none (glass shatters,
     * decoration and plants rot away).
     *
     * @param block the fallen block
     * @param roll a random number from 0 to 1
     * @param world to check which candidates exist
     * @return the rubble block id or null
     */
    static String rubble(AgeBlock block, double roll, AgeWorld world) {
        if (!isStructural(block) || isGlass(block)) {
            return null;
        }
        String path = block.path();
        String pick;
        if (path.contains("stone_brick") || path.equals("stone") || path.equals("smooth_stone")
            || path.startsWith("stone_") || path.equals("chiseled_stone_bricks")) {
            pick = roll < 0.45 ? "cobblestone" : roll < 0.65 ? "gravel" : roll < 0.8 ? "mossy_cobblestone"
                : "cracked_stone_bricks";
        } else if (path.contains("cobblestone")) {
            pick = roll < 0.6 ? "cobblestone" : roll < 0.8 ? "mossy_cobblestone" : "gravel";
        } else if (path.contains("deepslate")) {
            pick = roll < 0.75 ? "cobbled_deepslate" : "gravel";
        } else if (path.contains("blackstone")) {
            pick = roll < 0.8 ? "blackstone" : "gravel";
        } else if (isPlanks(block) || isLog(block) || path.startsWith("stripped_")) {
            if (roll < 0.4) {
                return null;
            }
            String full = fullBlock(block, world);
            return full != null && world.exists(full) ? full : null;
        } else {
            String full = fullBlock(block, world);
            return full != null && world.exists(full) ? full : null;
        }
        String id = "minecraft:" + pick;
        if (world.exists(id)) {
            return id;
        }
        return world.exists("minecraft:cobblestone") ? "minecraft:cobblestone" : null;
    }
}
