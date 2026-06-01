package com.modssync.core;

import static org.junit.jupiter.api.Assertions.*;
import com.modssync.model.ModRef;
import org.junit.jupiter.api.Test;

class ModTomlParserTest {
    @Test
    void parsesModIdAndVersion() {
        String toml = """
            [[mods]]
            modId="sodium"
            version="0.5.3"
            """;
        ModRef ref = ModTomlParser.parse(toml, "ignored");
        assertEquals("sodium", ref.modId());
        assertEquals("0.5.3", ref.version());
    }

    @Test
    void resolvesJarVersionPlaceholderFromManifest() {
        String toml = """
            [[mods]]
            modId="jei"
            version="${file.jarVersion}"
            """;
        ModRef ref = ModTomlParser.parse(toml, "15.2.0.27");
        assertEquals("jei", ref.modId());
        assertEquals("15.2.0.27", ref.version());
    }

    @Test
    void toleratesWhitespaceAndSingleQuotes() {
        String toml = "[[mods]]\n  modId = 'create' \n  version = '0.5.1' \n";
        ModRef ref = ModTomlParser.parse(toml, null);
        assertEquals("create", ref.modId());
        assertEquals("0.5.1", ref.version());
    }

    @Test
    void returnsNullWhenNoModId() {
        assertNull(ModTomlParser.parse("# empty\n", null));
    }
}
