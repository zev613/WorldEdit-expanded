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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * An in-memory world for tests: every block type exists, the selection is the box.
 */
final class MemoryAgeWorld implements AgeWorld {

    private final Map<String, AgeBlock> blocks = new HashMap<>();
    private final AgeBox box;
    private Predicate<String> exists = _ -> true;
    private int writes;

    MemoryAgeWorld(AgeBox box) {
        this.box = box;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    MemoryAgeWorld copy() {
        MemoryAgeWorld copy = new MemoryAgeWorld(box);
        copy.blocks.putAll(blocks);
        copy.exists = exists;
        return copy;
    }

    void setExists(Predicate<String> exists) {
        this.exists = exists;
    }

    AgeBox box() {
        return box;
    }

    int writes() {
        return writes;
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, AgeBlock block) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    put(x, y, z, block);
                }
            }
        }
    }

    void put(int x, int y, int z, AgeBlock block) {
        if (AgeMaterials.isAir(block)) {
            blocks.remove(key(x, y, z));
        } else {
            blocks.put(key(x, y, z), block);
        }
    }

    @Override
    public AgeBlock getBlock(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), AgeBlock.AIR);
    }

    @Override
    public void setBlock(int x, int y, int z, AgeBlock block) {
        writes++;
        put(x, y, z, block);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return box.contains(x, y, z);
    }

    @Override
    public boolean exists(String id) {
        return exists.test(id);
    }

    Map<String, AgeBlock> snapshot() {
        return new HashMap<>(blocks);
    }
}
