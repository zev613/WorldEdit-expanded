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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopyLimitTest {

    @Test
    void sameLimitAsScale() {
        assertEquals(ScaleResampler.MAX_VOLUME, CopyLimit.MAX_BLOCKS);
        assertEquals(CopyLimit.MAX_BLOCKS, GridLayout.MAX_VOLUME);
    }

    @Test
    void radialOfAScaledClipboardIsRefused() {
        // //copy 31x29x31, //scale 4, //radial 360
        long perCopy = 124L * 116 * 124;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CopyLimit.check(360, perCopy));
        assertEquals("That would be 642,101,760 blocks (360 copies of up to 1,783,616 blocks); at most "
            + "67,108,864 are allowed. Use fewer copies or a smaller clipboard.", e.getMessage());
        assertDoesNotThrow(() -> CopyLimit.check(37, perCopy));
        assertThrows(IllegalArgumentException.class, () -> CopyLimit.check(Long.MAX_VALUE, perCopy));
    }

    @Test
    void gridChecksTheTotal() {
        assertThrows(IllegalArgumentException.class, () -> GridLayout.check(100, 100, 0, 0, 0, 7000));
        assertDoesNotThrow(() -> GridLayout.check(100, 100, 0, 0, 0, 6000));
    }

    @Test
    void counts() {
        assertEquals("1 copy", Counts.of(1, "copy", "copies"));
        assertEquals("0 copies", Counts.of(0, "copy", "copies"));
        assertEquals("16,384 blocks", Counts.of(16384, "block"));
        assertEquals("1 plant", Counts.of(1, "plant"));
    }
}
