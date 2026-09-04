package uk.iwaservice.squadtpconquest.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;

import java.util.UUID;

/**
 * Sent to every online member of the spotter's team when {@code /conquest spot} marks an enemy.
 * A one-time position snapshot, not a live tracker — the client counts {@code durationTicks} down
 * locally and lets the mark expire on its own (see {@link ClientPacketHandler#handleSpot}).
 */
public record SpotPacket(UUID target, String targetName, ResourceLocation dimension, BlockPos pos,
                          int durationTicks) implements CustomPacketPayload {

    public static final Type<SpotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtpconquest.SquadTpConquest.MODID, "spot"));

    public static final StreamCodec<FriendlyByteBuf, SpotPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SpotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(SpotPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.target);
        buf.writeUtf(msg.targetName);
        buf.writeResourceLocation(msg.dimension);
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.durationTicks);
    }

    public static SpotPacket decode(FriendlyByteBuf buf) {
        return new SpotPacket(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(), buf.readBlockPos(),
                buf.readVarInt());
    }
}
