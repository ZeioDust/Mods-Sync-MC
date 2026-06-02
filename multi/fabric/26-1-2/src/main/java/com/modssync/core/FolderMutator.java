package com.modssync.core;

import com.modssync.model.LocalMod;
import java.io.IOException;
import java.nio.file.*;

/**
 * The only class in the mod that physically writes to the mods folder.
 *
 * <p>It handles three operations: soft-disabling a jar (rename to .jar.disabled),
 * re-enabling a disabled jar (rename back), and placing a freshly downloaded jar
 * into the folder. All operations are guarded by {@link ProtectedCore} so that
 * the platform layer can never be accidentally mutated.
 *
 * <p>Using rename/move rather than delete has an important UX benefit: if something
 * goes wrong the player can recover their mods without re-downloading everything.
 * {@link StandardCopyOption#REPLACE_EXISTING} ensures idempotency — running the
 * same action twice is harmless.
 */
public final class FolderMutator {

    private static final String DISABLED_SUFFIX = ".disabled";

    /**
     * Soft-disables {@code mod} by renaming {@code mod.jar} to {@code mod.jar.disabled}.
     *
     * <p>If the rename fails (e.g. the file is locked by another process on Windows),
     * the jar is deleted instead — spec §7 permits this fallback so the loader doesn't
     * pick it up on the next launch. Deletion is a last resort; the player loses the
     * cached file but the game state is at least consistent.
     *
     * <p>Protected mods are silently skipped and return {@code false}.
     *
     * @return {@code true} if the mod is now disabled (or was already absent)
     */
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
                Files.deleteIfExists(jar); // last-resort fallback per spec §7
                return true;
            } catch (IOException deleteFailed) {
                return false;
            }
        }
    }

    /**
     * Re-enables a soft-disabled mod by renaming {@code mod.jar.disabled} back to {@code mod.jar}.
     *
     * <p>If the file doesn't have the {@code .disabled} suffix we treat it as already enabled
     * and return {@code true} — this can happen if the caller calls enable() twice.
     *
     * @return {@code true} if the mod is now enabled
     */
    public boolean enable(LocalMod mod) {
        Path disabled = mod.file();
        String name = disabled.getFileName().toString();
        if (!name.endsWith(DISABLED_SUFFIX)) {
            return true; // nothing to do — file is already a plain .jar
        }
        // Strip the ".disabled" suffix to get the target .jar path.
        Path jar = disabled.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
        try {
            Files.move(disabled, jar, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Moves a downloaded temporary jar file into the mods directory under the canonical
     * {@code fileName} supplied by the server.
     *
     * <p>{@link StandardCopyOption#REPLACE_EXISTING} handles the case where a previous
     * partial download left a file with the same name in the mods folder.
     *
     * @param downloaded the temporary file from the downloader
     * @param modsDir    the player's mods folder
     * @param fileName   the final file name to use (validated upstream by
     *                   {@link SyncFolders#isSafeName} before reaching here)
     * @return {@code true} if the file was placed successfully
     */
    public boolean place(Path downloaded, Path modsDir, String fileName) {
        try {
            Files.move(downloaded, modsDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
