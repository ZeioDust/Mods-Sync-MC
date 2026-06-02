package com.modssync.core;

import com.modssync.model.*;
import java.util.*;

/**
 * Computes a {@link SyncPlan} by comparing the server's required mod list against
 * what the player has locally — the "mirror diff" at the heart of ModsSync.
 *
 * <p>The algorithm applies four rules (spec §1) in two passes:
 *
 * <p><strong>Pass 1 — walk the server list</strong> (what the server wants):
 * <ol>
 *   <li><strong>Rule 1 — Missing locally → download.</strong>
 *       If the server requires a mod the client has never seen, add it to
 *       {@code toDownload}.</li>
 *   <li><strong>Rule 2 — Version mismatch → replace.</strong>
 *       If the mod is present but at the wrong version, download the server's
 *       version <em>and</em> disable the existing one so both don't load together.
 *       Only the enabled copy needs to be disabled; a .jar.disabled at the wrong
 *       version is harmless to leave around (it was already inert).</li>
 *   <li><strong>Rule 3 — Correct version, currently disabled → enable.</strong>
 *       The player already has the right file from a previous sync; just rename it
 *       back from .jar.disabled to .jar.</li>
 *   <li><strong>Rule 4 — Correct version, already enabled → unchanged.</strong>
 *       Nothing to do; record it for logging completeness.</li>
 * </ol>
 *
 * <p><strong>Pass 2 — walk the local list</strong> (what the client has extra):
 * <ol start="5">
 *   <li><strong>Rule 5 — Locally enabled, server doesn't want it, not protected → disable.</strong>
 *       Extra client mods that the server doesn't list get soft-disabled so the
 *       server's modset is mirrored exactly. Protected mods (Fabric Loader, etc.)
 *       are exempt regardless. Already-disabled mods are skipped because they're
 *       already inert.</li>
 * </ol>
 *
 * <p>All ID comparisons are lowercased because Fabric mod IDs are case-insensitive
 * in practice, and mixed-case IDs in hand-written manifests are a real occurrence.
 */
public final class DiffEngine {

    /**
     * Runs the diff and returns the full sync plan.
     *
     * @param serverMods the authoritative list from the server
     * @param localMods  the current state of the player's mods folder
     * @return a plan describing every action needed (may be {@link SyncPlan#isEmpty() empty}
     *         if the folder already matches the server)
     */
    public SyncPlan diff(List<ServerMod> serverMods, List<LocalMod> localMods) {
        List<LocalMod> toDisable = new ArrayList<>();
        List<ServerMod> toDownload = new ArrayList<>();
        List<LocalMod> toEnable = new ArrayList<>();
        List<ModRef> unchanged = new ArrayList<>();

        // Build a lookup map so we can resolve each server mod to its local counterpart in O(1).
        // When the same mod id appears more than once (e.g. the correct enabled jar AND an old
        // copy left behind as .jar.disabled), the ENABLED copy must win. Otherwise a disabled
        // stale version could shadow the good enabled one, making the diff think the wrong
        // version is installed and re-downloading forever — an endless "please restart" loop.
        Map<String, LocalMod> localById = new HashMap<>();
        for (LocalMod m : localMods) {
            String key = m.modId().toLowerCase(Locale.ROOT);
            LocalMod existing = localById.get(key);
            if (existing == null || (!existing.enabled() && m.enabled())) {
                localById.put(key, m);
            }
        }

        // Track which IDs the server listed; used in pass 2 to find "extra" local mods.
        Set<String> serverIds = new HashSet<>();

        // --- Pass 1: walk the server list ---
        for (ServerMod sm : serverMods) {
            serverIds.add(sm.modId().toLowerCase(Locale.ROOT));
            LocalMod lm = localById.get(sm.modId().toLowerCase(Locale.ROOT));

            if (lm == null) {
                // Rule 1: client has never seen this mod — need to fetch it.
                toDownload.add(sm);

            } else if (!lm.version().equals(sm.version())) {
                // Rule 2: wrong version on disk — download the correct one.
                toDownload.add(sm);
                // Disable the stale local copy only if it's currently active;
                // a disabled stale copy can be left alone (it won't be loaded).
                if (lm.enabled()) {
                    toDisable.add(lm);
                }

            } else if (!lm.enabled()) {
                // Rule 3: right version but soft-disabled from a previous session — just re-enable it.
                toEnable.add(lm);

            } else {
                // Rule 4: correct version, already enabled — nothing to do.
                unchanged.add(new ModRef(lm.modId(), lm.version()));
            }
        }

        // --- Pass 2: find locally-enabled mods the server doesn't want ---
        for (LocalMod lm : localMods) {
            if (!lm.enabled()) {
                continue; // already disabled — not actively loaded, so ignore
            }
            if (serverIds.contains(lm.modId().toLowerCase(Locale.ROOT))) {
                continue; // this mod was handled in pass 1
            }
            if (ProtectedCore.isProtected(lm.modId())) {
                continue; // never touch Fabric Loader, Fabric API, ModsSync, etc.
            }
            // Rule 5: extra client mod the server doesn't list → soft-disable it.
            toDisable.add(lm);
        }

        return new SyncPlan(toDisable, toDownload, toEnable, unchanged);
    }
}
