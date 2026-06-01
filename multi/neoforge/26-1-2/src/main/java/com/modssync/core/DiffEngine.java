package com.modssync.core;

import com.modssync.model.*;
import java.util.*;

/** Produces a SyncPlan by applying the four mirror rules (spec §1). */
public final class DiffEngine {

    public SyncPlan diff(List<ServerMod> serverMods, List<LocalMod> localMods) {
        List<LocalMod> toDisable = new ArrayList<>();
        List<ServerMod> toDownload = new ArrayList<>();
        List<LocalMod> toEnable = new ArrayList<>();
        List<ModRef> unchanged = new ArrayList<>();

        Map<String, LocalMod> localById = new HashMap<>();
        for (LocalMod m : localMods) {
            localById.put(m.modId().toLowerCase(Locale.ROOT), m);
        }
        Set<String> serverIds = new HashSet<>();

        // Walk the server set: decide download / enable / unchanged per mod.
        for (ServerMod sm : serverMods) {
            serverIds.add(sm.modId().toLowerCase(Locale.ROOT));
            LocalMod lm = localById.get(sm.modId().toLowerCase(Locale.ROOT));
            if (lm == null) {
                toDownload.add(sm);                      // server-only → download
            } else if (!lm.version().equals(sm.version())) {
                toDownload.add(sm);                      // version mismatch → fetch server's
                if (lm.enabled()) {
                    toDisable.add(lm);                   // and disable the local version
                }
            } else if (!lm.enabled()) {
                toEnable.add(lm);                        // present, matching, disabled → enable
            } else {
                unchanged.add(new ModRef(lm.modId(), lm.version()));
            }
        }

        // Walk the local set: enabled mods the server lacks (and not protected) → disable.
        for (LocalMod lm : localMods) {
            if (!lm.enabled()) {
                continue;
            }
            if (serverIds.contains(lm.modId().toLowerCase(Locale.ROOT))) {
                continue; // handled above
            }
            if (ProtectedCore.isProtected(lm.modId())) {
                continue;
            }
            toDisable.add(lm);
        }

        return new SyncPlan(toDisable, toDownload, toEnable, unchanged);
    }
}
