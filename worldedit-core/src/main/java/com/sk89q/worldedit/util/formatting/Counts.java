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

package com.sk89q.worldedit.util.formatting;

import java.util.Locale;

/**
 * Counts in messages: one number format (with thousands separators) and the right word for one
 * or many, e.g. "1 copy", "2 copies", "16,384 blocks".
 */
public final class Counts {

    private Counts() {
    }

    /**
     * Format a whole number with thousands separators, e.g. 67,108,864.
     *
     * @param value the number
     * @return the text
     */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Format a count with the right word, e.g. "1 copy" or "1,200 copies".
     *
     * @param count the count
     * @param one the word for one
     * @param many the word for any other count
     * @return the text
     */
    public static String of(long count, String one, String many) {
        return number(count) + " " + (count == 1 ? one : many);
    }

    /**
     * Format a count of a word that takes an "s" for many, e.g. "1 block" or "3 blocks".
     *
     * @param count the count
     * @param one the word for one
     * @return the text
     */
    public static String of(long count, String one) {
        return of(count, one, one + "s");
    }
}
