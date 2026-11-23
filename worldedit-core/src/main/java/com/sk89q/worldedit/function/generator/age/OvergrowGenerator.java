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
 * Lets plants creep over a build or terrain: vines hanging down walls (more
 * on north faces and in the shade), leaf bushes at the foot of walls and in
 * corners, moss on top of surfaces, grass, ferns and flowers on exposed soil,
 * hanging roots and cave vines under overhangs and glow lichen in dark spots.
 * Growth is clumped by smooth noise.
 *
 * <p>Plants only go into air. The one exception: at amounts above 0.6 some
 * exposed stone and cobblestone tops turn into moss blocks.</p>
 */
public final class OvergrowGenerator {

    /**
     * How to overgrow.
     *
     * @param amount how overgrown, 0 (nothing) to 1
     * @param seed the seed; the same seed gives the same result
     * @param flowers whether flowers grow among the grass
     * @param lichen whether glow lichen grows in dark spots
     */
    public record Options(double amount, long seed, boolean flowers, boolean lichen) {
    }

    /**
     * What grew.
     *
     * @param changed blocks changed in total
     * @param vines vine blocks
     * @param bushes leaf blocks
     * @param moss moss carpets and moss blocks
     * @param plants grass, ferns and flowers
     * @param hanging hanging roots and cave vines
     * @param lichen glow lichen
     */
    public record Result(int changed, int vines, int bushes, int moss, int plants, int hanging, int lichen) {
    }

    /**
     * Horizontal directions: dx, dz, and the vine property for a block on that side.
     */
    private static final int[][] HORIZONTAL = { { 0, -1 }, { 0, 1 }, { 1, 0 }, { -1, 0 } };
    private static final String[] HORIZONTAL_NAMES = { "north", "south", "east", "west" };
    /**
     * Vines on north faces (vine cell north of the block, so attached to the
     * south) grow most, on south faces least.
     */
    private static final double[] FACE_FACTOR = { 0.6, 1.8, 1.0, 1.0 };
    private static final int SHADE_DISTANCE = 8;

    private final AgeWorld world;
    private final AgeBox box;
    private final double amount;
    private final long seed;
    private final AgeGrid grid;

    private OvergrowGenerator(AgeWorld world, AgeBox box, double amount, long seed) {
        this.world = world;
        this.box = box;
        this.amount = amount;
        this.seed = seed;
        this.grid = new AgeGrid(world, box);
    }

    /**
     * Overgrow a box.
     *
     * @param world the world
     * @param box the box (only blocks the world says are in the selection change)
     * @param options the options
     * @return what grew
     */
    public static Result overgrow(AgeWorld world, AgeBox box, Options options) {
        double amount = Math.clamp(options.amount(), 0.0, 1.0);
        if (amount <= 0) {
            return new Result(0, 0, 0, 0, 0, 0, 0);
        }
        return new OvergrowGenerator(world, box, amount, options.seed()).run(options);
    }

    private double hash(int salt, int x, int y, int z) {
        return AgeNoise.hash(seed, salt, x, y, z);
    }

    private Result run(Options options) {
        int moss = mossBlocks();
        int bushes = bushes();
        int vines = vines();
        int hanging = hanging();
        int lichen = options.lichen() ? lichen() : 0;
        int plants = groundPlants(options.flowers());
        moss += mossCarpets();
        int changed = grid.write(world);
        return new Result(changed, vines, bushes, moss, plants, hanging, lichen);
    }

    /**
     * Whether a cell is free for a plant: inside the selection and air.
     */
    private boolean free(int x, int y, int z) {
        return grid.inside(x, y, z) && grid.isAir(x, y, z);
    }

    private boolean place(int x, int y, int z, AgeBlock block) {
        return world.exists(block.id()) && free(x, y, z) && grid.set(x, y, z, block);
    }

    /**
     * Distance to the nearest structural block above, or a large number.
     */
    private int cover(int x, int y, int z, int limit) {
        for (int d = 1; d <= limit; d++) {
            if (grid.isStructural(x, y + d, z)) {
                return d;
            }
        }
        return Integer.MAX_VALUE;
    }

    private interface CellVisitor {
        int visit(int x, int y, int z);
    }

    private int forEachCell(boolean topDown, CellVisitor visitor) {
        int count = 0;
        for (int i = 0; i <= box.maxY() - box.minY(); i++) {
            int y = topDown ? box.maxY() - i : box.minY() + i;
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    count += visitor.visit(x, y, z);
                }
            }
        }
        return count;
    }

    /**
     * At high amounts, some exposed stone and cobblestone tops become moss blocks.
     */
    private int mossBlocks() {
        if (amount <= 0.6 || !world.exists("minecraft:moss_block")) {
            return 0;
        }
        AgeNoise noise = new AgeNoise(seed, 21, 5);
        return forEachCell(false, (x, y, z) -> {
            if (grid.inside(x, y, z) && AgeMaterials.isMossableTop(grid.get(x, y, z)) && grid.isAir(x, y + 1, z)
                && noise.value(x, y, z) > 1 - (amount - 0.6) * 1.6 && hash(22, x, y, z) < 0.75) {
                return grid.set(x, y, z, AgeBlock.of("moss_block")) ? 1 : 0;
            }
            return 0;
        });
    }

    /**
     * Leaf bushes at the foot of walls, more in corners.
     */
    private int bushes() {
        AgeNoise noise = new AgeNoise(seed, 23, 6);
        return forEachCell(false, (x, y, z) -> {
            if (!free(x, y, z) || !grid.isFull(x, y - 1, z)) {
                return 0;
            }
            boolean northSouth = false;
            boolean eastWest = false;
            for (int i = 0; i < 4; i++) {
                int nx = x + HORIZONTAL[i][0];
                int nz = z + HORIZONTAL[i][1];
                if (grid.isFull(nx, y, nz) && grid.isFull(nx, y + 1, nz)) {
                    if (i < 2) {
                        northSouth = true;
                    } else {
                        eastWest = true;
                    }
                }
            }
            if (!northSouth && !eastWest) {
                return 0;
            }
            double corner = northSouth && eastWest ? 3 : 1;
            double clump = noise.value(x, y, z) > 1 - amount * 1.2 ? 1.5 : 0.2;
            if (hash(24, x, y, z) >= amount * 0.1 * corner * clump) {
                return 0;
            }
            return bush(x, y, z);
        });
    }

    private int bush(int cx, int cy, int cz) {
        int radius = 1 + (hash(25, cx, cy, cz) < amount ? 1 : 0);
        String leaves = hash(26, cx, cy, cz) < 0.25 && world.exists("minecraft:azalea_leaves")
            ? "minecraft:azalea_leaves" : "minecraft:oak_leaves";
        AgeBlock block = AgeBlock.of(leaves).with("persistent", "true");
        int count = 0;
        double horizontal = (radius + 0.5) * (radius + 0.5);
        double vertical = (radius * 0.9 + 0.4) * (radius * 0.9 + 0.4);
        for (int dy = 0; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    double e = (dx * dx + dz * dz) / horizontal + dy * dy / vertical;
                    if (e > 1 + 0.3 * (hash(27, x, y, z) - 0.5)) {
                        continue;
                    }
                    // only on something: the ground or the layer of leaves below
                    if (grid.isAir(x, y - 1, z) || !grid.isFull(x, y - 1, z) && !AgeMaterials.isLeaves(
                        grid.get(x, y - 1, z))) {
                        continue;
                    }
                    if (place(x, y, z, block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Vines hanging down walls from where they start.
     */
    private int vines() {
        if (!world.exists("minecraft:vine")) {
            return 0;
        }
        AgeNoise noise = new AgeNoise(seed, 28, 5);
        return forEachCell(true, (x, y, z) -> {
            if (!free(x, y, z)) {
                return 0;
            }
            double clump = noise.value(x, y, z) > 1 - amount * 1.3 ? 1 : 0.1;
            double shade = cover(x, y, z, SHADE_DISTANCE) <= SHADE_DISTANCE ? 1.6 : 1;
            for (int i = 0; i < 4; i++) {
                int nx = x + HORIZONTAL[i][0];
                int nz = z + HORIZONTAL[i][1];
                if (!grid.isFull(nx, y, nz)) {
                    continue;
                }
                boolean top = !grid.isFull(nx, y + 1, nz) || !grid.isAir(x, y + 1, z);
                double chance = amount * 0.55 * FACE_FACTOR[i] * shade * clump * (top ? 1 : 0.12);
                if (hash(30 + i, x, y, z) < chance) {
                    int length = 2 + (int) (hash(34, x, y, z) * (2 + amount * 9));
                    return strand(x, y, z, i, length);
                }
            }
            return 0;
        });
    }

    private int strand(int x, int top, int z, int side, int length) {
        int count = 0;
        for (int y = top; y > top - length; y--) {
            if (!free(x, y, z) || !grid.isFull(x + HORIZONTAL[side][0], y, z + HORIZONTAL[side][1])) {
                break;
            }
            AgeBlock vine = AgeBlock.of("vine");
            for (int i = 0; i < 4; i++) {
                vine = vine.with(HORIZONTAL_NAMES[i],
                    String.valueOf(grid.isFull(x + HORIZONTAL[i][0], y, z + HORIZONTAL[i][1])));
            }
            vine = vine.with("up", String.valueOf(grid.isFull(x, y + 1, z)));
            if (place(x, y, z, vine)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Hanging roots and cave vines under overhangs.
     */
    private int hanging() {
        AgeNoise noise = new AgeNoise(seed, 35, 5);
        boolean caveVines = world.exists("minecraft:cave_vines") && world.exists("minecraft:cave_vines_plant");
        boolean roots = world.exists("minecraft:hanging_roots");
        if (!caveVines && !roots) {
            return 0;
        }
        return forEachCell(true, (x, y, z) -> {
            if (!free(x, y, z) || !grid.isFull(x, y + 1, z) || !grid.isAir(x, y - 1, z)) {
                return 0;
            }
            double clump = noise.value(x, y, z) > 1 - amount * 1.2 ? 1 : 0.1;
            if (hash(36, x, y, z) >= amount * 0.3 * clump) {
                return 0;
            }
            AgeBlock above = grid.get(x, y + 1, z);
            if (roots && (AgeMaterials.isSoil(above) || !caveVines)) {
                return place(x, y, z, AgeBlock.of("hanging_roots")) ? 1 : 0;
            }
            int length = 1 + (int) (hash(37, x, y, z) * (1 + amount * 3));
            int room = 0;
            while (room < length && free(x, y - room, z)) {
                room++;
            }
            int count = 0;
            for (int i = 0; i < room; i++) {
                String berries = String.valueOf(hash(38, x, y - i, z) < 0.15);
                AgeBlock block = AgeBlock.of(i == room - 1 ? "cave_vines" : "cave_vines_plant")
                    .with("berries", berries);
                if (place(x, y - i, z, block)) {
                    count++;
                }
            }
            return count;
        });
    }

    /**
     * Glow lichen in dark, enclosed spots.
     */
    private int lichen() {
        if (!world.exists("minecraft:glow_lichen")) {
            return 0;
        }
        AgeNoise noise = new AgeNoise(seed, 39, 4);
        String[] names = { "north", "south", "east", "west", "up", "down" };
        int[][] dirs = { { 0, 0, -1 }, { 0, 0, 1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 } };
        return forEachCell(false, (x, y, z) -> {
            if (!free(x, y, z) || cover(x, y, z, 4) > 4) {
                return 0;
            }
            int walls = 0;
            for (int[] d : HORIZONTAL) {
                for (int r = 1; r <= 4; r++) {
                    if (grid.isStructural(x + d[0] * r, y, z + d[1] * r)) {
                        walls++;
                        break;
                    }
                }
            }
            if (walls < 3 || noise.value(x, y, z) <= 1 - amount || hash(40, x, y, z) >= amount * 0.12) {
                return 0;
            }
            AgeBlock block = AgeBlock.of("glow_lichen");
            boolean any = false;
            for (int i = 0; i < 6; i++) {
                boolean face = grid.isFull(x + dirs[i][0], y + dirs[i][1], z + dirs[i][2]);
                any |= face;
                block = block.with(names[i], String.valueOf(face));
            }
            return any && place(x, y, z, block) ? 1 : 0;
        });
    }

    /**
     * Grass, ferns and flowers on exposed soil.
     */
    private int groundPlants(boolean flowers) {
        AgeNoise noise = new AgeNoise(seed, 41, 4);
        AgeNoise flowerNoise = new AgeNoise(seed, 42, 3);
        String grass = world.exists("minecraft:short_grass") ? "short_grass" : "grass";
        return forEachCell(false, (x, y, z) -> {
            if (!free(x, y, z) || !AgeMaterials.isSoil(grid.get(x, y - 1, z))) {
                return 0;
            }
            if (noise.value(x, y, z) <= 1 - amount * 1.1 || hash(43, x, y, z) >= 0.35 + amount * 0.5) {
                return 0;
            }
            double roll = hash(44, x, y, z);
            String plant;
            if (grid.get(x, y - 1, z).path().equals("moss_block") && roll < 0.4) {
                plant = "moss_carpet";
            } else if (roll < 0.62) {
                plant = grass;
            } else if (roll < 0.82) {
                plant = "fern";
            } else if (flowers) {
                // flowers of a kind grow together
                int kind = (int) (flowerNoise.value(x, 0, z) * AgeMaterials.FLOWERS.size() * 0.999);
                plant = AgeMaterials.FLOWERS.get(kind);
            } else {
                plant = grass;
            }
            return place(x, y, z, AgeBlock.of(plant)) ? 1 : 0;
        });
    }

    /**
     * Moss carpet on top of build surfaces.
     */
    private int mossCarpets() {
        if (!world.exists("minecraft:moss_carpet")) {
            return 0;
        }
        AgeNoise noise = new AgeNoise(seed, 45, 4.5);
        return forEachCell(false, (x, y, z) -> {
            if (!free(x, y, z) || !grid.isFull(x, y - 1, z) || AgeMaterials.isSoil(grid.get(x, y - 1, z))) {
                return 0;
            }
            if (noise.value(x, y, z) > 1 - amount * 0.7 && hash(46, x, y, z) < 0.75) {
                return place(x, y, z, AgeBlock.of("moss_carpet")) ? 1 : 0;
            }
            return 0;
        });
    }
}
