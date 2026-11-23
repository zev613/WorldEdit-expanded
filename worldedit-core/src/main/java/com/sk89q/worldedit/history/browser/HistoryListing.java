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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A player's undo history as {@code //history} shows it: edits saved on disk from
 * before a restart, then the edits in memory, oldest first.
 *
 * <p>Edits are numbered newest first (1 = the newest). An edit that is undone can be redone.
 * {@code //undo} always undoes the newest edit that isn't undone, and {@code //redo} redoes the
 * oldest undone edit, so "undo to here" and "redo to here" are just counts.</p>
 */
public final class HistoryListing {

    /**
     * Prefix of references to edits in memory.
     */
    public static final String MEMORY_PREFIX = "m";

    /**
     * Prefix of references to edits only on disk.
     */
    public static final String DISK_PREFIX = "d";

    /**
     * One edit.
     *
     * @param ref a reference that stays valid while the edit is in the history
     * @param description what made it
     * @param blocks the number of block changes, or -1 if unknown
     * @param timestamp when it was made (milliseconds), or -1 if unknown
     * @param undone whether it is undone (and can be redone)
     * @param inMemory whether it is in memory (otherwise only on disk)
     */
    public record Row(String ref, String description, int blocks, long timestamp, boolean undone, boolean inMemory) {
    }

    private final List<Row> oldestFirst;

    /**
     * Create a listing.
     *
     * @param oldestFirst the edits, oldest first
     */
    public HistoryListing(List<Row> oldestFirst) {
        this.oldestFirst = List.copyOf(oldestFirst);
    }

    /**
     * Get the number of edits.
     *
     * @return the size
     */
    public int size() {
        return oldestFirst.size();
    }

    /**
     * Get the edits, newest first; the edit at index {@code i} has number {@code i + 1}.
     *
     * @return the edits
     */
    public List<Row> newestFirst() {
        return oldestFirst.reversed();
    }

    /**
     * Find an edit by its reference or by its number (1 = newest).
     *
     * @param refOrNumber a reference such as {@code m12}, or a number
     * @return the index (oldest first), or -1
     */
    public int find(String refOrNumber) {
        String key = refOrNumber.trim().toLowerCase(Locale.ROOT);
        if (!key.isEmpty() && key.chars().allMatch(Character::isDigit)) {
            try {
                int number = Integer.parseInt(key);
                return number >= 1 && number <= size() ? size() - number : -1;
            } catch (NumberFormatException _) {
                return -1;
            }
        }
        for (int i = 0; i < oldestFirst.size(); i++) {
            if (oldestFirst.get(i).ref().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get an edit.
     *
     * @param index the index, oldest first
     * @return the edit
     */
    public Row get(int index) {
        return oldestFirst.get(index);
    }

    /**
     * Get the number (1 = newest) of an edit.
     *
     * @param index the index, oldest first
     * @return the number
     */
    public int numberOf(int index) {
        return size() - index;
    }

    /**
     * How many {@code //undo}s undo the edit at {@code index} and every newer edit.
     *
     * @param index the index, oldest first
     * @return the count, 0 if the edit is already undone
     */
    public int undoCountTo(int index) {
        if (get(index).undone()) {
            return 0;
        }
        int count = 0;
        for (int i = index; i < oldestFirst.size(); i++) {
            if (!oldestFirst.get(i).undone()) {
                count++;
            }
        }
        return count;
    }

    /**
     * How many {@code //redo}s redo every undone edit up to and including the one at {@code index}.
     *
     * @param index the index, oldest first
     * @return the count, 0 if the edit isn't undone
     */
    public int redoCountTo(int index) {
        if (!get(index).undone()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i <= index; i++) {
            if (oldestFirst.get(i).undone()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get the number of pages.
     *
     * @param perPage edits per page
     * @return the page count, at least 1
     */
    public int pageCount(int perPage) {
        return Math.max(1, (size() + perPage - 1) / perPage);
    }

    /**
     * Get one page of edits, newest first.
     *
     * @param page the page, starting at 1 (clamped to the valid range)
     * @param perPage edits per page
     * @return the edits
     */
    public List<Row> page(int page, int perPage) {
        int clamped = clampPage(page, perPage);
        List<Row> newest = newestFirst();
        int from = (clamped - 1) * perPage;
        int to = Math.min(newest.size(), from + perPage);
        return from >= to ? List.of() : new ArrayList<>(newest.subList(from, to));
    }

    /**
     * Clamp a page number to the valid range.
     *
     * @param page the page
     * @param perPage edits per page
     * @return the page, between 1 and {@link #pageCount(int)}
     */
    public int clampPage(int page, int perPage) {
        return Math.max(1, Math.min(page, pageCount(perPage)));
    }

    /**
     * Format how long ago something happened, e.g. "just now", "42 s ago", "3 min ago",
     * "2 h ago" or "5 days ago".
     *
     * @param then the time (milliseconds), or -1 if unknown
     * @param now the current time (milliseconds)
     * @return the text
     */
    public static String relativeTime(long then, long now) {
        if (then < 0) {
            return "some time ago";
        }
        long seconds = Math.max(0, (now - then) / 1000);
        if (seconds < 5) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + " s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        long days = hours / 24;
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    /**
     * Format a block count, e.g. "1 block", "12,345 blocks" or "? blocks".
     *
     * @param blocks the count, or -1 if unknown
     * @return the text
     */
    public static String formatBlocks(int blocks) {
        if (blocks < 0) {
            return "? blocks";
        }
        return String.format(Locale.ROOT, "%,d %s", blocks, blocks == 1 ? "block" : "blocks");
    }

    /**
     * Format one line of the listing (without the clickable parts).
     *
     * @param number the number (1 = newest)
     * @param row the edit
     * @param now the current time
     * @return the text
     */
    public static String formatLine(int number, Row row, long now) {
        return number + ". " + row.description() + " - " + formatBlocks(row.blocks()) + ", "
            + relativeTime(row.timestamp(), now) + (row.undone() ? " (undone)" : "")
            + (row.inMemory() ? "" : " [saved]");
    }

    /**
     * Get the time encoded in a disk history id ({@code <millis>-<sequence>}).
     *
     * @param id the id
     * @return the time, or -1
     */
    public static long timeFromDiskId(@Nullable String id) {
        if (id == null) {
            return -1;
        }
        int dash = id.indexOf('-');
        try {
            return Long.parseLong(dash < 0 ? id : id.substring(0, dash));
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}
