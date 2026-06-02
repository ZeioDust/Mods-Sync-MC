package com.modssync;

import com.modssync.client.ExternalDisabler;
import com.modssync.core.DiffEngine;
import com.modssync.core.FolderMutator;
import com.modssync.core.LocalInventory;
import com.modssync.core.PendingDisableStore;
import com.modssync.core.SnapshotStore;
import com.modssync.core.SyncFolders;
import com.modssync.model.LocalMod;
import com.modssync.model.PackFile;
import com.modssync.model.ServerMod;
import com.modssync.model.SyncPlan;
import com.modssync.net.ManifestPayload;
import com.modssync.net.ModFilePayload;
import com.modssync.net.PackManifestPayload;
import com.modssync.net.RequestFilesPayload;
import com.modssync.sync.SyncOrchestrator;
import com.modssync.sync.SyncResultHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CLIENT-SIDE entrypoint for ModsSync (implements {@link ClientModInitializer}).
 *
 * <p>The synchronisation flow has two distinct phases, which run at different points
 * in the connection lifecycle:
 *
 * <h3>1. Configuration phase — enforcement gate</h3>
 * <p>While the server is still negotiating the connection (before the player enters
 * the world), the server sends {@link ManifestPayload} and {@link PackManifestPayload}.
 * The receivers here compare those lists against what the client actually has loaded.
 * If the client carries <em>any</em> unauthorised extra mod or extra resource-pack,
 * the offending files are queued for removal and the connection is immediately severed.
 * The player must fully restart Minecraft for the pending-disable logic to clean them up.
 * This is what prevents someone from playing a session with a cheat mod (minimap, X-ray,
 * etc.) still active — they are kicked <em>before</em> the world loads.
 *
 * <h3>2. Play phase — cloning missing files</h3>
 * <p>Clients that pass the gate (no unauthorised extras) enter the world normally.
 * On the JOIN event we compare the server manifest against the local install: any
 * required file that is simply absent (the player hasn't downloaded it yet) is
 * requested from the server via {@link RequestFilesPayload} and the raw bytes are
 * written to disk as they arrive. Because these files aren't loaded by the current
 * JVM instance, a restart is mandatory after cloning — the client is kicked with a
 * translatable "please restart" message as soon as the last byte lands.
 *
 * <h3>On-exit file-lock bypass</h3>
 * <p>Fabric's mod loader holds every loaded {@code .jar} open for the entire session,
 * so it is impossible to rename/remove a mod file while the game is running. The
 * {@link ClientLifecycleEvents#CLIENT_STOPPING} listener therefore hands the mod-jar
 * rename list to {@link ExternalDisabler}, which launches a tiny headless JVM
 * ({@link DisablerMain}) that polls until the game has fully exited, then performs
 * the renames. Pack files (resourcepacks, shaderpacks) are handled differently —
 * they are not held open by the loader, so {@link ModsSyncPreLaunch} can rename them
 * at the start of the <em>next</em> launch without needing an external process.
 */
public class ModsSyncClient implements ClientModInitializer {

    /**
     * Number of cloned files still in flight for this session.
     * Decremented each time a {@link ModFilePayload} arrives; when it hits zero
     * every requested file has been written and we kick the client to trigger a reload.
     */
    private static final AtomicInteger DOWNLOADS_REMAINING = new AtomicInteger(0);

    @Override
    public void onInitializeClient() {
        // --- Configuration phase receivers ---
        // These fire during the handshake, before ClientPlayConnectionEvents.JOIN.
        // We store the manifests in SyncResultHolder so the JOIN handler can use them later,
        // then immediately check for unauthorized extras.
        ClientConfigurationNetworking.registerGlobalReceiver(ManifestPayload.TYPE, (payload, context) -> {
            SyncResultHolder.onManifestReceived(payload.mods());
            gateOnExtraMods(payload.mods(), context);
        });
        ClientConfigurationNetworking.registerGlobalReceiver(PackManifestPayload.TYPE, (payload, context) -> {
            SyncResultHolder.onPacksReceived(payload.packs());
            gateOnExtraPacks(payload.packs(), context);
        });

        // --- CLIENT_STOPPING: hand mod-jar renames off to the external disabler ---
        // Mod jars can't be renamed while the game is running because the loader holds
        // them open. We separate the pending-disable list into mod entries (need external
        // process) and everything else (packs, handled at preLaunch). Only the mod portion
        // is forwarded here; the remainder stays in the store for ModsSyncPreLaunch.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                PendingDisableStore store = pendingStore();
                List<PendingDisableStore.Entry> all = store.list();
                List<PendingDisableStore.Entry> mods = new ArrayList<>();
                List<PendingDisableStore.Entry> rest = new ArrayList<>();
                for (PendingDisableStore.Entry e : all) {
                    (SyncFolders.MODS.equals(e.folder()) ? mods : rest).add(e);
                }
                if (!mods.isEmpty()) {
                    // Launch the headless helper (see ExternalDisabler) — it outlives this JVM.
                    ExternalDisabler.scheduleAfterExit(FabricLoader.getInstance().getGameDir(), mods);
                    // Remove the forwarded mod entries from the store; non-mod entries remain
                    // for ModsSyncPreLaunch to handle on the next game launch.
                    store.replace(rest);
                }
            } catch (Throwable ignored) {
            }
        });

        // --- Play phase: clone missing files, then require a restart ---
        // Only clients that cleared the enforcement gate in the config phase get here.
        // Singleplayer sessions are skipped entirely — there is no server to sync with.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.hasSingleplayerServer()) {
                return;
            }
            // Consume the manifests that were stashed during the config phase.
            List<ServerMod> serverMods = SyncResultHolder.takePending();
            List<PackFile> serverPacks = SyncResultHolder.takePendingPacks();
            // Run the diff and file requests on a worker thread to avoid blocking the render thread.
            CompletableFuture.runAsync(() -> {
                List<String> modNames = missingModNames(serverMods);
                List<String> packNames = missingPackNames(serverPacks);
                int total = modNames.size() + packNames.size();
                DOWNLOADS_REMAINING.set(total);
                if (total == 0) {
                    return; // fully in sync — player can stay in the world without restarting
                }
                ModsSync.LOGGER.info("ModsSync: missing {} mod(s) + {} pack(s) -> cloning, then requiring restart.",
                        modNames.size(), packNames.size());
                if (!modNames.isEmpty()) {
                    ClientPlayNetworking.send(new RequestFilesPayload(SyncFolders.MODS, modNames));
                }
                if (!packNames.isEmpty()) {
                    ClientPlayNetworking.send(new RequestFilesPayload(SyncFolders.RESOURCEPACKS, packNames));
                }
            });
        });

        // --- Receive and write cloned file bytes ---
        ClientPlayNetworking.registerGlobalReceiver(ModFilePayload.TYPE, (payload, context) -> {
            // The server sends a sentinel "end" packet after all requested files; nothing to write.
            if (payload.isEnd()) {
                return;
            }
            String folder = payload.folder();
            if (!SyncFolders.isAllowed(folder) || !SyncFolders.isSafeName(payload.fileName())) {
                return;
            }
            if (payload.data().length > 0) {
                try {
                    Path dir = FabricLoader.getInstance().getGameDir().resolve(folder);
                    Files.createDirectories(dir);
                    Files.write(dir.resolve(payload.fileName()), payload.data());
                    ModsSync.LOGGER.info("ModsSync cloned {}/{} ({} bytes)", folder, payload.fileName(), payload.data().length);
                } catch (Exception e) {
                    ModsSync.LOGGER.warn("ModsSync failed to write {}/{}: {}", folder, payload.fileName(), e.toString());
                }
            }
            // The cloned files exist on disk but aren't loaded into this JVM session —
            // a restart is required for them to take effect. Kick as soon as the counter
            // reaches zero so the player isn't left in a partially-synced state.
            if (DOWNLOADS_REMAINING.decrementAndGet() == 0) {
                Connection connection = context.packetContext().get(PacketContext.CONNECTION);
                context.client().execute(() -> {
                    if (connection != null) {
                        connection.disconnect(Component.translatable("modssync.restart"));
                    }
                });
            }
        });
    }

    // ---- Configuration-phase enforcement gate ----

    /**
     * Checks whether the client has any mods that aren't in the server's manifest.
     * If so, schedules them for disabling and disconnects.
     *
     * <p>The diff is done via {@link SyncOrchestrator#computePlan}, which compares
     * the server list against the local inventory and returns the {@code toDisable}
     * set for any local-only extras.
     */
    private static void gateOnExtraMods(List<ServerMod> serverMods, ClientConfigurationNetworking.Context context) {
        if (serverMods == null || context.client().hasSingleplayerServer()) {
            return;
        }
        SyncPlan plan = buildOrchestrator().computePlan(serverMods);
        if (plan.toDisable().isEmpty()) {
            return; // no unauthorized mods
        }
        List<PendingDisableStore.Entry> extras = new ArrayList<>();
        for (LocalMod m : plan.toDisable()) {
            extras.add(new PendingDisableStore.Entry(SyncFolders.MODS, m.file().getFileName().toString()));
        }
        pendingStore().add(extras);
        ModsSync.LOGGER.info("ModsSync: {} unauthorized mod(s) present -> disconnecting to enforce restart.", extras.size());
        disconnect(context);
    }

    /**
     * Checks whether the client has any resource-pack zip files that the server
     * didn't include in its pack manifest.
     *
     * <p>Only {@code .zip} files in the {@code resourcepacks/} folder are checked;
     * folder-style packs and shaderpacks use a separate code path elsewhere.
     * Extras are queued for disabling and the connection is severed.
     */
    private static void gateOnExtraPacks(List<PackFile> serverPacks, ClientConfigurationNetworking.Context context) {
        if (serverPacks == null || context.client().hasSingleplayerServer()) {
            return;
        }
        // Build a set of pack file names the server considers legitimate.
        Set<String> offered = new HashSet<>();
        for (PackFile pf : serverPacks) {
            if (SyncFolders.RESOURCEPACKS.equals(pf.folder())) {
                offered.add(pf.fileName());
            }
        }
        List<PendingDisableStore.Entry> extras = new ArrayList<>();
        Path rp = FabricLoader.getInstance().getGameDir().resolve(SyncFolders.RESOURCEPACKS);
        if (Files.isDirectory(rp)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rp, "*.zip")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (!offered.contains(name)) {
                        extras.add(new PendingDisableStore.Entry(SyncFolders.RESOURCEPACKS, name));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (extras.isEmpty()) {
            return;
        }
        pendingStore().add(extras);
        ModsSync.LOGGER.info("ModsSync: {} unauthorized resourcepack(s) present -> disconnecting to enforce restart.", extras.size());
        disconnect(context);
    }

    /** Disconnects during the configuration phase with a localised "unauthorised" message. */
    private static void disconnect(ClientConfigurationNetworking.Context context) {
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        Minecraft mc = context.client();
        mc.execute(() -> {
            if (connection != null) {
                connection.disconnect(Component.translatable("modssync.unauthorized"));
            }
        });
    }

    // ---- Play-phase helpers (compliant clients only) ----

    /**
     * Returns the file names of server-required mods that are absent locally.
     * Uses {@link SyncOrchestrator#computePlan} — the {@code toDownload} list is the
     * set of server mods with no local counterpart.
     */
    private static List<String> missingModNames(List<ServerMod> serverMods) {
        if (serverMods == null) {
            return List.of();
        }
        SyncPlan plan = buildOrchestrator().computePlan(serverMods);
        return plan.toDownload().stream()
                .map(ServerMod::fileName)
                .filter(n -> n != null && !n.isBlank())
                .toList();
    }

    /**
     * Returns the file names of server resource/shader packs that are missing or
     * have changed size locally. Size comparison is a lightweight proxy for content
     * change — good enough for packs whose server copy is authoritative.
     */
    private static List<String> missingPackNames(List<PackFile> serverPacks) {
        if (serverPacks == null || serverPacks.isEmpty()) {
            return List.of();
        }
        Path gameDir = FabricLoader.getInstance().getGameDir();
        List<String> need = new ArrayList<>();
        for (PackFile pf : serverPacks) {
            if (!SyncFolders.RESOURCEPACKS.equals(pf.folder()) || !SyncFolders.isSafeName(pf.fileName())) {
                continue;
            }
            Path local = gameDir.resolve(pf.folder()).resolve(pf.fileName());
            boolean missing = !Files.isRegularFile(local);
            if (!missing) {
                try {
                    missing = Files.size(local) != pf.size();
                } catch (Exception e) {
                    missing = true;
                }
            }
            if (missing) {
                need.add(pf.fileName());
            }
        }
        return need;
    }

    /** Returns the persistent store of files queued for disabling on next start/exit. */
    private static PendingDisableStore pendingStore() {
        return new PendingDisableStore(
                FabricLoader.getInstance().getConfigDir().resolve("modssync-pending-disable.json"));
    }

    /**
     * Builds a loader-agnostic {@link SyncOrchestrator} wired to the local mods directory
     * and the home-snapshot config file. It only computes plans and renames jars locally;
     * missing files are cloned from the server over the network, not downloaded here.
     */
    public static SyncOrchestrator buildOrchestrator() {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path snapshot = FabricLoader.getInstance().getConfigDir().resolve("modssync-home.json");
        return new SyncOrchestrator(modsDir, new LocalInventory(), new DiffEngine(),
                new FolderMutator(), new SnapshotStore(snapshot));
    }
}
