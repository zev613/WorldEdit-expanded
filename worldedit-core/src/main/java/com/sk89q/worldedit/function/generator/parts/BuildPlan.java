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

package com.sk89q.worldedit.function.generator.parts;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The blocks of a building part, as offsets from where it is placed.
 */
public final class BuildPlan {

    /**
     * The largest number of blocks a plan may have by default.
     */
    public static final int DEFAULT_MAX_BLOCKS = 4_000_000;

    private final Map<BlockVector3, BuildBlock> blocks = new LinkedHashMap<>();
    private final int maxBlocks;

    /**
     * Create an empty plan.
     *
     * @param maxBlocks the largest number of blocks allowed
     */
    public BuildPlan(int maxBlocks) {
        this.maxBlocks = maxBlocks;
    }

    /**
     * Create an empty plan with the default size limit.
     */
    public BuildPlan() {
        this(DEFAULT_MAX_BLOCKS);
    }

    /**
     * Set a block, replacing what was planned there.
     *
     * @param x the x offset
     * @param y the y offset
     * @param z the z offset
     * @param block the block
     */
    public void put(int x, int y, int z, BuildBlock block) {
        put(BlockVector3.at(x, y, z), block);
    }

    /**
     * Set a block, replacing what was planned there.
     *
     * @param position the offset
     * @param block the block
     */
    public void put(BlockVector3 position, BuildBlock block) {
        if (blocks.size() >= maxBlocks && !blocks.containsKey(position)) {
            throw new IllegalArgumentException("That is too big (more than " + maxBlocks + " blocks)");
        }
        blocks.put(position, block);
    }

    /**
     * Set a block unless something is already planned there.
     *
     * @param x the x offset
     * @param y the y offset
     * @param z the z offset
     * @param block the block
     */
    public void putIfAbsent(int x, int y, int z, BuildBlock block) {
        BlockVector3 position = BlockVector3.at(x, y, z);
        if (!blocks.containsKey(position)) {
            put(position, block);
        }
    }

    /**
     * Get what is planned at a position.
     *
     * @param x the x offset
     * @param y the y offset
     * @param z the z offset
     * @return the block, or null if nothing
     */
    public BuildBlock get(int x, int y, int z) {
        return blocks.get(BlockVector3.at(x, y, z));
    }

    /**
     * All planned blocks.
     *
     * @return the blocks by offset
     */
    public Map<BlockVector3, BuildBlock> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    /**
     * The number of planned blocks.
     *
     * @return the count
     */
    public int size() {
        return blocks.size();
    }
}
