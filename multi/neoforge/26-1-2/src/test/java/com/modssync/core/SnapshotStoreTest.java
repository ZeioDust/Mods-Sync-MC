package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.LocalMod;
import com.modssync.model.ServerMod;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

    private static LocalMod local(String id, String ver, boolean enabled) {
        return new LocalMod(id, ver, Path.of(id + ".jar"), enabled);
    }

    @Test
    void recordsOnlyEnabledModsThenLoadsThem(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("home.json");
        SnapshotStore store = new SnapshotStore(file);
        assertFalse(store.exists());

        store.recordIfAbsent(List.of(
                local("sodium", "1.0", true),
                local("jei", "2.0", false))); // disabled -> excluded

        assertTrue(store.exists());
        List<ServerMod> home = store.loadAsServerMods();
        assertEquals(1, home.size());
        assertEquals("sodium", home.get(0).modId());
        assertEquals("1.0", home.get(0).version());
    }

    @Test
    void recordIfAbsentDoesNotOverwrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("home.json");
        SnapshotStore store = new SnapshotStore(file);
        store.recordIfAbsent(List.of(local("sodium", "1.0", true)));
        store.recordIfAbsent(List.of(local("create", "0.5", true))); // ignored
        List<ServerMod> home = store.loadAsServerMods();
        assertEquals(1, home.size());
        assertEquals("sodium", home.get(0).modId());
    }
}
