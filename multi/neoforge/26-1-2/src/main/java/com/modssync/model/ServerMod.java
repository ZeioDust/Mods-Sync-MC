package com.modssync.model;

/**
 * A mod the server requires. Fields beyond modId/version are optional (null when
 * unknown — e.g. on non-ModsSync servers we only learn modId+version).
 */
public record ServerMod(
        String modId,
        String version,
        String fileName,
        String sha1,
        String downloadUrl,
        String modrinthId,
        String curseforgeId) {

    /** Convenience for the handshake fallback where only id+version are known. */
    public static ServerMod ofIdVersion(String modId, String version) {
        return new ServerMod(modId, version, null, null, null, null, null);
    }
}
