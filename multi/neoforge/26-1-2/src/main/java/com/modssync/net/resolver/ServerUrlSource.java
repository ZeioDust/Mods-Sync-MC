package com.modssync.net.resolver;

import com.modssync.model.ServerMod;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Downloads from the manifest-provided downloadUrl and verifies sha1 when present. */
public final class ServerUrlSource implements ModSource {
    private final HttpClient http;

    public ServerUrlSource(HttpClient http) {
        this.http = http;
    }

    @Override
    public Path download(ServerMod mod, Path destDir) {
        if (mod.downloadUrl() == null || mod.downloadUrl().isBlank()) {
            return null;
        }
        try {
            String fileName = mod.fileName() != null ? mod.fileName() : mod.modId() + ".jar";
            Path dest = destDir.resolve(fileName);
            HttpResponse<Path> resp = http.send(
                    HttpRequest.newBuilder(URI.create(mod.downloadUrl())).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(dest));
            if (resp.statusCode() != 200) {
                return null;
            }
            if (mod.sha1() != null && !sha1(dest).equalsIgnoreCase(mod.sha1())) {
                java.nio.file.Files.deleteIfExists(dest);
                return null;
            }
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(java.nio.file.Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
