package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;
import uk.iwaservice.squadtpconquest.conquest.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Full player roster for the scoreboard screen (Right Alt by default),
 * broadcast once per second alongside {@link ConquestSyncPacket}. Shows revives rather than
 * assists (2026-08-20) — assists are still tracked and scored server-side
 * ({@code PlayerScore.assists}, {@code scorePerAssist}), just not displayed on this screen.
 */
public record ConquestScoreboardPacket(int roundElapsedSeconds, List<Entry> entries) {

    public record Entry(UUID uuid, String name, Team team, int kills, int deaths, int revives, int score,
                         int lifetimeKills, int lifetimeDeaths, int lifetimeRevives, int lifetimeScore) {}

    public static void encode(ConquestScoreboardPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.roundElapsedSeconds);
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.name());
            buf.writeEnum(e.team());
            buf.writeVarInt(e.kills());
            buf.writeVarInt(e.deaths());
            buf.writeVarInt(e.revives());
            buf.writeVarInt(e.score());
            buf.writeVarInt(e.lifetimeKills());
            buf.writeVarInt(e.lifetimeDeaths());
            buf.writeVarInt(e.lifetimeRevives());
            buf.writeVarInt(e.lifetimeScore());
        }
    }

    public static ConquestScoreboardPacket decode(FriendlyByteBuf buf) {
        int elapsed = buf.readVarInt();
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readEnum(Team.class),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new ConquestScoreboardPacket(elapsed, entries);
    }

    public static void handle(ConquestScoreboardPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleScoreboard(msg));
    }
}
