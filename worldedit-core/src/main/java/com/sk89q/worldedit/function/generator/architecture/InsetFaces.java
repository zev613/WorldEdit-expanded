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

/**
 * Sizes for {@code //insetfaces}: which faces of a box have room for a panel inside a border.
 */
public final class InsetFaces {

    private InsetFaces() {
    }

    private static boolean fits(int sizeA, int sizeB, int margin) {
        return sizeA - 1 >= 2L * margin && sizeB - 1 >= 2L * margin;
    }

    /**
     * The widest border that still leaves a panel on at least one face.
     *
     * @param sizeX the box size east-west
     * @param sizeY the box height
     * @param sizeZ the box size north-south
     * @return the largest margin that works
     */
    public static int maxMargin(int sizeX, int sizeY, int sizeZ) {
        int smallest = Math.min(sizeX, Math.min(sizeY, sizeZ));
        int largest = Math.max(sizeX, Math.max(sizeY, sizeZ));
        int middle = sizeX + sizeY + sizeZ - smallest - largest;
        return (middle - 1) / 2;
    }

    /**
     * How many faces get a panel: a face needs room for the border on both sides in both directions,
     * and the opposite face of a one-block-thin box is the same blocks, so it counts once.
     *
     * @param sizeX the box size east-west
     * @param sizeY the box height
     * @param sizeZ the box size north-south
     * @param margin the border width
     * @return the number of faces, 0 to 6
     */
    public static int faceCount(int sizeX, int sizeY, int sizeZ, int margin) {
        int count = 0;
        if (fits(sizeX, sizeY, margin)) {
            count += sizeZ > 1 ? 2 : 1;
        }
        if (fits(sizeZ, sizeY, margin)) {
            count += sizeX > 1 ? 2 : 1;
        }
        if (fits(sizeX, sizeZ, margin)) {
            count += sizeY > 1 ? 2 : 1;
        }
        return count;
    }
}
