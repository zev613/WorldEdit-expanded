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
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.diff.WorldDiff;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

/**
 * Pastes a clipboard so every vertical column follows the terrain under it ("conform paste").
 *
 * <p>The clipboard's bottom layer is its lowest layer with any non-air block. In every column it
 * is put on the terrain surface (see {@link ClipboardGround#isTerrain}) nearest to where a plain
 * paste would put it, and everything in the column moves with it, so parts that float above the
 * bottom layer in the clipboard (an arch, a lamp's arm) float by the same amount. Column shifts
 * are smoothed with {@link ConformOffsets}.</p>
 */
public final class ConformPaster {

    private final Clipboard clipboard;
    private final int smoothRadius;
    private final boolean waterIsGround;
    private final boolean ignoreAir;
    private final boolean pasteStructureVoid;
    private final boolean fillBelow;
    private final Mask sourceMask;
    private int[][] shifts;
    private int minShift;
    private int maxShift;

    /**
     * Create a paster.
     *
     * @param clipboard the clipboard, with any rotation already applied
     * @param smoothRadius the smoothing radius (0 = follow the terrain exactly)
     * @param waterIsGround true to rest on water surfaces instead of the bottom under water
     * @param ignoreAir true to skip air blocks
     * @param pasteStructureVoid true to paste structure void blocks
     * @param fillBelow true to fill gaps under each column with its bottom block
     * @param sourceMask a mask tested on clipboard positions, or null
     */
    public ConformPaster(Clipboard clipboard, int smoothRadius, boolean waterIsGround, boolean ignoreAir,
                         boolean pasteStructureVoid, boolean fillBelow, Mask sourceMask) {
        if (smoothRadius < 0 || smoothRadius > ConformOffsets.MAX_RADIUS) {
            throw new IllegalArgumentException("The smoothing radius must be between 0 and "
                + ConformOffsets.MAX_RADIUS + ".");
        }
        this.clipboard = clipboard;
        this.smoothRadius = smoothRadius;
        this.waterIsGround = waterIsGround;
        this.ignoreAir = ignoreAir;
        this.pasteStructureVoid = pasteStructureVoid;
        this.fillBelow = fillBelow;
        this.sourceMask = sourceMask;
    }

    private boolean skipped(BaseBlock block) {
        return (block.getBlockType() == BlockTypes.STRUCTURE_VOID && !pasteStructureVoid)
            || WorldDiff.isAir(block.toImmutableState());
    }

    /**
     * Work out the column shifts. Must be called before any block is placed.
     *
     * @param world the extent to read the terrain from
     * @param to where the clipboard origin goes
     * @param minY the world's lowest y
     * @param maxY the world's highest y
     * @return false if no column found any ground (nothing is shifted then)
     */
    public boolean measure(Extent world, BlockVector3 to, int minY, int maxY) {
        BlockVector3 lo = clipboard.getRegion().getMinimumPoint();
        BlockVector3 hi = clipboard.getRegion().getMaximumPoint();
        BlockVector3 origin = clipboard.getOrigin();
        int sizeX = hi.x() - lo.x() + 1;
        int sizeZ = hi.z() - lo.z() + 1;
        int baseY = to.y() + TransformedClipboardPlacer.lowestSolidOffset(clipboard);
        int[][] raw = new int[sizeX][sizeZ];
        for (int ix = 0; ix < sizeX; ix++) {
            for (int iz = 0; iz < sizeZ; iz++) {
                int wx = to.x() + lo.x() + ix - origin.x();
                int wz = to.z() + lo.z() + iz - origin.z();
                int surface = ClipboardGround.surfaceNear(world, wx, wz, baseY - 1, minY, maxY, waterIsGround);
                raw[ix][iz] = surface == Integer.MIN_VALUE ? ConformOffsets.MISSING : surface + 1 - baseY;
            }
        }
        int[][] computed = ConformOffsets.compute(raw, smoothRadius);
        boolean found = computed != null;
        shifts = found ? computed : new int[sizeX][sizeZ];
        minShift = Integer.MAX_VALUE;
        maxShift = Integer.MIN_VALUE;
        for (int[] row : shifts) {
            for (int shift : row) {
                minShift = Math.min(minShift, shift);
                maxShift = Math.max(maxShift, shift);
            }
        }
        return found;
    }

    /**
     * The smallest column shift (after {@link #measure}).
     *
     * @return the shift
     */
    public int getMinShift() {
        return minShift;
    }

    /**
     * The largest column shift (after {@link #measure}).
     *
     * @return the shift
     */
    public int getMaxShift() {
        return maxShift;
    }

    /**
     * Paste, after {@link #measure}.
     *
     * @param world the target extent (read for filling, written to)
     * @param to where the clipboard origin goes
     * @param minY the world's lowest y
     * @return the number of blocks set
     * @throws WorldEditException if the target refuses a block
     */
    public int place(Extent world, BlockVector3 to, int minY) throws WorldEditException {
        if (shifts == null) {
            throw new IllegalStateException("measure() first");
        }
        BlockVector3 lo = clipboard.getRegion().getMinimumPoint();
        BlockVector3 hi = clipboard.getRegion().getMaximumPoint();
        BlockVector3 offset = to.subtract(clipboard.getOrigin());
        int count = 0;
        for (int x = lo.x(); x <= hi.x(); x++) {
            for (int z = lo.z(); z <= hi.z(); z++) {
                int shift = shifts[x - lo.x()][z - lo.z()];
                BaseBlock bottom = null;
                int bottomY = 0;
                for (int y = lo.y(); y <= hi.y(); y++) {
                    BlockVector3 source = BlockVector3.at(x, y, z);
                    if (!clipboard.getRegion().contains(source)
                        || (sourceMask != null && !sourceMask.test(source))) {
                        continue;
                    }
                    BaseBlock block = clipboard.getFullBlock(source);
                    if (bottom == null && !skipped(block)) {
                        bottom = block;
                        bottomY = y;
                    }
                    if ((block.getBlockType() == BlockTypes.STRUCTURE_VOID && !pasteStructureVoid)
                        || (ignoreAir && WorldDiff.isAir(block.toImmutableState()))) {
                        continue;
                    }
                    if (world.setBlock(source.add(offset).add(0, shift, 0), block)) {
                        count++;
                    }
                }
                if (fillBelow && bottom != null) {
                    BlockVector3 below = BlockVector3.at(x, bottomY, z).add(offset).add(0, shift - 1, 0);
                    int lowest = Math.max(minY, below.y() - 64);
                    for (int y = below.y(); y >= lowest; y--) {
                        BlockVector3 target = below.withY(y);
                        if (ClipboardGround.isTerrain(world.getBlock(target), waterIsGround)) {
                            break;
                        }
                        if (world.setBlock(target, bottom)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
