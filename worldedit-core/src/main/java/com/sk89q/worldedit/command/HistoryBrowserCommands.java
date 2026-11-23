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
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.history.browser.HistoryEntryInfo;
import com.sk89q.worldedit.history.browser.HistoryListing;
import com.sk89q.worldedit.history.browser.HistoryListing.Row;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.persistent.HistorySnapshot;
import com.sk89q.worldedit.history.persistent.PersistentHistory;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.PaginationBox;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import org.apache.logging.log4j.Logger;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * {@code //history}: browse the undo history and undo or redo to any point in it.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class HistoryBrowserCommands {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final int PER_PAGE = 8;

    private final WorldEdit worldEdit;

    public HistoryBrowserCommands(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/history",
        aliases = { "/edits" },
        desc = "List your recent edits, and undo or redo back to any of them",
        descFooter =
            """
            //history [page] lists your edits, newest first. Click [undo to here] to undo that edit and \
            everything after it, or [redo to here] on an undone edit. Also: //history undo|redo|info|tp <number>.
            """
    )
    @CommandPermissions("worldedit.history.list")
    public void history(Actor actor, LocalSession session,
                        @Arg(desc = "A page number, or undo, redo, info or tp", def = "1")
                            String action,
                        @Arg(desc = "The edit: its number in the list (1 = newest)", def = "")
                            String edit) throws WorldEditException {
        String verb = action.toLowerCase(Locale.ROOT);
        if (!verb.isEmpty() && verb.chars().allMatch(Character::isDigit)) {
            list(actor, session, parsePage(verb));
            return;
        }
        if (verb.equals("list")) {
            list(actor, session, edit.isEmpty() ? 1 : parsePage(edit));
            return;
        }
        if (!List.of("undo", "redo", "info", "tp").contains(verb)) {
            actor.printError(TextComponent.of("Unknown action '" + action + "'. Use //history [page], or "
                + "//history undo|redo|info|tp <number>."));
            return;
        }
        if (edit.isEmpty()) {
            actor.printError(TextComponent.of("Which edit? E.g. //history " + verb + " 2 (see //history)."));
            return;
        }
        HistoryListing listing = listing(session);
        int index = listing.find(edit);
        if (index < 0) {
            actor.printError(TextComponent.of("There is no edit '" + edit + "' in your history any more. "
                + "Run //history to see it as it is now."));
            return;
        }
        switch (verb) {
            case "undo" -> undoTo(actor, session, listing, index);
            case "redo" -> redoTo(actor, session, listing, index);
            case "info" -> info(actor, session, listing, index);
            default -> teleport(actor, session, listing, index);
        }
    }

    private static int parsePage(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException _) {
            return 1;
        }
    }

    // ---- building the listing -------------------------------------------------------------

    static HistoryListing listing(LocalSession session) {
        List<Row> rows = new ArrayList<>();
        if (session.getHistoryListener() instanceof PersistentHistory persistent) {
            try {
                for (PersistentHistory.SavedEdit saved : persistent.listUnloaded()) {
                    rows.add(new Row(HistoryListing.DISK_PREFIX + saved.id(), HistoryEntryInfo.UNKNOWN, -1,
                        HistoryListing.timeFromDiskId(saved.id()), !saved.done(), false));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not list saved history", e);
            }
        }
        List<EditSession> inMemory = session.getHistoryEntries();
        int position = session.getHistoryPosition();
        for (int i = 0; i < inMemory.size(); i++) {
            EditSession editSession = inMemory.get(i);
            HistoryEntryInfo info = editSession.getHistoryInfo();
            String ref = HistoryListing.MEMORY_PREFIX + (info == null ? "x" + i : info.serial());
            rows.add(new Row(ref, info == null ? HistoryEntryInfo.UNKNOWN : info.description(),
                editSession.getBlockChangeCount(), info == null ? -1 : info.timestamp(), i >= position, true));
        }
        return new HistoryListing(rows);
    }

    private static @Nullable EditSession findInMemory(LocalSession session, Row row) {
        if (!row.inMemory()) {
            return null;
        }
        List<EditSession> inMemory = session.getHistoryEntries();
        for (int i = 0; i < inMemory.size(); i++) {
            EditSession editSession = inMemory.get(i);
            HistoryEntryInfo info = editSession.getHistoryInfo();
            String ref = HistoryListing.MEMORY_PREFIX + (info == null ? "x" + i : info.serial());
            if (ref.equals(row.ref())) {
                return editSession;
            }
        }
        return null;
    }

    // ---- list -----------------------------------------------------------------------------

    private void list(Actor actor, LocalSession session, int page) throws WorldEditException {
        HistoryListing listing = listing(session);
        if (listing.size() == 0) {
            actor.printInfo(TextComponent.of("Your history is empty: there is nothing to undo or redo."));
            return;
        }
        long now = System.currentTimeMillis();
        boolean player = actor instanceof Player;
        List<Component> lines = new ArrayList<>();
        List<Row> newest = listing.newestFirst();
        for (int i = 0; i < newest.size(); i++) {
            lines.add(line(i + 1, newest.get(i), now, player));
        }
        PaginationBox box = PaginationBox.fromComponents("Your edits, newest first", "//history %page%", lines);
        box.setComponentsPerPage(PER_PAGE);
        actor.print(box.create(listing.clampPage(page, PER_PAGE)));
    }

    private static Component line(int number, Row row, long now, boolean player) {
        TextComponent.Builder line = TextComponent.builder(number + ". ", TextColor.GRAY);
        line.append(TextComponent.of(row.description(), row.undone() ? TextColor.DARK_GRAY : TextColor.WHITE)
            .hoverEvent(HoverEvent.showText(TextComponent.of(row.inMemory()
                ? row.description() : "An edit saved before a restart (what made it isn't known)"))));
        line.append(TextComponent.of(" " + HistoryListing.formatBlocks(row.blocks()) + ", "
            + HistoryListing.relativeTime(row.timestamp(), now), TextColor.GRAY));
        if (row.undone()) {
            line.append(TextComponent.of(" (undone)", TextColor.GOLD));
        }
        if (row.undone()) {
            line.append(link(" [redo to here]", TextColor.GREEN, "//history redo " + row.ref(),
                "Redo every undone edit up to and including this one"));
        } else {
            line.append(link(" [undo to here]", TextColor.RED, "//history undo " + row.ref(),
                "Undo this edit and every newer one"));
        }
        line.append(link(" [info]", TextColor.AQUA, "//history info " + row.ref(), "Where and how big"));
        if (player) {
            line.append(link(" [tp]", TextColor.LIGHT_PURPLE, "//history tp " + row.ref(),
                "Teleport to the middle of this edit"));
        }
        return line.build();
    }

    private static Component link(String text, TextColor color, String command, String hover) {
        return TextComponent.of(text, color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(TextComponent.of(hover)));
    }

    // ---- undo / redo ----------------------------------------------------------------------

    private void undoTo(Actor actor, LocalSession session, HistoryListing listing, int index) {
        if (!actor.hasPermission("worldedit.history.undo") && !actor.hasPermission("worldedit.history.undo.self")) {
            actor.printError(TextComponent.of("You don't have permission to undo."));
            return;
        }
        Row row = listing.get(index);
        int count = listing.undoCountTo(index);
        if (count == 0) {
            actor.printError(TextComponent.of("Edit " + listing.numberOf(index) + " (" + row.description()
                + ") is already undone."));
            return;
        }
        int undone = HistoryPersistenceHelper.undo(worldEdit, actor, session, count);
        report(actor, undone, count, "Undid", row, listing.numberOf(index));
    }

    private void redoTo(Actor actor, LocalSession session, HistoryListing listing, int index) {
        if (!actor.hasPermission("worldedit.history.redo") && !actor.hasPermission("worldedit.history.redo.self")) {
            actor.printError(TextComponent.of("You don't have permission to redo."));
            return;
        }
        Row row = listing.get(index);
        int count = listing.redoCountTo(index);
        if (count == 0) {
            actor.printError(TextComponent.of("Edit " + listing.numberOf(index) + " (" + row.description()
                + ") isn't undone, so there is nothing to redo."));
            return;
        }
        int redone = HistoryPersistenceHelper.redo(worldEdit, actor, session, count);
        report(actor, redone, count, "Redid", row, listing.numberOf(index));
    }

    private static void report(Actor actor, int applied, int wanted, String verb, Row row, int number) {
        TextComponent.Builder message = TextComponent.builder();
        if (applied == wanted) {
            message.append(TextComponent.of(verb + " " + applied + (applied == 1 ? " edit" : " edits")
                + ", up to " + number + ". " + row.description() + ". ", TextColor.LIGHT_PURPLE));
        } else {
            message.append(TextComponent.of(verb + " only " + applied + " of " + wanted + " edits. ", TextColor.RED));
        }
        message.append(link("[show history]", TextColor.AQUA, "//history", "List your edits again"));
        actor.print(message.build());
    }

    // ---- info / tp ------------------------------------------------------------------------

    /**
     * Where an edit is.
     */
    private record EditArea(String worldName, BlockVector3 min, BlockVector3 max) {
        Vector3 center() {
            return min.toVector3().add(max.toVector3()).divide(2).add(0.5, 0, 0.5);
        }
    }

    private static @Nullable EditArea extentOf(Actor actor, LocalSession session, Row row) {
        if (row.inMemory()) {
            EditSession editSession = findInMemory(session, row);
            if (editSession == null) {
                return null;
            }
            String world = editSession.getWorld() == null ? "?" : editSession.getWorld().getName();
            BoundsBuilder bounds = new BoundsBuilder();
            Iterator<Change> changes = editSession.getChangeSet().forwardIterator();
            while (changes.hasNext()) {
                if (changes.next() instanceof BlockChange change) {
                    bounds.add(change.position());
                }
            }
            return bounds.build(world);
        }
        if (!(session.getHistoryListener() instanceof PersistentHistory persistent)) {
            return null;
        }
        String id = row.ref().substring(HistoryListing.DISK_PREFIX.length());
        HistorySnapshot snapshot;
        try {
            snapshot = persistent.readUnloaded(id);
        } catch (IOException e) {
            LOGGER.warn("Could not read saved history " + id, e);
            actor.printError(TextComponent.of("That saved edit could not be read; see the server log."));
            return null;
        }
        if (snapshot == null) {
            return null;
        }
        if (snapshot.barrier()) {
            actor.printError(TextComponent.of("That edit was too large to be saved to disk ("
                + snapshot.skippedChanges() + " changes), so its details are not known."));
            return null;
        }
        BoundsBuilder bounds = new BoundsBuilder();
        for (BlockChange change : snapshot.blocks()) {
            bounds.add(change.position());
        }
        return bounds.build(snapshot.worldName());
    }

    private static final class BoundsBuilder {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private int count;

        void add(BlockVector3 pos) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
            count++;
        }

        @Nullable EditArea build(String world) {
            if (count == 0) {
                return null;
            }
            return new EditArea(world, BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
        }
    }

    private static void info(Actor actor, LocalSession session, HistoryListing listing, int index) {
        Row row = listing.get(index);
        int number = listing.numberOf(index);
        actor.printInfo(TextComponent.of("Edit " + number + ": " + row.description()
            + (row.undone() ? " (undone)" : "") + (row.inMemory() ? "" : " (saved before a restart)")));
        actor.print(TextComponent.of("  Made " + HistoryListing.relativeTime(row.timestamp(),
            System.currentTimeMillis()) + ", " + HistoryListing.formatBlocks(row.blocks()) + " changed",
            TextColor.GRAY));
        EditArea extent = extentOf(actor, session, row);
        if (extent == null) {
            actor.print(TextComponent.of("  No block changes to show (only biomes or entities changed).",
                TextColor.GRAY));
        } else {
            BlockVector3 size = extent.max().subtract(extent.min()).add(1, 1, 1);
            actor.print(TextComponent.of(String.format(Locale.ROOT,
                "  World %s, from %d,%d,%d to %d,%d,%d (%d x %d x %d)",
                extent.worldName(), extent.min().x(), extent.min().y(), extent.min().z(),
                extent.max().x(), extent.max().y(), extent.max().z(), size.x(), size.y(), size.z()),
                TextColor.GRAY));
        }
        TextComponent.Builder links = TextComponent.builder(" ");
        if (row.undone()) {
            links.append(link(" [redo to here]", TextColor.GREEN, "//history redo " + row.ref(),
                "Redo every undone edit up to and including this one"));
        } else {
            links.append(link(" [undo to here]", TextColor.RED, "//history undo " + row.ref(),
                "Undo this edit and every newer one"));
        }
        if (actor instanceof Player && extent != null) {
            links.append(link(" [tp]", TextColor.LIGHT_PURPLE, "//history tp " + row.ref(),
                "Teleport to the middle of this edit"));
        }
        links.append(link(" [show history]", TextColor.AQUA, "//history", "List your edits again"));
        actor.print(links.build());
    }

    private static void teleport(Actor actor, LocalSession session, HistoryListing listing, int index) {
        if (!(actor instanceof Player player)) {
            actor.printError(TextComponent.of("Only players can teleport to an edit."));
            return;
        }
        if (!player.hasPermission("worldedit.history.tp")) {
            actor.printError(TextComponent.of("You don't have permission to teleport to edits."));
            return;
        }
        EditArea extent = extentOf(actor, session, listing.get(index));
        if (extent == null) {
            actor.printError(TextComponent.of("That edit changed no blocks, so there is nowhere to go."));
            return;
        }
        if (!player.getWorld().getName().equals(extent.worldName())) {
            actor.printError(TextComponent.of("That edit is in world '" + extent.worldName()
                + "'; go there first."));
            return;
        }
        player.findFreePosition(new Location(player.getWorld(), extent.center()));
        actor.printInfo(TextComponent.of("Teleported to edit " + listing.numberOf(index) + "."));
    }
}
