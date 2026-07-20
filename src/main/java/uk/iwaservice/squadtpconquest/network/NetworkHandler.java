package uk.iwaservice.squadtpconquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

/**
 * Server-to-client only channel. Clients never send conquest packets; all
 * actions go through /conquest commands, which are validated server-side.
 */
public final class NetworkHandler {

    // Bump whenever a packet's wire format changes (field added/removed/reordered).
    // A mismatch then fails the connection handshake with a clear message instead
    // of silently decoding a malformed packet and crashing the client mid-game.
    private static final String PROTOCOL_VERSION = "8";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SquadTpConquest.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(ConquestSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConquestSyncPacket::encode)
                .decoder(ConquestSyncPacket::decode)
                .consumerMainThread(ConquestSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(ConquestScoreboardPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConquestScoreboardPacket::encode)
                .decoder(ConquestScoreboardPacket::decode)
                .consumerMainThread(ConquestScoreboardPacket::handle)
                .add();
    }

    public static void send(ServerPlayer player, ConquestSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void send(ServerPlayer player, ConquestScoreboardPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private NetworkHandler() {}
}
