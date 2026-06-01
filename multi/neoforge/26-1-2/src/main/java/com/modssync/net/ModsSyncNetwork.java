package com.modssync.net;

import com.modssync.ModsSync;
import com.modssync.sync.SyncResultHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the ModsSync manifest payload and its client handler.
 *
 * NOTE: In NeoForge 26.1.2, {@code @EventBusSubscriber} has NO {@code bus} attribute —
 * it was removed and the annotation unconditionally targets the MOD event bus. This is
 * correct for {@link RegisterPayloadHandlersEvent}, which fires on the MOD bus.
 */
@EventBusSubscriber(modid = "modssync")
public final class ModsSyncNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        ModsSync.LOGGER.info("ModsSyncNetwork.register fired — payload registration running");
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.configurationToClient(
                ManifestPayload.TYPE,
                ManifestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SyncResultHolder.onManifestReceived(payload.mods())));
    }
}
