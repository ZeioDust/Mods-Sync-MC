package com.modssync.sync;

import com.modssync.model.ServerMod;
import java.util.List;

/**
 * Outcome of a sync. {@code restartRequired} is true iff the mods folder actually
 * changed (something disabled/enabled/placed). {@code unresolved} lists server
 * mods that could not be downloaded from any source.
 */
public record SyncResult(
        boolean restartRequired,
        int disabledCount,
        int enabledCount,
        int downloadedCount,
        List<ServerMod> unresolved) {}
