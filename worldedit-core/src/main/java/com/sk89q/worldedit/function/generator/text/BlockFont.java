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

import com.google.common.collect.ImmutableMap;

import java.util.Locale;
import java.util.Map;

/**
 * A built-in 5x7 pixel font for building block letters.
 *
 * <p>Lower case letters are drawn as capitals. Punctuation is narrower than letters.</p>
 */
public final class BlockFont {

    /**
     * The height of every glyph in pixels.
     */
    public static final int HEIGHT = 7;

    /**
     * The width of letters and digits in pixels.
     */
    public static final int WIDTH = 5;

    /**
     * The width of a space in pixels.
     */
    public static final int SPACE_WIDTH = 3;

    private static final Map<Character, Glyph> GLYPHS;

    /**
     * One character of the font.
     */
    public static final class Glyph {

        private final int width;
        private final int[] rows;

        private Glyph(int width, int[] rows) {
            this.width = width;
            this.rows = rows.clone();
        }

        /**
         * The width in pixels.
         *
         * @return the width
         */
        public int width() {
            return width;
        }

        /**
         * Whether a pixel is filled.
         *
         * @param x the column, 0 = left
         * @param y the row, 0 = top
         * @return true if filled
         */
        public boolean filled(int x, int y) {
            return (rows[y] >> (width - 1 - x) & 1) != 0;
        }
    }

    static {
        ImmutableMap.Builder<Character, Glyph> builder = ImmutableMap.builder();
        String[] data = {
            "A", ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#",
            "B", "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.",
            "C", ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.",
            "D", "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.",
            "E", "#####", "#....", "#....", "####.", "#....", "#....", "#####",
            "F", "#####", "#....", "#....", "####.", "#....", "#....", "#....",
            "G", ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####",
            "H", "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#",
            "I", ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.",
            "J", "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..",
            "K", "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#",
            "L", "#....", "#....", "#....", "#....", "#....", "#....", "#####",
            "M", "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#",
            "N", "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#",
            "O", ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.",
            "P", "####.", "#...#", "#...#", "####.", "#....", "#....", "#....",
            "Q", ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#",
            "R", "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#",
            "S", ".####", "#....", "#....", ".###.", "....#", "....#", "####.",
            "T", "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..",
            "U", "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.",
            "V", "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..",
            "W", "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.",
            "X", "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#",
            "Y", "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..",
            "Z", "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####",
            "0", ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.",
            "1", "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.",
            "2", ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####",
            "3", "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.",
            "4", "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.",
            "5", "#####", "#....", "####.", "....#", "....#", "#...#", ".###.",
            "6", "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.",
            "7", "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...",
            "8", ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.",
            "9", ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..",
            ".", ".....", ".....", ".....", ".....", ".....", ".....", "..#..",
            ",", ".....", ".....", ".....", ".....", ".....", "..#..", ".#...",
            "!", "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..",
            "?", ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..",
            ":", ".....", ".....", "..#..", ".....", ".....", "..#..", ".....",
            ";", ".....", ".....", "..#..", ".....", ".....", "..#..", ".#...",
            "'", "..#..", "..#..", ".....", ".....", ".....", ".....", ".....",
            "\"", ".#.#.", ".#.#.", ".....", ".....", ".....", ".....", ".....",
            "-", ".....", ".....", ".....", ".###.", ".....", ".....", ".....",
            "+", ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....",
            "=", ".....", ".....", "#####", ".....", "#####", ".....", ".....",
            "/", ".....", "....#", "...#.", "..#..", ".#...", "#....", ".....",
            "\\", ".....", "#....", ".#...", "..#..", "...#.", "....#", ".....",
            "(", "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.",
            ")", ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...",
            "[", ".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###.",
            "]", ".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###.",
            "_", ".....", ".....", ".....", ".....", ".....", ".....", "#####",
            "#", ".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#.",
            "&", ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#",
            "*", ".....", "..#..", "#.#.#", ".###.", "#.#.#", "..#..", ".....",
            "<", "...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#.",
            ">", ".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#...",
            "@", ".###.", "#...#", "#.###", "#.#.#", "#.###", "#....", ".###.",
            "%", "##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##",
            "$", "..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#..",
            "^", "..#..", ".#.#.", "#...#", ".....", ".....", ".....", ".....",
            "|", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..",
        };
        for (int i = 0; i < data.length; i += HEIGHT + 1) {
            char c = data[i].charAt(0);
            String[] rows = new String[HEIGHT];
            System.arraycopy(data, i + 1, rows, 0, HEIGHT);
            builder.put(c, toGlyph(rows, Character.isLetterOrDigit(c)));
        }
        GLYPHS = builder.build();
    }

    private BlockFont() {
    }

    private static Glyph toGlyph(String[] rows, boolean fullWidth) {
        int left = 0;
        int right = WIDTH - 1;
        if (!fullWidth) {
            // trim empty columns from punctuation so it isn't as wide as a letter
            while (left < right && emptyColumn(rows, left)) {
                left++;
            }
            while (right > left && emptyColumn(rows, right)) {
                right--;
            }
        }
        int width = right - left + 1;
        int[] bits = new int[HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            if (rows[y].length() != WIDTH) {
                throw new IllegalStateException("Bad glyph row: " + rows[y]);
            }
            for (int x = left; x <= right; x++) {
                if (rows[y].charAt(x) == '#') {
                    bits[y] |= 1 << (right - x);
                }
            }
        }
        return new Glyph(width, bits);
    }

    private static boolean emptyColumn(String[] rows, int x) {
        for (String row : rows) {
            if (row.charAt(x) == '#') {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a character can be drawn (spaces count).
     *
     * @param c the character
     * @return true if it has a glyph
     */
    public static boolean supports(char c) {
        return c == ' ' || GLYPHS.containsKey(normalize(c));
    }

    /**
     * The glyph of a character, or of {@code ?} if there is none. Not for spaces.
     *
     * @param c the character
     * @return the glyph
     */
    public static Glyph glyph(char c) {
        Glyph glyph = GLYPHS.get(normalize(c));
        return glyph == null ? GLYPHS.get('?') : glyph;
    }

    private static char normalize(char c) {
        return String.valueOf(c).toUpperCase(Locale.ROOT).charAt(0);
    }
}
