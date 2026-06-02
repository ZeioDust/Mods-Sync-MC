package com.modssync.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * Client → server payload sent during the configuration phase.
 *
 * <p>After the client has received and processed both {@link ManifestPayload} and
 * {@link PackManifestPayload}, it knows which files it is missing or needs to update.
 * It sends one {@code RequestFilesPayload} per folder (e.g. one for {@code "mods"},
 * one for {@code "resourcepacks"}) listing only the file names it actually needs.
 *
 * <p>The server handler streams each requested file back as a sequence of
 * {@link ModFilePayload} packets, finishing the batch with the end-sentinel packet
 * ({@link ModFilePayload#end()}).
 *
 * <p>Using a separate message per folder keeps the server-side handler simple: it
 * resolves the absolute path of {@code folder} once, then iterates {@code fileNames}
 * reading each from disk.
 */
public record RequestFilesPayload(String folder, List<String> fileNames) implements CustomPacketPayload {

    public static final Type<RequestFilesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "request_files"));

    /**
     * The codec encodes the folder name first, then a length-prefixed list of file
     * names. Both are plain UTF-8 strings; no null handling is needed because the
     * client always supplies concrete values.
     *
     * <p>Note: this uses the bare {@link FriendlyByteBuf} (configuration-phase buffer),
     * matching where in the connection lifecycle this packet is sent — the whole
     * match/download/disable now happens during configuration, before the world loads.
     */
    public static final StreamCodec<FriendlyByteBuf, RequestFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RequestFilesPayload::folder,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RequestFilesPayload::fileNames,
                    RequestFilesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
