package com.modssync.core;

import java.util.Locale;
import java.util.Set;

/**
 * A hard-coded allowlist of mod IDs that ModsSync must never disable or delete,
 * regardless of what the server's mod list says.
 *
 * <p>Without this guard, a misconfigured server could instruct the client to disable
 * Fabric Loader, Fabric API, or even ModsSync itself — all of which would leave the
 * game in an unbootable state or break the sync mechanism entirely.
 *
 * <p>The check is case-insensitive because Fabric mod IDs are lowercase by convention
 * but not enforced, so "FabricLoader" and "fabricloader" must both be caught.
 */
public final class ProtectedCore {

    private static final Set<String> PROTECTED = Set.of(
            "modssync",     // don't let the mod disable itself
            "minecraft",    // the game itself
            "fabricloader", // the mod loader
            "fabric",       // legacy loader alias
            "fabric-api",   // the core API almost every mod depends on
            "java");        // Fabric also exposes the JVM as a "mod"

    // Utility class — no instances.
    private ProtectedCore() {}

    /**
     * Returns {@code true} if the given mod ID belongs to the protected set and
     * must not be touched by any sync operation.
     */
    public static boolean isProtected(String modId) {
        return modId != null && PROTECTED.contains(modId.toLowerCase(Locale.ROOT));
    }
}
