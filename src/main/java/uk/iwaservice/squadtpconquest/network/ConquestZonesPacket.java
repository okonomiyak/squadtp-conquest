package uk.iwaservice.squadtpconquest.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Every non-capture-point zone outline (home/protect/spawn/combat/boundary/range boxes, team beacon
 * rings) for the client to trace with dust particles locally, so the server sends no particle
 * packets. Sent only when it changes. {@code rgb} is a packed 0xRRGGBB color.
 */
public record ConquestZonesPacket(List<Box> boxes, List<Ring> rings) implements CustomPacketPayload {

    public static final Type<ConquestZonesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtpconquest.SquadTpConquest.MODID, "conquest_zones"));

    public static final StreamCodec<FriendlyByteBuf, ConquestZonesPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), ConquestZonesPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Box(ResourceLocation dimension, BlockPos min, BlockPos max, int rgb) {}

    public record Ring(ResourceLocation dimension, BlockPos pos, int radius, int rgb) {}

    public static void encode(ConquestZonesPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.boxes.size());
        for (Box b : msg.boxes) {
            buf.writeResourceLocation(b.dimension());
            buf.writeBlockPos(b.min());
            buf.writeBlockPos(b.max());
            buf.writeInt(b.rgb());
        }
        buf.writeVarInt(msg.rings.size());
        for (Ring r : msg.rings) {
            buf.writeResourceLocation(r.dimension());
            buf.writeBlockPos(r.pos());
            buf.writeVarInt(r.radius());
            buf.writeInt(r.rgb());
        }
    }

    public static ConquestZonesPacket decode(FriendlyByteBuf buf) {
        int boxCount = buf.readVarInt();
        List<Box> boxes = new ArrayList<>(boxCount);
        for (int i = 0; i < boxCount; i++) {
            boxes.add(new Box(buf.readResourceLocation(), buf.readBlockPos(), buf.readBlockPos(), buf.readInt()));
        }
        int ringCount = buf.readVarInt();
        List<Ring> rings = new ArrayList<>(ringCount);
        for (int i = 0; i < ringCount; i++) {
            rings.add(new Ring(buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt(), buf.readInt()));
        }
        return new ConquestZonesPacket(boxes, rings);
    }
}
