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

import com.sk89q.worldedit.function.generator.nature.CoralGenerator;
import com.sk89q.worldedit.function.generator.nature.GiantMushroomGenerator;
import com.sk89q.worldedit.function.generator.warp.BuiltinWarps;
import com.sk89q.worldedit.function.generator.warp.NamedWarp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeParametersTest {

    private static final List<ShapeParameter> STAR = List.of(
        new ShapeParameter("points", 5, 3, 32, "Number of points"),
        new ShapeParameter("ring", 0.3, 0.05, 0.9, "Ring thickness")
    );

    private static String error(List<ShapeParameter> parameters, String... arguments) {
        return assertThrows(IllegalArgumentException.class,
            () -> ShapeParameters.parse(parameters, List.of(arguments))).getMessage();
    }

    @Test
    void numbersInOrderAndByName() {
        ShapeParameters values = ShapeParameters.parse(STAR, List.of("ring=0.4", "6"));
        assertEquals(6, values.get("points"));
        assertEquals(0.4, values.get("ring"));
        assertEquals(0.3, ShapeParameters.defaults(STAR).get("ring"));
    }

    @Test
    void spacesAroundEqualsAreJoined() {
        assertEquals(0.5, ShapeParameters.parse(STAR, List.of("ring", "=", "0.5")).get("ring"));
        assertEquals(0.5, ShapeParameters.parse(STAR, List.of("ring=", "0.5")).get("ring"));
        assertEquals(0.5, ShapeParameters.parse(STAR, List.of("ring", "=0.5")).get("ring"));
        assertEquals(List.of("6", "ring=0.5"), ShapeParameters.joinSpacedEquals(List.of("6", "ring", "=", "0.5")));
        assertEquals("ring= needs a value after it, e.g. ring=0.3", error(STAR, "ring="));
    }

    @Test
    void countsMustBeWhole() {
        assertTrue(STAR.getFirst().integer());
        assertFalse(STAR.get(1).integer());
        assertEquals("points must be a whole number", error(STAR, "points=5.5"));
        assertEquals("points must be a whole number", error(STAR, "5.5"));
        assertEquals(6, ShapeParameters.parse(STAR, List.of("6.0")).get("points"));
        // a count with a fractional range is not forced to be whole
        assertFalse(new ShapeParameter("count", 1, 0.5, 4, "").integer());
        assertTrue(new ShapeParameter("depth", 1, 1, 8, "").whole().integer());
    }

    @Test
    void settingGivenTwice() {
        assertEquals("ring was given twice", error(STAR, "6", "0.3", "ring=0.4"));
        assertEquals("ring was given twice", error(STAR, "ring=0.3", "ring=0.4"));
    }

    @Test
    void namedChoices() {
        List<ShapeParameter> mushroom = GiantMushroomGenerator.PARAMETERS;
        assertEquals(1, ShapeParameters.parse(mushroom, List.of("type=flat")).get("type"));
        assertEquals(2, ShapeParameters.parse(mushroom, List.of("bell")).get("type"));
        assertEquals(1, ShapeParameters.parse(mushroom, List.of("1")).get("type"));
        assertEquals("Unknown type 'square'. Choose round, flat or bell.", error(mushroom, "square"));
        assertEquals("Unknown type 'square'. Choose round, flat or bell.", error(mushroom, "type=square"));
        assertEquals("Unknown type '7'. Choose round, flat or bell.", error(mushroom, "7"));
        assertEquals("Unknown type 'nosuch'. Choose branching, brain, fan or tubes.",
            error(CoralGenerator.PARAMETERS, "nosuch"));
        // a word after the type is filled is still reported against the type
        assertEquals("Unknown type 'banana'. Choose round, flat or bell.", error(mushroom, "1", "5", "banana"));
    }

    @Test
    void axisWords() {
        NamedWarp bend = BuiltinWarps.find("bend").orElseThrow();
        assertEquals("Unknown axis 'q'. Choose auto, x, y or z.", assertThrows(IllegalArgumentException.class,
            () -> bend.parseSettings(List.of("90", "axis=q"))).getMessage());
        assertEquals(ShapeAxis.Y, bend.parseSettings(List.of("90", "axis=y")).get("axis"));
        assertEquals(ShapeAxis.Z, bend.parseSettings(List.of("90", "axis", "=", "z")).get("axis"));
        assertEquals(ShapeAxis.X, bend.parseSettings(List.of("x", "90")).get("axis"));
    }

    @Test
    void wordWithoutChoices() {
        assertEquals("'abc' is not a number (for points)", error(STAR, "abc"));
        assertEquals("'abc' is not a number or a setting.", error(STAR, "5", "0.3", "abc"));
        assertEquals("Too many parameters. Valid parameters, in order: points, ring", error(STAR, "5", "0.3", "1"));
    }
}
