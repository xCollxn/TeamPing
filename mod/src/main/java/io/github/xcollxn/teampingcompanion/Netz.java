package io.github.xcollxn.teampingcompanion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/** Raw UTF-8 JSON messages – match the Bukkit plugin channels 1:1 (teamping:status / teamping:action). */
final class Netz {
    private Netz() {}

    private static <T> StreamCodec<FriendlyByteBuf, T> jsonCodec(java.util.function.Function<String, T> neu, java.util.function.Function<T, String> text) {
        return StreamCodec.of(
                (buf, wert) -> buf.writeBytes(text.apply(wert).getBytes(StandardCharsets.UTF_8)),
                buf -> { byte[] b = new byte[buf.readableBytes()]; buf.readBytes(b); return neu.apply(new String(b, StandardCharsets.UTF_8)); });
    }

    record Status(String json) implements CustomPacketPayload {
        static final Type<Status> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamping", "status"));
        static final StreamCodec<FriendlyByteBuf, Status> CODEC = jsonCodec(Status::new, Status::json);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record Aktion(String json) implements CustomPacketPayload {
        static final Type<Aktion> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamping", "action"));
        static final StreamCodec<FriendlyByteBuf, Aktion> CODEC = jsonCodec(Aktion::new, Aktion::json);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
