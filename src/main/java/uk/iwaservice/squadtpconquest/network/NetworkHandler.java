package uk.iwaservice.squadtpconquest.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;

/**
 * Server-to-client only channel. Clients never send conquest packets; all
 * actions go through /conquest commands, which are validated server-side.
 */
public final class NetworkHandler {

    // Bump whenever a packet's wire format changes (field added/removed/reordered).
    // A mismatch then fails the connection handshake with a clear message instead
    // of silently decoding a malformed packet and crashing the client mid-game.
    private static final String PROTOCOL_VERSION = "21";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ConquestSyncPacket.TYPE, ConquestSyncPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleSync(msg));
        registrar.playToClient(ConquestScoreboardPacket.TYPE, ConquestScoreboardPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleScoreboard(msg));
        registrar.playToClient(SpotPacket.TYPE, SpotPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleSpot(msg));
        registrar.playToClient(PinPacket.TYPE, PinPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handlePin(msg));
        registrar.playToClient(KillFeedPacket.TYPE, KillFeedPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleKillFeed(msg));
    }

    public static void send(ServerPlayer player, ConquestSyncPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void send(ServerPlayer player, ConquestScoreboardPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void send(ServerPlayer player, SpotPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void send(ServerPlayer player, PinPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void broadcast(KillFeedPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    private NetworkHandler() {}
}
