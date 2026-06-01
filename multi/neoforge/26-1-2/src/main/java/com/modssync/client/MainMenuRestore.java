package com.modssync.client;

import com.modssync.ModsSyncClient;
import com.modssync.sync.SyncOrchestrator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds a "Restore My Modpack" button to the main menu (TitleScreen).
 *
 * <p>API used: {@code net.neoforged.neoforge.client.event.ScreenEvent.Init.Post}
 * (game/NeoForge event bus). Confirmed present in NeoForge 26.1.2.64-beta universal jar.
 * The event provides {@link ScreenEvent.Init#addListener} to inject widgets.</p>
 */
public final class MainMenuRestore {

    private MainMenuRestore() {}

    /** Listener registered on {@code NeoForge.EVENT_BUS} from {@code ModsSyncClient}. */
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        Button restoreButton = Button.builder(
                        Component.translatable("modssync.screen.restart.restore"),
                        b -> {
                            SyncOrchestrator orchestrator = ModsSyncClient.buildOrchestrator();
                            orchestrator.restoreHome();
                            // Leave player on the title screen (no screen change needed).
                        })
                .bounds(4, screen.height - 24, 100, 20)
                .build();
        event.addListener(restoreButton);
    }
}
