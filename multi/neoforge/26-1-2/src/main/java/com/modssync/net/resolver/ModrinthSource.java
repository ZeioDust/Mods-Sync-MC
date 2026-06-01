package com.modssync.net.resolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modssync.model.ServerMod;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Best-effort Modrinth resolution: prefer sha1 version lookup (exact); otherwise
 * search by modId for a version matching game 26.1.2 + loader neoforge.
 */
public final class ModrinthSource implements ModSource {
    private static final String API = "https://api.modrinth.com/v2";
    private static final String GAME_VERSION = "26.1.2";
    private static final String LOADER = "neoforge";
    private final HttpClient http;

    public ModrinthSource(HttpClient http) {
        this.http = http;
    }

    @Override
    public Path download(ServerMod mod, Path destDir) {
        try {
            JsonObject version = mod.sha1() != null ? byHash(mod.sha1()) : bySearch(mod);
            if (version == null) {
                return null;
            }
            JsonArray files = version.getAsJsonArray("files");
            if (files == null || files.isEmpty()) {
                return null;
            }
            JsonObject primary = files.get(0).getAsJsonObject();
            String url = primary.get("url").getAsString();
            String fileName = primary.get("filename").getAsString();
            Path dest = destDir.resolve(fileName);
            HttpResponse<Path> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(dest));
            return resp.statusCode() == 200 ? dest : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject byHash(String sha1) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(API + "/version_file/" + sha1 + "?algorithm=sha1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private JsonObject bySearch(ServerMod mod) throws Exception {
        String q = URLEncoder.encode(mod.modId(), StandardCharsets.UTF_8);
        String facets = URLEncoder.encode(
                "[[\"versions:" + GAME_VERSION + "\"],[\"categories:" + LOADER + "\"]]",
                StandardCharsets.UTF_8);
        HttpResponse<String> search = http.send(
                HttpRequest.newBuilder(URI.create(API + "/search?query=" + q + "&facets=" + facets + "&limit=1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (search.statusCode() != 200) return null;
        JsonArray hits = JsonParser.parseString(search.body()).getAsJsonObject().getAsJsonArray("hits");
        if (hits == null || hits.isEmpty()) return null;
        String projectId = hits.get(0).getAsJsonObject().get("project_id").getAsString();

        String loadersParam = URLEncoder.encode("[\"" + LOADER + "\"]", StandardCharsets.UTF_8);
        String gvParam = URLEncoder.encode("[\"" + GAME_VERSION + "\"]", StandardCharsets.UTF_8);
        HttpResponse<String> versions = http.send(
                HttpRequest.newBuilder(URI.create(
                        API + "/project/" + projectId + "/version?loaders=" + loadersParam + "&game_versions=" + gvParam)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (versions.statusCode() != 200) return null;
        JsonArray arr = JsonParser.parseString(versions.body()).getAsJsonArray();
        if (arr == null || arr.isEmpty()) return null;
        for (var el : arr) {
            JsonObject v = el.getAsJsonObject();
            if (mod.version().equals(v.get("version_number").getAsString())) {
                return v;
            }
        }
        return arr.get(0).getAsJsonObject();
    }
}
