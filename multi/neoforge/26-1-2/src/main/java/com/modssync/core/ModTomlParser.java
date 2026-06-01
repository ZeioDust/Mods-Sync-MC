package com.modssync.core;

import com.modssync.model.ModRef;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal extractor for the first [[mods]] entry's modId/version from a
 * neoforge.mods.toml. Not a full TOML parser — only the two fields we need.
 */
public final class ModTomlParser {
    private static final Pattern MOD_ID =
            Pattern.compile("(?m)^\\s*modId\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION =
            Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]");

    private ModTomlParser() {}

    /**
     * @param toml           contents of neoforge.mods.toml
     * @param jarManifestVer Implementation-Version from the jar manifest, used to
     *                       resolve a "${file.jarVersion}" placeholder; may be null.
     * @return the parsed ModRef, or null if no modId is present.
     */
    public static ModRef parse(String toml, String jarManifestVer) {
        Matcher idm = MOD_ID.matcher(toml);
        if (!idm.find()) {
            return null;
        }
        String modId = idm.group(1).trim();

        String version = "0.0.0";
        Matcher vm = VERSION.matcher(toml);
        if (vm.find()) {
            version = vm.group(1).trim();
        }
        if (version.startsWith("${") && jarManifestVer != null && !jarManifestVer.isBlank()) {
            version = jarManifestVer;
        }
        return new ModRef(modId, version);
    }
}
