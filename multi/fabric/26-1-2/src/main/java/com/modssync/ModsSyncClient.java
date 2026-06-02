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
import com.modssync.net.SyncDonePayload;
import com.modssync.sync.SyncOrchestrator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
 * <h3>Everything happens during the configuration phase</h3>
 * <p>The whole sync — comparing against the server, cloning missing files, and scheduling
 * unauthorised extras for removal — now runs while the client is still <em>configuring</em>
 * (the "Connecting / Loading…" screen), before the world ever loads. The server keeps the
 * client parked there with a {@link ModsSyncConfigTask}, so this all completes in a single
 * connection and the download progress bar shows on the loading screen rather than after
 * the player has already spawned in.
 *
 * <p>The flow, once the server's manifests arrive:
 * <ol>
 *   <li><strong>Schedule removals.</strong> Any unauthorised extra mod, or any loose
 *       {@code .zip} resource pack the server doesn't offer (a possible X-ray pack), is
 *       queued for disabling. Packs bundled <em>inside</em> a mod jar (e.g. Simple Voice
 *       Chat's) are not standalone files, so they ride along in the jar and are never touched.</li>
 *   <li><strong>Clone what's missing.</strong> Every required file the client doesn't have
 *       is requested via {@link RequestFilesPayload}; the server streams the bytes back as
 *       {@link ModFilePayload} packets, which are written straight to disk.</li>
 *   <li><strong>Decide the outcome.</strong>
 *       If anything was downloaded or scheduled for removal, the connection is severed with a
 *       "please restart" message (the new/disabled files only take effect on a fresh launch).
 *       If the client was already fully in sync, it sends {@link SyncDonePayload} and the
 *       server lets it into the world.</li>
 * </ol>
 *
 * <h3>On-exit file-lock bypass</h3>
 * <p>Fabric's mod loader holds every loaded {@code .jar} open for the entire session, so it
 * is impossible to rename/remove a mod file while the game is running. The
 * {@link ClientLifecycleEvents#CLIENT_STOPPING} listener hands the mod-jar rename list to
 * {@link ExternalDisabler}, which launches a tiny headless JVM ({@link DisablerMain}) that
 * polls until the game has fully exited, then performs the renames. Pack files aren't held
 * open by the loader, so {@link ModsSyncPreLaunch} renames those at the next launch.
 */
public class ModsSyncClient implements ClientModInitializer {

    /**
     * Number of cloned files still in flight for this session. Decremented each time a
     * {@link ModFilePayload} arrives; when it hits zero every requested file has been
     * written and we sever the connection to force the required reload.
     */
    private static final AtomicInteger DOWNLOADS_REMAINING = new AtomicInteger(0);

    /** Total files requested this session (the download progress screen's denominator). */
    private static final AtomicInteger DOWNLOAD_TOTAL = new AtomicInteger(0);

    /** The server's mod manifest, stashed when it arrives so the pack handler can run the full diff. */
    private static volatile List<ServerMod> pendingMods;

    /** The server's pack manifest, stashed when it arrives (always after the mod manifest). */
    private static volatile List<PackFile> pendingPacks;

    /** Resource-pack file names the current server offers — kept for the mid-session guard. */
    private static volatile Set<String> serverPackNames = Set.of();

    /** True while connected to a ModsSync server, so the reload guard knows to enforce. */
    private static volatile boolean packEnforceActive = false;

    /** The current play connection, captured at JOIN so the mid-session guard can disconnect. */
    private static volatile Connection currentConnection;

    /** Throttle counter for the per-tick pack guard — we only re-check every {@link #GUARD_INTERVAL_TICKS} ticks. */
    private static int guardTickCounter = 0;

    /** ~2 seconds at 20 TPS. Often enough to catch a mid-session pack change, cheap enough to ignore. */
    private static final int GUARD_INTERVAL_TICKS = 40;

    /** Live counters read by {@link com.modssync.client.DownloadScreen} each frame. */
    public static int downloadsRemaining() {
        return DOWNLOADS_REMAINING.get();
    }

    public static int downloadTotal() {
        return DOWNLOAD_TOTAL.get();
    }

    @Override
    public void onInitializeClient() {
        // --- Configuration phase receivers ---
        // The mod manifest arrives first and is just stashed. The pack manifest arrives second
        // (the server sends them in that order on one ordered connection) and triggers the full
        // sync, by which point we have both halves of the picture.
        ClientConfigurationNetworking.registerGlobalReceiver(ManifestPayload.TYPE, (payload, context) ->
                pendingMods = payload.mods());

        ClientConfigurationNetworking.registerGlobalReceiver(PackManifestPayload.TYPE, (payload, context) -> {
            pendingPacks = payload.packs();
            runSync(context);
        });

        // Receive and write cloned file bytes (still in the configuration phase).
        ClientConfigurationNetworking.registerGlobalReceiver(ModFilePayload.TYPE, (payload, context) -> {
            // The server sends a sentinel "end" packet after each folder's batch; nothing to write or count.
            if (payload.isEnd()) {
                return;
            }
            Minecraft mc = context.client();
            String folder = payload.folder();
            if (SyncFolders.isAllowed(folder) && SyncFolders.isSafeName(payload.fileName()) && payload.data().length > 0) {
                try {
                    Path dir = FabricLoader.getInstance().getGameDir().resolve(folder);
                    Files.createDirectories(dir);
                    Files.write(dir.resolve(payload.fileName()), payload.data());
                    ModsSync.LOGGER.info("ModsSync cloned {}/{} ({} bytes)", folder, payload.fileName(), payload.data().length);
                } catch (Exception e) {
                    ModsSync.LOGGER.warn("ModsSync failed to write {}/{}: {}", folder, payload.fileName(), e.toString());
                }
            }
            // The vanilla configuration flow shows its own screen; keep our progress bar in front
            // so the player sees the (potentially large) transfer instead of a frozen "Loading…".
            mc.execute(() -> {
                if (!(mc.screen instanceof com.modssync.client.DownloadScreen)) {
                    mc.setScreen(new com.modssync.client.DownloadScreen());
                }
            });
            // Every non-sentinel packet counts down, even ones we skipped writing, so a single
            // bad name can't leave the counter stuck above zero and hang the client forever.
            if (DOWNLOADS_REMAINING.decrementAndGet() == 0) {
                disconnectToRestart(mc, context.packetContext().get(PacketContext.CONNECTION));
            }
        });

        // --- CLIENT_STOPPING: hand mod-jar renames off to the external disabler ---
        // Mod jars can't be renamed while the game is running because the loader holds them
        // open. We split the pending-disable list into mod entries (need the external process)
        // and everything else (packs, handled at preLaunch). Only the mod portion is forwarded.
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
                    // Remove the forwarded mod entries; non-mod entries stay for ModsSyncPreLaunch.
                    store.replace(rest);
                }
            } catch (Throwable ignored) {
            }
        });

        // --- Play phase: arm the mid-session guard ---
        // Compliant clients (already fully synced) get here. The sync itself is already done;
        // all we do now is remember the connection so the resource-reload guard can kick the
        // player if they apply a disallowed pack mid-session.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.hasSingleplayerServer()) {
                return;
            }
            packEnforceActive = true;
            currentConnection = handler.getConnection();
        });

        // Clear the guard and per-connection state when we leave the server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            packEnforceActive = false;
            serverPackNames = Set.of();
            currentConnection = null;
            pendingMods = null;
            pendingPacks = null;
        });

        // --- Mid-session guard (issue #3): re-check packs whenever resources reload ---
        // If the player adds & applies a disallowed resource pack while in-game (which triggers
        // a resource reload), catch it here and kick — the config-time sync can't see later changes.
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public net.minecraft.resources.Identifier getFabricId() {
                        return net.minecraft.resources.Identifier.fromNamespaceAndPath("modssync", "pack_guard");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        checkPacksMidSession();
                    }
                });

        // --- Mid-session guard, part 2: poll periodically ---
        // A resource reload only fires when packs are *applied*, and not always reliably for every
        // toggle. Polling the selected-pack set a few times a second guarantees we notice a player
        // enabling a disallowed pack even if no reload event reaches us.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!packEnforceActive) {
                return;
            }
            if (++guardTickCounter >= GUARD_INTERVAL_TICKS) {
                guardTickCounter = 0;
                checkPacksMidSession();
            }
        });
    }

    // ---- Configuration-phase sync orchestration ----

    /**
     * Runs the full sync once both manifests have arrived. Invoked from the pack-manifest
     * receiver (which fires after the mod-manifest receiver), so {@link #pendingMods} and
     * {@link #pendingPacks} are both populated.
     *
     * <p>The heavy part (scanning the local mods folder, diffing, hashing pack sizes) runs on
     * a worker thread so we never stall netty's event loop — a big modpack means reading many
     * jar manifests off disk. Network sends and screen changes are dispatched back to the
     * appropriate threads.
     */
    private static void runSync(ClientConfigurationNetworking.Context context) {
        Minecraft client = context.client();
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        List<ServerMod> mods = pendingMods != null ? pendingMods : List.of();
        List<PackFile> packs = pendingPacks != null ? pendingPacks : List.of();

        // Singleplayer integrated server: there is nothing to enforce (client and server share
        // one mods folder). We MUST still complete the task, or the world would hang on loading.
        if (client.hasSingleplayerServer()) {
            sendDone();
            return;
        }

        // Remember the offered packs now so the mid-session guard is armed correctly at JOIN.
        serverPackNames = offeredPackNames(packs);

        CompletableFuture.runAsync(() -> {
            // 1. Queue unauthorised extras (extra mods + loose packs the server doesn't offer).
            List<PendingDisableStore.Entry> extras = new ArrayList<>();
            extras.addAll(extraModEntries(mods));
            extras.addAll(extraPackEntries(packs));
            if (!extras.isEmpty()) {
                pendingStore().add(extras);
                ModsSync.LOGGER.info("ModsSync: {} unauthorized file(s) present -> scheduled for removal.", extras.size());
            }

            // 2. Work out what's missing locally and needs cloning from the server.
            List<String> modNames = missingModNames(mods);
            List<String> packNames = missingPackNames(packs);
            int total = modNames.size() + packNames.size();
            DOWNLOAD_TOTAL.set(total);
            DOWNLOADS_REMAINING.set(total);

            // 3a. Nothing to download — decide between "let me in" and "restart to apply removals".
            if (total == 0) {
                if (extras.isEmpty()) {
                    sendDone(); // fully compliant: ask the server to release us into the world
                } else {
                    // Removals are queued but need a restart to take effect; don't enter the world.
                    disconnectToRestart(client, connection);
                }
                return;
            }

            // 3b. Files to clone — show the progress bar and request them. The ModFilePayload
            // receiver writes each file, counts down, and severs the connection when done.
            ModsSync.LOGGER.info("ModsSync: cloning {} mod(s) + {} pack(s) during configuration, then requiring restart.",
                    modNames.size(), packNames.size());
            client.execute(() -> client.setScreen(new com.modssync.client.DownloadScreen()));
            if (!modNames.isEmpty()) {
                ClientConfigurationNetworking.send(new RequestFilesPayload(SyncFolders.MODS, modNames));
            }
            if (!packNames.isEmpty()) {
                ClientConfigurationNetworking.send(new RequestFilesPayload(SyncFolders.RESOURCEPACKS, packNames));
            }
        });
    }

    /** Tells the server we're fully in sync, so it completes its config task and lets us in. */
    private static void sendDone() {
        ClientConfigurationNetworking.send(new SyncDonePayload());
    }

    /**
     * Severs the connection and shows the "please restart" screen.
     *
     * <p>Unlike the play phase, dropping the raw {@link Connection} during the configuration
     * phase does not move the client off whatever screen it's on — it would just freeze on the
     * download bar at 100%. So we explicitly navigate to a {@link net.minecraft.client.gui.screens.DisconnectedScreen}
     * carrying the restart message, with the title screen as its parent. Both run on the client
     * thread so the order is deterministic.
     */
    private static void disconnectToRestart(Minecraft mc, Connection connection) {
        Component reason = Component.translatable("modssync.restart");
        mc.execute(() -> {
            if (connection != null) {
                connection.disconnect(reason);
            }
            mc.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                    new net.minecraft.client.gui.screens.TitleScreen(),
                    Component.translatable("modssync.screen.restart.title"),
                    reason));
        });
    }

    // ---- Enforcement helpers (compute only — they never touch the connection) ----

    /**
     * Returns pending-disable entries for client mods the server doesn't list. Uses
     * {@link SyncOrchestrator#computePlan}, whose {@code toDisable} set is exactly the
     * local-only extras (protected platform mods are already excluded there).
     */
    private static List<PendingDisableStore.Entry> extraModEntries(List<ServerMod> serverMods) {
        if (serverMods == null) {
            return List.of();
        }
        SyncPlan plan = buildOrchestrator().computePlan(serverMods);
        List<PendingDisableStore.Entry> extras = new ArrayList<>();
        for (LocalMod m : plan.toDisable()) {
            extras.add(new PendingDisableStore.Entry(SyncFolders.MODS, m.file().getFileName().toString()));
        }
        return extras;
    }

    /**
     * Returns pending-disable entries for any standalone {@code .zip} in the client's
     * {@code resourcepacks/} folder that the server doesn't offer (a possible X-ray pack).
     * Only loose {@code .zip} files count — packs bundled inside a mod jar are never touched.
     */
    private static List<PendingDisableStore.Entry> extraPackEntries(List<PackFile> serverPacks) {
        List<PendingDisableStore.Entry> extras = new ArrayList<>();
        if (serverPacks == null) {
            return extras;
        }
        Set<String> offered = offeredPackNames(serverPacks);
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
        return extras;
    }

    /** Resource-pack file names the server offers (used by the clone step + mid-session guard). */
    private static Set<String> offeredPackNames(List<PackFile> serverPacks) {
        Set<String> names = new HashSet<>();
        if (serverPacks != null) {
            for (PackFile pf : serverPacks) {
                if (SyncFolders.RESOURCEPACKS.equals(pf.folder())) {
                    names.add(pf.fileName());
                }
            }
        }
        return names;
    }

    /** Prefix Minecraft gives file-based resource packs in the {@code resourcepacks/} folder. */
    private static final String FILE_PACK_PREFIX = "file/";

    /**
     * Issue #3 guard: detect a disallowed resource pack mid-session and kick. Invoked both on
     * every resource reload and on a periodic client tick (see {@code onInitializeClient}).
     *
     * <p>Two complementary checks, because each alone has a blind spot:
     * <ul>
     *   <li><strong>Applied packs.</strong> We read the pack repository's currently
     *       <em>selected</em> packs. Any {@code file/…} pack (a loose {@code .zip} in the
     *       player's {@code resourcepacks/} folder) the server didn't offer means the player
     *       turned on a cheat pack (e.g. X-ray) — kick. Mod-bundled packs (Voice Chat) are not
     *       {@code file/} packs, so they're never flagged.</li>
     *   <li><strong>Files on disk.</strong> Any non-offered {@code .zip} sitting in the folder
     *       is also scheduled for removal, so it's gone after the restart even if it wasn't the
     *       one actively applied.</li>
     * </ul>
     *
     * <p>We do NOT require the server's own packs to be <em>selected</em> — they're cloned as
     * files, not force-applied, so a compliant player legitimately has them deselected.
     */
    private static void checkPacksMidSession() {
        if (!packEnforceActive || currentConnection == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            return;
        }

        boolean violation = false;

        // Check 1: is a disallowed file-pack currently APPLIED?
        try {
            for (String id : mc.getResourcePackRepository().getSelectedIds()) {
                if (id.startsWith(FILE_PACK_PREFIX)) {
                    String name = id.substring(FILE_PACK_PREFIX.length());
                    if (!serverPackNames.contains(name)) {
                        violation = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Check 2: schedule any non-offered .zip in the folder for removal on restart.
        List<PendingDisableStore.Entry> extras = new ArrayList<>();
        Path rp = FabricLoader.getInstance().getGameDir().resolve(SyncFolders.RESOURCEPACKS);
        if (Files.isDirectory(rp)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rp, "*.zip")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (!serverPackNames.contains(name)) {
                        extras.add(new PendingDisableStore.Entry(SyncFolders.RESOURCEPACKS, name));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Nothing applied and nothing extra on disk → all good.
        if (!violation && extras.isEmpty()) {
            return;
        }
        if (!extras.isEmpty()) {
            pendingStore().add(extras);
        }
        ModsSync.LOGGER.info("ModsSync: unauthorized resourcepack detected mid-session (applied={}, extra files={}) -> disconnecting.",
                violation, extras.size());
        Connection c = currentConnection;
        mc.execute(() -> {
            if (c != null) {
                c.disconnect(Component.translatable("modssync.unauthorized"));
            }
        });
    }

    // ---- Missing-file computation ----

    /**
     * Returns the file names of server-required mods that are absent locally. Uses
     * {@link SyncOrchestrator#computePlan} — the {@code toDownload} list is the set of
     * server mods with no local counterpart.
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
     * Returns the file names of server resource packs that are missing or have a different
     * size locally. Size is a lightweight proxy for content change — good enough for packs
     * whose server copy is authoritative.
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
