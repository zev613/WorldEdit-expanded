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

package com.sk89q.worldedit.function.clipboard;

import com.sk89q.worldedit.util.formatting.Counts;

import java.util.List;

/**
 * The limit on how many blocks repeated pastes ({@code //radial}, {@code //grid},
 * {@code //stackpath}) may cover together: the same limit {@code //scale} uses.
 */
public final class CopyLimit {

    /** The most blocks all copies together may cover. */
    public static final long MAX_BLOCKS = ScaleResampler.MAX_VOLUME;

    private CopyLimit() {
    }

    /**
     * Get the number of blocks a placer covers (its bounding box, after turning).
     *
     * @param placer the placer
     * @return the volume of its bounding box
     */
    public static long volume(TransformedClipboardPlacer placer) {
        return span(placer.getMinimum().x(), placer.getMaximum().x())
            * span(placer.getMinimum().y(), placer.getMaximum().y())
            * span(placer.getMinimum().z(), placer.getMaximum().z());
    }

    private static long span(int min, int max) {
        return (long) max - min + 1;
    }

    /**
     * Check copies that are all the same size.
     *
     * @param copies the number of copies
     * @param blocksPerCopy the blocks one copy covers
     * @throws IllegalArgumentException with a friendly message when it is too much
     */
    public static void check(long copies, long blocksPerCopy) {
        long total = blocksPerCopy > 0 && copies > Long.MAX_VALUE / blocksPerCopy
            ? Long.MAX_VALUE : copies * blocksPerCopy;
        check(copies, blocksPerCopy, total);
    }

    /**
     * Check placers before placing them.
     *
     * @param placers the placers, one per copy
     * @throws IllegalArgumentException with a friendly message when they cover too much
     */
    public static void check(List<TransformedClipboardPlacer> placers) {
        long total = 0;
        long largest = 0;
        for (TransformedClipboardPlacer placer : placers) {
            long volume = volume(placer);
            largest = Math.max(largest, volume);
            total = total > Long.MAX_VALUE - volume ? Long.MAX_VALUE : total + volume;
        }
        check(placers.size(), largest, total);
    }

    /**
     * Check a total.
     *
     * @param copies the number of copies
     * @param blocksPerCopy the blocks the largest copy covers
     * @param total the blocks all copies cover
     * @throws IllegalArgumentException with a friendly message when it is too much
     */
    public static void check(long copies, long blocksPerCopy, long total) {
        if (total > MAX_BLOCKS) {
            throw new IllegalArgumentException("That would be " + format(total) + " blocks ("
                + plural(copies, "copy", "copies") + " of up to " + format(blocksPerCopy)
                + " blocks); at most " + format(MAX_BLOCKS)
                + " are allowed. Use fewer copies or a smaller clipboard.");
        }
    }

    /**
     * Format a whole number with thousands separators, e.g. 67,108,864.
     *
     * @param value the number
     * @return the text
     */
    public static String format(long value) {
        return Counts.number(value);
    }

    /**
     * Format a count with the right word, e.g. "1 copy" or "1,200 copies".
     *
     * @param count the count
     * @param one the word for one
     * @param many the word for any other count
     * @return the text
     */
    public static String plural(long count, String one, String many) {
        return Counts.of(count, one, many);
    }
}
