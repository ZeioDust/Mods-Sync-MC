package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.LocalMod;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalInventoryTest {

    /** Writes a jar containing a fabric.mod.json at the root with the given id/version. */
    private static void writeModJar(Path jar, String modId, String version) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            String json = "{\"schemaVersion\":1,\"id\":\"" + modId + "\",\"version\":\"" + version + "\"}";
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    @Test
    void scansEnabledAndDisabledJars(@TempDir Path dir) throws Exception {
        writeModJar(dir.resolve("sodium.jar"), "sodium", "0.5.3");
        writeModJar(dir.resolve("jei.jar.disabled"), "jei", "15.2.0");
        Files.writeString(dir.resolve("notajar.txt"), "ignore me");

        List<LocalMod> mods = new LocalInventory().scan(dir);

        assertEquals(2, mods.size());
        LocalMod sodium = mods.stream().filter(m -> m.modId().equals("sodium")).findFirst().orElseThrow();
        assertTrue(sodium.enabled());
        assertEquals("0.5.3", sodium.version());
        LocalMod jei = mods.stream().filter(m -> m.modId().equals("jei")).findFirst().orElseThrow();
        assertFalse(jei.enabled());
    }

    @Test
    void emptyDirReturnsEmptyList(@TempDir Path dir) throws Exception {
        assertTrue(new LocalInventory().scan(dir).isEmpty());
    }

    @Test
    void skipsJarsWithoutFabricModJson(@TempDir Path dir) throws Exception {
        try (var zos = new ZipOutputStream(Files.newOutputStream(dir.resolve("lib.jar")))) {
            zos.putNextEntry(new ZipEntry("README.txt"));
            zos.write("hi".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertTrue(new LocalInventory().scan(dir).isEmpty());
    }
}
