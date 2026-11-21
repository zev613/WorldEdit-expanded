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
import com.sk89q.worldedit.util.Direction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Maps flat, picture-like coordinates onto the world so that a picture faces the player.
 *
 * <p>{@code u} goes to the right (as seen by a player looking in the {@code facing} direction),
 * {@code v} goes up the picture and {@code w} is the thickness. Standing upright, the picture's up is the
 * world's up and the thickness goes away from the player. Lying flat, the picture's up points away from the
 * player (so it reads correctly when looking down at it) and the thickness goes up.</p>
 *
 * @param facing the horizontal direction the player looks in
 * @param flat whether the picture lies on the ground
 */
public record FacingPlane(Direction facing, boolean flat) {

    public FacingPlane {
        checkNotNull(facing);
        checkArgument(facing.isCardinal(), "facing must be north, east, south or west");
    }

    /**
     * The cardinal direction a player with the given yaw looks in.
     *
     * @param yaw the yaw in degrees (0 = south, 90 = west, as Minecraft does it)
     * @return north, east, south or west
     */
    public static Direction cardinalFromYaw(double yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        }
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * The direction to the right of a horizontal direction.
     *
     * @param facing north, east, south or west
     * @return the direction 90 degrees clockwise (seen from above)
     */
    public static Direction rightOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + facing);
        };
    }

    /**
     * The world offset of a picture coordinate.
     *
     * @param u blocks to the right
     * @param v blocks up the picture
     * @param w blocks of thickness
     * @return the offset from the picture's bottom-left corner
     */
    public BlockVector3 map(int u, int v, int w) {
        BlockVector3 right = rightOf(facing).toBlockVector();
        BlockVector3 forward = facing.toBlockVector();
        BlockVector3 up = BlockVector3.UNIT_Y;
        if (flat) {
            return right.multiply(u).add(forward.multiply(v)).add(up.multiply(w));
        }
        return right.multiply(u).add(up.multiply(v)).add(forward.multiply(w));
    }
}
