package com.modssync.sync;

import com.modssync.model.ServerMod;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Static handoff point between the network/handshake layer and the join gate. */
public final class SyncResultHolder {
    private static final AtomicReference<List<ServerMod>> pendingServerMods = new AtomicReference<>();

    private SyncResultHolder() {}

    public static void onManifestReceived(List<ServerMod> mods) {
        pendingServerMods.set(mods);
    }

    /** Returns true if a pending manifest is present (non-destructive peek). */
    public static boolean hasPending() {
        return pendingServerMods.get() != null;
    }

    /** Atomically returns and clears the server mod set captured during configuration, or null. */
    public static List<ServerMod> takePending() {
        return pendingServerMods.getAndSet(null);
    }
}
