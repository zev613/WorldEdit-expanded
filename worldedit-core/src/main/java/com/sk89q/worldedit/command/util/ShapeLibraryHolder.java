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

package com.sk89q.worldedit.command.util;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.generator.shape.ShapeLibrary;

/**
 * Holds the shared {@link ShapeLibrary} used by the shape commands and brush.
 */
public final class ShapeLibraryHolder {

    public static final String PRESET_FILE = "shape-presets.txt";

    private static volatile ShapeLibrary library;

    private ShapeLibraryHolder() {
    }

    /**
     * Get the library, loading saved presets the first time.
     *
     * @return the library
     */
    public static ShapeLibrary get() {
        ShapeLibrary current = library;
        if (current == null) {
            synchronized (ShapeLibraryHolder.class) {
                current = library;
                if (current == null) {
                    current = new ShapeLibrary(WorldEdit.getInstance().getWorkingDirectoryPath(PRESET_FILE));
                    library = current;
                }
            }
        }
        return current;
    }
}
