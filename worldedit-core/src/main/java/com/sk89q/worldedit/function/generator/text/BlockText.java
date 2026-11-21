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

package com.sk89q.worldedit.function.generator.text;

import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Lays out text in the {@link BlockFont} and turns it into block positions.
 */
public final class BlockText {

    /**
     * The longest text allowed.
     */
    public static final int MAX_LENGTH = 256;

    private final List<int[]> pixels;
    private final int width;
    private final Set<Character> unsupported;

    private BlockText(List<int[]> pixels, int width, Set<Character> unsupported) {
        this.pixels = pixels;
        this.width = width;
        this.unsupported = unsupported;
    }

    /**
     * Lay out a line of text, one pixel gap between characters.
     *
     * @param text the text
     * @return the layout
     */
    public static BlockText layout(String text) {
        checkArgument(!text.isBlank(), "There is no text to build");
        checkArgument(text.length() <= MAX_LENGTH, "The text is too long (at most " + MAX_LENGTH + " characters)");
        List<int[]> pixels = new ArrayList<>();
        Set<Character> unsupported = new LinkedHashSet<>();
        int cursor = 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                cursor += BlockFont.SPACE_WIDTH;
                continue;
            }
            if (!BlockFont.supports(c)) {
                unsupported.add(c);
            }
            BlockFont.Glyph glyph = BlockFont.glyph(c);
            for (int x = 0; x < glyph.width(); x++) {
                for (int y = 0; y < BlockFont.HEIGHT; y++) {
                    if (glyph.filled(x, y)) {
                        // y counts up from the bottom row
                        pixels.add(new int[] {cursor + x, BlockFont.HEIGHT - 1 - y});
                    }
                }
            }
            width = cursor + glyph.width();
            cursor += glyph.width() + 1;
        }
        return new BlockText(pixels, width, unsupported);
    }

    /**
     * The filled pixels, each as {@code {x, y}} with x from the left and y up from the bottom.
     *
     * @return the pixels
     */
    public List<int[]> pixels() {
        return pixels;
    }

    /**
     * The width of the text in pixels.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Characters that had no glyph and were drawn as {@code ?}.
     *
     * @return the characters
     */
    public Set<Character> unsupported() {
        return unsupported;
    }

    /**
     * The block offsets of the text, from its bottom-left corner.
     *
     * @param plane which way the text faces
     * @param scale blocks per pixel
     * @param depth thickness in blocks
     * @return the offsets
     */
    public List<BlockVector3> blocks(FacingPlane plane, int scale, int depth) {
        checkArgument(scale >= 1, "scale must be at least 1");
        checkArgument(depth >= 1, "depth must be at least 1");
        List<BlockVector3> blocks = new ArrayList<>(pixels.size() * scale * scale * depth);
        for (int[] pixel : pixels) {
            for (int sx = 0; sx < scale; sx++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int w = 0; w < depth; w++) {
                        blocks.add(plane.map(pixel[0] * scale + sx, pixel[1] * scale + sy, w));
                    }
                }
            }
        }
        return blocks;
    }
}
