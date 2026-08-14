package uk.iwaservice.squadtpconquest.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;

import java.util.UUID;

/**
 * Sent to every online member of the placer's team when {@code /conquest pin} marks a location, or
 * to clear one early via {@code /conquest pin clear}. Keyed by {@code placer} — each player has at
 * most one active pin, so placing a new one implicitly replaces their previous pin on receivers'
 * clients. When {@code cleared} is true, {@code dimension}/{@code pos}/{@code durationTicks} are
 * unused placeholders and the receiver should just remove the placer's existing pin, if any.
 */
public record PinPacket(UUID placer, String placerName, ResourceLocation dimension, BlockPos pos,
                         int durationTicks, boolean cleared) {

    public static void encode(PinPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.placer);
        buf.writeUtf(msg.placerName);
        buf.writeResourceLocation(msg.dimension);
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.durationTicks);
        buf.writeBoolean(msg.cleared);
    }

    public static PinPacket decode(FriendlyByteBuf buf) {
        return new PinPacket(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(), buf.readBlockPos(),
                buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PinPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePin(msg));
    }
}
