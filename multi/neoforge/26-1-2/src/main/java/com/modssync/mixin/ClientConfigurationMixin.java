package com.modssync.mixin;

import com.modssync.model.ServerMod;
import com.modssync.sync.SyncResultHolder;
import java.util.ArrayList;
import java.util.List;
// Note: ServerMod import retained for the TODO(Task 15) mods list construction below.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fallback capture of the server's mod list on non-ModsSync servers (spec §3b).
 *
 * NOTE TO IMPLEMENTER / FUTURE MAINTAINER: the exact target class/method depends on
 * the NeoForge 26.1.2 client configuration networking internals. This scaffold targets
 * net.minecraft.client.multiplayer.ClientPacketListener (a verified-stable client
 * networking class confirmed present via the NeoForge 26.1.2.64-beta userdev jar
 * patches). The injection body is intentionally inert — the `mods` list is always
 * empty until Task 15 confirms the correct class/method and populates it from the
 * negotiated server mod map.
 *
 * The aware-server payload path (Task 12) is unaffected: if a ModsSync manifest
 * already arrived via that path, this mixin exits early.
 *
 * require=0 means a target-mismatch degrades gracefully (no crash) during Task 15
 * refinement.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener")
public class ClientConfigurationMixin {

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void modssync$captureServerMods(CallbackInfo ci) {
        if (SyncResultHolder.hasPending()) {
            return; // a ModsSync manifest already arrived; aware-server path wins
        }
        List<ServerMod> mods = new ArrayList<>();
        // TODO(Task 15): populate `mods` from the negotiated server mod map for this connection.
        if (!mods.isEmpty()) {
            SyncResultHolder.onManifestReceived(mods);
        }
    }
}
