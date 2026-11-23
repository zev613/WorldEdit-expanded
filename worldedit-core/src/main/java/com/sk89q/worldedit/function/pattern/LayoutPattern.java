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

package com.sk89q.worldedit.function.pattern;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A pattern that picks one of several sub-patterns for each block using a {@link PatternLayouts.Layout},
 * such as stripes, a checkerboard or bricks.
 */
public class LayoutPattern extends AbstractPattern {

    private final PatternLayouts.Layout layout;
    private final List<Pattern> patterns;

    /**
     * Create a new instance.
     *
     * @param layout gives the index of the pattern to use for each block (wrapped around the list)
     * @param patterns the patterns
     */
    public LayoutPattern(PatternLayouts.Layout layout, List<Pattern> patterns) {
        checkNotNull(layout, "layout cannot be null");
        checkNotNull(patterns, "patterns cannot be null");
        checkArgument(!patterns.isEmpty(), "patterns cannot be empty");
        this.layout = layout;
        this.patterns = ImmutableList.copyOf(patterns);
    }

    @Override
    public BaseBlock applyBlock(BlockVector3 position) {
        int index = Math.floorMod(layout.index(position.x(), position.y(), position.z()), patterns.size());
        return patterns.get(index).applyBlock(position);
    }
}
