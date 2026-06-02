package com.modssync.core;

import com.modssync.model.LocalMod;
import com.modssync.model.ModRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans the player's local mods directory and builds a list of {@link LocalMod} entries,
 * one per readable Fabric mod jar (whether enabled or disabled).
 *
 * <p>Only files ending in {@code .jar} or {@code .jar.disabled} are examined; everything
 * else (directories, configs, metadata files) is skipped. Non-Fabric jars — ones that
 * lack a {@code fabric.mod.json} entry — are also skipped silently, because the mods
 * folder sometimes contains Forge or vanilla jars, and we don't want to crash on them.
 */
public final class LocalInventory {

    private static final String DISABLED_SUFFIX = ".jar.disabled";

    /**
     * Enumerates all mod jars in {@code modsDir} and returns their parsed identities.
     *
     * <p>If {@code modsDir} doesn't exist or can't be read, an empty list is returned
     * rather than throwing — this keeps the sync flow working even on fresh installs
     * where the folder hasn't been created yet.
     *
     * @return one {@link LocalMod} per readable mod jar (enabled or disabled)
     */
    public List<LocalMod> scan(Path modsDir) {
        List<LocalMod> result = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                boolean enabled;
                if (name.endsWith(DISABLED_SUFFIX)) {
                    enabled = false;
                } else if (name.endsWith(".jar")) {
                    enabled = true;
                } else {
                    continue; // not a jar — skip
                }
                LocalMod mod = read(p, enabled);
                if (mod != null) {
                    result.add(mod);
                }
            }
        } catch (IOException e) {
            // A directory we cannot read yields no mods rather than crashing the sync.
        }
        return result;
    }

    /**
     * Reads the {@code fabric.mod.json} manifest out of a jar zip and constructs a
     * {@link LocalMod}, or returns {@code null} if the jar is unreadable, is not a
     * Fabric mod, or has an invalid manifest.
     *
     * <p>ZipFile is used instead of ZipInputStream because it allows random-access to
     * the manifest entry by name without scanning the entire archive sequentially.
     */
    private LocalMod read(Path jar, boolean enabled) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                // Not a Fabric mod (could be a bundled library or Forge jar). Skip it.
                return null;
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            ModRef ref = ModJsonParser.parse(json);
            if (ref == null) {
                return null;
            }
            return new LocalMod(ref.modId(), ref.version(), jar, enabled);
        } catch (IOException e) {
            // Corrupt or locked jar — skip rather than abort the whole scan.
            return null;
        }
    }
}
