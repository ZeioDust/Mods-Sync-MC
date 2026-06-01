package com.modssync.net;

import com.modssync.model.ServerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Server → client list of the server's required mods (spec §3a). */
public record ManifestPayload(List<ServerMod> mods) implements CustomPacketPayload {

    public static final Type<ManifestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "manifest"));

    // StreamCodec.composite only goes up to 6 fields; ServerMod has 7 — manual codec required.
    private static final StreamCodec<FriendlyByteBuf, ServerMod> MOD_CODEC =
            new StreamCodec<>() {
                @Override
                public ServerMod decode(FriendlyByteBuf buf) {
                    String id      = buf.readUtf();
                    String version = buf.readUtf();
                    String file    = buf.readUtf();
                    String sha     = buf.readUtf();
                    String url     = buf.readUtf();
                    String mr      = buf.readUtf();
                    String cf      = buf.readUtf();
                    return new ServerMod(
                            id,
                            blankToNull(version),
                            blankToNull(file),
                            blankToNull(sha),
                            blankToNull(url),
                            blankToNull(mr),
                            blankToNull(cf));
                }

                @Override
                public void encode(FriendlyByteBuf buf, ServerMod sm) {
                    buf.writeUtf(nz(sm.modId()));
                    buf.writeUtf(nz(sm.version()));
                    buf.writeUtf(nz(sm.fileName()));
                    buf.writeUtf(nz(sm.sha1()));
                    buf.writeUtf(nz(sm.downloadUrl()));
                    buf.writeUtf(nz(sm.modrinthId()));
                    buf.writeUtf(nz(sm.curseforgeId()));
                }
            };

    public static final StreamCodec<FriendlyByteBuf, ManifestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    MOD_CODEC.apply(ByteBufCodecs.list()), ManifestPayload::mods,
                    ManifestPayload::new);

    private static String nz(String s) { return s == null ? "" : s; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
