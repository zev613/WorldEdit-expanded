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

package com.sk89q.worldedit.function.generator.image;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The blocks pixel art can be built from, with the average colour of each block's texture.
 */
public final class BlockPalette {

    /**
     * A block and its colour.
     *
     * @param id the block id
     * @param rgb the average colour as {@code 0xRRGGBB}
     * @param group the palette it belongs to
     */
    public record Entry(String id, int rgb, String group) {

        public Entry {
            checkNotNull(id);
            checkNotNull(group);
        }
    }

    /**
     * The palette names, apart from {@code all}.
     */
    public static final List<String> GROUPS = List.of("wool", "concrete", "terracotta", "planks", "stone", "other");

    private static final List<Entry> ENTRIES;

    static {
        ImmutableList.Builder<Entry> builder = ImmutableList.builder();
        String[] colours = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        int[] wool = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C527, 0x70B919, 0xED8DAC, 0x3E4447,
            0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519};
        int[] concrete = {0xCFD5D6, 0xE06100, 0xA9309F, 0x2389C6, 0xF0AF15, 0x5EA818, 0xD5658E, 0x36393D,
            0x7D7D73, 0x157788, 0x641F9C, 0x2C2E8F, 0x603B1F, 0x495B24, 0x8E2020, 0x080A0F};
        int[] terracotta = {0xD1B2A1, 0xA15325, 0x95586C, 0x716C89, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23,
            0x876A61, 0x565B5B, 0x764656, 0x4A3B5B, 0x4D3323, 0x4C532A, 0x8F3D2E, 0x251610};
        for (int i = 0; i < colours.length; i++) {
            builder.add(new Entry(colours[i] + "_wool", wool[i], "wool"));
            builder.add(new Entry(colours[i] + "_concrete", concrete[i], "concrete"));
            builder.add(new Entry(colours[i] + "_terracotta", terracotta[i], "terracotta"));
        }
        builder.add(new Entry("terracotta", 0x985E43, "terracotta"));

        Object[] others = {
            "oak_planks", 0xA2824E, "planks",
            "spruce_planks", 0x725430, "planks",
            "birch_planks", 0xC0AF79, "planks",
            "jungle_planks", 0xA07350, "planks",
            "acacia_planks", 0xA85A32, "planks",
            "dark_oak_planks", 0x422B14, "planks",
            "mangrove_planks", 0x753630, "planks",
            "cherry_planks", 0xE2B2AC, "planks",
            "bamboo_planks", 0xC1AD50, "planks",
            "crimson_planks", 0x653046, "planks",
            "warped_planks", 0x2B6863, "planks",
            "stone", 0x7D7D7D, "stone",
            "cobblestone", 0x7F7F7F, "stone",
            "smooth_stone", 0x9E9E9E, "stone",
            "stone_bricks", 0x7A797A, "stone",
            "andesite", 0x888888, "stone",
            "diorite", 0xBCBCBC, "stone",
            "granite", 0x956755, "stone",
            "polished_granite", 0x9A6A59, "stone",
            "deepslate", 0x505052, "stone",
            "cobbled_deepslate", 0x4D4D50, "stone",
            "tuff", 0x6C6D66, "stone",
            "calcite", 0xDFE0DC, "stone",
            "blackstone", 0x2A2429, "stone",
            "sandstone", 0xD8CB9B, "stone",
            "red_sandstone", 0xB5611F, "stone",
            "bricks", 0x966153, "stone",
            "mud_bricks", 0x89674F, "stone",
            "quartz_block", 0xEBE5DE, "stone",
            "obsidian", 0x0F0A18, "stone",
            "netherrack", 0x612626, "stone",
            "nether_bricks", 0x2C151A, "stone",
            "end_stone", 0xDBDE9E, "stone",
            "prismarine", 0x639C97, "stone",
            "dark_prismarine", 0x335B4B, "stone",
            "purpur_block", 0xA97DA9, "stone",
            "snow_block", 0xF9FEFE, "other",
            "clay", 0xA0A6B3, "other",
            "dirt", 0x866043, "other",
            "packed_ice", 0x8DB4FA, "other",
            "gold_block", 0xF6D03D, "other",
            "iron_block", 0xDCDCDC, "other",
            "diamond_block", 0x62EDE4, "other",
            "emerald_block", 0x2ACB57, "other",
            "lapis_block", 0x1E438C, "other",
            "redstone_block", 0xAF1805, "other",
            "coal_block", 0x100F0F, "other",
            "copper_block", 0xC06B4F, "other",
            "sponge", 0xC3C04A, "other",
            "hay_block", 0xA68826, "other",
            "melon", 0x6F911E, "other",
            "pumpkin", 0xC67618, "other",
            "slime_block", 0x6FC05B, "other",
            "honeycomb_block", 0xE5941D, "other",
            "moss_block", 0x596D2D, "other",
        };
        for (int i = 0; i < others.length; i += 3) {
            builder.add(new Entry((String) others[i], (Integer) others[i + 1], (String) others[i + 2]));
        }
        ENTRIES = builder.build();
    }

    private BlockPalette() {
    }

    /**
     * Every block in every palette.
     *
     * @return the entries
     */
    public static List<Entry> all() {
        return ENTRIES;
    }

    /**
     * The blocks of a palette.
     *
     * @param names {@code all}, a palette name, or several separated by commas (e.g. {@code wool,concrete})
     * @param exists which block ids exist; others are left out
     * @return the entries
     * @throws IllegalArgumentException if a name is unknown or no blocks are left
     */
    public static List<Entry> forName(String names, Predicate<String> exists) {
        Set<String> groups = new LinkedHashSet<>();
        for (String raw : Splitter.on(',').split(names.toLowerCase(Locale.ROOT))) {
            String name = raw.trim();
            switch (name) {
                case "all", "" -> groups.addAll(GROUPS);
                case "wood" -> groups.add("planks");
                case "misc" -> groups.add("other");
                default -> {
                    if (!GROUPS.contains(name)) {
                        throw new IllegalArgumentException("Unknown palette '" + name + "'. Palettes: all, "
                            + String.join(", ", GROUPS) + " (combine them with commas, e.g. wool,concrete)");
                    }
                    groups.add(name);
                }
            }
        }
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (groups.contains(entry.group()) && exists.test(entry.id())) {
                result.add(entry);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("None of the blocks in that palette exist in this game version");
        }
        return result;
    }
}
