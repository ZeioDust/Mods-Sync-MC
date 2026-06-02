package com.modssync.net;

import com.modssync.model.PackFile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * Server → client payload sent during the configuration phase, after {@link ManifestPayload}.
 *
 * <p>Where {@link ManifestPayload} covers mods, this message covers resource packs and
 * shader packs. The server lists every pack it wants the client to have, including the
 * subfolder it lives in (e.g. {@code "resourcepacks"} or {@code "shaderpacks"}), the
 * file name, and its byte size. The client uses the size to decide whether it already
 * has an up-to-date copy before requesting a transfer via {@link RequestFilesPayload}.
 *
 * <p>Unlike {@link ManifestPayload}, all three {@link PackFile} fields are mandatory
 * non-null strings/longs, so a simple {@code StreamCodec.composite} with three
 * {@link ByteBufCodecs} entries is sufficient — no manual codec needed.
 */
public record PackManifestPayload(List<PackFile> packs) implements CustomPacketPayload {

    public static final Type<PackManifestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "pack_manifest"));

    /**
     * Codec for a single {@link PackFile}: folder name, file name, and file size in bytes.
     * {@code VAR_LONG} is used for size to keep the encoding compact for small files
     * while still supporting files larger than {@code Integer.MAX_VALUE}.
     */
    private static final StreamCodec<FriendlyByteBuf, PackFile> PACK_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PackFile::folder,
                    ByteBufCodecs.STRING_UTF8, PackFile::fileName,
                    ByteBufCodecs.VAR_LONG, PackFile::size,
                    PackFile::new);

    /** Wraps a variable-length list of {@link PackFile} entries into the payload record. */
    public static final StreamCodec<FriendlyByteBuf, PackManifestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    PACK_CODEC.apply(ByteBufCodecs.list()), PackManifestPayload::packs,
                    PackManifestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
