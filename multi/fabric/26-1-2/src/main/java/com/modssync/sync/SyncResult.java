package com.modssync.sync;

import com.modssync.model.ServerMod;
import java.util.List;

/**
 * Immutable summary of a completed sync operation.
 *
 * <p>{@code restartRequired} is {@code true} if anything in the mods folder actually
 * changed (at least one jar was disabled, enabled, or downloaded). A restart is needed
 * in that case because the running JVM's class-path no longer matches the folder state.
 *
 * <p>{@code unresolved} lists the server mods that {@link SyncOrchestrator#apply} could
 * not download from any configured source (CurseForge, Modrinth, etc.). In the normal
 * multiplayer flow this list is always empty because downloads go through the server
 * channel rather than through the acquirer.
 */
public record SyncResult(
        boolean restartRequired,
        int disabledCount,
        int enabledCount,
        int downloadedCount,
        List<ServerMod> unresolved) {}
