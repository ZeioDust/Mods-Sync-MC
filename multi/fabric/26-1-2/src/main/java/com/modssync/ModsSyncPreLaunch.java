package com.modssync;

import com.modssync.core.PendingDisableStore;
import com.modssync.core.SyncFolders;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-launch entrypoint that runs as early as possible — before any mod or resource-pack
 * has been loaded into the JVM. This is the right moment to rename pack files that were
 * flagged as unauthorised on a previous server connection.
 *
 * <h3>Why packs here, mods elsewhere?</h3>
 * <p>By the time {@code onPreLaunch()} is called, Fabric's mod loader has already opened
 * every {@code .jar} in the {@code mods/} folder and holds the file handles for the
 * entire session. Renaming a locked jar while the game is running will fail on Windows
 * (and silently do nothing useful on most platforms). Mod-jar disabling is therefore
 * deferred to {@link ExternalDisabler}: a headless helper JVM that runs <em>after</em>
 * the game exits and the locks are released.
 *
 * <p>Resource-pack and shader-pack files are <strong>not</strong> locked at preLaunch —
 * Minecraft hasn't started rendering yet — so we can safely rename them here.
 *
 * <h3>Failure safety</h3>
 * <p>Any exception is caught at the outermost level so this code can never prevent the
 * game from launching. Entries that can't be renamed (unexpected lock, permission error)
 * are kept in the store and retried on the next launch.
 */
public class ModsSyncPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path pendingFile = FabricLoader.getInstance().getConfigDir().resolve("modssync-pending-disable.json");
            PendingDisableStore store = new PendingDisableStore(pendingFile);
            List<PendingDisableStore.Entry> all = store.list();
            List<PendingDisableStore.Entry> remaining = new ArrayList<>();
            int disabled = 0;
            for (PendingDisableStore.Entry e : all) {
                if (!SyncFolders.isAllowed(e.folder()) || !SyncFolders.isSafeName(e.fileName())) {
                    continue; // drop malformed/invalid entries silently
                }
                // Mod jars are already opened/locked by the loader before preLaunch — they are
                // disabled by the external helper after the game exits, NOT here. Only packs
                // (not yet loaded) can be safely renamed at preLaunch. Leave mod entries in the
                // store; ExternalDisabler will consume them at CLIENT_STOPPING.
                if (!SyncFolders.PACK_FOLDERS.contains(e.folder())) {
                    remaining.add(e);
                    continue;
                }
                Path src = gameDir.resolve(e.folder()).resolve(e.fileName());
                Path dst = src.resolveSibling(e.fileName() + ".disabled");
                try {
                    if (Files.isRegularFile(src)) {
                        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        disabled++;
                    }
                    // If src is already gone it was handled previously — drop the entry.
                } catch (Exception ex) {
                    remaining.add(e); // rename failed (unlikely but keep for retry next launch)
                }
            }
            store.replace(remaining);
            if (disabled > 0) {
                System.out.println("[ModsSync] preLaunch disabled " + disabled + " extra pack(s) to match the server.");
            }
        } catch (Throwable ignored) {
            // never block game launch
        }
    }
}
