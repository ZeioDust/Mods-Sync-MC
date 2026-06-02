package com.modssync.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.modssync.model.LocalMod;
import com.modssync.model.ServerMod;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the player's original ("home") mod set to disk so it can be restored later.
 *
 * <p>When a player first connects to a ModsSync server, the mod will modify their local
 * mods folder. The snapshot is written <em>before</em> that first sync so that the
 * player can revert to their original configuration (spec §1 "restore" feature) without
 * manually remembering what they had installed.
 *
 * <p>The snapshot is written once and never overwritten by normal sync operations —
 * {@link #recordIfAbsent} is a no-op if the file already exists. This means the home
 * state is always the <em>pre-ModsSync</em> state, not the most recent server sync.
 *
 * <p>On-disk format: a pretty-printed JSON array of {@code {modId, version}} objects.
 * Keeping it human-readable makes it easy for players to inspect or manually restore.
 */
public final class SnapshotStore {

    /**
     * Minimal on-disk representation of a mod entry.
     * We intentionally drop the file path — it's runtime state that changes between
     * installs and isn't needed to identify or re-enable a mod.
     */
    public record Entry(String modId, String version) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    private final Path file;

    public SnapshotStore(Path file) {
        this.file = file;
    }

    /** Returns {@code true} if a snapshot file already exists on disk. */
    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /**
     * Captures the currently-enabled mods as the home snapshot, but only if no
     * snapshot has been recorded yet. Disabled mods are excluded — they were already
     * inactive before ModsSync ran and aren't part of the player's "active" setup.
     *
     * <p>Failure to write (e.g. permissions issue) is silently ignored. The worst
     * consequence is that the restore feature won't be available; the sync still works.
     */
    public void recordIfAbsent(List<LocalMod> currentMods) {
        if (exists()) {
            return; // snapshot already taken — don't overwrite it
        }
        List<Entry> entries = new ArrayList<>();
        for (LocalMod m : currentMods) {
            if (m.enabled()) {
                entries.add(new Entry(m.modId(), m.version()));
            }
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(entries, LIST_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Non-fatal: without a snapshot, restore is simply unavailable.
        }
    }

    /**
     * Loads the home snapshot and returns it as a list of {@link ServerMod} objects.
     *
     * <p>The {@link ServerMod} type is used here as a convenient "target state" type
     * that {@link DiffEngine} already knows how to process. Since snapshot entries only
     * carry id+version (no download URL), {@link ServerMod#ofIdVersion} is used — the
     * diff engine can still detect what needs to change; it just can't auto-download
     * anything that's genuinely missing.
     *
     * @return the saved mod list, or an empty list if the snapshot doesn't exist or is unreadable
     */
    public List<ServerMod> loadAsServerMods() {
        List<ServerMod> result = new ArrayList<>();
        if (!exists()) {
            return result;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Entry> entries = GSON.fromJson(json, LIST_TYPE);
            if (entries != null) {
                for (Entry e : entries) {
                    result.add(ServerMod.ofIdVersion(e.modId(), e.version()));
                }
            }
        } catch (IOException e) {
            // Treat an unreadable snapshot as empty; the player won't be able to restore.
        }
        return result;
    }
}
