package com.modssync.net.resolver;

import com.modssync.model.ServerMod;
import java.net.http.HttpClient;
import java.nio.file.Path;

/**
 * CurseForge resolution. Requires a user-supplied API key (spec §6b). When no key
 * is configured this source is inert (returns null), so the Acquirer falls through.
 */
public final class CurseForgeSource implements ModSource {
    private final HttpClient http;
    private final String apiKey;

    public CurseForgeSource(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    @Override
    public Path download(ServerMod mod, Path destDir) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return CurseForgeApi.resolveAndDownload(http, apiKey, mod, destDir);
    }
}
