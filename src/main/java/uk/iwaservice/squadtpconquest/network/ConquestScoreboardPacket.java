package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
public record ConquestScoreboardPacket(int roundElapsedSeconds, List<Entry> entries) implements CustomPacketPayload {

    public static final Type<ConquestScoreboardPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.squadtpconquest.SquadTpConquest.MODID, "conquest_scoreboard"));

    public static final StreamCodec<FriendlyByteBuf, ConquestScoreboardPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), ConquestScoreboardPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** {@code nameColor} is an RGB int (see {@code ChatFormatting#getColor}), 0 meaning "use the default text color" - see {@code /conquest namecolor}. */
    public record Entry(UUID uuid, String name, Team team, int kills, int deaths, int revives, int captures, int score,
                         int lifetimeKills, int lifetimeDeaths, int lifetimeRevives, int lifetimeCaptures,
                         int lifetimeScore, int nameColor) {}

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
            buf.writeVarInt(e.captures());
            buf.writeVarInt(e.score());
            buf.writeVarInt(e.lifetimeKills());
            buf.writeVarInt(e.lifetimeDeaths());
            buf.writeVarInt(e.lifetimeRevives());
            buf.writeVarInt(e.lifetimeCaptures());
            buf.writeVarInt(e.lifetimeScore());
            buf.writeInt(e.nameColor());
        }
    }

    public static ConquestScoreboardPacket decode(FriendlyByteBuf buf) {
        int elapsed = buf.readVarInt();
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readEnum(Team.class),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readInt()));
        }
        return new ConquestScoreboardPacket(elapsed, entries);
    }
}
