package com.modssync.net.resolver;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.ServerMod;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcquirerTest {

    private static ModSource source(String label, boolean succeeds) {
        return (mod, destDir) -> {
            if (!succeeds) return null;
            try {
                Path f = destDir.resolve(mod.modId() + "-" + label + ".jar");
                Files.writeString(f, label);
                return f;
            } catch (Exception e) {
                return null;
            }
        };
    }

    @Test
    void usesFirstSucceedingSourceInOrder(@TempDir Path dir) {
        Acquirer acquirer = new Acquirer(List.of(
                source("server", false),
                source("modrinth", true),
                source("curse", true)));

        var result = acquirer.acquire(List.of(ServerMod.ofIdVersion("create", "0.5")), dir);

        assertEquals(1, result.downloaded().size());
        assertTrue(result.downloaded().get(0).getFileName().toString().contains("modrinth"));
        assertTrue(result.unresolved().isEmpty());
    }

    @Test
    void reportsUnresolvedWhenAllSourcesFail(@TempDir Path dir) {
        Acquirer acquirer = new Acquirer(List.of(
                source("server", false),
                source("modrinth", false)));

        var result = acquirer.acquire(List.of(ServerMod.ofIdVersion("secret", "1.0")), dir);

        assertTrue(result.downloaded().isEmpty());
        assertEquals(1, result.unresolved().size());
        assertEquals("secret", result.unresolved().get(0).modId());
    }
}
