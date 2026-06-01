package com.modssync.client;

import com.modssync.sync.SyncOrchestrator;
import com.modssync.sync.SyncResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Shown after a sync that changed the mods folder (spec §8). */
public class RestartGateScreen extends Screen {

    private final SyncResult result;
    private final SyncOrchestrator orchestrator;

    public RestartGateScreen(SyncResult result, SyncOrchestrator orchestrator) {
        super(Component.translatable("modssync.screen.restart.title"));
        this.result = result;
        this.orchestrator = orchestrator;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("modssync.screen.restart.exit_game"),
                b -> Minecraft.getInstance().stop())
                .bounds(cx - 100, y, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("modssync.screen.restart.exit_server"),
                b -> Minecraft.getInstance().setScreen(new TitleScreen()))
                .bounds(cx - 100, y + 24, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("modssync.screen.restart.restore"),
                b -> {
                    orchestrator.restoreHome();
                    Minecraft.getInstance().setScreen(new TitleScreen());
                })
                .bounds(cx - 100, y + 48, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font,
                Component.translatable("modssync.screen.restart.message"),
                this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        String summary = "Disabled " + result.disabledCount()
                + " · Enabled " + result.enabledCount()
                + " · Downloaded " + result.downloadedCount()
                + (result.unresolved().isEmpty() ? "" : " · Unresolved " + result.unresolved().size());
        g.centeredText(this.font, summary, this.width / 2, this.height / 2 - 44, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
