package com.modssync.core;

import com.modssync.model.LocalMod;
import com.modssync.model.ModRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Scans a mods directory and reports every mod jar it finds. */
public final class LocalInventory {

    private static final String DISABLED_SUFFIX = ".jar.disabled";

    /** @return one LocalMod per readable mod jar (enabled or disabled). */
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
                    continue;
                }
                LocalMod mod = read(p, enabled);
                if (mod != null) {
                    result.add(mod);
                }
            }
        } catch (IOException e) {
            // A directory we cannot read yields no mods rather than crashing the join.
        }
        return result;
    }

    private LocalMod read(Path jar, boolean enabled) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry tomlEntry = zip.getEntry("META-INF/neoforge.mods.toml");
            if (tomlEntry == null) {
                return null;
            }
            String toml;
            try (InputStream in = zip.getInputStream(tomlEntry)) {
                toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String manifestVer = readManifestVersion(zip);
            ModRef ref = ModTomlParser.parse(toml, manifestVer);
            if (ref == null) {
                return null;
            }
            return new LocalMod(ref.modId(), ref.version(), jar, enabled);
        } catch (IOException e) {
            return null;
        }
    }

    private String readManifestVersion(ZipFile zip) throws IOException {
        ZipEntry mf = zip.getEntry("META-INF/MANIFEST.MF");
        if (mf == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(mf)) {
            Manifest manifest = new Manifest(in);
            return manifest.getMainAttributes().getValue("Implementation-Version");
        }
    }
}
