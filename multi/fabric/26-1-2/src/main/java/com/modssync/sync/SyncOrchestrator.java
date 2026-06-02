package com.modssync.sync;

import com.modssync.core.*;
import com.modssync.model.*;
import java.nio.file.Path;
import java.util.List;

/**
 * Coordinates the full mod-synchronisation pipeline in a loader-agnostic way.
 *
 * <p>The pipeline has two independent parts that run at different points in time:
 *
 * <h3>computePlan — fast, local-only diff</h3>
 * <p>Scans the local mods directory, records a "home snapshot" on first run so the
 * player's original modpack can be restored later, then diffs the local inventory
 * against the server's target list. The result is a {@link SyncPlan} containing three
 * sets: mods to disable (local extras), mods to enable (previously disabled that the
 * server wants), and mods to download (missing entirely). No network I/O occurs here.
 *
 * <h3>apply — local disk mutation</h3>
 * <p>Executes a plan's local part: renames jars to {@code .disabled} or back. It does
 * not download anything — missing mods are cloned directly from the server over the
 * network (see {@code ModFilePayload}), so {@code apply()} reports the to-download set
 * back as "unresolved" for the caller to handle elsewhere.
 */
public final class SyncOrchestrator {

    private final Path modsDir;
    private final LocalInventory inventory;
    private final DiffEngine diff;
    private final FolderMutator mutator;
    private final SnapshotStore snapshot;

    public SyncOrchestrator(Path modsDir, LocalInventory inventory, DiffEngine diff,
                            FolderMutator mutator, SnapshotStore snapshot) {
        this.modsDir = modsDir;
        this.inventory = inventory;
        this.diff = diff;
        this.mutator = mutator;
        this.snapshot = snapshot;
    }

    /**
     * Fast, local-only: scan the mods folder, record the home snapshot (once), and diff
     * against the server's target list.
     *
     * @param target the mods the server expects the client to have
     * @return a plan describing what needs to change (disable / enable / download)
     */
    public SyncPlan computePlan(List<ServerMod> target) {
        List<LocalMod> local = inventory.scan(modsDir);
        // recordIfAbsent writes the snapshot only on first call; subsequent calls are no-ops.
        snapshot.recordIfAbsent(local);
        return diff.diff(target, local);
    }

    /**
     * Applies the local part of a plan — renames jars to disabled/enabled.
     *
     * <p>This orchestrator no longer downloads anything itself. In the live multiplayer
     * flow, missing mods are cloned directly from the server over the network
     * (see {@link com.modssync.ModsSyncClient} and {@code ModFilePayload}), so the
     * {@code toDownload} entries are reported back as {@code unresolved} here — they're
     * "not handled by apply()" and are fetched elsewhere.
     *
     * @param plan the work to perform
     * @return a {@link SyncResult} summarising what changed; {@code unresolved} = the
     *         mods this method didn't fetch (the network layer handles those)
     */
    public SyncResult apply(SyncPlan plan) {
        int disabled = 0;
        int enabled = 0;

        for (LocalMod m : plan.toDisable()) {
            if (mutator.disable(m)) disabled++;
        }
        for (LocalMod m : plan.toEnable()) {
            if (mutator.enable(m)) enabled++;
        }

        boolean changed = disabled > 0 || enabled > 0;
        return new SyncResult(changed, disabled, enabled, 0, plan.toDownload());
    }

    /**
     * Convenience method: {@code computePlan} + {@code apply} in one call.
     * Used by tests and the restore-home flow.
     */
    public SyncResult syncTo(List<ServerMod> target) {
        return apply(computePlan(target));
    }

    /** Restores the player's original "home" modpack from the snapshot recorded on first sync. */
    public SyncResult restoreHome() {
        return syncTo(snapshot.loadAsServerMods());
    }
}
