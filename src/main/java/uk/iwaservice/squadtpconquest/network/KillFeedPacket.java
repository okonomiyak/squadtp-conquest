package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Broadcast to every online player when a kill is credited (see
 * {@code ScoreEvents.broadcastKillFeed}), for the top-right kill feed overlay. Names only, not
 * UUIDs — the feed is purely cosmetic and doesn't need to resolve back to a player afterward.
 * {@code durationTicks} mirrors {@link SpotPacket}/{@link PinPacket}: the server owns
 * {@code killFeedDurationSeconds}, the client just counts the given tick count down locally.
 */
public record KillFeedPacket(String attackerName, String victimName, int durationTicks) implements CustomPacketPayload {

    public static final Type<KillFeedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtpconquest.SquadTpConquest.MODID, "kill_feed"));

    public static final StreamCodec<FriendlyByteBuf, KillFeedPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), KillFeedPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(KillFeedPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.attackerName);
        buf.writeUtf(msg.victimName);
        buf.writeVarInt(msg.durationTicks);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }
}
