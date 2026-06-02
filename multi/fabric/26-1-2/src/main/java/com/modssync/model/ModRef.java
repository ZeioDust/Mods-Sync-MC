package com.modssync.model;

/**
 * A lightweight reference to a mod — just its Fabric mod ID and version string.
 *
 * <p>This is the smallest unit of mod identity used throughout the sync system.
 * It appears in the {@link SyncPlan#unchanged()} list (mods that need no action)
 * and as the base data carried by both {@link LocalMod} and {@link ServerMod}.
 *
 * <p>Version equality is intentionally <em>exact string comparison</em>; no semver
 * parsing is done. This keeps things simple and avoids false matches when servers
 * use non-standard version schemes.
 */
public record ModRef(String modId, String version) {}
