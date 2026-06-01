package com.modssync.model;

import java.util.List;

/**
 * The result of diffing server mods against the local folder.
 * toDisable / toEnable are local jars; toDownload are server mods to fetch.
 */
public record SyncPlan(
        List<LocalMod> toDisable,
        List<ServerMod> toDownload,
        List<LocalMod> toEnable,
        List<ModRef> unchanged) {

    public boolean isEmpty() {
        return toDisable.isEmpty() && toDownload.isEmpty() && toEnable.isEmpty();
    }
}
