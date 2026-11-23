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

package com.sk89q.worldedit.extension.factory.parser.saved;

import com.sk89q.worldedit.WorldEdit;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Holds the server-wide saved patterns ({@code saved-patterns.txt}) and saved
 * masks ({@code saved-masks.txt}) in WorldEdit's working directory. They are
 * read the first time they are used.
 */
public final class SavedInputs {

    public static final String PATTERN_FILE = "saved-patterns.txt";
    public static final String MASK_FILE = "saved-masks.txt";

    private static final SavedInputStore UNCONFIGURED_PATTERNS = new SavedInputStore(null, "pattern");
    private static final SavedInputStore UNCONFIGURED_MASKS = new SavedInputStore(null, "mask");

    private static volatile SavedInputStore patterns;
    private static volatile SavedInputStore masks;

    private SavedInputs() {
    }

    /**
     * Get the saved patterns.
     *
     * @return the store
     */
    public static SavedInputStore patterns() {
        SavedInputStore current = patterns;
        if (current == null) {
            synchronized (SavedInputs.class) {
                current = patterns;
                if (current == null) {
                    current = create(PATTERN_FILE, "pattern");
                    if (current == null) {
                        return UNCONFIGURED_PATTERNS;
                    }
                    patterns = current;
                }
            }
        }
        return current;
    }

    /**
     * Get the saved masks.
     *
     * @return the store
     */
    public static SavedInputStore masks() {
        SavedInputStore current = masks;
        if (current == null) {
            synchronized (SavedInputs.class) {
                current = masks;
                if (current == null) {
                    current = create(MASK_FILE, "mask");
                    if (current == null) {
                        return UNCONFIGURED_MASKS;
                    }
                    masks = current;
                }
            }
        }
        return current;
    }

    private static @Nullable SavedInputStore create(String fileName, String kind) {
        Path file;
        try {
            file = WorldEdit.getInstance().getWorkingDirectoryPath(fileName);
        } catch (RuntimeException _) {
            // WorldEdit isn't configured yet (e.g. in tests); try again next time
            return null;
        }
        return new SavedInputStore(file, kind);
    }
}
