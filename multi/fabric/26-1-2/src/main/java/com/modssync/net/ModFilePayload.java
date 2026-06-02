package com.modssync.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client payload sent during the configuration phase (registered as a "large" payload).
 *
 * <p>This is the workhorse of the file-transfer protocol. Each packet carries the
 * raw bytes of exactly one file together with the destination folder and file name.
 * The client writes {@code data} to {@code <minecraftDir>/<folder>/<fileName>}.
 *
 * <p><b>End-of-batch sentinel:</b> after all requested files for a given folder have
 * been sent, the server sends one extra packet with an empty {@code fileName} (and
 * empty {@code data}). The client uses {@link #isEnd()} to detect this marker and
 * knows the transfer for that folder is complete. See {@link #end()} for the
 * factory method that creates the sentinel.
 *
 * <p>The 100 MB cap ({@link #MAX_BYTES}) exists to prevent a malicious or
 * misconfigured server from allocating an unbounded buffer on the client during
 * decoding. Individual mod jars that exceed this limit cannot be transferred via
 * this mechanism.
 */
public record ModFilePayload(String folder, String fileName, byte[] data) implements CustomPacketPayload {

    public static final Type<ModFilePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "mod_file"));

    /** Hard cap on the byte array the codec will decode, guarding against oversized packets. */
    private static final int MAX_BYTES = 100 * 1024 * 1024;

    /**
     * Encodes folder, fileName, and the raw file bytes in that order.
     * {@code byteArray(MAX_BYTES)} writes a var-int length prefix followed by the
     * raw bytes, and enforces the cap on the read side.
     */
    public static final StreamCodec<FriendlyByteBuf, ModFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ModFilePayload::folder,
                    ByteBufCodecs.STRING_UTF8, ModFilePayload::fileName,
                    ByteBufCodecs.byteArray(MAX_BYTES), ModFilePayload::data,
                    ModFilePayload::new);

    /**
     * Creates the end-of-batch sentinel packet. The server sends this after the last
     * real file in a batch so the client knows the transfer is complete and can
     * proceed (e.g. prompt for a restart).
     */
    public static ModFilePayload end() {
        return new ModFilePayload("", "", new byte[0]);
    }

    /**
     * Returns {@code true} when this packet is the end-of-batch sentinel rather than
     * a real file. Callers check this before trying to write {@code data} to disk.
     */
    public boolean isEnd() {
        return fileName == null || fileName.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
