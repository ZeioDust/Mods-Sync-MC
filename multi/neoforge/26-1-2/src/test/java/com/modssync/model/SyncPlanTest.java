package com.modssync.model;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncPlanTest {
    @Test
    void emptyPlanReportsEmpty() {
        SyncPlan plan = new SyncPlan(List.of(), List.of(), List.of(), List.of());
        assertTrue(plan.isEmpty());
    }

    @Test
    void planWithDisableIsNotEmpty() {
        LocalMod m = new LocalMod("foo", "1.0", Path.of("foo.jar"), true);
        SyncPlan plan = new SyncPlan(List.of(m), List.of(), List.of(), List.of());
        assertFalse(plan.isEmpty());
    }
}
