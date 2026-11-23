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

package com.sk89q.worldedit.history.browser;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The small bit of metadata kept with each edit in the undo history, for {@code //history}.
 *
 * @param serial a number that identifies the edit while the server runs
 * @param description what made the edit, e.g. {@code //set stone}
 * @param timestamp when it was remembered, in milliseconds since the epoch
 */
public record HistoryEntryInfo(long serial, String description, long timestamp) {

    /**
     * The longest description kept.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 48;

    /**
     * The description used when nothing better is known.
     */
    public static final String UNKNOWN = "edit";

    private static final AtomicLong SERIALS = new AtomicLong();

    /**
     * Create metadata for a newly remembered edit.
     *
     * @param description what made the edit (a command line, or {@code null})
     * @param timestamp when
     * @return the metadata
     */
    public static HistoryEntryInfo create(@Nullable String description, long timestamp) {
        return new HistoryEntryInfo(SERIALS.incrementAndGet(), describe(description), timestamp);
    }

    /**
     * Tidy a command line into a short description: whitespace collapsed and
     * long text cut off with {@code ...}.
     *
     * @param raw the command line, or {@code null}
     * @return the description
     */
    public static String describe(@Nullable String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String text = raw.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return UNKNOWN;
        }
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            text = text.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
        }
        return text;
    }
}
