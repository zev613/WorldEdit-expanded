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

import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelArtTest {

    private static List<BlockPalette.Entry> palette(String name) {
        return BlockPalette.forName(name, _ -> true);
    }

    @Test
    void palettesHaveUniqueKnownBlocks() {
        Set<String> ids = new HashSet<>();
        for (BlockPalette.Entry entry : BlockPalette.all()) {
            assertTrue(ids.add(entry.id()), "duplicate " + entry.id());
            assertTrue(entry.id().matches("[a-z_]+"), entry.id());
        }
        assertEquals(16, palette("wool").size());
        assertEquals(16, palette("concrete").size());
        assertEquals(32, palette("wool,concrete").size());
        assertEquals(BlockPalette.all().size(), palette("all").size());
        assertEquals(palette("planks"), palette("wood"));
        assertThrows(IllegalArgumentException.class, () -> palette("rainbow"));
        assertThrows(IllegalArgumentException.class, () -> BlockPalette.forName("wool", _ -> false));
        // missing blocks are left out
        assertEquals(15, BlockPalette.forName("wool", id -> !id.equals("red_wool")).size());
    }

    @Test
    void nearestColourLooksRight() {
        ColorMatcher wool = new ColorMatcher(palette("wool"));
        assertEquals("red_wool", wool.nearest(0xC02020).id());
        assertEquals("white_wool", wool.nearest(0xFFFFFF).id());
        assertEquals("black_wool", wool.nearest(0x000000).id());
        assertEquals("blue_wool", wool.nearest(0x2030A0).id());
        assertEquals("lime_wool", wool.nearest(0x80D020).id());
        assertEquals("yellow_wool", wool.nearest(0xFFE040).id());
        ColorMatcher all = new ColorMatcher(palette("all"));
        assertEquals("gold_block", all.nearest(0xF6D03D).id());
        // lab: white is L=100, black L=0
        assertEquals(100, ColorMatcher.toLab(0xFFFFFF)[0], 0.01);
        assertEquals(0, ColorMatcher.toLab(0x000000)[0], 0.01);
        // a published CIEDE2000 test pair (Sharma, Wu and Dalal 2005)
        assertEquals(2.0425, ColorMatcher.deltaE2000(new double[] {50, 2.6772, -79.7751},
            new double[] {50, 0, -82.7485}), 0.0001);
        assertEquals(0, ColorMatcher.deltaE2000(new double[] {40, 10, 10}, new double[] {40, 10, 10}), 1e-9);
    }

    @Test
    void resizeKeepsTheShapeAndAveragesColours() {
        assertEquals(50, PixelArt.heightFor(200, 100, 100));
        assertEquals(1, PixelArt.heightFor(1000, 1, 10));

        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        // left half red, right half: one white and one transparent column
        for (int y = 0; y < 2; y++) {
            image.setRGB(0, y, 0xFFFF0000);
            image.setRGB(1, y, 0xFFFF0000);
            image.setRGB(2, y, 0xFFFFFFFF);
            image.setRGB(3, y, 0x00000000);
        }
        int[][] small = PixelArt.resize(image, 2, 1);
        assertEquals(0xFFFF0000, small[0][0]);
        // half transparent, colour not darkened by the transparent pixels
        assertEquals(0x80, small[0][1] >>> 24);
        assertEquals(0xFFFFFF, small[0][1] & 0xFFFFFF);

        int[][] big = PixelArt.resize(image, 8, 4);
        assertEquals(0xFFFF0000, big[3][3]);
        assertEquals(0, big[0][7] >>> 24);
    }

    @Test
    void transparentPixelsAreSkippedAndTheTopIsUp() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFA02828); // top left red
        image.setRGB(1, 0, 0x00000000); // top right transparent
        image.setRGB(0, 1, 0xFF2040C0); // bottom left blue
        image.setRGB(1, 1, 0xFFFFFFFF); // bottom right white
        PixelArt art = PixelArt.create(image, 2, new ColorMatcher(palette("concrete")));
        assertEquals(2, art.width());
        assertEquals(2, art.height());
        assertNull(art.at(1, 0));
        Map<BlockVector3, String> blocks = art.blocks(new FacingPlane(Direction.NORTH, false));
        assertEquals(3, blocks.size());
        assertEquals("red_concrete", blocks.get(BlockVector3.at(0, 1, 0)));
        assertEquals("blue_concrete", blocks.get(BlockVector3.at(0, 0, 0)));
        assertEquals("white_concrete", blocks.get(BlockVector3.at(1, 0, 0)));

        Map<BlockVector3, String> flat = art.blocks(new FacingPlane(Direction.NORTH, true));
        assertEquals("red_concrete", flat.get(BlockVector3.at(0, 0, -1)));

        assertThrows(IllegalArgumentException.class,
            () -> PixelArt.create(new BufferedImage(1, 2000, BufferedImage.TYPE_INT_RGB), 1, new ColorMatcher(palette("wool"))));
    }
}
