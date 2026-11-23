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

package com.sk89q.worldedit.function.generator.style;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The building styles that ship with WorldEdit.
 */
public final class BuiltinStyles {

    private BuiltinStyles() {
    }

    /**
     * Create the built-in styles, in display order.
     *
     * @return the styles
     */
    public static List<BuildingStyle> create() {
        return List.of(
            style("modern", "White concrete, grey glass ribbon windows, flat roof",
                "white_concrete", "gray_stained_glass", "light_gray_concrete", "white_concrete",
                "smooth_stone", "gray_concrete", "black_concrete", "sea_lantern"),
            style("art_deco", "1930s tower: piers between vertical window strips, setbacks and a stepped crown",
                "white_terracotta", "black_stained_glass", "smooth_quartz", "quartz_pillar",
                "polished_andesite", "cut_copper", "gold_block", "glowstone"),
            style("futuristic", "Rounded tower with cyan glass and glowing bands, topped by a spire",
                "white_concrete", "light_blue_stained_glass", "cyan_terracotta", "iron_block",
                "smooth_quartz", "iron_block", "cyan_concrete", "sea_lantern"),
            style("gothic", "Stone buttresses and pointed lancet windows, with a spire",
                "stone_bricks", "gray_stained_glass", "chiseled_stone_bricks", "deepslate_bricks",
                "dark_oak_planks", "deepslate_tiles", "polished_blackstone", "glowstone"),
            style("brutalist", "Heavy concrete fins and bands with small windows",
                "light_gray_concrete", "tinted_glass", "stone", "gray_concrete",
                "polished_andesite", "stone", "smooth_stone", "glowstone"),
            style("glass", "Curtain-wall tower: glass all over with thin mullions",
                "light_blue_stained_glass", "light_blue_stained_glass", "light_gray_concrete", "light_gray_concrete",
                "smooth_stone", "light_gray_concrete", "black_concrete", "sea_lantern"),
            style("brick", "Classic New York brick block with punched windows and a water tower",
                "bricks", "glass", "smooth_stone", "stone_bricks",
                "oak_planks", "stone_bricks", "dark_oak_planks", "glowstone"),
            style("sandstone", "Warm sandstone with arched windows and arcades",
                "smooth_sandstone", "glass", "cut_sandstone", "chiseled_sandstone",
                "sandstone", "cut_sandstone", "terracotta", "glowstone")
        );
    }

    private static BuildingStyle style(String name, String description, String wall, String window, String trim,
                                       String pillar, String floor, String roof, String accent, String light) {
        Map<StyleRole, String> blocks = new EnumMap<>(StyleRole.class);
        blocks.put(StyleRole.WALL, wall);
        blocks.put(StyleRole.WINDOW, window);
        blocks.put(StyleRole.TRIM, trim);
        blocks.put(StyleRole.PILLAR, pillar);
        blocks.put(StyleRole.FLOOR, floor);
        blocks.put(StyleRole.ROOF, roof);
        blocks.put(StyleRole.ACCENT, accent);
        blocks.put(StyleRole.LIGHT, light);
        return new BuildingStyle(name, name, description, blocks);
    }
}
