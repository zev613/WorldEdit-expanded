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

package com.sk89q.worldedit.util;

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.SolidBlockMask;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds the first solid block along a line of sight, far beyond the loaded area.
 *
 * <p>Unlike {@link TargetBlock}, this walks the exact blocks the line passes through
 * (so it never skips a corner), and it handles chunks that are not loaded:</p>
 * <ul>
 *     <li>chunks that exist on disk are read normally (loading them if needed);</li>
 *     <li>chunks that were never generated are not generated just to look through them.
 *     Instead, the world generator's terrain height is used when the platform can tell us
 *     (this is what distant-terrain mods draw), otherwise they are treated as empty.</li>
 * </ul>
 */
public final class LongRangeTrace {

    /**
     * The result of a trace.
     *
     * @param position the block that was hit
     * @param estimated true if the hit is on terrain that has not been generated yet,
     *     so its height comes from the world generator
     * @param distance the distance from the eye to the hit
     */
    public record Hit(BlockVector3 position, boolean estimated, double distance) {
    }

    private final World world;
    private final Mask solid;
    private final Map<BlockVector2, Boolean> generated = new HashMap<>();
    private final Map<BlockVector2, OptionalInt> estimates = new HashMap<>();

    /**
     * Create a trace.
     *
     * @param world the world
     * @param solid which blocks stop the trace
     */
    public LongRangeTrace(World world, Mask solid) {
        this.world = checkNotNull(world);
        this.solid = checkNotNull(solid);
    }

    /**
     * Height of a player's eyes above their feet.
     */
    public static final double EYE_HEIGHT = 1.62;

    /**
     * Trace a player's line of sight to the first solid block.
     *
     * @param player the player
     * @param maxDistance how far to look
     * @return the hit, or null
     */
    @Nullable
    public static Hit fromPlayer(Player player, double maxDistance) {
        Location location = player.getLocation();
        World world = player.getWorld();
        return new LongRangeTrace(world, new SolidBlockMask(world))
            .trace(location.toVector().add(0, EYE_HEIGHT, 0), location.getDirection(), maxDistance);
    }

    /**
     * Send a player to the block they are looking at, however far away it is.
     *
     * @param player the player
     * @param maxDistance how far to look
     * @return the hit, or null if there was nothing in range (the player is not moved)
     */
    @Nullable
    public static Hit jump(Player player, double maxDistance) {
        Hit hit = fromPlayer(player, maxDistance);
        if (hit != null) {
            player.findFreePosition(new Location(player.getWorld(), hit.position().toVector3()));
        }
        return hit;
    }

    private boolean isGenerated(int chunkX, int chunkZ) {
        return generated.computeIfAbsent(BlockVector2.at(chunkX, chunkZ), c -> world.isChunkGenerated(c.x(), c.z()));
    }

    private OptionalInt estimate(int x, int z) {
        return estimates.computeIfAbsent(BlockVector2.at(x, z), c -> world.estimateSurfaceHeight(c.x(), c.z()));
    }

    /**
     * Trace from an eye position in a direction.
     *
     * @param eye the eye position
     * @param direction the view direction (need not be normalized)
     * @param maxDistance how far to look, in blocks
     * @return the first solid block, or null if none within range
     */
    @Nullable
    public Hit trace(Vector3 eye, Vector3 direction, double maxDistance) {
        checkArgument(maxDistance > 0, "maxDistance must be positive");
        double length = direction.length();
        if (length < 1e-9) {
            return null;
        }
        double dx = direction.x() / length;
        double dy = direction.y() / length;
        double dz = direction.z() / length;

        // Amanatides & Woo voxel traversal
        int x = (int) Math.floor(eye.x());
        int y = (int) Math.floor(eye.y());
        int z = (int) Math.floor(eye.z());
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : boundary(eye.x(), x, stepX) * deltaX;
        double maxY = stepY == 0 ? Double.POSITIVE_INFINITY : boundary(eye.y(), y, stepY) * deltaY;
        double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : boundary(eye.z(), z, stepZ) * deltaZ;

        int minY = world.getMinY();
        int maxWorldY = world.getMaxY();
        double travelled = 0;
        boolean first = true;
        while (travelled <= maxDistance) {
            if ((y < minY && stepY <= 0) || (y > maxWorldY && stepY >= 0)) {
                // left the world and not coming back
                return null;
            }
            if (!first && y >= minY && y <= maxWorldY) {
                Hit hit = test(x, y, z, travelled);
                if (hit != null) {
                    return hit;
                }
            }
            first = false;
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                travelled = maxX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                travelled = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                travelled = maxZ;
                maxZ += deltaZ;
            }
        }
        return null;
    }

    private static double boundary(double position, int block, int step) {
        return step > 0 ? block + 1 - position : position - block;
    }

    @Nullable
    private Hit test(int x, int y, int z, double distance) {
        if (isGenerated(x >> 4, z >> 4)) {
            BlockVector3 position = BlockVector3.at(x, y, z);
            return solid.test(position) ? new Hit(position, false, distance) : null;
        }
        OptionalInt height = estimate(x, z);
        // the generator's height is the first free block above the surface
        if (height.isPresent() && y < height.getAsInt()) {
            return new Hit(BlockVector3.at(x, y, z), true, distance);
        }
        return null;
    }
}
