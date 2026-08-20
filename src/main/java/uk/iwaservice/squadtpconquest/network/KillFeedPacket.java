package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;

/**
 * Broadcast to every online player when a kill is credited (see
 * {@code ScoreEvents.broadcastKillFeed}), for the top-right kill feed overlay. Names only, not
 * UUIDs — the feed is purely cosmetic and doesn't need to resolve back to a player afterward.
 * {@code durationTicks} mirrors {@link SpotPacket}/{@link PinPacket}: the server owns
 * {@code killFeedDurationSeconds}, the client just counts the given tick count down locally.
 */
public record KillFeedPacket(String attackerName, String victimName, int durationTicks) {

    public static void encode(KillFeedPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.attackerName);
        buf.writeUtf(msg.victimName);
        buf.writeVarInt(msg.durationTicks);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(KillFeedPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleKillFeed(msg));
    }
}
