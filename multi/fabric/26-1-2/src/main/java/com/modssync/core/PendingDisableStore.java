package com.modssync.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists a queue of files that need to be renamed to {@code .disabled} on the
 * <em>next</em> game launch, rather than during the current session.
 *
 * <p>The fundamental problem this solves: when a player is already in-game (or even
 * just past the Fabric preLaunch stage), the JVM has already loaded mod jars and
 * resourcepack zips. On Windows especially, open file handles prevent renaming those
 * files while they're in use. So instead of attempting an in-place disable and failing,
 * the sync code records the "to-be-disabled" files here. On the next launch, the
 * preLaunch entrypoint calls {@link #takeAll()}, renames everything, and clears the
 * queue — all before any jar is loaded.
 *
 * <p>All public methods are {@code synchronized} because in rare cases the in-game
 * sync UI and background tasks might call into this from different threads.
 *
 * <p>On-disk format: pretty-printed JSON array of {@code {folder, fileName}} pairs,
 * kept human-readable so players and support staff can inspect or clear the queue manually.
 */
public final class PendingDisableStore {

    /**
     * Identifies a single file to be disabled: the folder it lives in
     * (e.g. "mods", "resourcepacks") and its plain file name.
     */
    public record Entry(String folder, String fileName) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    private final Path file;

    public PendingDisableStore(Path file) {
        this.file = file;
    }

    /**
     * Appends entries to the pending-disable queue, skipping duplicates.
     *
     * <p>Duplicate detection is based on exact folder+fileName equality so that calling
     * add() multiple times for the same file (e.g. because the player re-connected to
     * the same server) doesn't inflate the queue with redundant entries.
     */
    public synchronized void add(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<Entry> current = load();
        for (Entry e : entries) {
            boolean dup = current.stream().anyMatch(
                    x -> x.folder().equals(e.folder()) && x.fileName().equals(e.fileName()));
            if (!dup) {
                current.add(e);
            }
        }
        write(current);
    }

    /**
     * Returns all pending entries and clears the queue atomically.
     *
     * <p>This is the preLaunch consumer: read-then-clear means each entry is processed
     * exactly once. If the process crashes between the read and the write, the entries
     * will be re-processed on the following launch — renaming an already-disabled file
     * is idempotent, so this is safe.
     */
    public synchronized List<Entry> takeAll() {
        List<Entry> current = load();
        write(new ArrayList<>()); // clear the queue
        return current;
    }

    /**
     * Returns the current pending entries without clearing the queue.
     * Useful for showing the player a preview of what will happen on next launch.
     */
    public synchronized List<Entry> list() {
        return load();
    }

    /**
     * Replaces the entire pending list with the given entries.
     * Used when the caller wants full control over the queue contents
     * (e.g. to remove a specific entry after manual handling).
     */
    public synchronized void replace(List<Entry> entries) {
        write(entries == null ? new ArrayList<>() : entries);
    }

    /**
     * Reads the JSON file from disk and deserialises it.
     * Returns an empty list if the file doesn't exist, can't be read, or contains null.
     */
    private List<Entry> load() {
        try {
            if (!Files.isRegularFile(file)) {
                return new ArrayList<>();
            }
            List<Entry> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Serialises {@code list} to disk, creating parent directories if needed. Failures are silently ignored. */
    private void write(List<Entry> list) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(list, LIST_TYPE), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
