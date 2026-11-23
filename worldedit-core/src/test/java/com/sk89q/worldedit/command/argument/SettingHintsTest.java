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

package com.sk89q.worldedit.command.argument;

import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.ShapeLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingHintsTest {

    private static final ShapeLibrary LIBRARY = new ShapeLibrary(null);
    private static final Function<String, Optional<NamedShape>> SHAPES = LIBRARY::find;

    private static List<String> hints(String line, String input) {
        return SettingHints.suggest(line, input, SHAPES);
    }

    @Test
    void shapeSettingsFollowTheChosenShape() {
        List<String> star = hints("//shape star gold_block 10 ", "");
        assertTrue(star.contains("points="), star.toString());
        assertTrue(star.contains("-x"));
        assertFalse(star.contains("loops="));
        List<String> knot = hints("//shape trefoil stone 10 ", "");
        assertTrue(knot.contains("loops="), knot.toString());
        // settings already typed are not offered again
        assertFalse(hints("//shape star gold_block 10 points=6 ", "").contains("points="));
    }

    @Test
    void afterEqualsTheDefaultOrTheChoicesAreOffered() {
        assertTrue(hints("//shape star gold_block 10 points=", "points=").contains("points=5"));
        List<String> crowns = hints("//skyscraper art_deco 20 crown=", "crown=");
        assertTrue(crowns.containsAll(List.of("crown=flat", "crown=spire", "crown=stepped", "crown=pyramid")), crowns.toString());
        assertTrue(hints("//warp bend 90 axis=", "axis=").containsAll(List.of("axis=x", "axis=z", "axis=auto")));
    }

    @Test
    void natureWarpBuildAndAgeCommands() {
        assertTrue(hints("//coral brain_coral_block 4 ", "").containsAll(List.of("brain", "fan", "type=", "seed=", "-c")));
        assertTrue(hints("//giantmushroom mushroom_stem red_mushroom_block 6 type=", "type=").contains("type=flat"));
        assertTrue(hints("//rock stone 5 ", "").contains("roughness="));
        List<String> twist = hints("//warp twist ", "");
        assertTrue(twist.contains("angle="), twist.toString());
        assertFalse(twist.contains("amplitude="));
        assertTrue(hints("//warp wave ", "").contains("amplitude="));
        assertTrue(hints("//build tower stone ", "").contains("battlements="));
        assertTrue(hints("//ruin ", "").containsAll(List.of("amount=", "seed=", "-r", "-g")));
        assertTrue(hints("/brush shape heart red_wool 5 ", "").contains("-h"));
        assertTrue(hints("//combine add knot 5 ", "").contains("winds="));
        assertTrue(hints("//skyscraper glass 10 ", "").containsAll(List.of("width=", "window=", "trim=")));
    }

    @Test
    void unknownCommandsAndNamesGiveNothing() {
        assertTrue(hints("//set ", "").isEmpty());
        assertTrue(hints("//shape nosuchshape stone 5 ", "").isEmpty());
        assertTrue(hints("", "").isEmpty());
    }
}
