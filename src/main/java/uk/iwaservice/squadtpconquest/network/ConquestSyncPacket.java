package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.squadtpconquest.client.ClientPacketHandler;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * Full conquest state pushed to one player: every capture point's status,
 * tickets, their team and whether they may use the admin controls. Broadcast
 * once per tick, and sent on demand when a flag block is right-clicked
 * (openScreen = true tells the client to pop the GUI if it isn't already open).
 */
public record ConquestSyncPacket(List<PointStatus> points,
                                  int ticketsA, int ticketsB, boolean active, RoundState state, GameMode mode,
                                  Team yourTeam, boolean canAdmin, boolean openScreen) {

    /** One capture point as seen by a specific viewer (contested/inZone are per-viewer). */
    public record PointStatus(String name, int radius, Team owner, Team capturingTeam, double flagLevel,
                               boolean contested, boolean inZone) {}

    public static void encode(ConquestSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.points.size());
        for (PointStatus p : msg.points) {
            buf.writeUtf(p.name());
            buf.writeVarInt(p.radius());
            buf.writeEnum(p.owner());
            buf.writeEnum(p.capturingTeam());
            buf.writeDouble(p.flagLevel());
            buf.writeBoolean(p.contested());
            buf.writeBoolean(p.inZone());
        }
        buf.writeVarInt(msg.ticketsA);
        buf.writeVarInt(msg.ticketsB);
        buf.writeBoolean(msg.active);
        buf.writeEnum(msg.state);
        buf.writeEnum(msg.mode);
        buf.writeEnum(msg.yourTeam);
        buf.writeBoolean(msg.canAdmin);
        buf.writeBoolean(msg.openScreen);
    }

    public static ConquestSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PointStatus> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new PointStatus(buf.readUtf(), buf.readVarInt(), buf.readEnum(Team.class),
                    buf.readEnum(Team.class), buf.readDouble(), buf.readBoolean(), buf.readBoolean()));
        }
        int ticketsA = buf.readVarInt();
        int ticketsB = buf.readVarInt();
        boolean active = buf.readBoolean();
        RoundState state = buf.readEnum(RoundState.class);
        GameMode mode = buf.readEnum(GameMode.class);
        Team yourTeam = buf.readEnum(Team.class);
        boolean canAdmin = buf.readBoolean();
        boolean openScreen = buf.readBoolean();
        return new ConquestSyncPacket(points, ticketsA, ticketsB, active, state, mode, yourTeam, canAdmin, openScreen);
    }

    public static void handle(ConquestSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
    }
}
