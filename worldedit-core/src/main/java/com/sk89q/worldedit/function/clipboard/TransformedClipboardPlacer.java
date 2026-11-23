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

package com.sk89q.worldedit.function.clipboard;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.diff.WorldDiff;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashSet;
import java.util.Set;

/**
 * Places a clipboard with any transform (such as a rotation by 30 degrees) by inverse mapping:
 * every target block looks up the clipboard block that lands on it, so turned copies have no
 * holes. Positions are taken relative to the clipboard origin and rounded to the nearest block.
 * Directional block states (stairs, logs, ...) are turned to the closest direction they support.
 */
public final class TransformedClipboardPlacer {

    private final Clipboard clipboard;
    private final Transform transform;
    private final Transform inverse;
    private final boolean ignoreAir;
    private final BlockVector3 min;
    private final BlockVector3 max;

    /**
     * Create a placer.
     *
     * @param clipboard the clipboard
     * @param transform the transform applied to positions relative to the clipboard origin
     * @param ignoreAir true to leave the target alone where the clipboard has air
     */
    public TransformedClipboardPlacer(Clipboard clipboard, Transform transform, boolean ignoreAir) {
        this.clipboard = clipboard;
        this.transform = transform;
        this.inverse = transform.inverse();
        this.ignoreAir = ignoreAir;
        Region region = clipboard.getRegion();
        BlockVector3 lo = region.getMinimumPoint().subtract(clipboard.getOrigin());
        BlockVector3 hi = region.getMaximumPoint().subtract(clipboard.getOrigin());
        double[] mins = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
        double[] maxs = { -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        // use the blocks' outer faces, not their centres, so thin clipboards still get a box to fill
        for (int i = 0; i < 8; i++) {
            Vector3 corner = Vector3.at(
                (i & 1) == 0 ? lo.x() - 0.5 : hi.x() + 0.5,
                (i & 2) == 0 ? lo.y() - 0.5 : hi.y() + 0.5,
                (i & 4) == 0 ? lo.z() - 0.5 : hi.z() + 0.5);
            Vector3 t = transform.apply(corner);
            double[] values = { t.x(), t.y(), t.z() };
            for (int a = 0; a < 3; a++) {
                mins[a] = Math.min(mins[a], values[a]);
                maxs[a] = Math.max(maxs[a], values[a]);
            }
        }
        this.min = BlockVector3.at(round(mins[0] + 0.5), round(mins[1] + 0.5), round(mins[2] + 0.5));
        this.max = BlockVector3.at(round(maxs[0] - 0.5), round(maxs[1] - 0.5), round(maxs[2] - 0.5));
    }

    private static int round(double value) {
        return (int) Math.floor(value + 0.5);
    }

    /**
     * The smallest target offset (relative to where the origin is placed) that may be changed.
     *
     * @return the minimum offset
     */
    public BlockVector3 getMinimum() {
        return min;
    }

    /**
     * The largest target offset (relative to where the origin is placed) that may be changed.
     *
     * @return the maximum offset
     */
    public BlockVector3 getMaximum() {
        return max;
    }

    /**
     * Find the clipboard block (absolute clipboard position) that lands on a target offset.
     *
     * @param offset the target offset relative to where the origin is placed
     * @return the clipboard position, or null if nothing lands there
     */
    public BlockVector3 sourceFor(BlockVector3 offset) {
        Vector3 back = inverse.apply(offset.toVector3());
        BlockVector3 source = BlockVector3.at(round(back.x()), round(back.y()), round(back.z()))
            .add(clipboard.getOrigin());
        return clipboard.getRegion().contains(source) ? source : null;
    }

    /**
     * Place the clipboard.
     *
     * @param target the target extent
     * @param to where the clipboard origin goes
     * @return the number of blocks set
     * @throws WorldEditException if the target refuses a block (e.g. too many changes)
     */
    public int place(Extent target, BlockVector3 to) throws WorldEditException {
        int count = 0;
        Set<BlockVector3> filled = new HashSet<>();
        Region region = clipboard.getRegion();
        BlockVector3 origin = clipboard.getOrigin();
        // 1. every clipboard block lands once, at the block nearest to where it is carried
        for (BlockVector3 source : region) {
            Vector3 there = transform.apply(source.subtract(origin).toVector3());
            BlockVector3 offset = BlockVector3.at(round(there.x()), round(there.y()), round(there.z()));
            if (filled.add(offset)) {
                count += put(target, to, offset, source);
            }
        }
        // 2. turning a solid area by an odd angle leaves gaps between those blocks; fill them by
        // looking back into the clipboard, but only inside solid areas, so thin posts and walls
        // are not doubled
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockVector3 offset = BlockVector3.at(x, y, z);
                    if (filled.contains(offset)) {
                        continue;
                    }
                    BlockVector3 source = sourceFor(offset);
                    if (source == null || !isInterior(source)) {
                        continue;
                    }
                    filled.add(offset);
                    count += put(target, to, offset, source);
                }
            }
        }
        return count;
    }

    /**
     * Whether the four horizontal neighbours of a clipboard block are in the clipboard and are air
     * exactly when it is, i.e. the block is inside an area rather than on its edge.
     */
    private boolean isInterior(BlockVector3 source) {
        Region region = clipboard.getRegion();
        boolean air = WorldDiff.isAir(clipboard.getBlock(source));
        BlockVector3[] neighbours = {
            source.add(1, 0, 0), source.add(-1, 0, 0), source.add(0, 0, 1), source.add(0, 0, -1),
        };
        for (BlockVector3 neighbour : neighbours) {
            if (!region.contains(neighbour) || WorldDiff.isAir(clipboard.getBlock(neighbour)) != air) {
                return false;
            }
        }
        return true;
    }

    private int put(Extent target, BlockVector3 to, BlockVector3 offset, BlockVector3 source) throws WorldEditException {
        BaseBlock block = clipboard.getFullBlock(source);
        if (block.getBlockType() == BlockTypes.STRUCTURE_VOID
            || (ignoreAir && WorldDiff.isAir(block.toImmutableState()))) {
            return 0;
        }
        return target.setBlock(to.add(offset), BlockTransformExtent.transform(block, transform)) ? 1 : 0;
    }

    /**
     * Get the lowest offset y (relative to the origin) of a non-air block in a clipboard.
     *
     * @param clipboard the clipboard
     * @return the lowest solid offset, or the region's lowest offset if all is air
     */
    public static int lowestSolidOffset(Clipboard clipboard) {
        Region region = clipboard.getRegion();
        BlockVector3 lo = region.getMinimumPoint();
        BlockVector3 hi = region.getMaximumPoint();
        for (int y = lo.y(); y <= hi.y(); y++) {
            for (int x = lo.x(); x <= hi.x(); x++) {
                for (int z = lo.z(); z <= hi.z(); z++) {
                    if (!WorldDiff.isAir(clipboard.getBlock(BlockVector3.at(x, y, z)))) {
                        return y - clipboard.getOrigin().y();
                    }
                }
            }
        }
        return lo.y() - clipboard.getOrigin().y();
    }

    /**
     * Get a holder's clipboard with its transform (from //rotate and //flip) applied, so that
     * further work can use plain clipboard coordinates. Returns the clipboard itself when there
     * is no transform.
     *
     * @param holder the holder
     * @return a clipboard with the same origin
     * @throws WorldEditException if copying fails
     */
    public static Clipboard flatten(ClipboardHolder holder) throws WorldEditException {
        Clipboard clipboard = holder.getClipboard();
        if (holder.getTransform().isIdentity()) {
            return clipboard;
        }
        TransformedClipboardPlacer placer = new TransformedClipboardPlacer(clipboard, holder.getTransform(), false);
        BlockVector3 origin = clipboard.getOrigin();
        BlockArrayClipboard result = new BlockArrayClipboard(
            new CuboidRegion(origin.add(placer.getMinimum()), origin.add(placer.getMaximum())));
        result.setOrigin(origin);
        placer.place(result, origin);
        return result;
    }
}
