package com.modssync;

import com.modssync.core.ModJsonParser;
import com.modssync.core.ProtectedCore;
import com.modssync.core.SyncFolders;
import com.modssync.model.ModRef;
import com.modssync.model.PackFile;
import com.modssync.model.ServerMod;
import com.modssync.net.ManifestPayload;
import com.modssync.net.ModFilePayload;
import com.modssync.net.PackManifestPayload;
import com.modssync.net.RequestFilesPayload;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SERVER-SIDE entrypoint for ModsSync (implements {@link ModInitializer}).
 *
 * <p>Responsibilities at a glance:
 * <ol>
 *   <li>Register the four network payload channels used by the protocol.</li>
 *   <li>During the Fabric <em>configuration</em> phase (before the player enters the world),
 *       reject clients that do not have ModsSync installed, then push the list of
 *       required mods ({@link ManifestPayload}) and required packs ({@link PackManifestPayload})
 *       to every connecting client.</li>
 *   <li>During the <em>play</em> phase, serve raw file bytes back to compliant clients
 *       that asked for files they are missing ({@link RequestFilesPayload} →
 *       {@link ModFilePayload}).</li>
 * </ol>
 *
 * <p>The config-phase advertisement is intentionally done early so the enforcement gate
 * in {@link ModsSyncClient} can kick cheaters <em>before</em> they ever enter a world.
 */
public class ModsSync implements ModInitializer {
    public static final String MODID = "modssync";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Hard cap on a single file transfer to prevent OOM on the client. 100 MB should cover any realistic mod. */
    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;

    @Override
    public void onInitialize() {
        // Register all payload types before any networking event fires.
        // Configuration-bound payloads are clientbound only (server → client during handshake).
        // The file-request channel is play-phase: client asks, server streams bytes back.
        PayloadTypeRegistry.clientboundConfiguration().register(ManifestPayload.TYPE, ManifestPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(PackManifestPayload.TYPE, PackManifestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestFilesPayload.TYPE, RequestFilesPayload.STREAM_CODEC);
        // ModFilePayload can be large (full mod jar), so it uses the "large" overload that lifts the default size limit.
        PayloadTypeRegistry.clientboundPlay().registerLarge(ModFilePayload.TYPE, ModFilePayload.STREAM_CODEC, MAX_FILE_BYTES);

        // --- Configuration phase: advertise what the client must mirror ---
        // This runs once per connecting client, before they enter the world.
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            // If the client doesn't have ModsSync it won't recognise our channel at all.
            // Kick early with a clear message rather than letting them fall through to a confusing timeout.
            if (!ServerConfigurationNetworking.canSend(handler, ManifestPayload.TYPE)) {
                handler.disconnect(net.minecraft.network.chat.Component.literal(
                        "This server requires the ModsSync mod. Install it to join."));
                LOGGER.info("ModsSync rejected a client without ModsSync installed");
                return;
            }
            List<ServerMod> mods = buildModManifest();
            ServerConfigurationNetworking.send(handler, new ManifestPayload(mods));
            List<PackFile> packs = buildPackManifest();
            ServerConfigurationNetworking.send(handler, new PackManifestPayload(packs));
            LOGGER.info("ModsSync advertised {} mods and {} packs to a configuring client", mods.size(), packs.size());
        });

        // --- Play phase: serve file bytes on demand ---
        // Only compliant clients (those that passed the config gate) reach this point.
        // They send a RequestFilesPayload listing file names they need; we stream each one back.
        ServerPlayNetworking.registerGlobalReceiver(RequestFilesPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String folder = payload.folder();
            // Only serve from explicitly whitelisted folders (mods, resourcepacks, shaderpacks).
            // This guards against a crafted packet trying to read arbitrary server files.
            if (SyncFolders.isAllowed(folder)) {
                for (String name : payload.fileNames()) {
                    byte[] data = readFileBytes(folder, name);
                    ServerPlayNetworking.send(player, new ModFilePayload(folder, name, data == null ? new byte[0] : data));
                }
            }
            // Always send the sentinel "end" packet so the client knows the stream is complete
            // and can count down its download counter correctly.
            ServerPlayNetworking.send(player, ModFilePayload.end());
            LOGGER.info("ModsSync streamed {} {} file(s) to {}", payload.fileNames().size(), folder, player.getName().getString());
        });
    }

    private static Path folderPath(String folder) {
        return FabricLoader.getInstance().getGameDir().resolve(folder);
    }

    /**
     * Builds the list of mods the client should mirror.
     *
     * <p>Scans every {@code .jar} in the server's {@code mods/} folder, reads its
     * {@code fabric.mod.json}, and excludes:
     * <ul>
     *   <li>Mods whose {@code environment} is {@code "server"} — clients don't need those.</li>
     *   <li>Platform/loader mods listed in {@link ProtectedCore} (Fabric API, Fabric Loader,
     *       ModsSync itself, etc.) — these are not something we want to push around.</li>
     * </ul>
     */
    private static List<ServerMod> buildModManifest() {
        List<ServerMod> result = new ArrayList<>();
        Path dir = folderPath(SyncFolders.MODS);
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                String json = readFabricModJson(jar);
                if (json == null) {
                    continue;
                }
                ModRef ref = ModJsonParser.parse(json);
                if (ref == null) {
                    continue;
                }
                // Skip server-only mods — the client doesn't load them.
                if ("server".equalsIgnoreCase(ModJsonParser.environment(json))) {
                    continue;
                }
                // Skip protected platform mods (loader, API, ModsSync itself).
                if (ProtectedCore.isProtected(ref.modId())) {
                    continue;
                }
                result.add(new ServerMod(ref.modId(), ref.version(),
                        jar.getFileName().toString(), null, null, null, null));
            }
        } catch (Exception e) {
            LOGGER.warn("ModsSync failed to scan mods folder: {}", e.toString());
        }
        return result;
    }

    /**
     * Builds the list of resource/shader packs the client should mirror.
     *
     * <p>Only regular files are included — unpacked folder-style packs are skipped
     * because we can't stream a directory as a single payload.
     * File size is included so the client can detect stale copies without hashing.
     */
    private static List<PackFile> buildPackManifest() {
        List<PackFile> result = new ArrayList<>();
        for (String folder : SyncFolders.PACK_FOLDERS) {
            Path dir = folderPath(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream) {
                    if (!Files.isRegularFile(p)) {
                        continue; // only zip/file packs are cloneable, not unpacked folders
                    }
                    result.add(new PackFile(folder, p.getFileName().toString(), Files.size(p)));
                }
            } catch (Exception e) {
                LOGGER.warn("ModsSync failed to scan {} folder: {}", folder, e.toString());
            }
        }
        return result;
    }

    /**
     * Reads raw bytes for {@code fileName} inside {@code folder}.
     *
     * <p>Both the folder and the file name are validated before touching the filesystem:
     * {@link SyncFolders#isAllowed} ensures we only serve from whitelisted directories, and
     * {@link SyncFolders#isSafeName} rejects names containing path separators or {@code ..}.
     * The {@code p.getParent().equals(dir)} check is a final defence against symlink escapes.
     *
     * @return the file's bytes, or {@code null} if missing/invalid/unreadable.
     */
    private static byte[] readFileBytes(String folder, String fileName) {
        if (!SyncFolders.isAllowed(folder) || !SyncFolders.isSafeName(fileName)) {
            return null;
        }
        try {
            Path dir = folderPath(folder);
            Path p = dir.resolve(fileName);
            if (Files.isRegularFile(p) && p.getParent().equals(dir)) {
                return Files.readAllBytes(p);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Extracts and returns the text of {@code fabric.mod.json} from inside a mod jar.
     * Returns {@code null} if the jar is absent, malformed, or doesn't contain the descriptor.
     */
    private static String readFabricModJson(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
