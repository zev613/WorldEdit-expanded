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

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Scales a clipboard around its origin with {@link ScaleResampler}.
 */
public final class ClipboardScaler {

    private ClipboardScaler() {
    }

    /**
     * Scale a clipboard. The result keeps the origin, so pasting it lands where the original did,
     * grown or shrunk around the paste position. Block states are kept as they are; block entity
     * data (chest contents, sign text) is kept on one copy of each block only.
     *
     * @param clipboard the clipboard, with no transform pending
     * @param factor the factor along x, y and z
     * @param solidFraction see {@link ScaleResampler#resample}
     * @param maxVolume the largest result allowed
     * @return the scaled clipboard
     */
    public static Clipboard scale(Clipboard clipboard, double[] factor, double solidFraction, long maxVolume) {
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 lo = clipboard.getRegion().getMinimumPoint().subtract(origin);
        BlockVector3 hi = clipboard.getRegion().getMaximumPoint().subtract(origin);
        ScaleResampler resampler = new ScaleResampler(
            new int[] { lo.x(), lo.y(), lo.z() }, new int[] { hi.x(), hi.y(), hi.z() }, factor);
        resampler.checkVolume(maxVolume);
        int[] dMin = resampler.getDestinationMin();
        int[] dMax = resampler.getDestinationMax();
        BlockArrayClipboard result = new BlockArrayClipboard(new CuboidRegion(
            origin.add(dMin[0], dMin[1], dMin[2]), origin.add(dMax[0], dMax[1], dMax[2])));
        result.setOrigin(origin);

        Map<BlockState, Integer> ids = new HashMap<>();
        ScaleResampler.Source source = (x, y, z) -> {
            BlockState state = clipboard.getBlock(origin.add(x, y, z));
            if (state.getBlockType().getMaterial().isAir()) {
                return 0;
            }
            return ids.computeIfAbsent(state, _ -> ids.size() + 1);
        };
        resampler.resample(source, solidFraction, (dx, dy, dz, sx, sy, sz, primary) -> {
            BlockVector3 from = origin.add(sx, sy, sz);
            result.setBlock(origin.add(dx, dy, dz),
                primary ? clipboard.getFullBlock(from) : clipboard.getBlock(from).toBaseBlock());
        });
        return result;
    }
}
