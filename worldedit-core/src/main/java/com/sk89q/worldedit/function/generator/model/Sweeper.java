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

package com.sk89q.worldedit.function.generator.model;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Drags a {@link CrossSection} along a {@link SweepPath}.
 *
 * <p>Every block near the path finds the nearest point of the path and checks where it lies in the
 * cross-section standing upright at that point. Because each block is checked (instead of stamping the
 * cross-section down at intervals), there are no gaps on the outside of curves.</p>
 */
public final class Sweeper {

    private static final double END_EXTENSION = 2;

    private Sweeper() {
    }

    private static final class Nearest {
        double distanceSq;
        int sample;
    }

    /**
     * Sweep a cross-section along a path.
     *
     * @param path the path
     * @param section the cross-section
     * @param hollow keep only the outside skin (the ends stay open, like a pipe)
     * @param <T> the kind of block
     * @return the blocks, by world position
     */
    public static <T> Map<BlockVector3, T> sweep(SweepPath path, CrossSection<T> section, boolean hollow) {
        double reach = section.reach();
        if (!(reach > 0) || reach > 512) {
            throw new IllegalArgumentException("That cross-section is too big.");
        }
        double estimate = (path.length() + 2 * reach + 2) * (2 * reach + 2) * (2 * reach + 2);
        if (estimate > DrawingModeller.MAX_BLOCKS * 2) {
            throw new IllegalArgumentException("That would be too big: use a shorter path or a smaller size.");
        }
        int count = path.size();
        // with hollow, the path carries on a little past both ends so the ends are not capped
        int extra = hollow ? (int) Math.ceil((reach + END_EXTENSION) / SweepPath.SPACING) : 0;
        int total = count + 2 * extra;
        Vector3[] positions = new Vector3[total];
        int[] frames = new int[total];
        for (int i = 0; i < total; i++) {
            int index = i - extra;
            int frame = Math.max(0, Math.min(count - 1, index));
            frames[i] = frame;
            Vector3 base = path.position(frame);
            double beyond = index < 0 ? index * SweepPath.SPACING
                : index >= count ? (index - count + 1) * SweepPath.SPACING : 0;
            positions[i] = base.add(path.forward(frame).multiply(beyond));
        }

        int box = (int) Math.ceil(reach) + 1;
        Map<BlockVector3, Nearest> nearest = new HashMap<>();
        for (int i = 0; i < total; i++) {
            Vector3 centre = positions[i];
            Vector3 forward = path.forward(frames[i]);
            boolean end = i == 0 || i == total - 1;
            int cx = (int) Math.floor(centre.x());
            int cy = (int) Math.floor(centre.y());
            int cz = (int) Math.floor(centre.z());
            for (int x = cx - box; x <= cx + box; x++) {
                for (int y = cy - box; y <= cy + box; y++) {
                    for (int z = cz - box; z <= cz + box; z++) {
                        double dx = x + 0.5 - centre.x();
                        double dy = y + 0.5 - centre.y();
                        double dz = z + 0.5 - centre.z();
                        double distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq > (reach + 1) * (reach + 1)) {
                            continue;
                        }
                        // a block well in front of or behind this sample is closer to another sample
                        double ahead = dx * forward.x() + dy * forward.y() + dz * forward.z();
                        if (!end && Math.abs(ahead) > 1) {
                            continue;
                        }
                        BlockVector3 key = BlockVector3.at(x, y, z);
                        Nearest best = nearest.get(key);
                        if (best == null) {
                            best = new Nearest();
                            best.distanceSq = distanceSq;
                            best.sample = i;
                            nearest.put(key, best);
                        } else if (distanceSq < best.distanceSq) {
                            best.distanceSq = distanceSq;
                            best.sample = i;
                        }
                    }
                }
            }
        }

        Map<BlockVector3, T> result = new HashMap<>();
        Set<BlockVector3> extended = new HashSet<>();
        for (Map.Entry<BlockVector3, Nearest> entry : nearest.entrySet()) {
            BlockVector3 block = entry.getKey();
            int sample = entry.getValue().sample;
            int frame = frames[sample];
            Vector3 offset = block.toVector3().add(0.5, 0.5, 0.5).subtract(positions[sample]);
            double ahead = offset.dot(path.forward(frame));
            if (Math.abs(ahead) > 0.5 + 1e-9) {
                continue;
            }
            int index = sample - extra;
            double along = Math.max(0, Math.min(path.length(), index * SweepPath.SPACING + ahead));
            T value = section.at(offset.dot(path.right(frame)), offset.dot(path.up(frame)), along);
            if (value == null) {
                continue;
            }
            if (index < 0 || index >= count) {
                extended.add(block);
            } else {
                result.put(block, value);
            }
        }
        if (!hollow) {
            return result;
        }
        Map<BlockVector3, T> skin = new HashMap<>();
        for (Map.Entry<BlockVector3, T> entry : result.entrySet()) {
            BlockVector3 block = entry.getKey();
            for (BlockVector3 side : SIDES) {
                BlockVector3 next = block.add(side);
                if (!result.containsKey(next) && !extended.contains(next)) {
                    skin.put(block, entry.getValue());
                    break;
                }
            }
        }
        return skin;
    }

    private static final BlockVector3[] SIDES = {
        BlockVector3.UNIT_X, BlockVector3.UNIT_MINUS_X, BlockVector3.UNIT_Y, BlockVector3.UNIT_MINUS_Y,
        BlockVector3.UNIT_Z, BlockVector3.UNIT_MINUS_Z,
    };
}
