package com.modssync.sync;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.core.*;
import com.modssync.model.ServerMod;
import com.modssync.net.resolver.Acquirer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncOrchestratorTest {

    private static void writeModJar(Path jar, String modId, String version) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zos.write(("[[mods]]\nmodId=\"" + modId + "\"\nversion=\"" + version + "\"\n")
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private SyncOrchestrator orchestrator(Path modsDir, Path snapshot) {
        return new SyncOrchestrator(
                modsDir,
                new LocalInventory(),
                new DiffEngine(),
                new FolderMutator(),
                new SnapshotStore(snapshot),
                new Acquirer(List.of()));
    }

    @Test
    void matchingSetNeedsNoRestart(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("mods"));
        writeModJar(mods.resolve("jei.jar"), "jei", "15.2");
        SyncOrchestrator orch = orchestrator(mods, dir.resolve("home.json"));

        SyncResult result = orch.syncTo(List.of(ServerMod.ofIdVersion("jei", "15.2")));

        assertFalse(result.restartRequired());
    }

    @Test
    void extraClientModIsDisabledAndRestartRequired(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("mods"));
        writeModJar(mods.resolve("sodium.jar"), "sodium", "1.0");
        SyncOrchestrator orch = orchestrator(mods, dir.resolve("home.json"));

        SyncResult result = orch.syncTo(List.of());

        assertTrue(result.restartRequired());
        assertFalse(Files.exists(mods.resolve("sodium.jar")));
        assertTrue(Files.exists(mods.resolve("sodium.jar.disabled")));
    }

    @Test
    void missingModWithNoWorkingSourceIsUnresolved(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("mods"));
        SyncOrchestrator orch = orchestrator(mods, dir.resolve("home.json"));

        SyncResult result = orch.syncTo(List.of(ServerMod.ofIdVersion("create", "0.5")));

        assertEquals(1, result.unresolved().size());
        assertEquals("create", result.unresolved().get(0).modId());
        assertFalse(result.restartRequired());
    }

    @Test
    void recordsHomeSnapshotOnFirstSync(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("mods"));
        writeModJar(mods.resolve("jei.jar"), "jei", "15.2");
        Path snap = dir.resolve("home.json");
        SyncOrchestrator orch = orchestrator(mods, snap);

        orch.syncTo(List.of(ServerMod.ofIdVersion("jei", "15.2")));

        assertTrue(Files.exists(snap));
    }
}
