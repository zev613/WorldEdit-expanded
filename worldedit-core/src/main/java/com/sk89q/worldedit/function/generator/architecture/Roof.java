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

package com.sk89q.worldedit.function.generator.architecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The plain logic behind {@code //roof}: reading the words the player typed and working out the height
 * of the roof over every column of the footprint.
 */
public final class Roof {

    /**
     * The tallest roof that may be built, in blocks.
     */
    public static final int MAX_HEIGHT = 256;

    /**
     * The widest overhang, in blocks.
     */
    public static final int MAX_OVERHANG = 32;

    /**
     * The steepest pitch.
     */
    public static final double MAX_PITCH = 10;

    /**
     * The roof shapes.
     */
    public enum Type {
        /**
         * Two slopes meeting at a ridge along the long side, with flat gable ends.
         */
        GABLE,
        /**
         * Slopes on all four sides.
         */
        HIP,
        /**
         * One slope rising from west to east.
         */
        SHED,
        /**
         * A rounded, barrel-like roof.
         */
        CURVED,
        /**
         * Steep lower slopes on all sides with a gentle top.
         */
        MANSARD;

        /**
         * The name the player types.
         *
         * @return the lower case name
         */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Look up a roof type by name, ignoring case.
         *
         * @param word the word typed
         * @return the type, if it is one
         */
        public static Optional<Type> find(String word) {
            for (Type type : values()) {
                if (type.id().equalsIgnoreCase(word)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }

        /**
         * All type names, for messages.
         *
         * @return "gable, hip, shed, curved or mansard"
         */
        public static String names() {
            List<String> ids = Arrays.stream(values()).map(Type::id).toList();
            return String.join(", ", ids.subList(0, ids.size() - 1)) + " or " + ids.getLast();
        }
    }

    /**
     * What the player asked for.
     *
     * @param type the roof shape
     * @param pattern the pattern text, still to be parsed
     * @param overhang how far the roof reaches past the selection, in blocks
     * @param pitch how steep the roof is (1 = 45 degrees)
     */
    public record Request(Type type, String pattern, int overhang, double pitch) {
    }

    private Roof() {
    }

    /**
     * Read the words typed after {@code //roof}: an optional roof type and a pattern, in either order,
     * then the overhang and the pitch as plain numbers.
     *
     * @param words the words, without flags
     * @return the request
     * @throws IllegalArgumentException with a message for the player if the words do not make sense
     */
    public static Request parse(List<String> words) {
        Type type = null;
        List<String> others = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            Optional<Type> asType = Type.find(word);
            if (asType.isPresent() && type == null) {
                type = asType.get();
            } else if (isNumber(word)) {
                numbers.add(word);
            } else {
                others.add(word);
            }
        }
        if (others.size() > 1) {
            if (type == null) {
                throw new IllegalArgumentException("Unknown roof type '" + others.getFirst() + "'; use "
                    + Type.names() + ". Example: //roof hip stone_bricks");
            }
            throw new IllegalArgumentException("Give one roof type and one pattern, then the overhang and pitch "
                + "as numbers; '" + others.get(1) + "' is extra. Example: //roof hip stone_bricks 1 0.5");
        }
        if (others.isEmpty()) {
            throw new IllegalArgumentException("Give a pattern for the roof too, e.g. //roof "
                + (type == null ? Type.GABLE : type).id() + " stone_bricks");
        }
        if (numbers.size() > 2) {
            throw new IllegalArgumentException("Too many numbers: //roof takes an overhang and a pitch, e.g. "
                + "//roof gable stone_bricks 1 0.5");
        }
        int overhang = 0;
        if (!numbers.isEmpty()) {
            double value = Double.parseDouble(numbers.getFirst());
            if (value != Math.rint(value) || value < 0 || value > MAX_OVERHANG) {
                throw new IllegalArgumentException("The overhang must be a whole number of blocks from 0 to "
                    + MAX_OVERHANG + ", not " + numbers.getFirst());
            }
            overhang = (int) value;
        }
        double pitch = 1;
        if (numbers.size() > 1) {
            pitch = Double.parseDouble(numbers.get(1));
            if (!(pitch > 0) || pitch > MAX_PITCH) {
                throw new IllegalArgumentException("The pitch must be more than 0 and at most 10 "
                    + "(1 = 45 degrees, 0.5 = gentle, 2 = steep), not " + numbers.get(1));
            }
        }
        return new Request(type == null ? Type.GABLE : type, others.getFirst(), overhang, pitch);
    }

    private static boolean isNumber(String word) {
        char last = word.charAt(word.length() - 1);
        if (!Character.isDigit(last) && last != '.') {
            return false;
        }
        try {
            Double.parseDouble(word);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    /**
     * Whether the selection is tall enough to set the roof height itself (three or more blocks high).
     *
     * @param selectionHeight the selection height in blocks
     * @return true if the roof is built inside the selection's height
     */
    public static boolean fillsSelectionHeight(int selectionHeight) {
        return selectionHeight >= 3;
    }

    /**
     * How high the ridge is above the bottom of the roof, in blocks.
     *
     * <p>For a flat selection (one or two blocks high) the roof sits on top and rises
     * {@code pitch} blocks for every block inwards from the edge, so 1 is 45 degrees. For a taller
     * selection the roof is built from the selection's bottom and the pitch scales the selection's
     * height (1 = exactly as tall as the selection, 0.5 = half as tall, 2 = twice as tall).</p>
     *
     * @param sizeX the footprint size east-west, including any overhang
     * @param sizeZ the footprint size north-south, including any overhang
     * @param selectionHeight the selection height in blocks
     * @param pitch the pitch
     * @return the rise in blocks (fractional)
     */
    public static double rise(int sizeX, int sizeZ, int selectionHeight, double pitch) {
        if (fillsSelectionHeight(selectionHeight)) {
            return (selectionHeight - 1) * pitch;
        }
        double halfSpan = Math.max(1.0, Math.min(sizeX - 1, sizeZ - 1) / 2.0);
        return halfSpan * pitch;
    }

    /**
     * Work out the roof height above its base for every column.
     *
     * @param type the roof shape
     * @param sizeX the footprint size east-west
     * @param sizeZ the footprint size north-south
     * @param rise the height of the ridge, from {@link #rise(int, int, int, double)}
     * @return heights indexed {@code [x][z]}, from 0 at the eaves up to the rise
     */
    public static int[][] heights(Type type, int sizeX, int sizeZ, double rise) {
        double spanX = Math.max(1.0, sizeX - 1);
        double spanZ = Math.max(1.0, sizeZ - 1);
        double halfSpan = Math.max(1.0, Math.min(spanX, spanZ) / 2.0);
        int[][] heights = new int[sizeX][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double distX = Math.min(x, sizeX - 1 - x);
                double distZ = Math.min(z, sizeZ - 1 - z);
                double t = switch (type) {
                    case HIP -> Math.min(1.0, Math.min(distX, distZ) / halfSpan);
                    case SHED -> x / spanX;
                    case CURVED -> Math.pow(Math.min(1.0, distX / (spanX / 2.0)), 0.65);
                    case MANSARD -> {
                        double raw = Math.min(1.0, Math.min(distX, distZ) / halfSpan);
                        yield raw < 0.4 ? (raw / 0.4) * 0.7 : 0.7 + ((raw - 0.4) / 0.6) * 0.3;
                    }
                    case GABLE -> spanX <= spanZ
                        ? Math.min(1.0, distX / (spanX / 2.0))
                        : Math.min(1.0, distZ / (spanZ / 2.0));
                };
                heights[x][z] = (int) Math.round(t * rise);
            }
        }
        return heights;
    }
}
