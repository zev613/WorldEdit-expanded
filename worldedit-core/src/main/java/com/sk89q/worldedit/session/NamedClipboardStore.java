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

package com.sk89q.worldedit.session;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores named clipboards on disk as Sponge (v3) schematics under
 * {@code <working dir>/clipboards/<player uuid>/<name>.schem}.
 *
 * <p>{@link LocalSession} does not know which player owns it, so the disk
 * logic lives here and is used from the command layer with the actor's
 * UUID.</p>
 */
public class NamedClipboardStore {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    /**
     * Allowed clipboard names: letters, digits, {@code _} and {@code -}, 1 to 32 characters.
     */
    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9_-]{1,32}$");

    public static final ClipboardFormat FORMAT = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC;
    private static final String EXTENSION = "." + FORMAT.getPrimaryFileExtension();

    private final Path baseDirectory;

    /**
     * Create a store rooted at the given directory.
     *
     * @param baseDirectory the directory that holds the per-player folders
     */
    public NamedClipboardStore(Path baseDirectory) {
        this.baseDirectory = checkNotNull(baseDirectory);
    }

    /**
     * Create a store in {@code <WorldEdit working dir>/clipboards}.
     *
     * @return the store
     */
    public static NamedClipboardStore getDefault() {
        return new NamedClipboardStore(WorldEdit.getInstance().getWorkingDirectoryPath("clipboards"));
    }

    /**
     * Normalise and validate a clipboard name.
     *
     * @param name the raw name
     * @return the lower-case name, or {@code null} if it is not allowed
     */
    public static @Nullable String sanitizeName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return VALID_NAME.matcher(lower).matches() ? lower : null;
    }

    private static String requireValidName(String name) {
        String sanitized = sanitizeName(name);
        if (sanitized == null) {
            throw new IllegalArgumentException("Invalid clipboard name '" + name
                + "': use 1-32 letters, digits, '_' or '-'.");
        }
        return sanitized;
    }

    /**
     * Get the folder of a player.
     *
     * @param owner the player UUID
     * @return the folder (may not exist)
     */
    public Path getPlayerDirectory(UUID owner) {
        return baseDirectory.resolve(owner.toString());
    }

    /**
     * Get the file a clipboard is saved to.
     *
     * @param owner the player UUID
     * @param name the clipboard name
     * @return the file
     * @throws IllegalArgumentException if the name is invalid
     */
    public Path getFile(UUID owner, String name) {
        Path dir = getPlayerDirectory(owner).toAbsolutePath().normalize();
        Path file = dir.resolve(requireValidName(name) + EXTENSION).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("Invalid clipboard name '" + name + "'");
        }
        return file;
    }

    /**
     * Save a clipboard. The holder's transform is applied before saving.
     *
     * @param owner the player UUID
     * @param name the clipboard name
     * @param holder the clipboard
     * @throws IOException on I/O error
     * @throws WorldEditException if the transformed clipboard cannot be created
     */
    public void save(UUID owner, String name, ClipboardHolder holder) throws IOException, WorldEditException {
        Path file = getFile(owner, name);
        Files.createDirectories(file.getParent());
        Clipboard target = holder.getClipboard().transform(holder.getTransform());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp));
                 ClipboardWriter writer = FORMAT.getWriter(out)) {
                writer.write(target);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Load a saved clipboard.
     *
     * @param owner the player UUID
     * @param name the clipboard name
     * @return the clipboard, or {@code null} if it is not saved
     * @throws IOException on I/O error or if the file is not a valid schematic
     */
    public @Nullable ClipboardHolder load(UUID owner, String name) throws IOException {
        Path file = getFile(owner, name);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
             ClipboardReader reader = FORMAT.getReader(in)) {
            return new ClipboardHolder(reader.read());
        }
    }

    /**
     * Whether a clipboard is saved on disk.
     *
     * @param owner the player UUID
     * @param name the clipboard name
     * @return true if saved
     */
    public boolean exists(UUID owner, String name) {
        return sanitizeName(name) != null && Files.isRegularFile(getFile(owner, name));
    }

    /**
     * Delete a saved clipboard.
     *
     * @param owner the player UUID
     * @param name the clipboard name
     * @return true if a file was deleted
     * @throws IOException on I/O error
     */
    public boolean delete(UUID owner, String name) throws IOException {
        return Files.deleteIfExists(getFile(owner, name));
    }

    /**
     * List the names of saved clipboards.
     *
     * @param owner the player UUID
     * @return sorted names
     */
    public List<String> list(UUID owner) {
        Path dir = getPlayerDirectory(owner);
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + EXTENSION)) {
            for (Path p : stream) {
                String fileName = p.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - EXTENSION.length());
                if (sanitizeName(name) != null && sanitizeName(name).equals(name)) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list saved clipboards in " + dir, e);
        }
        names.sort(null);
        return names;
    }

    /**
     * Get a named clipboard from the session, falling back to the copy saved
     * on disk for the actor. A clipboard loaded from disk is cached in the
     * session.
     *
     * @param session the session
     * @param actor the actor whose saved clipboards are searched
     * @param name the clipboard name
     * @return the clipboard
     * @throws EmptyClipboardException if neither in memory nor on disk
     */
    public ClipboardHolder getNamedClipboard(LocalSession session, Actor actor, String name)
            throws EmptyClipboardException {
        try {
            return session.getNamedClipboard(name);
        } catch (EmptyClipboardException _) {
            if (sanitizeName(name) == null) {
                throw noSuchClipboard(name);
            }
            ClipboardHolder loaded;
            try {
                loaded = load(actor.getUniqueId(), name);
            } catch (IOException ex) {
                LOGGER.warn("Failed to load saved clipboard '" + name + "' of " + actor.getName(), ex);
                throw new EmptyClipboardException(TextComponent.of("The saved clipboard '" + name
                    + "' could not be read: " + ex.getMessage()));
            }
            if (loaded == null) {
                throw noSuchClipboard(name);
            }
            session.setNamedClipboard(name, loaded);
            return loaded;
        }
    }

    /**
     * The error for a named clipboard that is neither in memory nor on disk.
     *
     * @param name the name that was asked for
     * @return the exception
     */
    public static EmptyClipboardException noSuchClipboard(String name) {
        return new EmptyClipboardException(TextComponent.of("There is no clipboard called '" + name
            + "' (see //clipboards list)."));
    }
}
