package com.modssync.sync;

import com.modssync.core.*;
import com.modssync.model.*;
import com.modssync.net.resolver.Acquirer;
import java.nio.file.Path;
import java.util.List;

/** Drives inventory → snapshot → diff → acquire → mutate. Loader-agnostic. */
public final class SyncOrchestrator {

    private final Path modsDir;
    private final LocalInventory inventory;
    private final DiffEngine diff;
    private final FolderMutator mutator;
    private final SnapshotStore snapshot;
    private final Acquirer acquirer;

    public SyncOrchestrator(Path modsDir, LocalInventory inventory, DiffEngine diff,
                            FolderMutator mutator, SnapshotStore snapshot, Acquirer acquirer) {
        this.modsDir = modsDir;
        this.inventory = inventory;
        this.diff = diff;
        this.mutator = mutator;
        this.snapshot = snapshot;
        this.acquirer = acquirer;
    }

    /** Sync the mods folder to match the given target set (server mods, or snapshot for restore). */
    public SyncResult syncTo(List<ServerMod> target) {
        List<LocalMod> local = inventory.scan(modsDir);
        snapshot.recordIfAbsent(local);

        SyncPlan plan = diff.diff(target, local);

        int disabled = 0;
        int enabled = 0;

        for (LocalMod m : plan.toDisable()) {
            if (mutator.disable(m)) disabled++;
        }
        for (LocalMod m : plan.toEnable()) {
            if (mutator.enable(m)) enabled++;
        }

        Acquirer.Result acq = acquirer.acquire(plan.toDownload(), modsDir);
        int downloaded = acq.downloaded().size();

        boolean changed = disabled > 0 || enabled > 0 || downloaded > 0;
        return new SyncResult(changed, disabled, enabled, downloaded, acq.unresolved());
    }

    /** Restore the player's home modpack snapshot. */
    public SyncResult restoreHome() {
        return syncTo(snapshot.loadAsServerMods());
    }
}
