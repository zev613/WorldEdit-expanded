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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A working copy of the box plus a one block border, with the material
 * classification of each block cached. Only blocks inside the selection can
 * be changed; {@link #write(AgeWorld)} writes back the ones that did change.
 */
final class AgeGrid {

    private static final int AIR = 1;
    private static final int STRUCTURAL = 2;
    private static final int FULL = 4;
    private static final int TERRAIN = 8;

    private final AgeBox box;
    private final int ox;
    private final int oy;
    private final int oz;
    private final int sx;
    private final int sy;
    private final int sz;
    private final int[] original;
    private final int[] current;
    private final BitSet inside;
    private final List<AgeBlock> palette = new ArrayList<>();
    private final Map<AgeBlock, Integer> paletteIndex = new HashMap<>();
    private int[] flags = new int[16];

    AgeGrid(AgeWorld world, AgeBox box) {
        this.box = box;
        ox = box.minX() - 1;
        oy = box.minY() - 1;
        oz = box.minZ() - 1;
        sx = box.maxX() - box.minX() + 3;
        sy = box.maxY() - box.minY() + 3;
        sz = box.maxZ() - box.minZ() + 3;
        original = new int[sx * sy * sz];
        inside = new BitSet(original.length);
        for (int y = oy; y < oy + sy; y++) {
            for (int z = oz; z < oz + sz; z++) {
                for (int x = ox; x < ox + sx; x++) {
                    int i = index(x, y, z);
                    original[i] = paletteId(world.getBlock(x, y, z));
                    if (box.contains(x, y, z) && world.contains(x, y, z)) {
                        inside.set(i);
                    }
                }
            }
        }
        current = original.clone();
    }

    AgeBox box() {
        return box;
    }

    private int index(int x, int y, int z) {
        return ((y - oy) * sz + (z - oz)) * sx + (x - ox);
    }

    private boolean inGrid(int x, int y, int z) {
        return x >= ox && y >= oy && z >= oz && x < ox + sx && y < oy + sy && z < oz + sz;
    }

    private int paletteId(AgeBlock block) {
        Integer id = paletteIndex.get(block);
        if (id != null) {
            return id;
        }
        int next = palette.size();
        palette.add(block);
        paletteIndex.put(block, next);
        if (next >= flags.length) {
            flags = Arrays.copyOf(flags, flags.length * 2);
        }
        int f = 0;
        if (AgeMaterials.isAir(block)) {
            f |= AIR;
        }
        if (AgeMaterials.isStructural(block)) {
            f |= STRUCTURAL;
        }
        if (AgeMaterials.isFullCube(block)) {
            f |= FULL;
        }
        if (AgeMaterials.isTerrain(block)) {
            f |= TERRAIN;
        }
        flags[next] = f;
        return next;
    }

    private int flagsAt(int x, int y, int z) {
        return inGrid(x, y, z) ? flags[current[index(x, y, z)]] : AIR;
    }

    /**
     * Whether a position is inside the selection (and may be changed).
     */
    boolean inside(int x, int y, int z) {
        return inGrid(x, y, z) && inside.get(index(x, y, z));
    }

    AgeBlock get(int x, int y, int z) {
        return inGrid(x, y, z) ? palette.get(current[index(x, y, z)]) : AgeBlock.AIR;
    }

    AgeBlock original(int x, int y, int z) {
        return inGrid(x, y, z) ? palette.get(original[index(x, y, z)]) : AgeBlock.AIR;
    }

    boolean isAir(int x, int y, int z) {
        return (flagsAt(x, y, z) & AIR) != 0;
    }

    boolean isStructural(int x, int y, int z) {
        return (flagsAt(x, y, z) & STRUCTURAL) != 0;
    }

    boolean isFull(int x, int y, int z) {
        return (flagsAt(x, y, z) & FULL) != 0;
    }

    boolean isTerrain(int x, int y, int z) {
        return (flagsAt(x, y, z) & TERRAIN) != 0;
    }

    /**
     * Change a block inside the selection; positions outside are ignored.
     *
     * @return true if it was set
     */
    boolean set(int x, int y, int z, AgeBlock block) {
        if (!inside(x, y, z)) {
            return false;
        }
        current[index(x, y, z)] = paletteId(block);
        return true;
    }

    /**
     * Find the structural blocks connected (through structural blocks inside the
     * selection) to the bottom of the selection or to structural blocks outside it.
     *
     * @return a set of grid indices, see {@link #supported(BitSet, int, int, int)}
     */
    BitSet support() {
        BitSet seen = new BitSet(current.length);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!inside(x, y, z) || !isStructural(x, y, z)) {
                        continue;
                    }
                    boolean anchored = y == box.minY() && !inside(x, y - 1, z) || anchoredOutside(x, y, z);
                    if (anchored) {
                        seen.set(index(x, y, z));
                        queue.add(new int[] { x, y, z });
                    }
                }
            }
        }
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                int nz = p[2] + d[2];
                if (!inside(nx, ny, nz) || !isStructural(nx, ny, nz)) {
                    continue;
                }
                int i = index(nx, ny, nz);
                if (!seen.get(i)) {
                    seen.set(i);
                    queue.add(new int[] { nx, ny, nz });
                }
            }
        }
        return seen;
    }

    private boolean anchoredOutside(int x, int y, int z) {
        return outsideStructural(x + 1, y, z) || outsideStructural(x - 1, y, z)
            || outsideStructural(x, y - 1, z) || outsideStructural(x, y + 1, z)
            || outsideStructural(x, y, z + 1) || outsideStructural(x, y, z - 1);
    }

    private boolean outsideStructural(int x, int y, int z) {
        return inGrid(x, y, z) && !inside(x, y, z) && isStructural(x, y, z);
    }

    boolean supported(BitSet support, int x, int y, int z) {
        return inGrid(x, y, z) && support.get(index(x, y, z));
    }

    /**
     * Write every changed block back.
     *
     * @param world the world
     * @return the number of blocks written
     */
    int write(AgeWorld world) {
        int changed = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    int i = index(x, y, z);
                    if (current[i] != original[i]) {
                        world.setBlock(x, y, z, palette.get(current[i]));
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
