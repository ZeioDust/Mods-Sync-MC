package com.modssync.sync;

import com.modssync.model.PackFile;
import com.modssync.model.ServerMod;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Static handoff point that bridges the <em>configuration phase</em> (where the server
 * manifests arrive) and the <em>play phase</em> (where missing files are requested).
 *
 * <p>Fabric's networking events run on different threads at different points in the
 * connection lifecycle, so we can't simply pass data through a local variable. Instead,
 * the configuration-phase receivers store the manifests here, and the JOIN handler
 * retrieves them.
 *
 * <p>Both fields use {@link AtomicReference} so the store/take operations are
 * thread-safe without explicit synchronisation. The {@code take*} methods use
 * {@link AtomicReference#getAndSet getAndSet(null)} — a single atomic operation that
 * both reads and clears the reference — preventing the JOIN handler from accidentally
 * processing a stale manifest from a previous connection attempt.
 */
public final class SyncResultHolder {
    private static final AtomicReference<List<ServerMod>> pendingServerMods = new AtomicReference<>();
    private static final AtomicReference<List<PackFile>> pendingPacks = new AtomicReference<>();

    private SyncResultHolder() {}

    /** Called by the {@link com.modssync.net.ManifestPayload} receiver during configuration. */
    public static void onManifestReceived(List<ServerMod> mods) {
        pendingServerMods.set(mods);
    }

    /** Called by the {@link com.modssync.net.PackManifestPayload} receiver during configuration. */
    public static void onPacksReceived(List<PackFile> packs) {
        pendingPacks.set(packs);
    }

    /** Returns true if a pending mod manifest is present (non-destructive peek). */
    public static boolean hasPending() {
        return pendingServerMods.get() != null;
    }

    /**
     * Atomically returns and clears the server mod list captured during configuration.
     * Returns {@code null} if no manifest has been received (e.g. singleplayer, or
     * called a second time for the same connection).
     */
    public static List<ServerMod> takePending() {
        return pendingServerMods.getAndSet(null);
    }

    /**
     * Atomically returns and clears the pack list captured during configuration.
     * Returns {@code null} if no pack manifest has been received.
     */
    public static List<PackFile> takePendingPacks() {
        return pendingPacks.getAndSet(null);
    }
}
