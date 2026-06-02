package com.modssync.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modssync.model.ModRef;

/**
 * Extracts the minimal identity fields from a {@code fabric.mod.json} manifest.
 *
 * <p>This is intentionally not a full schema parser — we only need {@code id},
 * {@code version}, and {@code environment}. Pulling in a full Fabric schema
 * dependency just for these three fields would be overkill and fragile across
 * Fabric versions. Gson's JsonObject approach handles quirky/extended manifests
 * gracefully: unknown fields are simply ignored.
 *
 * <p>All methods treat malformed or missing JSON defensively: they return safe
 * defaults ({@code null} or {@code "*"}) rather than throwing, because a single
 * unreadable mod should never abort a full scan.
 */
public final class ModJsonParser {

    // Utility class — no instances.
    private ModJsonParser() {}

    /**
     * Reads the {@code "environment"} field from a {@code fabric.mod.json} string.
     *
     * <p>The Fabric schema defines three valid values:
     * <ul>
     *   <li>{@code "*"}      — loads on both client and server (most mods)</li>
     *   <li>{@code "client"} — client-side only</li>
     *   <li>{@code "server"} — server-side only</li>
     * </ul>
     * We default to {@code "*"} if the field is absent or the JSON is invalid,
     * which is the conservative choice — it keeps the mod visible rather than
     * silently hiding it.
     *
     * @return the environment string, never {@code null}
     */
    public static String environment(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.has("environment") ? obj.get("environment").getAsString() : "*";
        } catch (Exception e) {
            return "*";
        }
    }

    /**
     * Parses a {@code fabric.mod.json} string and returns the mod's identity.
     *
     * <p>Version defaults to {@code "0.0.0"} if the field is absent — some very
     * simple mods omit it, and we need a non-null string for equality comparisons.
     * The id is trimmed because trailing whitespace in hand-edited manifests is a
     * real occurrence and causes spurious mismatches.
     *
     * @return the parsed {@link ModRef}, or {@code null} if the JSON has no valid {@code id}
     */
    public static ModRef parse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj == null || !obj.has("id")) {
                return null;
            }
            String id = obj.get("id").getAsString().trim();
            if (id.isEmpty()) {
                return null;
            }
            String version = obj.has("version") ? obj.get("version").getAsString().trim() : "0.0.0";
            return new ModRef(id, version);
        } catch (Exception e) {
            return null;
        }
    }
}
