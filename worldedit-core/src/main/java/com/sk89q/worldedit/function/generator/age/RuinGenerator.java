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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Turns the build in a box into a ruin: walls and roofs crumble in coherent
 * chunks (more towards the top and the outer edges), whatever is left
 * without support falls, the rest weathers (cracked and mossy bricks,
 * stripped or rotten wood, broken glass, oxidised copper, furnishings gone),
 * and fallen material piles up as rubble at the foot of the walls.
 *
 * <p>The ground is the run of natural blocks (dirt, grass, stone...) from the
 * bottom of the box up in each column; everything above it is the build.</p>
 */
public final class RuinGenerator {

    /**
     * How to ruin.
     *
     * @param amount how ruined, 0 (untouched) to 1
     * @param seed the seed; the same seed gives the same ruin
     * @param rubble whether fallen blocks leave rubble piles
     * @param greenery whether grass, moss and dirt patches appear on flat surfaces
     */
    public record Options(double amount, long seed, boolean rubble, boolean greenery) {
    }

    /**
     * What happened.
     *
     * @param changed blocks changed in total
     * @param knockedOut blocks knocked out by decay
     * @param collapsed blocks that fell because nothing held them up any more
     * @param weathered blocks weathered, broken or rotted away
     * @param rubble rubble blocks placed
     * @param greenery grass, moss and dirt placed
     */
    public record Result(int changed, int knockedOut, int collapsed, int weathered, int rubble, int greenery) {
    }

    private record Fallen(int x, int y, int z, AgeBlock block) {
    }

    private static final int[][] HORIZONTAL = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final int[][] SIX = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private final AgeWorld world;
    private final AgeBox box;
    private final double amount;
    private final long seed;
    private final AgeGrid grid;
    private final int[][] ground;
    private final List<Fallen> fallen = new ArrayList<>();
    private int buildMinY = Integer.MAX_VALUE;
    private int buildMaxY = Integer.MIN_VALUE;
    private double centerX;
    private double centerZ;
    private double halfX;
    private double halfZ;

    private RuinGenerator(AgeWorld world, AgeBox box, double amount, long seed) {
        this.world = world;
        this.box = box;
        this.amount = amount;
        this.seed = seed;
        this.grid = new AgeGrid(world, box);
        this.ground = new int[box.maxX() - box.minX() + 1][box.maxZ() - box.minZ() + 1];
    }

    /**
     * Ruin the build in a box.
     *
     * @param world the world
     * @param box the box (only blocks the world says are in the selection change)
     * @param options the options
     * @return what happened
     */
    public static Result ruin(AgeWorld world, AgeBox box, Options options) {
        double amount = Math.clamp(options.amount(), 0.0, 1.0);
        if (amount <= 0) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        return new RuinGenerator(world, box, amount, options.seed()).run(options);
    }

    private double hash(int salt, int x, int y, int z) {
        return AgeNoise.hash(seed, salt, x, y, z);
    }

    /**
     * The first y above the natural ground of a column (the bottom of the box
     * if the column has no ground in the selection).
     */
    private int groundAt(int x, int z) {
        return ground[x - box.minX()][z - box.minZ()];
    }

    private Result run(Options options) {
        findGroundAndBuild();
        if (buildMinY > buildMaxY) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        BitSet supportedBefore = grid.support();
        int knockedOut = decay();
        int weathered = weather();
        int collapsed = collapse(supportedBefore);
        weathered += dropLooseDecor();
        int rubble = options.rubble() ? rubble() : 0;
        int greenery = options.greenery() ? greenery() : 0;
        int changed = grid.write(world);
        return new Result(changed, knockedOut, collapsed, weathered, rubble, greenery);
    }

    private void findGroundAndBuild() {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int y = box.minY();
                while (y <= box.maxY() && grid.inside(x, y, z) && grid.isTerrain(x, y, z)) {
                    y++;
                }
                ground[x - box.minX()][z - box.minZ()] = y;
                for (; y <= box.maxY(); y++) {
                    if (grid.inside(x, y, z) && grid.isStructural(x, y, z)) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                        buildMinY = Math.min(buildMinY, y);
                        buildMaxY = Math.max(buildMaxY, y);
                    }
                }
            }
        }
        centerX = (minX + maxX) / 2.0;
        centerZ = (minZ + maxZ) / 2.0;
        halfX = Math.max(0.5, (maxX - minX) / 2.0);
        halfZ = Math.max(0.5, (maxZ - minZ) / 2.0);
    }

    private boolean isBuild(int x, int y, int z) {
        return grid.inside(x, y, z) && y >= groundAt(x, z);
    }

    private double height(int y) {
        return (y - buildMinY) / (double) Math.max(1, buildMaxY - buildMinY);
    }

    private double edge(int x, int z) {
        return Math.clamp(Math.max(Math.abs(x - centerX) / halfX, Math.abs(z - centerZ) / halfZ), 0.0, 1.0);
    }

    private void remove(int x, int y, int z) {
        fallen.add(new Fallen(x, y, z, grid.get(x, y, z)));
        grid.set(x, y, z, AgeBlock.AIR);
    }

    /**
     * Knock out blocks in coherent chunks, more near the top, the outer
     * edges and on spans (floors and roofs with nothing under them).
     */
    private int decay() {
        AgeNoise noise = new AgeNoise(seed, 1, 4.5);
        List<int[]> doomed = new ArrayList<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    // natural blocks (a dirt ledge, a rock) stay
                    if (!isBuild(x, y, z) || !grid.isStructural(x, y, z) || grid.isTerrain(x, y, z)) {
                        continue;
                    }
                    double h = height(y);
                    boolean span = y > groundAt(x, z) && !grid.isStructural(x, y - 1, z);
                    double base = y == buildMinY ? 0.35 : 1.0;
                    double threshold = amount * base * (0.12 + 0.95 * h * h + 0.2 * edge(x, z) + (span ? 0.35 : 0));
                    double value = 0.8 * noise.value(x, y, z) + 0.2 * hash(2, x, y, z);
                    if (value < threshold) {
                        doomed.add(new int[] { x, y, z });
                    }
                }
            }
        }
        for (int[] p : doomed) {
            remove(p[0], p[1], p[2]);
        }
        return doomed.size();
    }

    /**
     * Weather what is left.
     */
    private int weather() {
        AgeNoise moss = new AgeNoise(seed, 3, 5);
        AgeNoise rot = new AgeNoise(seed, 4, 3);
        int count = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!isBuild(x, y, z) || grid.isAir(x, y, z) || grid.isTerrain(x, y, z)) {
                        continue;
                    }
                    AgeBlock block = grid.get(x, y, z);
                    double roll = hash(5, x, y, z);
                    double h = height(y);
                    if (AgeMaterials.isDecor(block)) {
                        if (decorKey(block, x, y, z) < Math.min(1.0, amount * 1.8)) {
                            grid.set(x, y, z, AgeBlock.AIR);
                            count++;
                        }
                    } else if (AgeMaterials.isGlass(block)) {
                        if (roll < Math.min(0.97, amount * 1.9)) {
                            grid.set(x, y, z, AgeBlock.AIR);
                            count++;
                        }
                    } else if (AgeMaterials.isPlanks(block)) {
                        if (0.6 * rot.value(x, y, z) + 0.4 * roll < amount * 0.45) {
                            remove(x, y, z);
                            count++;
                        }
                    } else if (AgeMaterials.isLog(block)) {
                        if (0.6 * rot.value(x, y, z) + 0.4 * roll < amount * 0.15) {
                            remove(x, y, z);
                            count++;
                        } else if (roll < amount * 0.7 && change(x, y, z, block, AgeMaterials.stripped(block))) {
                            count++;
                        }
                    } else if (AgeMaterials.isOxidisable(block)) {
                        int stages = (int) Math.min(3, Math.floor(amount * 3 + hash(6, x, y, z)));
                        String path = block.path();
                        String target = null;
                        for (int i = 0; i < stages; i++) {
                            String next = AgeMaterials.nextOxidation(path);
                            if (next == null || !world.exists("minecraft:" + next)) {
                                break;
                            }
                            path = next;
                            target = "minecraft:" + next;
                        }
                        if (change(x, y, z, block, target)) {
                            count++;
                        }
                    } else {
                        String mossy = AgeMaterials.mossy(block);
                        String cracked = AgeMaterials.cracked(block);
                        boolean mossHere = moss.value(x, y, z) * (1.25 - 0.5 * h) > 1 - amount * 0.8;
                        if (mossy != null && mossHere && change(x, y, z, block, mossy)) {
                            count++;
                        } else if (cracked != null && roll < amount * 0.55 && change(x, y, z, block, cracked)) {
                            count++;
                        } else if (mossy != null && roll < amount * 0.2 && change(x, y, z, block, mossy)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * A roll shared by both halves of doors and beds, so they go as a whole.
     */
    private double decorKey(AgeBlock block, int x, int y, int z) {
        String path = block.path();
        if (path.endsWith("_door") && "upper".equals(block.get("half"))) {
            return hash(7, x, y - 1, z);
        }
        if (path.endsWith("_bed") && "head".equals(block.get("part"))) {
            String facing = block.get("facing");
            int dx = "east".equals(facing) ? 1 : "west".equals(facing) ? -1 : 0;
            int dz = "south".equals(facing) ? 1 : "north".equals(facing) ? -1 : 0;
            return hash(7, x - dx, y, z - dz);
        }
        return hash(7, x, y, z);
    }

    private boolean change(int x, int y, int z, AgeBlock block, String id) {
        if (id == null || !world.exists(id)) {
            return false;
        }
        return grid.set(x, y, z, block.withId(id));
    }

    /**
     * Remove everything that stood before but is no longer connected to the
     * ground.
     */
    private int collapse(BitSet supportedBefore) {
        BitSet supportedAfter = grid.support();
        List<int[]> loose = new ArrayList<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (grid.inside(x, y, z) && grid.isStructural(x, y, z)
                        && grid.supported(supportedBefore, x, y, z) && !grid.supported(supportedAfter, x, y, z)) {
                        loose.add(new int[] { x, y, z });
                    }
                }
            }
        }
        for (int[] p : loose) {
            remove(p[0], p[1], p[2]);
        }
        return loose.size();
    }

    /**
     * Torches, doors and the like that lost the wall they hung on.
     */
    private int dropLooseDecor() {
        int count = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!grid.inside(x, y, z) || !AgeMaterials.isDecor(grid.get(x, y, z))) {
                        continue;
                    }
                    // the upper half of a door stands on its lower half
                    boolean held = AgeMaterials.isDecor(grid.get(x, y - 1, z));
                    for (int[] d : SIX) {
                        if (grid.isStructural(x + d[0], y + d[1], z + d[2])) {
                            held = true;
                            break;
                        }
                    }
                    if (!held) {
                        grid.set(x, y, z, AgeBlock.AIR);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Fallen blocks pile up at the foot of the walls.
     */
    private int rubble() {
        AgeNoise pile = new AgeNoise(seed, 8, 3);
        int count = 0;
        for (Fallen f : fallen) {
            if (hash(9, f.x(), f.y(), f.z()) >= 0.6) {
                continue;
            }
            String id = AgeMaterials.rubble(f.block(), hash(10, f.x(), f.y(), f.z()), world);
            if (id == null) {
                continue;
            }
            // push outwards, away from the middle of the build, and scatter a little
            int distance = (int) (hash(11, f.x(), f.y(), f.z()) * 3);
            double rx = (f.x() - centerX) / halfX;
            double rz = (f.z() - centerZ) / halfZ;
            int dx = 0;
            int dz = 0;
            if (Math.abs(rx) >= Math.abs(rz)) {
                dx = (int) Math.signum(rx) * distance;
            } else {
                dz = (int) Math.signum(rz) * distance;
            }
            dx += (int) Math.floor(hash(12, f.x(), f.y(), f.z()) * 3) - 1;
            dz += (int) Math.floor(hash(13, f.x(), f.y(), f.z()) * 3) - 1;
            int x = f.x() + dx;
            int z = f.z() + dz;
            int y = Math.min(f.y(), box.maxY());
            if (!box.contains(x, y, z) || !grid.isAir(x, y, z)) {
                continue;
            }
            while (y > box.minY() && grid.isAir(x, y - 1, z)) {
                y--;
            }
            AgeBlock below = grid.get(x, y - 1, z);
            if (!grid.isStructural(x, y - 1, z) || !grid.inside(x, y, z)
                || (below.path().endsWith("_slab") && !"double".equals(below.get("type")))) {
                continue;
            }
            int cap = groundAt(x, z) + (pile.value(x, 0, z) > 0.45 ? 1 : 0);
            if (y > cap) {
                continue;
            }
            AgeBlock block = AgeBlock.of(id);
            String slab = AgeMaterials.slabOf(id, world);
            if (slab != null && (y == cap || hash(14, x, y, z) < 0.25) && hash(15, x, y, z) < 0.6) {
                block = AgeBlock.of(slab).with("type", "bottom");
            }
            grid.set(x, y, z, block);
            count++;
        }
        return count;
    }

    /**
     * Grass, dirt and moss patches on flat surfaces.
     */
    private int greenery() {
        AgeNoise patch = new AgeNoise(seed, 16, 3.5);
        int count = 0;
        for (int y = box.minY(); y < box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!grid.inside(x, y, z) || !grid.inside(x, y + 1, z) || !grid.isFull(x, y, z)
                        || !grid.isAir(x, y + 1, z)) {
                        continue;
                    }
                    int flat = 0;
                    for (int[] d : HORIZONTAL) {
                        if (grid.isStructural(x + d[0], y, z + d[1])) {
                            flat++;
                        }
                    }
                    if (flat < 3 || patch.value(x, y, z) <= 1 - amount * 0.75) {
                        continue;
                    }
                    double roll = hash(17, x, y, z);
                    AgeBlock top = grid.get(x, y, z);
                    if (y <= groundAt(x, z)) {
                        String id = top.path().equals("grass_block") ? null
                            : roll < 0.5 ? "minecraft:grass_block" : roll < 0.75 ? "minecraft:coarse_dirt"
                            : "minecraft:moss_block";
                        if (id != null && world.exists(id) && grid.set(x, y, z, AgeBlock.of(id))) {
                            count++;
                        }
                        String now = grid.get(x, y, z).path();
                        if ((now.equals("grass_block") || now.equals("moss_block"))
                            && hash(18, x, y, z) < 0.35 && world.exists("minecraft:short_grass")
                            && grid.set(x, y + 1, z, AgeBlock.of("short_grass"))) {
                            count++;
                        }
                    } else if (roll < 0.55) {
                        if (world.exists("minecraft:moss_carpet") && grid.set(x, y + 1, z, AgeBlock.of("moss_carpet"))) {
                            count++;
                        }
                    } else if (world.exists("minecraft:moss_block") && grid.set(x, y, z, AgeBlock.of("moss_block"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
