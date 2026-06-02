package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.ModRef;
import org.junit.jupiter.api.Test;

class ModJsonParserTest {
    @Test
    void parsesIdAndVersion() {
        ModRef ref = ModJsonParser.parse("{\"id\":\"sodium\",\"version\":\"0.5.3\"}");
        assertNotNull(ref);
        assertEquals("sodium", ref.modId());
        assertEquals("0.5.3", ref.version());
    }

    @Test
    void defaultsVersionWhenMissing() {
        ModRef ref = ModJsonParser.parse("{\"id\":\"create\"}");
        assertNotNull(ref);
        assertEquals("create", ref.modId());
        assertEquals("0.0.0", ref.version());
    }

    @Test
    void returnsNullWhenNoId() {
        assertNull(ModJsonParser.parse("{\"name\":\"no id here\"}"));
    }

    @Test
    void returnsNullOnInvalidJson() {
        assertNull(ModJsonParser.parse("not json"));
    }
}
