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

package com.sk89q.worldedit.function.generator.style;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleLibraryTest {

    @Test
    void builtinStylesAreCompleteAndDistinct() {
        StyleLibrary library = new StyleLibrary(null);
        Set<String> names = library.getBuiltins().stream().map(BuildingStyle::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(List.of("modern", "art_deco", "futuristic", "gothic", "brutalist", "glass",
            "brick", "sandstone")), names.toString());
        Set<Map<StyleRole, String>> palettes = library.getBuiltins().stream().map(BuildingStyle::blocks)
            .collect(Collectors.toSet());
        assertEquals(names.size(), palettes.size(), "every built-in style has its own blocks");
        for (BuildingStyle style : library.getBuiltins()) {
            assertEquals(style.name(), style.family());
            for (StyleRole role : StyleRole.values()) {
                assertFalse(style.block(role).isBlank());
            }
        }
        assertTrue(library.find("Art-Deco").isPresent());
    }

    @Test
    void roleOverridesArePickedOutOfSettings() {
        BuildingStyle.Overrides split = BuildingStyle.splitOverrides(
            List.of("width=20", "window=cyan_stained_glass", "30", "walls=bricks", "oak_stairs[facing=north]"));
        assertEquals(Map.of(StyleRole.WINDOW, "cyan_stained_glass", StyleRole.WALL, "bricks"), split.overrides());
        assertEquals(List.of("width=20", "30", "oak_stairs[facing=north]"), split.remaining());
        assertThrows(IllegalArgumentException.class, () -> BuildingStyle.splitOverrides(List.of("roof=")));

        BuildingStyle modern = new StyleLibrary(null).find("modern").orElseThrow();
        BuildingStyle changed = modern.withOverrides(split.overrides());
        assertEquals("cyan_stained_glass", changed.block(StyleRole.WINDOW));
        assertEquals("bricks", changed.block(StyleRole.WALL));
        assertEquals(modern.block(StyleRole.ROOF), changed.block(StyleRole.ROOF));
        assertEquals(List.of("wall=bricks", "window=cyan_stained_glass"), changed.differencesFrom(modern));
    }

    @Test
    void savedStylesSurviveAReload(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("building-styles.txt");
        StyleLibrary library = new StyleLibrary(file);
        library.save("mytower", "art_deco", Map.of(StyleRole.WINDOW, "cyan_stained_glass"));
        // a style based on a saved style keeps both sets of changes
        library.save("mytower2", "mytower", Map.of(StyleRole.LIGHT, "sea_lantern"));
        assertThrows(IllegalArgumentException.class, () -> library.save("modern", "glass", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> library.save("x y", "glass", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> library.save("ok", "nope", Map.of()));
        assertTrue(Files.readString(file).contains("mytower = art_deco window=cyan_stained_glass"));

        StyleLibrary reloaded = new StyleLibrary(file);
        BuildingStyle second = reloaded.find("mytower2").orElseThrow();
        assertEquals("art_deco", second.family());
        assertEquals("cyan_stained_glass", second.block(StyleRole.WINDOW));
        assertEquals("sea_lantern", second.block(StyleRole.LIGHT));
        assertEquals(reloaded.find("art_deco").orElseThrow().block(StyleRole.WALL), second.block(StyleRole.WALL));

        assertTrue(reloaded.delete("mytower"));
        assertFalse(reloaded.delete("mytower"));
        assertFalse(new StyleLibrary(file).find("mytower").isPresent());
        assertTrue(new StyleLibrary(file).find("mytower2").isPresent());
    }
}
