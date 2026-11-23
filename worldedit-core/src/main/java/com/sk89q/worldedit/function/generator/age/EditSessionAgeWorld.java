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

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The ageing generators' view of an {@link EditSession} and a selection, so
 * every change goes through the edit session (one undoable edit).
 */
public final class EditSessionAgeWorld implements AgeWorld {

    private final EditSession editSession;
    private final Region region;
    private final Map<BlockState, AgeBlock> toAge = new HashMap<>();
    private final Map<AgeBlock, BlockState> toState = new HashMap<>();
    private final Map<String, Boolean> existing = new HashMap<>();
    private MaxChangedBlocksException failure;

    /**
     * Create a view.
     *
     * @param editSession the edit session
     * @param region the selection
     */
    public EditSessionAgeWorld(EditSession editSession, Region region) {
        this.editSession = editSession;
        this.region = region;
    }

    /**
     * The box of a selection.
     *
     * @param region the selection
     * @return the box
     */
    public static AgeBox boxOf(Region region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new AgeBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    @Override
    public AgeBlock getBlock(int x, int y, int z) {
        BlockState state = editSession.getBlock(BlockVector3.at(x, y, z));
        return toAge.computeIfAbsent(state, EditSessionAgeWorld::convert);
    }

    private static AgeBlock convert(BlockState state) {
        Map<String, String> props = new TreeMap<>();
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            props.put(entry.getKey().name(), String.valueOf(entry.getValue()).toLowerCase(Locale.ROOT));
        }
        return new AgeBlock(state.getBlockType().id(), props);
    }

    private BlockState convert(AgeBlock block) {
        BlockType type = BlockTypes.get(block.id());
        if (type == null) {
            return null;
        }
        BlockState state = type.getDefaultState();
        for (Map.Entry<String, String> entry : block.properties().entrySet()) {
            Property<?> property = type.getPropertyMap().get(entry.getKey());
            if (property != null) {
                state = with(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <V> BlockState with(BlockState state, Property<V> property, String value) {
        try {
            return state.with(property, property.getValueFor(value));
        } catch (RuntimeException _) {
            return state;
        }
    }

    @Override
    public void setBlock(int x, int y, int z, AgeBlock block) {
        if (failure != null) {
            return;
        }
        BlockState state = toState.computeIfAbsent(block, this::convert);
        if (state == null) {
            return;
        }
        try {
            editSession.setBlock(BlockVector3.at(x, y, z), state);
        } catch (MaxChangedBlocksException e) {
            failure = e;
        }
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return region.contains(BlockVector3.at(x, y, z));
    }

    @Override
    public boolean exists(String id) {
        return existing.computeIfAbsent(id, i -> BlockTypes.get(i) != null);
    }

    /**
     * Throw the change limit error, if writing stopped because of it.
     *
     * @throws MaxChangedBlocksException if the limit was hit
     */
    public void rethrow() throws MaxChangedBlocksException {
        if (failure != null) {
            throw failure;
        }
    }
}
