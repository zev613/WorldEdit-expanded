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

import com.sk89q.worldedit.util.Direction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * What goes in one block of a building part.
 *
 * @param role which pattern to use
 * @param facing for {@link Role#STAIR}, the direction you walk up the stair (the stair's {@code facing})
 * @param upsideDown for {@link Role#STAIR}, whether the stair is upside down
 */
public record BuildBlock(Role role, Direction facing, boolean upsideDown) {

    /**
     * A block of the main pattern.
     */
    public static final BuildBlock MAIN = new BuildBlock(Role.MAIN, Direction.NORTH, false);

    /**
     * A block of the detail pattern (windows, panes, railings).
     */
    public static final BuildBlock DETAIL = new BuildBlock(Role.DETAIL, Direction.NORTH, false);

    /**
     * Which pattern a block uses.
     */
    public enum Role {
        /**
         * The main pattern.
         */
        MAIN,
        /**
         * The detail pattern: windows, panes and railings.
         */
        DETAIL,
        /**
         * A stair block if one was given, otherwise the main pattern.
         */
        STAIR
    }

    public BuildBlock {
        checkNotNull(role);
        checkNotNull(facing);
    }

    /**
     * A stair facing the given way.
     *
     * @param facing the direction you walk up it
     * @return the block
     */
    public static BuildBlock stair(Direction facing) {
        return new BuildBlock(Role.STAIR, facing, false);
    }
}
