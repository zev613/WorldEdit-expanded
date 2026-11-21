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

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Turns an image into blocks: shrinks or enlarges it to a size in blocks, then picks the closest coloured
 * block for each pixel. Pixels that are mostly transparent are left out.
 */
public final class PixelArt {

    /**
     * The widest or tallest pixel art allowed, in blocks.
     */
    public static final int MAX_SIDE = 1024;

    private final BlockPalette.Entry[][] grid;

    private PixelArt(BlockPalette.Entry[][] grid) {
        this.grid = grid;
    }

    /**
     * The height in blocks for a width, keeping the image's shape.
     *
     * @param imageWidth the image width in pixels
     * @param imageHeight the image height in pixels
     * @param width the width in blocks
     * @return the height in blocks
     */
    public static int heightFor(int imageWidth, int imageHeight, int width) {
        return Math.max(1, (int) Math.round((double) width * imageHeight / imageWidth));
    }

    /**
     * Resize an image by averaging the pixels each block covers (or stretching them when enlarging).
     *
     * @param image the image
     * @param width the new width
     * @param height the new height
     * @return {@code [row][column]} ARGB colours, row 0 at the top; colour is not premultiplied
     */
    public static int[][] resize(BufferedImage image, int width, int height) {
        checkArgument(width >= 1 && height >= 1, "size must be at least 1x1");
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        int[] source = image.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
        double scaleX = (double) sourceWidth / width;
        double scaleY = (double) sourceHeight / height;
        int[][] result = new int[height][width];
        for (int row = 0; row < height; row++) {
            double top = row * scaleY;
            double bottom = top + scaleY;
            for (int column = 0; column < width; column++) {
                double left = column * scaleX;
                double right = left + scaleX;
                double alpha = 0;
                double red = 0;
                double green = 0;
                double blue = 0;
                double area = 0;
                for (int sy = (int) Math.floor(top); sy < Math.min(sourceHeight, Math.ceil(bottom)); sy++) {
                    double wy = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) Math.floor(left); sx < Math.min(sourceWidth, Math.ceil(right)); sx++) {
                        double weight = wy * (Math.min(right, sx + 1) - Math.max(left, sx));
                        if (weight <= 0) {
                            continue;
                        }
                        int argb = source[sy * sourceWidth + sx];
                        double a = ((argb >>> 24) & 0xFF) / 255.0;
                        area += weight;
                        alpha += weight * a;
                        red += weight * a * ((argb >> 16) & 0xFF);
                        green += weight * a * ((argb >> 8) & 0xFF);
                        blue += weight * a * (argb & 0xFF);
                    }
                }
                if (alpha <= 0 || area <= 0) {
                    continue;
                }
                int a = clamp(alpha / area * 255);
                result[row][column] = a << 24 | clamp(red / alpha) << 16 | clamp(green / alpha) << 8 | clamp(blue / alpha);
            }
        }
        return result;
    }

    private static int clamp(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    /**
     * Make pixel art from an image.
     *
     * @param image the image
     * @param width the width in blocks
     * @param matcher picks the blocks
     * @return the pixel art
     */
    public static PixelArt create(BufferedImage image, int width, ColorMatcher matcher) {
        int height = heightFor(image.getWidth(), image.getHeight(), width);
        checkArgument(width <= MAX_SIDE && height <= MAX_SIDE,
            "That would be %sx%s blocks; at most %s each way", width, height, MAX_SIDE);
        int[][] colours = resize(image, width, height);
        BlockPalette.Entry[][] grid = new BlockPalette.Entry[height][width];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int argb = colours[row][column];
                if ((argb >>> 24) >= 128) {
                    grid[row][column] = matcher.nearest(argb);
                }
            }
        }
        return new PixelArt(grid);
    }

    /**
     * The width in blocks.
     *
     * @return the width
     */
    public int width() {
        return grid.length == 0 ? 0 : grid[0].length;
    }

    /**
     * The height in blocks.
     *
     * @return the height
     */
    public int height() {
        return grid.length;
    }

    /**
     * The block for a pixel.
     *
     * @param column the column, 0 = left
     * @param row the row, 0 = top
     * @return the block, or null if the pixel is transparent
     */
    public BlockPalette.Entry at(int column, int row) {
        return grid[row][column];
    }

    /**
     * The blocks of the picture, from its bottom-left corner.
     *
     * @param plane which way the picture faces
     * @return block ids by offset
     */
    public Map<BlockVector3, String> blocks(FacingPlane plane) {
        Map<BlockVector3, String> blocks = new LinkedHashMap<>();
        int height = height();
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width(); column++) {
                BlockPalette.Entry entry = grid[row][column];
                if (entry != null) {
                    blocks.put(plane.map(column, height - 1 - row, 0), entry.id());
                }
            }
        }
        return blocks;
    }
}
