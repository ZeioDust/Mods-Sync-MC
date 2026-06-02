package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.LocalMod;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderMutatorTest {

    @Test
    void disableRenamesToDisabled(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("sodium.jar");
        Files.writeString(jar, "x");
        LocalMod mod = new LocalMod("sodium", "1.0", jar, true);

        boolean ok = new FolderMutator().disable(mod);

        assertTrue(ok);
        assertFalse(Files.exists(jar));
        assertTrue(Files.exists(dir.resolve("sodium.jar.disabled")));
    }

    @Test
    void disableRefusesProtectedMod(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("fabricloader.jar");
        Files.writeString(jar, "x");
        LocalMod mod = new LocalMod("fabricloader", "1.0", jar, true);

        boolean ok = new FolderMutator().disable(mod);

        assertFalse(ok);
        assertTrue(Files.exists(jar), "protected mod must remain untouched");
    }

    @Test
    void enableRenamesBack(@TempDir Path dir) throws Exception {
        Path disabled = dir.resolve("jei.jar.disabled");
        Files.writeString(disabled, "x");
        LocalMod mod = new LocalMod("jei", "1.0", disabled, false);

        boolean ok = new FolderMutator().enable(mod);

        assertTrue(ok);
        assertTrue(Files.exists(dir.resolve("jei.jar")));
        assertFalse(Files.exists(disabled));
    }
}
