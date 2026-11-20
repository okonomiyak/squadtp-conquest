package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.conquest.Team;

/**
 * Sent to every online player when a round ends on its own (not {@code /conquest stop}), to open
 * the result screen. {@code winner} is {@link Team#NEUTRAL} for a draw or Squad Deathmatch;
 * {@code sdmWinner} is the winning squad number, 0 when there is none.
 */
public record RoundResultPacket(Team winner, int sdmWinner, Component subtitle) implements CustomPacketPayload {

    public static final Type<RoundResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtpconquest.SquadTpConquest.MODID, "round_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoundResultPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), RoundResultPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RoundResultPacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeEnum(msg.winner);
        buf.writeVarInt(msg.sdmWinner);
        ComponentSerialization.STREAM_CODEC.encode(buf, msg.subtitle);
    }

    public static RoundResultPacket decode(RegistryFriendlyByteBuf buf) {
        return new RoundResultPacket(buf.readEnum(Team.class), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
    }
}
