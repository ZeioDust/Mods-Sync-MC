package com.modssync.core;

import java.util.Set;

/**
 * Central registry of folder names that ModsSync is permitted to read from or write to,
 * along with utilities for validating file names before touching the filesystem.
 *
 * <p>Keeping these constants in one place means any new folder support only needs to be
 * added here; the rest of the codebase queries {@link #isAllowed} rather than hard-coding
 * strings. It also makes it easy to audit exactly what the mod can touch.
 *
 * <p>Note that {@code shaderpacks} is declared as a constant but is intentionally absent
 * from {@link #ALLOWED} and {@link #PACK_FOLDERS}. It's here for potential future use or
 * reference — the mod does not currently write to shaderpacks.
 */
public final class SyncFolders {

    public static final String MODS = "mods";
    public static final String RESOURCEPACKS = "resourcepacks";
    /** Declared for completeness; not currently included in ALLOWED or PACK_FOLDERS. */
    public static final String SHADERPACKS = "shaderpacks";

    /**
     * The set of folders the mod may write files into.
     * Any folder name arriving from the server is validated against this set before use.
     */
    public static final Set<String> ALLOWED = Set.of(MODS, RESOURCEPACKS);

    /**
     * Folders that are synced <em>additively</em>: files the server advertises but the
     * client is missing will be downloaded, but files the client has that the server
     * doesn't mention are left untouched. This is intentional — resourcepacks are often
     * player-curated, so we only add what the server wants, never remove extras.
     */
    public static final Set<String> PACK_FOLDERS = Set.of(RESOURCEPACKS);

    // Utility class — no instances.
    private SyncFolders() {}

    /**
     * Returns {@code true} if the mod is permitted to write into {@code folder}.
     * Rejects nulls and any folder name not in {@link #ALLOWED}.
     */
    public static boolean isAllowed(String folder) {
        return folder != null && ALLOWED.contains(folder);
    }

    /**
     * Validates that {@code name} is a bare file name with no directory components.
     * This blocks path-traversal payloads like {@code "../../.minecraft/options.txt"}
     * or absolute paths that could escape the target folder.
     */
    public static boolean isSafeName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }
}
