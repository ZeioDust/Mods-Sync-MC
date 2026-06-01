package com.modssync.net.resolver;

import com.google.gson.*;
import com.modssync.model.ServerMod;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class CurseForgeApi {
    private static final String API = "https://api.curseforge.com/v1";
    private static final int MINECRAFT_GAME_ID = 432;
    private static final int NEOFORGE_LOADER_TYPE = 6;
    private static final String GAME_VERSION = "26.1.2";

    private CurseForgeApi() {}

    static Path resolveAndDownload(HttpClient http, String apiKey, ServerMod mod, Path destDir) {
        try {
            String q = URLEncoder.encode(mod.modId(), StandardCharsets.UTF_8);
            HttpResponse<String> search = http.send(
                    HttpRequest.newBuilder(URI.create(API + "/mods/search?gameId=" + MINECRAFT_GAME_ID
                            + "&searchFilter=" + q + "&modLoaderType=" + NEOFORGE_LOADER_TYPE
                            + "&gameVersion=" + GAME_VERSION))
                            .header("x-api-key", apiKey).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (search.statusCode() != 200) return null;
            JsonArray data = JsonParser.parseString(search.body()).getAsJsonObject().getAsJsonArray("data");
            if (data == null || data.isEmpty()) return null;
            int modId = data.get(0).getAsJsonObject().get("id").getAsInt();

            HttpResponse<String> files = http.send(
                    HttpRequest.newBuilder(URI.create(API + "/mods/" + modId + "/files?gameVersion="
                            + GAME_VERSION + "&modLoaderType=" + NEOFORGE_LOADER_TYPE))
                            .header("x-api-key", apiKey).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (files.statusCode() != 200) return null;
            JsonArray fileData = JsonParser.parseString(files.body()).getAsJsonObject().getAsJsonArray("data");
            if (fileData == null || fileData.isEmpty()) return null;
            JsonObject file = fileData.get(0).getAsJsonObject();
            String url = file.get("downloadUrl").getAsString();
            String fileName = file.get("fileName").getAsString();
            Path dest = destDir.resolve(fileName);
            HttpResponse<Path> dl = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(dest));
            return dl.statusCode() == 200 ? dest : null;
        } catch (Exception e) {
            return null;
        }
    }
}
