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

package com.sk89q.worldedit.function.generator.age;

/**
 * The block box an ageing generator works in (inclusive corners).
 *
 * @param minX lowest x
 * @param minY lowest y
 * @param minZ lowest z
 * @param maxX highest x
 * @param maxY highest y
 * @param maxZ highest z
 */
public record AgeBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * Create a box.
     */
    public AgeBox {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("Empty box");
        }
    }

    /**
     * The number of blocks in the box.
     *
     * @return the volume
     */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Whether a position is in the box.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return true if inside
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
