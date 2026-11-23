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

import com.sk89q.worldedit.history.browser.HistoryListing.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryListingTest {

    private static final long NOW = 1_700_000_000_000L;

    private static Row row(String ref, boolean undone, boolean inMemory) {
        return new Row(ref, "//set " + ref, 10, NOW - 60_000, undone, inMemory);
    }

    /**
     * Two saved edits from disk (d1 done, d2 done), then memory edits m1, m2 done and m3, m4 undone.
     */
    private static HistoryListing sample() {
        return new HistoryListing(List.of(
            row("d1", false, false),
            row("d2", false, false),
            row("m1", false, true),
            row("m2", false, true),
            row("m3", true, true),
            row("m4", true, true)));
    }

    @Test
    void numbersEditsNewestFirst() {
        HistoryListing listing = sample();
        assertEquals("m4", listing.newestFirst().getFirst().ref());
        assertEquals("d1", listing.newestFirst().getLast().ref());
        assertEquals(1, listing.numberOf(5));
        assertEquals(6, listing.numberOf(0));
    }

    @Test
    void findsByNumberOrReference() {
        HistoryListing listing = sample();
        assertEquals(5, listing.find("1"));
        assertEquals(0, listing.find("6"));
        assertEquals(-1, listing.find("7"));
        assertEquals(-1, listing.find("0"));
        assertEquals(2, listing.find("M1"));
        assertEquals(-1, listing.find("m99"));
        assertEquals(-1, listing.find("99999999999999999999"));
    }

    @Test
    void undoToHereCountsDoneEditsFromThereOn() {
        HistoryListing listing = sample();
        assertEquals(1, listing.undoCountTo(3)); // m2: the newest done edit
        assertEquals(2, listing.undoCountTo(2)); // m1
        assertEquals(4, listing.undoCountTo(0)); // d1: everything
        assertEquals(0, listing.undoCountTo(4)); // m3 is already undone
    }

    @Test
    void redoToHereCountsUndoneEditsUpToThere() {
        HistoryListing listing = sample();
        assertEquals(1, listing.redoCountTo(4)); // m3: the oldest undone edit
        assertEquals(2, listing.redoCountTo(5)); // m4
        assertEquals(0, listing.redoCountTo(3)); // m2 isn't undone
    }

    @Test
    void undoneSavedEditsAreRedoneFirst() {
        HistoryListing listing = new HistoryListing(List.of(
            row("d1", false, false),
            row("d2", true, false),
            row("m1", true, true)));
        assertEquals(1, listing.redoCountTo(1));
        assertEquals(2, listing.redoCountTo(2));
        assertEquals(1, listing.undoCountTo(0));
    }

    @Test
    void pagesAreClamped() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            rows.add(row("m" + i, false, true));
        }
        HistoryListing listing = new HistoryListing(rows);
        assertEquals(3, listing.pageCount(8));
        assertEquals(8, listing.page(1, 8).size());
        assertEquals("m18", listing.page(1, 8).getFirst().ref());
        assertEquals(3, listing.page(3, 8).size());
        assertEquals(3, listing.page(99, 8).size());
        assertEquals(1, listing.clampPage(-4, 8));
        assertEquals(1, new HistoryListing(List.of()).pageCount(8));
        assertTrue(new HistoryListing(List.of()).page(1, 8).isEmpty());
    }

    @Test
    void formatsRelativeTimes() {
        assertEquals("just now", HistoryListing.relativeTime(NOW - 2_000, NOW));
        assertEquals("just now", HistoryListing.relativeTime(NOW + 5_000, NOW));
        assertEquals("42 s ago", HistoryListing.relativeTime(NOW - 42_000, NOW));
        assertEquals("3 min ago", HistoryListing.relativeTime(NOW - 3 * 60_000 - 10_000, NOW));
        assertEquals("2 h ago", HistoryListing.relativeTime(NOW - 2 * 3_600_000 - 1, NOW));
        assertEquals("1 day ago", HistoryListing.relativeTime(NOW - 25 * 3_600_000L, NOW));
        assertEquals("5 days ago", HistoryListing.relativeTime(NOW - 5 * 86_400_000L, NOW));
        assertEquals("some time ago", HistoryListing.relativeTime(-1, NOW));
    }

    @Test
    void formatsBlockCountsAndLines() {
        assertEquals("1 block", HistoryListing.formatBlocks(1));
        assertEquals("12,345 blocks", HistoryListing.formatBlocks(12_345));
        assertEquals("? blocks", HistoryListing.formatBlocks(-1));
        assertEquals("1. //set m3 - 10 blocks, 1 min ago (undone)",
            HistoryListing.formatLine(1, row("m3", true, true), NOW));
        assertEquals("2. //set d1 - 10 blocks, 1 min ago [saved]",
            HistoryListing.formatLine(2, row("d1", false, false), NOW));
    }

    @Test
    void readsTimesFromDiskIds() {
        assertEquals(1_700_000_000_123L, HistoryListing.timeFromDiskId("001700000000123-000004"));
        assertEquals(-1, HistoryListing.timeFromDiskId("garbage-1"));
        assertEquals(-1, HistoryListing.timeFromDiskId(null));
    }

    @Test
    void describesEditsShortly() {
        assertEquals("edit", HistoryEntryInfo.describe(null));
        assertEquals("edit", HistoryEntryInfo.describe("   "));
        assertEquals("//set stone", HistoryEntryInfo.describe("  //set   stone "));
        String longCommand = "//replace " + "stone,".repeat(20);
        String described = HistoryEntryInfo.describe(longCommand);
        assertEquals(HistoryEntryInfo.MAX_DESCRIPTION_LENGTH, described.length());
        assertTrue(described.endsWith("..."));
        HistoryEntryInfo first = HistoryEntryInfo.create("//set stone", NOW);
        HistoryEntryInfo second = HistoryEntryInfo.create("//set stone", NOW);
        assertNotEquals(first.serial(), second.serial());
        assertEquals(NOW, first.timestamp());
    }
}
