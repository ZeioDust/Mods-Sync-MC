package com.modssync.net;

import com.modssync.model.ServerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * Server → client payload sent during the configuration phase.
 *
 * <p>This is the first message in the ModsSync protocol. The server sends the complete
 * list of mods it considers required, described by {@link ServerMod}. The client
 * compares this list against its local mods folder to figure out what it is missing
 * or has wrong, then sends a {@link RequestFilesPayload} back to ask for the
 * missing files.
 *
 * <p>Each {@link ServerMod} carries up to seven fields (modId, version, fileName,
 * sha1, downloadUrl, modrinthId, curseforgeId). Several of those are optional and
 * are represented as {@code null} in Java — but the network protocol cannot encode
 * {@code null} strings, so we round-trip them as empty strings.
 */
public record ManifestPayload(List<ServerMod> mods) implements CustomPacketPayload {

    public static final Type<ManifestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "manifest"));

    /**
     * Hand-written codec for a single {@link ServerMod}.
     *
     * <p>{@code StreamCodec.composite} only accepts up to 6 field extractors, but
     * {@link ServerMod} has 7 fields, so we cannot use the convenience factory here.
     * Instead we manually call {@code readUtf}/{@code writeUtf} for each field in a
     * fixed order — both sides must stay in sync.
     *
     * <p>Null-handling: {@code null} fields are written as {@code ""} and read back
     * as {@code null} via {@link #blankToNull}. This preserves the "absent" semantic
     * that the rest of the mod relies on (e.g. {@code mod.sha1() == null} means no
     * checksum is available).
     */
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
                    // Convert empty strings back to null — callers treat null as "not set".
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
                    // nz() converts null → "" so writeUtf never receives a null argument.
                    buf.writeUtf(nz(sm.modId()));
                    buf.writeUtf(nz(sm.version()));
                    buf.writeUtf(nz(sm.fileName()));
                    buf.writeUtf(nz(sm.sha1()));
                    buf.writeUtf(nz(sm.downloadUrl()));
                    buf.writeUtf(nz(sm.modrinthId()));
                    buf.writeUtf(nz(sm.curseforgeId()));
                }
            };

    /**
     * The outer codec just wraps a list of {@link ServerMod} into the payload record.
     * {@code MOD_CODEC.apply(ByteBufCodecs.list())} produces a codec that prefixes
     * the list with its element count and then encodes each element using MOD_CODEC.
     */
    public static final StreamCodec<FriendlyByteBuf, ManifestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    MOD_CODEC.apply(ByteBufCodecs.list()), ManifestPayload::mods,
                    ManifestPayload::new);

    /** Converts {@code null} to {@code ""} for wire encoding. */
    private static String nz(String s) { return s == null ? "" : s; }

    /** Converts blank/empty strings back to {@code null} after decoding. */
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
