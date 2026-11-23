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

package com.sk89q.worldedit.function.generator.skyscraper;

import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Generates a skyscraper block by block, in the look of a building style's family.
 *
 * <p>Coordinates are local: {@code x} runs across the front from {@code 0} to {@code width - 1},
 * {@code z} runs from the front (entrance) wall at {@code 0} to the back wall at {@code depth - 1}
 * (the entrance canopy sticks out to {@code z = -2}), and {@code y} goes up from the ground floor at
 * {@code 0}. Use {@link #toWorld} to place it in the world.</p>
 */
public final class Skyscraper {

    /**
     * The settings, in the order plain numbers fill them.
     */
    public static final List<ShapeParameter> PARAMETERS = List.of(
        new ShapeParameter("width", 15, 5, 256, "footprint size across the entrance side"),
        new ShapeParameter("depth", 15, 5, 256, "footprint size from front to back"),
        new ShapeParameter("story", 4, 3, 12, "blocks per story (the lobby is one taller)"),
        new ShapeParameter("setbacks", -1, -1, 6, "times the tower steps inward going up (-1 = the style's choice)"),
        new ShapeParameter("taper", 0, 0, 64, "blocks each side narrows evenly from bottom to top"),
        new ShapeParameter("shape", -1, -1, 3, "0 box, 1 rounded corners, 2 round, 3 L-shape (-1 = the style's choice)"),
        new ShapeParameter("crown", -1, -1, 3, "0 flat roof, 1 spire, 2 stepped crown, 3 pyramid (-1 = the style's choice)"),
        new ShapeParameter("seed", -1, -1, Integer.MAX_VALUE, "seed for rooftop details (-1 = random)"),
        new ShapeParameter("interior", 1, 0, 1, "1 = floors with a central stairwell core, 0 = floor slabs only")
    );

    /**
     * Names for the {@code shape} setting, by number.
     */
    public static final List<String> SHAPE_NAMES = List.of("box", "rounded", "round", "l");

    /**
     * Names for the {@code crown} setting, by number.
     */
    public static final List<String> CROWN_NAMES = List.of("flat", "spire", "stepped", "pyramid");

    /**
     * The most stories that can be built.
     */
    public static final int MAX_STORIES = 250;

    /**
     * How far in front of the placement position the front wall is.
     */
    public static final int FRONT_GAP = 3;

    /**
     * Receives the blocks of a building.
     */
    public interface Sink {

        /**
         * Place a block.
         *
         * @param x local x
         * @param y local y
         * @param z local z
         * @param role the block's role
         */
        void block(int x, int y, int z, StyleRole role);

        /**
         * Clear a block (inside rooms, doorways).
         *
         * @param x local x
         * @param y local y
         * @param z local z
         */
        void air(int x, int y, int z);
    }

    /**
     * How the outside walls are patterned.
     */
    public enum Facade {
        /** Horizontal bands of windows between solid spandrels. */
        RIBBON,
        /** Punched windows in a regular grid. */
        GRID,
        /** Vertical piers with window strips and dark spandrels between them. */
        VERTICAL,
        /** Buttresses with narrow pointed windows. */
        LANCET,
        /** Heavy fins and bands with small windows. */
        FINS,
        /** All glass with thin mullions. */
        CURTAIN,
        /** Glass with glowing accent bands. */
        BANDS,
        /** Arched windows between columns. */
        ARCADE
    }

    /**
     * The look a style family gives a skyscraper.
     *
     * @param facade the wall pattern
     * @param cornerPillars whether square corners get a pillar
     * @param shape the default footprint shape
     * @param crown the default crown
     * @param setbacks the default number of setbacks
     * @param trimEvery a trim band every this many stories (0 = none)
     */
    public record Design(Facade facade, boolean cornerPillars, int shape, int crown, int setbacks, int trimEvery) {
    }

    /**
     * The look of a built-in style family.
     *
     * @param family the family (built-in style name)
     * @return the design
     */
    public static Design designFor(String family) {
        return switch (family.toLowerCase(Locale.ROOT)) {
            case "art_deco" -> new Design(Facade.VERTICAL, true, 0, 2, 3, 0);
            case "futuristic" -> new Design(Facade.BANDS, false, 2, 1, 0, 0);
            case "gothic" -> new Design(Facade.LANCET, true, 0, 1, 1, 6);
            case "brutalist" -> new Design(Facade.FINS, true, 0, 0, 1, 0);
            case "glass" -> new Design(Facade.CURTAIN, false, 1, 0, 0, 0);
            case "brick" -> new Design(Facade.GRID, true, 0, 0, 0, 5);
            case "sandstone" -> new Design(Facade.ARCADE, true, 0, 3, 1, 3);
            default -> new Design(Facade.RIBBON, false, 0, 0, 0, 0);
        };
    }

    private static final int[][] RING = {
        {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0},
    };

    private final String family;
    private final Design design;
    private final int stories;
    private final int width;
    private final int depth;
    private final int storyHeight;
    private final int lobbyHeight;
    private final int setbacks;
    private final int taper;
    private final int shape;
    private final int crown;
    private final long seed;
    private final boolean interior;
    private final int[] insets;
    private final Map<Integer, boolean[][]> masks = new HashMap<>();
    private int coreX;
    private int coreZ;
    private boolean hasCore;

    /**
     * Plan a skyscraper.
     *
     * @param family the style family (a built-in style name) that decides the look
     * @param stories the number of stories, including the lobby
     * @param parameters the settings (see {@link #PARAMETERS})
     * @param randomSeed the seed used when the {@code seed} setting is -1
     */
    public Skyscraper(String family, int stories, ShapeParameters parameters, long randomSeed) {
        checkNotNull(family);
        checkArgument(stories >= 1 && stories <= MAX_STORIES, "Stories must be between 1 and " + MAX_STORIES);
        this.family = family.toLowerCase(Locale.ROOT);
        this.design = designFor(family);
        this.stories = stories;
        this.width = parameters.getInt("width");
        this.depth = parameters.getInt("depth");
        this.storyHeight = parameters.getInt("story");
        this.lobbyHeight = storyHeight + 1;
        int setbackSetting = parameters.getInt("setbacks");
        this.setbacks = setbackSetting < 0 ? design.setbacks() : setbackSetting;
        this.taper = parameters.getInt("taper");
        int shapeSetting = parameters.getInt("shape");
        this.shape = shapeSetting < 0 ? design.shape() : shapeSetting;
        int crownSetting = parameters.getInt("crown");
        this.crown = crownSetting < 0 ? design.crown() : crownSetting;
        long seedSetting = Math.round(parameters.get("seed"));
        this.seed = seedSetting < 0 ? randomSeed : seedSetting;
        this.interior = parameters.getInt("interior") == 1;
        this.insets = planInsets();
        planCore();
    }

    private int[] planInsets() {
        int[] result = new int[stories];
        int step = Math.max(1, (int) Math.round(Math.min(width, depth) * 0.1));
        int maxInset = (Math.min(width, depth) - 5) / 2;
        int[] boundaries = new int[setbacks];
        for (int i = 0; i < setbacks; i++) {
            boundaries[i] = Math.max(1, (int) Math.round(stories * (0.45 + 0.55 * i / setbacks)));
        }
        for (int s = 0; s < stories; s++) {
            int inset = 0;
            for (int boundary : boundaries) {
                if (s >= boundary) {
                    inset += step;
                }
            }
            inset += stories > 1 ? taper * s / (stories - 1) : 0;
            result[s] = Math.max(0, Math.min(maxInset, inset));
        }
        return result;
    }

    private void planCore() {
        int top = insets[stories - 1];
        // the middle of the top floor (for an L, the middle of its full-depth half)
        coreZ = (depth - 1) / 2;
        coreX = shape == 3 ? (top + width / 2 - 1) / 2 : (width - 1) / 2;
        if (!interior) {
            return;
        }
        boolean[][] mask = mask(top);
        hasCore = true;
        for (int dx = -3; dx <= 3 && hasCore; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!inside(mask, coreX + dx, coreZ + dz)) {
                    hasCore = false;
                    break;
                }
            }
        }
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int stories() {
        return stories;
    }

    /**
     * The footprint shape used (0 box, 1 rounded, 2 round, 3 L).
     *
     * @return the shape number
     */
    public int shape() {
        return shape;
    }

    /**
     * The crown used (0 flat, 1 spire, 2 stepped, 3 pyramid).
     *
     * @return the crown number
     */
    public int crown() {
        return crown;
    }

    public int setbacks() {
        return setbacks;
    }

    public long seed() {
        return seed;
    }

    /**
     * Whether a stairwell core is built (it needs room for a 7x7 block core on the top floor).
     *
     * @return true if there is a core
     */
    public boolean hasCore() {
        return hasCore;
    }

    /**
     * The y of the floor slab of a story; {@code floorY(stories())} is the main roof.
     *
     * @param story the story, from 0
     * @return the y
     */
    public int floorY(int story) {
        return story == 0 ? 0 : lobbyHeight + (story - 1) * storyHeight;
    }

    /**
     * The height of the main roof slab.
     *
     * @return the y of the roof
     */
    public int roofY() {
        return floorY(stories);
    }

    /**
     * How far a story's walls are set in from the footprint's edge.
     *
     * @param story the story, from 0
     * @return the inset in blocks
     */
    public int inset(int story) {
        return insets[story];
    }

    /**
     * The world offset (from the placement position) of a local position. The front wall's middle is
     * {@link #FRONT_GAP} blocks from the placement position in the direction opposite to {@code entrance},
     * so the entrance faces that way.
     *
     * @param entrance the direction the entrance faces (north, east, south or west)
     * @param width the building's width
     * @param x local x
     * @param y local y
     * @param z local z
     * @return the world offset
     */
    public static BlockVector3 toWorld(Direction entrance, int width, int x, int y, int z) {
        BlockVector3 right = FacingPlane.rightOf(entrance).toBlockVector();
        BlockVector3 back = entrance.toBlockVector().multiply(-1);
        return right.multiply(x - (width - 1) / 2).add(back.multiply(z + FRONT_GAP)).add(0, y, 0);
    }

    // ---- footprint -------------------------------------------------------------------------------

    /**
     * The footprint of a level set in by some blocks, as {@code [x][z]}.
     */
    boolean[][] mask(int inset) {
        return masks.computeIfAbsent(inset, this::computeMask);
    }

    private boolean[][] computeMask(int inset) {
        boolean[][] mask = new boolean[width][depth];
        int x0 = inset;
        int x1 = width - 1 - inset;
        int z0 = inset;
        int z1 = depth - 1 - inset;
        if (x1 < x0 || z1 < z0) {
            return mask;
        }
        double w = x1 - x0 + 1;
        double d = z1 - z0 + 1;
        double radius = Math.max(1.5, Math.min(w, d) / 4.0);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double fx = x + 0.5 - (x0 + w / 2);
                double fz = z + 0.5 - (z0 + d / 2);
                mask[x][z] = switch (shape) {
                    case 1 -> {
                        double px = Math.abs(fx) - (w / 2 - radius);
                        double pz = Math.abs(fz) - (d / 2 - radius);
                        yield px <= 0 || pz <= 0 || px * px + pz * pz <= radius * radius;
                    }
                    case 2 -> (fx * fx) / (w * w / 4) + (fz * fz) / (d * d / 4) <= 1.0001;
                    case 3 -> !(x >= width / 2 && z >= depth / 2);
                    default -> true;
                };
            }
        }
        return mask;
    }

    private boolean inside(boolean[][] mask, int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < depth && mask[x][z];
    }

    private boolean perimeter(boolean[][] mask, int x, int z) {
        return mask[x][z] && (!inside(mask, x - 1, z) || !inside(mask, x + 1, z)
            || !inside(mask, x, z - 1) || !inside(mask, x, z + 1));
    }

    private boolean isEmpty(boolean[][] mask) {
        for (boolean[] column : mask) {
            for (boolean cell : column) {
                if (cell) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Position along the facade, counted from the middle of the side the wall faces.
     */
    private int facadeColumn(boolean[][] mask, int inset, int x, int z) {
        boolean outX = !inside(mask, x - 1, z) || !inside(mask, x + 1, z);
        boolean outZ = !inside(mask, x, z - 1) || !inside(mask, x, z + 1);
        int alongX = Math.abs(2 * x - (width - 1)) / 2;
        int alongZ = Math.abs(2 * z - (depth - 1)) / 2;
        if (outX && !outZ) {
            return alongZ;
        }
        if (outZ && !outX) {
            return alongX;
        }
        double ax = Math.abs(x - (width - 1) / 2.0) / Math.max(1, width - 2 * inset);
        double az = Math.abs(z - (depth - 1) / 2.0) / Math.max(1, depth - 2 * inset);
        return ax >= az ? alongZ : alongX;
    }

    private boolean squareCorner(boolean[][] mask, int x, int z) {
        boolean outX = !inside(mask, x - 1, z) || !inside(mask, x + 1, z);
        boolean outZ = !inside(mask, x, z - 1) || !inside(mask, x, z + 1);
        return outX && outZ && (shape == 0 || shape == 3);
    }

    // ---- generation ------------------------------------------------------------------------------

    /**
     * Generate the building.
     *
     * @param sink receives the blocks
     */
    public void generate(Sink sink) {
        for (int s = 0; s < stories; s++) {
            generateStory(sink, s);
        }
        if (hasCore) {
            generateCore(sink);
        }
        generateEntrance(sink);
        generateRoof(sink);
    }

    private void generateStory(Sink sink, int story) {
        int inset = insets[story];
        boolean[][] mask = mask(inset);
        boolean[][] below = story > 0 ? mask(insets[story - 1]) : mask;
        int y0 = floorY(story);
        int height = story == 0 ? lobbyHeight : storyHeight;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (inCore(x, z)) {
                    continue;
                }
                if (mask[x][z]) {
                    boolean edge = perimeter(mask, x, z);
                    for (int r = 0; r < height; r++) {
                        if (edge) {
                            sink.block(x, y0 + r, z, facade(mask, inset, x, z, story, r, height));
                        } else if (r == 0) {
                            sink.block(x, y0, z, x % 4 == 2 && z % 4 == 2 ? StyleRole.LIGHT : StyleRole.FLOOR);
                        } else {
                            sink.air(x, y0 + r, z);
                        }
                    }
                } else if (below[x][z]) {
                    // a terrace where the tower steps in
                    boolean edge = perimeter(below, x, z);
                    sink.block(x, y0, z, edge ? StyleRole.TRIM : StyleRole.ROOF);
                    if (edge) {
                        sink.block(x, y0 + 1, z, StyleRole.TRIM);
                    }
                }
            }
        }
    }

    private StyleRole facade(boolean[][] mask, int inset, int x, int z, int story, int r, int height) {
        if (design.cornerPillars() && squareCorner(mask, x, z)) {
            return StyleRole.PILLAR;
        }
        int t = facadeColumn(mask, inset, x, z);
        if (story == 0) {
            if (r == 0 || r == height - 1) {
                return StyleRole.TRIM;
            }
            return t % 4 == 3 ? StyleRole.PILLAR : StyleRole.WINDOW;
        }
        boolean band = r == 0 && design.trimEvery() > 0 && story % design.trimEvery() == 0;
        return switch (design.facade()) {
            case RIBBON -> band || r == 0 || r == height - 1 ? StyleRole.WALL : StyleRole.WINDOW;
            case GRID -> band ? StyleRole.TRIM
                : t % 2 == 1 && r >= 1 && r <= height - 2 ? StyleRole.WINDOW : StyleRole.WALL;
            case VERTICAL -> t % 3 == 0 ? StyleRole.PILLAR : r == 0 ? StyleRole.ACCENT : StyleRole.WINDOW;
            case LANCET -> {
                if (t % 4 == 0) {
                    yield StyleRole.PILLAR;
                }
                if (band) {
                    yield StyleRole.TRIM;
                }
                int top = t % 4 == 2 ? height - 1 : height - 2;
                yield r >= 1 && r <= top ? StyleRole.WINDOW : StyleRole.WALL;
            }
            case FINS -> {
                if (t % 4 == 0) {
                    yield StyleRole.PILLAR;
                }
                if (r == 0) {
                    yield StyleRole.TRIM;
                }
                yield t % 4 != 2 && r <= height - 2 ? StyleRole.WINDOW : StyleRole.WALL;
            }
            case CURTAIN -> t % 5 == 0 ? StyleRole.PILLAR : r == 0 ? StyleRole.TRIM : StyleRole.WINDOW;
            case BANDS -> r == 0 ? (t % 6 == 0 ? StyleRole.LIGHT : StyleRole.ACCENT) : StyleRole.WINDOW;
            case ARCADE -> {
                if (t % 4 == 0) {
                    yield StyleRole.PILLAR;
                }
                if (band) {
                    yield StyleRole.TRIM;
                }
                int top = t % 4 == 2 ? height - 2 : height - 3;
                yield r >= 1 && r <= top ? StyleRole.WINDOW : StyleRole.WALL;
            }
        };
    }

    private boolean inCore(int x, int z) {
        return hasCore && Math.abs(x - coreX) <= 2 && Math.abs(z - coreZ) <= 2;
    }

    private void generateCore(Sink sink) {
        int roof = roofY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring <= 1) {
                    sink.block(coreX + dx, 0, coreZ + dz, StyleRole.FLOOR);
                } else {
                    sink.block(coreX + dx, 0, coreZ + dz, StyleRole.WALL);
                }
            }
        }
        for (int y = 1; y < roof; y++) {
            int step = (y - 1) % RING.length;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = coreX + dx;
                    int z = coreZ + dz;
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    if (ring == 2) {
                        sink.block(x, y, z, StyleRole.WALL);
                    } else if (ring == 0) {
                        sink.block(x, y, z, StyleRole.PILLAR);
                    } else if (dx == RING[step][0] && dz == RING[step][1]) {
                        sink.block(x, y, z, StyleRole.FLOOR);
                    } else {
                        sink.air(x, y, z);
                    }
                }
            }
        }
        // a doorway into the stairwell on every side of every floor
        for (int s = 0; s < stories; s++) {
            int y0 = floorY(s);
            for (int r = 1; r <= 2; r++) {
                sink.air(coreX, y0 + r, coreZ - 2);
                sink.air(coreX, y0 + r, coreZ + 2);
                sink.air(coreX - 2, y0 + r, coreZ);
                sink.air(coreX + 2, y0 + r, coreZ);
            }
        }
    }

    private void generateEntrance(Sink sink) {
        boolean[][] mask = mask(insets[0]);
        int halfDoor = width - 2 * insets[0] >= 9 ? 1 : 0;
        int doorTop = Math.min(3, lobbyHeight - 2);
        for (int x = 0; x < width; x++) {
            int t = Math.abs(2 * x - (width - 1)) / 2;
            if (t > halfDoor + 1) {
                continue;
            }
            int front = -1;
            for (int z = 0; z < depth; z++) {
                if (mask[x][z]) {
                    front = z;
                    break;
                }
            }
            if (front < 0) {
                continue;
            }
            for (int r = 1; r <= doorTop + 1; r++) {
                if (t <= halfDoor && r <= doorTop) {
                    sink.air(x, r, front);
                } else {
                    sink.block(x, r, front, StyleRole.ACCENT);
                }
            }
            for (int r = 1; r <= doorTop; r++) {
                if (t <= halfDoor && inside(mask, x, front + 1) && !inCore(x, front + 1)) {
                    sink.air(x, r, front + 1);
                }
            }
            // canopy over the door
            for (int dz = 1; dz <= 2; dz++) {
                boolean lamp = t == halfDoor + 1 && dz == 2;
                sink.block(x, doorTop + 1, front - dz, lamp ? StyleRole.LIGHT : StyleRole.ACCENT);
            }
        }
    }

    // ---- roof and crown --------------------------------------------------------------------------

    private void generateRoof(Sink sink) {
        int top = insets[stories - 1];
        boolean[][] mask = mask(top);
        int roof = roofY();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (mask[x][z]) {
                    sink.block(x, roof, z, perimeter(mask, x, z) ? StyleRole.TRIM : StyleRole.ROOF);
                }
            }
        }
        if (crown != 3 && crown != 2) {
            parapet(sink, mask, roof + 1);
        }
        Random random = new Random(seed);
        switch (crown) {
            case 1 -> spire(sink, top, roof);
            case 2 -> steppedCrown(sink, top, roof);
            case 3 -> pyramid(sink, top, roof);
            default -> rooftop(sink, mask, top, roof, random);
        }
    }

    private void parapet(Sink sink, boolean[][] mask, int y) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (perimeter(mask, x, z)) {
                    sink.block(x, y, z, StyleRole.TRIM);
                }
            }
        }
    }

    private int centerX() {
        return coreX;
    }

    private int centerZ() {
        return coreZ;
    }

    private void spire(Sink sink, int inset, int roof) {
        int base = Math.max(1, (Math.min(width, depth) - 2 * inset) / 6);
        int height = Math.max(6, (int) Math.round(roof * 0.15));
        int cx = centerX();
        int cz = centerZ();
        for (int k = 0; k < height; k++) {
            int half = (int) Math.round(base * (1 - k / (double) height));
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == half) {
                        sink.block(cx + dx, roof + 1 + k, cz + dz, StyleRole.ROOF);
                    }
                }
            }
        }
        int antenna = Math.max(3, height / 2);
        for (int k = 0; k < antenna; k++) {
            sink.block(cx, roof + 1 + height + k, cz, StyleRole.TRIM);
        }
        sink.block(cx, roof + 1 + height + antenna, cz, StyleRole.LIGHT);
    }

    private void steppedCrown(Sink sink, int inset, int roof) {
        int y = roof + 1;
        int tiers = 0;
        for (int tier = 1; tier <= 3; tier++) {
            int tierInset = inset + 2 * tier;
            boolean[][] mask = mask(tierInset);
            if (isEmpty(mask) || width - 2 * tierInset < 3 || depth - 2 * tierInset < 3) {
                break;
            }
            tiers++;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (!mask[x][z]) {
                        continue;
                    }
                    boolean edge = perimeter(mask, x, z);
                    for (int r = 0; r < storyHeight; r++) {
                        if (r == storyHeight - 1) {
                            sink.block(x, y + r, z, edge ? StyleRole.TRIM : StyleRole.ROOF);
                        } else if (edge) {
                            int t = facadeColumn(mask, tierInset, x, z);
                            sink.block(x, y + r, z, squareCorner(mask, x, z) || t % 2 == 0
                                ? StyleRole.PILLAR : StyleRole.WINDOW);
                        } else {
                            sink.air(x, y + r, z);
                        }
                    }
                }
            }
            y += storyHeight;
        }
        // the edge of the main roof gets a parapet round the first tier
        parapetOutside(sink, mask(inset), mask(inset + 2), roof + 1);
        int cx = centerX();
        int cz = centerZ();
        if (tiers == 0) {
            parapet(sink, mask(inset), roof + 1);
        }
        sink.block(cx, y, cz, StyleRole.LIGHT);
        for (int k = 1; k <= 3; k++) {
            sink.block(cx, y + k, cz, StyleRole.ACCENT);
        }
    }

    private void parapetOutside(Sink sink, boolean[][] outer, boolean[][] inner, int y) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (perimeter(outer, x, z) && !inner[x][z]) {
                    sink.block(x, y, z, StyleRole.TRIM);
                }
            }
        }
    }

    private void pyramid(Sink sink, int inset, int roof) {
        int k = 1;
        int lastY = roof;
        boolean[][] last = mask(inset);
        while (true) {
            boolean[][] mask = mask(inset + k);
            if (isEmpty(mask)) {
                break;
            }
            last = mask;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (perimeter(mask, x, z)) {
                        sink.block(x, roof + k, z, StyleRole.ROOF);
                    }
                }
            }
            lastY = roof + k;
            k++;
        }
        if (inside(last, centerX(), centerZ())) {
            sink.block(centerX(), lastY + 1, centerZ(), StyleRole.LIGHT);
        }
    }

    private void rooftop(Sink sink, boolean[][] mask, int inset, int roof, Random random) {
        int w = width - 2 * inset;
        int d = depth - 2 * inset;
        if (w < 9 || d < 9) {
            return;
        }
        // find a free spot for a water tower or a plant room, away from the parapet
        int px = centerX();
        int pz = centerZ();
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = inset + 3 + random.nextInt(Math.max(1, w - 6));
            int z = inset + 3 + random.nextInt(Math.max(1, d - 6));
            if (fits(mask, x, z, 3)) {
                px = x;
                pz = z;
                break;
            }
        }
        if (!fits(mask, px, pz, 3)) {
            return;
        }
        if (family.equals("brick")) {
            waterTower(sink, px, pz, roof + 1);
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    sink.block(px + dx, roof + 1, pz + dz, StyleRole.WALL);
                    sink.block(px + dx, roof + 2, pz + dz, dx == 0 && dz == 0 ? StyleRole.LIGHT : StyleRole.TRIM);
                }
            }
        }
    }

    private boolean fits(boolean[][] mask, int x, int z, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!inside(mask, x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void waterTower(Sink sink, int cx, int cz, int y) {
        for (int k = 0; k < 3; k++) {
            for (int[] leg : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                sink.block(cx + leg[0], y + k, cz + leg[1], StyleRole.ACCENT);
            }
        }
        for (int k = 3; k < 8; k++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 2.3) {
                        continue;
                    }
                    if (k == 3 || distance > 1.2) {
                        sink.block(cx + dx, y + k, cz + dz, StyleRole.ACCENT);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sink.block(cx + dx, y + 8, cz + dz, StyleRole.ROOF);
            }
        }
        sink.block(cx, y + 9, cz, StyleRole.ROOF);
    }
}
