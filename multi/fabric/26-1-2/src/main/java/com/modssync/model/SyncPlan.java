package com.modssync.model;

import java.util.List;

/**
 * The complete set of actions required to bring the local mods folder in sync with
 * what the server expects, as computed by {@link com.modssync.core.DiffEngine}.
 *
 * <p>Each list is mutually exclusive — a given mod will appear in exactly one:
 * <ul>
 *   <li>{@code toDisable}  — local mods that need to be soft-disabled (renamed .disabled)
 *                            because the server doesn't want them, or because a different
 *                            version will replace them.</li>
 *   <li>{@code toDownload} — mods the server requires but that are either absent locally
 *                            or present at the wrong version.</li>
 *   <li>{@code toEnable}   — mods already on disk (currently disabled) whose version
 *                            matches the server; just needs renaming back to .jar.</li>
 *   <li>{@code unchanged}  — mods that match the server exactly and are already enabled;
 *                            tracked for logging/UI completeness, no action needed.</li>
 * </ul>
 */
public record SyncPlan(
        List<LocalMod> toDisable,
        List<ServerMod> toDownload,
        List<LocalMod> toEnable,
        List<ModRef> unchanged) {

    /** Returns {@code true} if no action is required — the local folder is already in sync. */
    public boolean isEmpty() {
        return toDisable.isEmpty() && toDownload.isEmpty() && toEnable.isEmpty();
    }
}
