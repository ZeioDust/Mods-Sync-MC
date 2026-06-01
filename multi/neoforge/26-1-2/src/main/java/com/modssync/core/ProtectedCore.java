package com.modssync.core;

import java.util.Locale;
import java.util.Set;

/** Mods that ModsSync must never disable or delete (spec §4). */
public final class ProtectedCore {
    private static final Set<String> PROTECTED = Set.of(
            "modssync",
            "neoforge",
            "minecraft",
            "fml");

    private ProtectedCore() {}

    public static boolean isProtected(String modId) {
        return modId != null && PROTECTED.contains(modId.toLowerCase(Locale.ROOT));
    }
}
