package com.modssync.core;

import com.modssync.model.LocalMod;
import java.io.IOException;
import java.nio.file.*;

/** Applies enable/disable/place actions to mod jars, guarding protected mods. */
public final class FolderMutator {

    private static final String DISABLED_SUFFIX = ".disabled";

    /** Rename mod.jar -> mod.jar.disabled; if rename fails, delete. Protected mods are skipped. */
    public boolean disable(LocalMod mod) {
        if (ProtectedCore.isProtected(mod.modId())) {
            return false;
        }
        Path jar = mod.file();
        Path disabled = jar.resolveSibling(jar.getFileName().toString() + DISABLED_SUFFIX);
        try {
            Files.move(jar, disabled, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException renameFailed) {
            try {
                Files.deleteIfExists(jar); // fallback per spec §7
                return true;
            } catch (IOException deleteFailed) {
                return false;
            }
        }
    }

    /** Rename mod.jar.disabled -> mod.jar. */
    public boolean enable(LocalMod mod) {
        Path disabled = mod.file();
        String name = disabled.getFileName().toString();
        if (!name.endsWith(DISABLED_SUFFIX)) {
            return true; // already enabled
        }
        Path jar = disabled.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
        try {
            Files.move(disabled, jar, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Move a downloaded jar into the mods folder under the given file name. */
    public boolean place(Path downloaded, Path modsDir, String fileName) {
        try {
            Files.move(downloaded, modsDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
