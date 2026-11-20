package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;
import uk.iwaservice.squadtpconquest.conquest.Team;

/**
 * Sent to every online player when a round ends on its own (not {@code /conquest stop}), to open
 * the result screen. {@code winner} is {@link Team#NEUTRAL} for a draw or Squad Deathmatch;
 * {@code sdmWinner} is the winning squad number, 0 when there is none.
 */
public record RoundResultPacket(Team winner, int sdmWinner, Component subtitle) {

    public static void encode(RoundResultPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.winner);
        buf.writeVarInt(msg.sdmWinner);
        buf.writeComponent(msg.subtitle);
    }

    public static RoundResultPacket decode(FriendlyByteBuf buf) {
        return new RoundResultPacket(buf.readEnum(Team.class), buf.readVarInt(), buf.readComponent());
    }

    public static void handle(RoundResultPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleRoundResult(msg));
    }
}
