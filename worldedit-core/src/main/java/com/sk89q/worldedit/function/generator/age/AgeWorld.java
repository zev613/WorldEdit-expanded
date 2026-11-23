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
 * The blocks the ageing generators read and write. The generators read the
 * box (plus a one block border) once, work on a copy and then write only the
 * blocks that changed.
 */
public interface AgeWorld {

    /**
     * Read a block.
     *
     * @param x x
     * @param y y
     * @param z z
     * @return the block (air where there is none)
     */
    AgeBlock getBlock(int x, int y, int z);

    /**
     * Write a block.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param block the block
     */
    void setBlock(int x, int y, int z, AgeBlock block);

    /**
     * Whether a position is part of the selection (only those are changed).
     *
     * @param x x
     * @param y y
     * @param z z
     * @return true if it may be changed
     */
    boolean contains(int x, int y, int z);

    /**
     * Whether a block type exists, so variants that are missing (e.g. on older
     * versions) are skipped.
     *
     * @param id the namespaced id
     * @return true if it exists
     */
    boolean exists(String id);
}
