package com.modssync.model;

/**
 * Describes a mod that the server expects the client to have installed.
 *
 * <p>When the client connects to a full ModsSync server, all fields are populated:
 * {@code fileName} is the canonical jar name used when writing the file to disk,
 * {@code sha1} lets us verify download integrity, {@code downloadUrl} is the direct
 * file URL, and {@code modrinthId}/{@code curseforgeId} are fallbacks for resolving
 * the mod from platform APIs if the primary URL fails.
 *
 * <p>When falling back to a basic handshake (non-ModsSync server or older protocol),
 * only {@code modId} and {@code version} are available — everything else is {@code null}.
 * Use {@link #ofIdVersion} for that case. The null fields effectively mean "we know this
 * mod should be present, but we can't help you download it".
 */
public record ServerMod(
        String modId,
        String version,
        /** The file name to use when writing the jar to the mods folder. Null if unknown. */
        String fileName,
        /** SHA-1 hex digest of the jar for integrity verification. Null if unknown. */
        String sha1,
        /** Direct download URL for the jar. Null if unknown. */
        String downloadUrl,
        /** Modrinth project/version ID, used as a download fallback. Null if unknown. */
        String modrinthId,
        /** CurseForge project/file ID, used as a download fallback. Null if unknown. */
        String curseforgeId) {

    /**
     * Creates a minimal ServerMod with only identity information.
     * Used when syncing against a server that doesn't supply full metadata —
     * the DiffEngine can still detect mismatches; it just can't auto-download.
     */
    public static ServerMod ofIdVersion(String modId, String version) {
        return new ServerMod(modId, version, null, null, null, null, null);
    }
}
