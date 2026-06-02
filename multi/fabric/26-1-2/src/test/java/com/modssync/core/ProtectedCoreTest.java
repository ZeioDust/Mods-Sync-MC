package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProtectedCoreTest {
    @Test
    void coreModsAreProtected() {
        assertTrue(ProtectedCore.isProtected("modssync"));
        assertTrue(ProtectedCore.isProtected("minecraft"));
        assertTrue(ProtectedCore.isProtected("fabricloader"));
        assertTrue(ProtectedCore.isProtected("fabric"));
        assertTrue(ProtectedCore.isProtected("java"));
    }

    @Test
    void protectionIsCaseInsensitive() {
        assertTrue(ProtectedCore.isProtected("FabricLoader"));
    }

    @Test
    void otherModsAreNotProtected() {
        assertFalse(ProtectedCore.isProtected("sodium"));
        assertFalse(ProtectedCore.isProtected("jei"));
        assertFalse(ProtectedCore.isProtected("neoforge"));
    }
}
