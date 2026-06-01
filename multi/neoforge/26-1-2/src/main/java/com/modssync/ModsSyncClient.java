package com.modssync;

import com.modssync.client.MainMenuRestore;
import com.modssync.client.RestartGateScreen;
import com.modssync.core.*;
import com.modssync.model.ServerMod;
import com.modssync.net.resolver.*;
import com.modssync.sync.SyncOrchestrator;
import com.modssync.sync.SyncResult;
import com.modssync.sync.SyncResultHolder;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ModsSync.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ModsSync.MODID, value = Dist.CLIENT)
public class ModsSyncClient {

    public ModsSyncClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // ScreenEvent fires on the game (NeoForge) bus, not the mod bus.
        NeoForge.EVENT_BUS.addListener(MainMenuRestore::onScreenInit);
    }

    public static SyncOrchestrator buildOrchestrator() {
        Path modsDir = FMLPaths.MODSDIR.get();
        Path snapshot = FMLPaths.CONFIGDIR.get().resolve("modssync-home.json");
        HttpClient http = HttpClient.newHttpClient();
        Acquirer acquirer = new Acquirer(List.of(
                new ServerUrlSource(http),
                new ModrinthSource(http),
                new CurseForgeSource(http, Config.CURSEFORGE_API_KEY.get())));
        return new SyncOrchestrator(modsDir, new LocalInventory(), new DiffEngine(),
                new FolderMutator(), new SnapshotStore(snapshot), acquirer);
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        List<ServerMod> serverMods = SyncResultHolder.takePending();
        if (serverMods == null) {
            return;
        }
        SyncOrchestrator orchestrator = buildOrchestrator();
        SyncResult result = orchestrator.syncTo(serverMods);
        if (result.restartRequired()) {
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().setScreen(new RestartGateScreen(result, orchestrator)));
        }
    }
}
