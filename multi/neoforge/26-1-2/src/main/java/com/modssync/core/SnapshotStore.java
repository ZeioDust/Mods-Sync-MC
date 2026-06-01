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

/** Persists the player's home (original) mod set for the restore feature (spec §1). */
public final class SnapshotStore {

    /** Minimal on-disk shape. */
    public record Entry(String modId, String version) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    private final Path file;

    public SnapshotStore(Path file) {
        this.file = file;
    }

    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /** Records the currently-enabled mods as the home snapshot, only if none exists yet. */
    public void recordIfAbsent(List<LocalMod> currentMods) {
        if (exists()) {
            return;
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

    /** Loads the home snapshot as ServerMods so DiffEngine can target it for restore. */
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
            // Treat an unreadable snapshot as empty.
        }
        return result;
    }
}
