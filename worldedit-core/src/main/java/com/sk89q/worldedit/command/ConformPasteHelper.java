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

package com.sk89q.worldedit.command;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.clipboard.ConformPaster;
import com.sk89q.worldedit.function.clipboard.TransformedClipboardPlacer;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;

/**
 * The {@code //paste -c} (conform) part of {@link ClipboardCommands#paste}.
 */
final class ConformPasteHelper {

    private ConformPasteHelper() {
    }

    static void paste(Actor actor, World world, LocalSession session, EditSession editSession,
                      ClipboardHolder holder, BlockVector3 to, int smoothRadius, boolean waterIsGround,
                      boolean ignoreAir, boolean pasteStructureVoid, boolean fillBelow, Mask sourceMask,
                      boolean selectPasted, boolean onlySelect, boolean entitiesOrBiomes) throws WorldEditException {
        boolean turned = !holder.getTransform().isIdentity();
        Clipboard clipboard = TransformedClipboardPlacer.flatten(holder);
        Mask mask = sourceMask;
        if (mask != null && turned) {
            actor.printError(TextComponent.of("-m cannot be used with -c on a rotated or flipped clipboard; "
                + "the mask was ignored."));
            mask = null;
        }
        if (entitiesOrBiomes) {
            actor.printError(TextComponent.of("-e and -b are not supported with -c and were ignored."));
        }
        ConformPaster paster;
        try {
            paster = new ConformPaster(clipboard, smoothRadius, waterIsGround, ignoreAir, pasteStructureVoid,
                fillBelow, mask);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        int minY = world.getMinY();
        int maxY = world.getMaxY();
        boolean found = paster.measure(editSession, to, minY, maxY);
        if (!found) {
            actor.printError(TextComponent.of("No ground found under the clipboard; pasting it flat."));
        }
        int changed = 0;
        if (!onlySelect) {
            changed = paster.place(editSession, to, minY);
        }
        if (selectPasted || onlySelect) {
            BlockVector3 offset = to.subtract(clipboard.getOrigin());
            BlockVector3 min = clipboard.getRegion().getMinimumPoint().add(offset).add(0, paster.getMinShift(), 0);
            BlockVector3 max = clipboard.getRegion().getMaximumPoint().add(offset).add(0, paster.getMaxShift(), 0);
            CuboidRegionSelector selector = session.getRegionSelector(world) instanceof ExtendingCuboidRegionSelector
                ? new ExtendingCuboidRegionSelector(world, min, max)
                : new CuboidRegionSelector(world, min, max);
            session.setRegionSelector(world, selector);
            selector.learnChanges();
            selector.explainRegionAdjust(actor, session);
        }
        if (onlySelect) {
            actor.printInfo(TextComponent.of("Selected where the conformed paste would go."));
        } else {
            actor.printInfo(TextComponent.of("Pasted to follow the terrain at " + to + " (" + changed
                + " blocks, columns moved " + paster.getMinShift() + " to " + paster.getMaxShift() + ")."));
        }
    }
}
