package com.modssync.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server signal sent during the configuration phase.
 *
 * <p>The whole sync (download missing files, schedule disables) now runs while the
 * client is still configuring — held there by a server-side configuration task. When
 * a client is already fully in sync (nothing to download, nothing to disable), it has
 * no reason to disconnect, so it sends this empty "I'm done, let me in" signal. The
 * server responds by completing its configuration task, which lets the client finish
 * the handshake and enter the world.
 *
 * <p>Clients that <em>do</em> have work simply sever the connection after cloning /
 * scheduling instead of sending this — they must restart before they can join anyway.
 *
 * <p>The payload carries no data; {@link StreamCodec#unit} encodes it as zero bytes.
 */
public record SyncDonePayload() implements CustomPacketPayload {

    public static final Type<SyncDonePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("modssync", "sync_done"));

    /** No fields to encode — a unit codec always decodes to the same singleton-like value. */
    public static final StreamCodec<FriendlyByteBuf, SyncDonePayload> STREAM_CODEC =
            StreamCodec.unit(new SyncDonePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
