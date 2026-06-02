package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffEngineTest {

    private static LocalMod local(String id, String ver, boolean enabled) {
        return new LocalMod(id, ver, Path.of(id + ".jar"), enabled);
    }

    @Test
    void clientOnlyModIsDisabled() {
        var plan = new DiffEngine().diff(
                List.of(),
                List.of(local("sodium", "1.0", true)));
        assertEquals(1, plan.toDisable().size());
        assertEquals("sodium", plan.toDisable().get(0).modId());
    }

    @Test
    void protectedClientOnlyModIsNotDisabled() {
        var plan = new DiffEngine().diff(
                List.of(),
                List.of(local("fabricloader", "1.0", true)));
        assertTrue(plan.toDisable().isEmpty());
    }

    @Test
    void serverOnlyModIsDownloaded() {
        var plan = new DiffEngine().diff(
                List.of(ServerMod.ofIdVersion("create", "0.5.1")),
                List.of());
        assertEquals(1, plan.toDownload().size());
        assertEquals("create", plan.toDownload().get(0).modId());
    }

    @Test
    void sameVersionEnabledIsUnchanged() {
        var plan = new DiffEngine().diff(
                List.of(ServerMod.ofIdVersion("jei", "15.2")),
                List.of(local("jei", "15.2", true)));
        assertTrue(plan.isEmpty());
        assertEquals(1, plan.unchanged().size());
    }

    @Test
    void sameVersionDisabledIsEnabled() {
        var plan = new DiffEngine().diff(
                List.of(ServerMod.ofIdVersion("jei", "15.2")),
                List.of(local("jei", "15.2", false)));
        assertEquals(1, plan.toEnable().size());
        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void differentVersionDownloadsServerAndDisablesLocal() {
        var plan = new DiffEngine().diff(
                List.of(ServerMod.ofIdVersion("create", "0.5.1")),
                List.of(local("create", "0.4.0", true)));
        assertEquals(1, plan.toDownload().size());
        assertEquals("0.5.1", plan.toDownload().get(0).version());
        assertEquals(1, plan.toDisable().size());
        assertEquals("0.4.0", plan.toDisable().get(0).version());
    }
}
