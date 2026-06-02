package com.modssync.client;

import com.modssync.ModsSyncClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Progress screen shown while ModsSync clones mods/packs from the server. For a big
 * server (e.g. 300 mods) the transfer isn't instant, so this gives the player live
 * feedback (a bar + "X / N") instead of a frozen world or a surprise kick. It reads the
 * live counters from {@link ModsSyncClient}; the connection is dropped (with the
 * "please restart" message) once everything has arrived.
 */
public class DownloadScreen extends Screen {

    private static final int SLOTS = 24;

    public DownloadScreen() {
        super(Component.translatable("modssync.screen.downloading.title"));
    }

    @Override
    protected void init() {
        // No widgets — this is a passive progress display.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int total = ModsSyncClient.downloadTotal();
        int done = Math.max(0, total - ModsSyncClient.downloadsRemaining());
        float frac = total > 0 ? (float) done / total : 0f;
        int filled = Math.min(SLOTS, Math.round(SLOTS * frac));

        g.centeredText(this.font, Component.translatable("modssync.screen.downloading.title"),
                this.width / 2, this.height / 2 - 24, 0xFFFFFF);

        String bar = "█".repeat(filled) + "░".repeat(SLOTS - filled);
        g.centeredText(this.font, Component.literal(bar), this.width / 2, this.height / 2, 0xFF55FF55);

        g.centeredText(this.font, Component.literal(done + " / " + total + "   (" + (int) (frac * 100) + "%)"),
                this.width / 2, this.height / 2 + 16, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
