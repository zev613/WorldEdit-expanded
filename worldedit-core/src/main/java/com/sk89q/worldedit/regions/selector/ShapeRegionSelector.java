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

package com.sk89q.worldedit.regions.selector;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.internal.cui.CUIRegion;
import com.sk89q.worldedit.internal.cui.SelectionPointEvent;
import com.sk89q.worldedit.internal.cui.SelectionShapeEvent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.ShapeRegion;
import com.sk89q.worldedit.regions.selector.limit.SelectorLimits;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;

import java.util.List;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Selects the voxels of a named shape ({@code //selshape heart 10}).
 *
 * <p>The shape itself is fixed when the selector is made. Clicking with the wand (either button), or
 * {@code //pos1} / {@code //pos2}, moves the shape so that its placement point (its centre, or the middle
 * of its bottom with {@code -b}) is on the clicked block. Clearing the selection with {@code //sel}
 * hides the shape until the next click. WorldEdit CUI shows the bounding box.</p>
 */
public class ShapeRegionSelector implements RegionSelector, CUIRegion {

    private final ShapeRegion region;
    private final BlockVector3 anchorOffset;
    private final String description;
    private boolean placed;

    /**
     * Create a selector holding a shape.
     *
     * @param world the world, or null
     * @param voxels the voxels of the shape, as offsets from its centre
     * @param position the placement position
     * @param anchorOffset where the centre of the shape is relative to the placement position
     * @param description a short description such as "heart 10 (lobes=1)"
     */
    public ShapeRegionSelector(@Nullable World world, VoxelGrid voxels, BlockVector3 position,
                               BlockVector3 anchorOffset, String description) {
        checkNotNull(position);
        this.anchorOffset = checkNotNull(anchorOffset);
        this.description = checkNotNull(description);
        this.region = new ShapeRegion(world, voxels, position.add(anchorOffset));
        this.placed = true;
    }

    /**
     * The point the shape was placed at (and moves with wand clicks).
     *
     * @return the placement position
     */
    public BlockVector3 getAnchor() {
        return region.getOrigin().subtract(anchorOffset);
    }

    /**
     * Move the shape so its placement point is at the given position.
     *
     * @param position the new placement position
     * @return false if the shape was already there
     */
    public boolean moveTo(BlockVector3 position) {
        checkNotNull(position);
        BlockVector3 change = position.subtract(getAnchor());
        if (placed && change.equals(BlockVector3.ZERO)) {
            return false;
        }
        region.shift(change);
        placed = true;
        return true;
    }

    @Nullable
    @Override
    public World getWorld() {
        return region.getWorld();
    }

    @Override
    public void setWorld(@Nullable World world) {
        region.setWorld(world);
    }

    @Override
    public boolean selectPrimary(BlockVector3 position, SelectorLimits limits) {
        return moveTo(position);
    }

    @Override
    public boolean selectSecondary(BlockVector3 position, SelectorLimits limits) {
        return moveTo(position);
    }

    @Override
    public void explainPrimarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        explainMove(actor, session);
    }

    @Override
    public void explainSecondarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        explainMove(actor, session);
    }

    private void explainMove(Actor actor, LocalSession session) {
        actor.printInfo(TextComponent.of("Shape " + description + " moved to " + getAnchor()
            + " (" + region.getVolume() + " blocks)."));
        session.describeCUI(actor);
    }

    @Override
    public void explainRegionAdjust(Actor actor, LocalSession session) {
        session.dispatchCUIEvent(actor, new SelectionShapeEvent(getTypeID()));
        describeCUI(session, actor);
    }

    @Override
    public BlockVector3 getPrimaryPosition() throws IncompleteRegionException {
        if (!placed) {
            throw new IncompleteRegionException();
        }
        return getAnchor();
    }

    @Override
    public Region getRegion() throws IncompleteRegionException {
        if (!placed) {
            throw new IncompleteRegionException();
        }
        return region;
    }

    @Override
    public Region getIncompleteRegion() {
        return region;
    }

    @Override
    public boolean isDefined() {
        return placed;
    }

    @Override
    public long getVolume() {
        return placed ? region.getVolume() : 0;
    }

    @Override
    public void learnChanges() {
        // the region is shifted in place, so there is nothing to learn
    }

    @Override
    public void clear() {
        placed = false;
    }

    @Override
    public String getTypeName() {
        return "shape";
    }

    @Override
    public List<Component> getSelectionInfoLines() {
        if (!placed) {
            return List.of(TextComponent.of("Shape: " + description + " (not placed; click with the wand to place it)",
                TextColor.LIGHT_PURPLE));
        }
        return List.of(
            TextComponent.of("Shape: " + description, TextColor.LIGHT_PURPLE),
            TextComponent.of("Placed at: " + getAnchor(), TextColor.LIGHT_PURPLE),
            TextComponent.of("Bounds: " + region.getMinimumPoint() + " - " + region.getMaximumPoint(), TextColor.LIGHT_PURPLE)
        );
    }

    @Override
    public void describeCUI(LocalSession session, Actor player) {
        if (placed) {
            session.dispatchCUIEvent(player, new SelectionPointEvent(0, region.getMinimumPoint(), getVolume()));
            session.dispatchCUIEvent(player, new SelectionPointEvent(1, region.getMaximumPoint(), getVolume()));
        }
    }

    @Override
    public void describeLegacyCUI(LocalSession session, Actor player) {
        describeCUI(session, player);
    }

    @Override
    public int getProtocolVersion() {
        return 0;
    }

    @Override
    public String getTypeID() {
        // CUI has no shape type, so it shows the bounding box
        return "cuboid";
    }

    @Override
    public String getLegacyTypeID() {
        return "cuboid";
    }
}
