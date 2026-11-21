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


package com.sk89q.worldedit.function.generator.shape;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeFlagsTest {

    @Test
    void flagsAfterSettingsArePickedOut() {
        ShapeFlags flags = ShapeFlags.parse(List.of("points=6", "-hb", "0.3", "-r", "45", "-t", "-90"), "hbf", "rt");
        assertTrue(flags.has('h'));
        assertTrue(flags.has('b'));
        assertFalse(flags.has('f'));
        assertEquals(45, flags.value('r', 0));
        assertEquals(-90, flags.value('t', 0));
        assertEquals(List.of("points=6", "0.3"), flags.remaining());
    }

    @Test
    void negativeNumbersAreSettingsNotFlags() {
        ShapeFlags flags = ShapeFlags.parse(List.of("-45", "twist=-90"), "h", "rt");
        assertEquals(List.of("-45", "twist=-90"), flags.remaining());
    }

    @Test
    void wordFlagsTakeTheNextArgument() {
        ShapeFlags flags = ShapeFlags.parse(List.of("loops=3", "-a", "8", "-m", "!air", "-c", "30"), "h", "acs", "m");
        assertEquals("!air", flags.text('m').orElseThrow());
        assertEquals(8, flags.value('a', 0));
        assertEquals(30, flags.value('c', 0));
        assertTrue(flags.text('x').isEmpty());
        assertEquals(List.of("loops=3"), flags.remaining());
        assertThrows(IllegalArgumentException.class, () -> ShapeFlags.parse(List.of("-m"), "h", "a", "m"));
    }

    @Test
    void badFlagsAreReported() {
        assertThrows(IllegalArgumentException.class, () -> ShapeFlags.parse(List.of("-x"), "h", "rt"));
        assertThrows(IllegalArgumentException.class, () -> ShapeFlags.parse(List.of("-r"), "h", "rt"));
        assertThrows(IllegalArgumentException.class, () -> ShapeFlags.parse(List.of("-r", "abc"), "h", "rt"));
    }
}
