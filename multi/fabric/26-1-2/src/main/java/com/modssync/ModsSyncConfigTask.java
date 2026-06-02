package com.modssync;

import com.modssync.model.PackFile;
import com.modssync.model.ServerMod;
import com.modssync.net.ManifestPayload;
import com.modssync.net.PackManifestPayload;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import java.util.List;
import java.util.function.Consumer;

/**
 * Server-side {@link ConfigurationTask} that keeps a connecting client parked on the
 * "Connecting / Loading…" screen until ModsSync has finished syncing it.
 *
 * <p>This is the key to doing everything in a <em>single</em> connection. Vanilla would
 * otherwise blow straight through the configuration phase and drop the player into the
 * world before any files were transferred. By adding this task in
 * {@code ServerConfigurationConnectionEvents.CONFIGURE}, the server promises Fabric it
 * has unfinished configuration work, so it waits.
 *
 * <p>When the task becomes current, {@link #start} pushes both manifests
 * ({@link ManifestPayload} + {@link PackManifestPayload}) to the client. From there one
 * of two things happens:
 * <ul>
 *   <li>The client needs files or has unauthorised extras → it clones / schedules the
 *       removals and then simply disconnects (the player must restart to load them), so
 *       this task is never completed — the dropped connection ends it.</li>
 *   <li>The client is already fully in sync → it sends a
 *       {@link com.modssync.net.SyncDonePayload}, and the server calls
 *       {@code handler.completeTask(TYPE)}, releasing the client into the world.</li>
 * </ul>
 *
 * <p>The manifests are sent through the task's packet {@code sender} (rather than a raw
 * {@code ServerConfigurationNetworking.send}) because that is the contract for a
 * configuration task — packets it emits are tied to the task's lifecycle.
 */
public final class ModsSyncConfigTask implements ConfigurationTask {

    /** Unique identifier Fabric/Minecraft use to track and complete this task. */
    public static final Type TYPE = new Type("modssync:sync");

    private final List<ServerMod> mods;
    private final List<PackFile> packs;

    public ModsSyncConfigTask(List<ServerMod> mods, List<PackFile> packs) {
        this.mods = mods;
        this.packs = packs;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        // Order matters: the client relies on the mod manifest arriving before the pack
        // manifest (it runs the full diff once the second one lands). TCP preserves the
        // order of these two writes on the same connection, so this is safe.
        sender.accept(ServerConfigurationNetworking.createClientboundPacket(new ManifestPayload(mods)));
        sender.accept(ServerConfigurationNetworking.createClientboundPacket(new PackManifestPayload(packs)));
        ModsSync.LOGGER.info("ModsSync advertised {} mods and {} packs to a configuring client", mods.size(), packs.size());
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
