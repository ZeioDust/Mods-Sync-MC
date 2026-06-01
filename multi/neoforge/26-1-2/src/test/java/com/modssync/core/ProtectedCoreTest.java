package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProtectedCoreTest {
    @Test
    void coreModsAreProtected() {
        assertTrue(ProtectedCore.isProtected("modssync"));
        assertTrue(ProtectedCore.isProtected("neoforge"));
        assertTrue(ProtectedCore.isProtected("minecraft"));
        assertTrue(ProtectedCore.isProtected("fml"));
    }

    @Test
    void protectionIsCaseInsensitive() {
        assertTrue(ProtectedCore.isProtected("NeoForge"));
    }

    @Test
    void otherModsAreNotProtected() {
        assertFalse(ProtectedCore.isProtected("sodium"));
        assertFalse(ProtectedCore.isProtected("jei"));
    }
}
