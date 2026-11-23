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

package com.sk89q.worldedit.history.persistent;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.session.HistoryListener;
import com.sk89q.worldedit.world.World;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Keeps the last N edits of a player on disk so that they can still be
 * undone after a restart or after the session expired.
 *
 * <p><b>Layout.</b> Every remembered edit is written to {@code <dir>/<id>.weh} (GZip NBT, see
 * {@link HistorySnapshot}); {@code id} sorts chronologically. When the edit is
 * undone the file is renamed to {@code <id>.weh.undone}, and back when it is
 * redone. Remembering a new edit deletes all {@code .undone} files, exactly like
 * the in-memory history drops its redo entries. Only the newest
 * {@code maxEntries} files are kept.</p>
 *
 * <p><b>Undo after restart.</b> Files that do not belong to an edit in the in-memory history are older than
 * everything in memory. When the in-memory history has nothing left to undo,
 * {@link #undoFromDisk} undoes the newest such "done" file; {@link #redoFromDisk}
 * redoes the oldest such "undone" file, which must happen before redoing
 * in-memory edits.</p>
 *
 * <p>Snapshots are taken on the calling thread (cheap copy of the net change),
 * file I/O runs on a single background thread, in order.</p>
 */
public class PersistentHistory implements HistoryListener {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    /**
     * Edits with more changes than this are not saved; a barrier is saved instead.
     */
    public static final int MAX_PERSISTED_CHANGES = 2_000_000;

    static final String DONE_SUFFIX = ".weh";
    static final String UNDONE_SUFFIX = ".weh.undone";
    private static final Pattern FILE_PATTERN = Pattern.compile("^(\\d{15}-\\d{6})\\.weh(\\.undone)?$");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldEdit History Writer");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * Result of a disk undo / redo.
     *
     * @param status the status
     * @param worldName the world of the edit, if known
     * @param changes number of block changes applied
     */
    public record DiskResult(Status status, @Nullable String worldName, int changes) {
    }

    /**
     * Status of a disk undo / redo.
     */
    public enum Status {
        /** Nothing persisted to undo/redo. */
        NONE,
        /** The edit was applied. */
        APPLIED,
        /** The next persisted edit was too large to be saved. */
        BARRIER,
        /** The world of the edit is not loaded. */
        WORLD_MISSING,
        /** The file could not be read. */
        FAILED
    }

    private static final class Entry {
        final String id;
        int snapshotSize;

        Entry(String id, int snapshotSize) {
            this.id = id;
            this.snapshotSize = snapshotSize;
        }
    }

    private final Path directory;
    private final int maxEntries;
    private final Function<String, @Nullable World> worldLookup;
    private final HistorySnapshot.Resolver resolver;
    private final IdentityHashMap<EditSession, Entry> mapped = new IdentityHashMap<>();

    /**
     * Create persistent history for a directory.
     *
     * @param directory the player's history directory
     * @param maxEntries the number of edits to keep
     * @param worldLookup finds a loaded world by name
     * @param resolver resolves blocks when reading
     */
    public PersistentHistory(Path directory, int maxEntries, Function<String, @Nullable World> worldLookup,
                             HistorySnapshot.Resolver resolver) {
        this.directory = checkNotNull(directory);
        this.maxEntries = maxEntries;
        this.worldLookup = checkNotNull(worldLookup);
        this.resolver = checkNotNull(resolver);
    }

    /**
     * Create persistent history for a player in {@code <working dir>/history/<uuid>}.
     *
     * @param owner the player UUID
     * @param maxEntries the number of edits to keep
     * @return the instance
     */
    public static PersistentHistory forPlayer(UUID owner, int maxEntries) {
        Path dir = WorldEdit.getInstance().getWorkingDirectoryPath("history").resolve(owner.toString());
        return new PersistentHistory(dir, maxEntries, PersistentHistory::findLoadedWorld,
            HistorySnapshot.platformResolver());
    }

    private static @Nullable World findLoadedWorld(String name) {
        try {
            for (World world : WorldEdit.getInstance().getPlatformManager()
                    .queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                if (world.getName().equals(name)) {
                    return world;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Could not list worlds", e);
        }
        return null;
    }

    public Path getDirectory() {
        return directory;
    }

    // ---- listener -------------------------------------------------------------------------

    @Override
    public synchronized void onRemember(EditSession editSession, Collection<EditSession> history) {
        Set<EditSession> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(history);
        mapped.keySet().retainAll(live);
        // Edits whose change set grew after they were remembered are written again.
        for (Map.Entry<EditSession, Entry> entry : mapped.entrySet()) {
            if (entry.getKey().getChangeSet().size() != entry.getValue().snapshotSize) {
                writeSnapshot(entry.getKey(), entry.getValue());
            }
        }
        Entry entry = new Entry(newId(), 0);
        mapped.put(editSession, entry);
        enqueue(() -> {
            for (FileInfo info : listFiles()) {
                if (!info.done()) {
                    Files.deleteIfExists(info.path());
                }
            }
            return null;
        });
        writeSnapshot(editSession, entry);
        enqueue(() -> {
            List<FileInfo> files = listFiles();
            for (int i = 0; i < files.size() - maxEntries; i++) {
                Files.deleteIfExists(files.get(i).path());
            }
            return null;
        });
    }

    @Override
    public synchronized void onUndo(EditSession editSession) {
        Entry entry = mapped.get(editSession);
        if (entry != null) {
            if (editSession.getChangeSet().size() != entry.snapshotSize) {
                writeSnapshot(editSession, entry);
            }
            rename(entry.id, true);
        }
    }

    @Override
    public synchronized void onRedo(EditSession editSession) {
        Entry entry = mapped.get(editSession);
        if (entry != null) {
            if (editSession.getChangeSet().size() != entry.snapshotSize) {
                writeSnapshot(editSession, entry);
            }
            rename(entry.id, false);
        }
    }

    @Override
    public synchronized void onModified(EditSession editSession) {
        Entry entry = mapped.get(editSession);
        if (entry != null) {
            writeSnapshot(editSession, entry);
        }
    }

    @Override
    public synchronized void onClear() {
        mapped.clear();
        enqueue(() -> {
            for (FileInfo info : listFiles()) {
                Files.deleteIfExists(info.path());
            }
            return null;
        });
    }

    // ---- disk undo / redo -----------------------------------------------------------------

    /**
     * Undo the newest persisted edit that is not part of the in-memory history.
     *
     * @param session the session (for side effects and similar settings)
     * @param actor the actor
     * @param blockBag the block bag, or null
     * @return the result
     */
    public synchronized DiskResult undoFromDisk(LocalSession session, Actor actor, @Nullable BlockBag blockBag) {
        return applyFromDisk(session, actor, blockBag, true);
    }

    /**
     * Redo the oldest persisted undone edit that is not part of the in-memory
     * history. Such edits are older than any in-memory edit, so they must be
     * redone first.
     *
     * @param session the session
     * @param actor the actor
     * @param blockBag the block bag, or null
     * @return the result
     */
    public synchronized DiskResult redoFromDisk(LocalSession session, Actor actor, @Nullable BlockBag blockBag) {
        return applyFromDisk(session, actor, blockBag, false);
    }

    /**
     * Whether there is a persisted undone edit outside the in-memory history,
     * i.e. whether {@link #redoFromDisk} would do something.
     *
     * @return true if so
     */
    public synchronized boolean hasDiskRedo() {
        return findCandidate(false) != null;
    }

    /**
     * An edit saved on disk that is not part of the in-memory history.
     *
     * @param id the id; it starts with the time in milliseconds
     * @param done true if it is not undone
     */
    public record SavedEdit(String id, boolean done) {
    }

    /**
     * List the saved edits that are not part of the in-memory history (they are
     * older than everything in memory), oldest first. This does not read the files.
     *
     * @return the edits
     */
    public synchronized List<SavedEdit> listUnloaded() {
        List<FileInfo> files = await(this::listFiles);
        Set<String> inMemory = new HashSet<>();
        for (Entry entry : mapped.values()) {
            inMemory.add(entry.id);
        }
        List<SavedEdit> result = new ArrayList<>();
        for (FileInfo info : files) {
            if (!inMemory.contains(info.id())) {
                result.add(new SavedEdit(info.id(), info.done()));
            }
        }
        return result;
    }

    /**
     * Read a saved edit that is not part of the in-memory history.
     *
     * @param id the id from {@link #listUnloaded()}
     * @return the snapshot, or {@code null} if there is no such edit
     * @throws IOException if it can't be read
     */
    public synchronized @Nullable HistorySnapshot readUnloaded(String id) throws IOException {
        for (FileInfo info : await(this::listFiles)) {
            if (info.id().equals(id)) {
                return HistorySnapshot.read(info.path(), resolver);
            }
        }
        return null;
    }

    private @Nullable FileInfo findCandidate(boolean undo) {
        List<FileInfo> files = await(this::listFiles);
        Set<String> inMemory = new HashSet<>();
        for (Entry entry : mapped.values()) {
            inMemory.add(entry.id);
        }
        FileInfo candidate = null;
        for (FileInfo info : files) {
            if (inMemory.contains(info.id()) || info.done() != undo) {
                continue;
            }
            if (undo) {
                candidate = info; // newest wins
            } else {
                return info; // oldest wins
            }
        }
        return candidate;
    }

    @SuppressWarnings("deprecation")
    private DiskResult applyFromDisk(LocalSession session, Actor actor, @Nullable BlockBag blockBag, boolean undo) {
        FileInfo info = findCandidate(undo);
        if (info == null) {
            return new DiskResult(Status.NONE, null, 0);
        }
        HistorySnapshot snapshot;
        try {
            snapshot = HistorySnapshot.read(info.path(), resolver);
        } catch (IOException e) {
            LOGGER.warn("Failed to read persisted history " + info.path(), e);
            return new DiskResult(Status.FAILED, null, 0);
        }
        if (snapshot.barrier()) {
            return new DiskResult(Status.BARRIER, snapshot.worldName(), 0);
        }
        World world = worldLookup.apply(snapshot.worldName());
        if (world == null) {
            return new DiskResult(Status.WORLD_MISSING, snapshot.worldName(), 0);
        }
        try (EditSession carrier = WorldEdit.getInstance().newEditSessionBuilder().world(world).build();
             EditSession target = createTargetSession(session, world, actor, blockBag)) {
            snapshot.addTo(carrier.getChangeSet());
            if (undo) {
                carrier.undo(target);
            } else {
                carrier.redo(target);
            }
        }
        rename(info.id(), undo);
        await(() -> null);
        return new DiskResult(Status.APPLIED, snapshot.worldName(), snapshot.blocks().size());
    }

    /**
     * Create an edit session to apply an undo/redo into, configured like
     * {@link LocalSession#undo} does.
     *
     * @param session the session
     * @param world the world
     * @param actor the actor
     * @param blockBag the block bag, or null
     * @return the edit session
     */
    @SuppressWarnings("deprecation")
    public static EditSession createTargetSession(LocalSession session, World world, Actor actor,
                                                  @Nullable BlockBag blockBag) {
        EditSession target = WorldEdit.getInstance().newEditSessionBuilder()
            .world(world).blockBag(blockBag).actor(actor).build();
        target.setSideEffectApplier(session.getSideEffectSet());
        target.setReorderMode(session.getReorderMode());
        if (target.getSurvivalExtent() != null) {
            target.getSurvivalExtent().setStripNbt(!actor.hasPermission("worldedit.setnbt"));
        }
        target.setTickingWatchdog(session.isTickingWatchdog());
        return target;
    }

    // ---- file handling --------------------------------------------------------------------

    record FileInfo(String id, Path path, boolean done) {
    }

    List<FileInfo> listFiles() throws IOException {
        List<FileInfo> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path p : stream) {
                Matcher matcher = FILE_PATTERN.matcher(p.getFileName().toString());
                if (matcher.matches()) {
                    result.add(new FileInfo(matcher.group(1), p, matcher.group(2) == null));
                }
            }
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return result;
    }

    private static String newId() {
        return String.format(Locale.ROOT, "%015d-%06d", System.currentTimeMillis(),
            Math.floorMod(SEQUENCE.getAndIncrement(), 1_000_000));
    }

    private void writeSnapshot(EditSession editSession, Entry entry) {
        HistorySnapshot snapshot;
        try {
            entry.snapshotSize = editSession.getChangeSet().size();
            snapshot = HistorySnapshot.capture(editSession.getChangeSet(), editSession.getWorld() == null
                ? "" : editSession.getWorld().getName(), currentDataVersion(), MAX_PERSISTED_CHANGES);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to capture history for persistence", e);
            return;
        }
        if (snapshot.barrier()) {
            LOGGER.info("Edit with " + snapshot.skippedChanges() + " changes is too large to be saved to disk history; "
                + "older persisted history will not be undoable past it.");
        }
        String id = entry.id;
        enqueue(() -> {
            Files.createDirectories(directory);
            Path undone = directory.resolve(id + UNDONE_SUFFIX);
            Path target = Files.exists(undone) ? undone : directory.resolve(id + DONE_SUFFIX);
            Path temp = directory.resolve(id + ".tmp");
            snapshot.write(temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
    }

    private static int currentDataVersion() {
        try {
            return WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.WORLD_EDITING).getDataVersion();
        } catch (RuntimeException _) {
            return -1;
        }
    }

    private void rename(String id, boolean toUndone) {
        enqueue(() -> {
            Path done = directory.resolve(id + DONE_SUFFIX);
            Path undone = directory.resolve(id + UNDONE_SUFFIX);
            Path from = toUndone ? done : undone;
            Path to = toUndone ? undone : done;
            if (Files.exists(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
    }

    private interface IoTask<T> {
        T run() throws IOException;
    }

    private void enqueue(IoTask<?> task) {
        IO.execute(() -> {
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Persistent history I/O failed in " + directory, e);
            }
        });
    }

    private <T> Future<T> submit(IoTask<T> task) {
        return IO.submit(() -> {
            try {
                return task.run();
            } catch (IOException e) {
                LOGGER.warn("Persistent history I/O failed in " + directory, e);
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Wait for all pending I/O and run a task on the I/O thread.
     */
    private <T> T await(IoTask<T> task) {
        try {
            return submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Wait until all pending writes have finished (for tests and shutdown).
     */
    public void flush() {
        await(() -> null);
    }
}
