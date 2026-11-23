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
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Locatable;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.history.SelectiveUndo;
import com.sk89q.worldedit.history.persistent.PersistentHistory;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.HistoryListener;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Selective undo and persistent (on-disk) history support for
 * {@link HistoryCommands}.
 */
final class HistoryPersistenceHelper {

    private HistoryPersistenceHelper() {
    }

    /**
     * Undo only the block changes matching a mask in the last {@code times}
     * edits of the in-memory history. The reverted positions are neutralised
     * in each history entry (see {@link SelectiveUndo}); the history pointer
     * does not move.
     */
    static void selectiveUndo(Actor actor, LocalSession session, int times, Mask mask) {
        World actorWorld = actor instanceof Locatable locatable && locatable.getExtent() instanceof World w ? w : null;
        int entries = 0;
        int reverted = 0;
        for (int i = 0; i < times; i++) {
            EditSession entry = session.getUndoHistoryEntry(i);
            if (entry == null) {
                break;
            }
            World world = entry.getWorld();
            if (world == null) {
                continue;
            }
            if (actorWorld != null && !actorWorld.equals(world)) {
                actor.printError(TextComponent.of("Edit #" + (i + 1) + " was made in world '" + world.getName()
                    + "'; go there to undo it selectively (the mask is tested in your current world)."));
                break;
            }
            // Evaluate the mask for every position before changing anything.
            Map<BlockVector3, BaseBlock> toRevert = SelectiveUndo.collectUndo(entry.getChangeSet(), mask::test);
            if (toRevert.isEmpty()) {
                continue;
            }
            BlockBag blockBag = actor instanceof Player player ? session.getBlockBag(player) : null;
            try (EditSession carrier = WorldEdit.getInstance().newEditSessionBuilder().world(world).build();
                 EditSession target = PersistentHistory.createTargetSession(session, world, actor, blockBag)) {
                SelectiveUndo.fillUndoChanges(carrier.getChangeSet(), toRevert);
                carrier.undo(target);
            }
            if (blockBag != null) {
                blockBag.flushChanges();
            }
            SelectiveUndo.neutralise(entry.getChangeSet(), toRevert);
            HistoryListener listener = session.getHistoryListener();
            if (listener != null) {
                listener.onModified(entry);
            }
            entries++;
            reverted += toRevert.size();
        }
        if (reverted > 0) {
            actor.printInfo(TextComponent.of("Selectively undid " + reverted + " block change(s) in " + entries
                + " edit(s). The rest of those edits is still in your history."));
        } else if (session.getUndoHistoryEntry(0) == null) {
            actor.printError(TextComponent.of("Nothing to undo in this session's history (selective undo does not "
                + "use edits restored from disk)."));
        } else {
            actor.printError(TextComponent.of("No block changes in the last " + times + " edit(s) match the mask."));
        }
    }

    /**
     * Undo the newest edits, from memory and then from disk, like {@code //undo times}.
     *
     * @return how many edits were undone
     */
    static int undo(WorldEdit worldEdit, Actor actor, LocalSession session, int times) {
        int timesUndone = 0;
        for (int i = 0; i < times; ++i) {
            BlockBag blockBag = actor instanceof Player player ? session.getBlockBag(player) : null;
            EditSession undone = session.undo(blockBag, actor);
            if (undone != null) {
                timesUndone++;
                worldEdit.flushBlockBag(actor, undone);
            } else if (undoFromDisk(actor, session, blockBag)) {
                timesUndone++;
            } else {
                break;
            }
        }
        return timesUndone;
    }

    /**
     * Redo the oldest undone edits, from disk and then from memory, like {@code //redo times}.
     *
     * @return how many edits were redone
     */
    static int redo(WorldEdit worldEdit, Actor actor, LocalSession session, int times) {
        int timesRedone = 0;
        for (int i = 0; i < times; ++i) {
            BlockBag blockBag = actor instanceof Player player ? session.getBlockBag(player) : null;
            Boolean diskRedo = redoFromDisk(actor, session, blockBag);
            if (diskRedo != null) {
                if (!diskRedo) {
                    break;
                }
                timesRedone++;
                continue;
            }
            EditSession redone = session.redo(blockBag, actor);
            if (redone != null) {
                timesRedone++;
                worldEdit.flushBlockBag(actor, redone);
            } else {
                break;
            }
        }
        return timesRedone;
    }

    /**
     * Undo the newest persisted edit, if persistent history is enabled.
     *
     * @return true if something was undone
     */
    static boolean undoFromDisk(Actor actor, LocalSession session, @Nullable BlockBag blockBag) {
        if (!(session.getHistoryListener() instanceof PersistentHistory history)) {
            return false;
        }
        return report(actor, history.undoFromDisk(session, actor, blockBag), blockBag, "undo");
    }

    /**
     * Redo the oldest persisted undone edit that predates the in-memory history.
     *
     * @return null if there is no such edit (the caller should redo from memory),
     *     otherwise whether the redo succeeded
     */
    static @Nullable Boolean redoFromDisk(Actor actor, LocalSession session, @Nullable BlockBag blockBag) {
        if (!(session.getHistoryListener() instanceof PersistentHistory history) || !history.hasDiskRedo()) {
            return null;
        }
        return report(actor, history.redoFromDisk(session, actor, blockBag), blockBag, "redo");
    }

    private static boolean report(Actor actor, PersistentHistory.DiskResult result, @Nullable BlockBag blockBag,
                                  String what) {
        switch (result.status()) {
            case APPLIED -> {
                if (blockBag != null) {
                    blockBag.flushChanges();
                }
                actor.printInfo(TextComponent.of("Applied " + what + " of a saved edit (" + result.changes()
                    + " blocks in world '" + result.worldName() + "') from disk history."));
                return true;
            }
            case BARRIER -> actor.printError(TextComponent.of("The next saved edit was too large to be kept on disk; "
                + "older saved history cannot be " + what + "ne safely. Use //clearhistory to discard it."));
            case WORLD_MISSING -> actor.printError(TextComponent.of("The next saved edit belongs to world '"
                + result.worldName() + "', which is not loaded."));
            case FAILED -> actor.printError(TextComponent.of("The next saved edit could not be read; see the server log."));
            default -> {
            }
        }
        return false;
    }
}
