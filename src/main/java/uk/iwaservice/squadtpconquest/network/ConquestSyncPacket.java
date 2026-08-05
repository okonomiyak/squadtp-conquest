package uk.iwaservice.squadtpconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
                                  Team yourTeam, boolean canAdmin, boolean openScreen,
                                  Team attackerTeam, int sectorIndex, int sectorCount,
                                  int attackerTickets, int respawnWaveSecondsRemaining,
                                  List<CallInStatus> callIns, int availableScore) {

    /**
     * One capture point as seen by a specific viewer (contested/inZone are per-viewer).
     * {@code active} is false for breakthrough points outside the currently active sector
     * (locked ahead, or already cleared behind the front line) — always true in other modes.
     * {@code sectorNumber} is 0 outside breakthrough or for a point not assigned to any sector.
     */
    public record PointStatus(String name, int radius, Team owner, Team capturingTeam, double flagLevel,
                               boolean contested, boolean inZone, boolean active, int sectorNumber) {}

    /** One registered /conquest callin, for the player-facing GUI list (see availableScore). */
    public record CallInStatus(String name, int scoreCost, ResourceLocation itemId, int count) {}

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
            buf.writeBoolean(p.active());
            buf.writeVarInt(p.sectorNumber());
        }
        buf.writeVarInt(msg.ticketsA);
        buf.writeVarInt(msg.ticketsB);
        buf.writeBoolean(msg.active);
        buf.writeEnum(msg.state);
        buf.writeEnum(msg.mode);
        buf.writeEnum(msg.yourTeam);
        buf.writeBoolean(msg.canAdmin);
        buf.writeBoolean(msg.openScreen);
        buf.writeEnum(msg.attackerTeam);
        buf.writeVarInt(msg.sectorIndex);
        buf.writeVarInt(msg.sectorCount);
        buf.writeVarInt(msg.attackerTickets);
        buf.writeVarInt(msg.respawnWaveSecondsRemaining);
        buf.writeVarInt(msg.callIns.size());
        for (CallInStatus c : msg.callIns) {
            buf.writeUtf(c.name());
            buf.writeVarInt(c.scoreCost());
            buf.writeResourceLocation(c.itemId());
            buf.writeVarInt(c.count());
        }
        buf.writeVarInt(msg.availableScore);
    }

    public static ConquestSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PointStatus> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new PointStatus(buf.readUtf(), buf.readVarInt(), buf.readEnum(Team.class),
                    buf.readEnum(Team.class), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt()));
        }
        int ticketsA = buf.readVarInt();
        int ticketsB = buf.readVarInt();
        boolean active = buf.readBoolean();
        RoundState state = buf.readEnum(RoundState.class);
        GameMode mode = buf.readEnum(GameMode.class);
        Team yourTeam = buf.readEnum(Team.class);
        boolean canAdmin = buf.readBoolean();
        boolean openScreen = buf.readBoolean();
        Team attackerTeam = buf.readEnum(Team.class);
        int sectorIndex = buf.readVarInt();
        int sectorCount = buf.readVarInt();
        int attackerTickets = buf.readVarInt();
        int respawnWaveSecondsRemaining = buf.readVarInt();
        int callInCount = buf.readVarInt();
        List<CallInStatus> callIns = new ArrayList<>(callInCount);
        for (int i = 0; i < callInCount; i++) {
            callIns.add(new CallInStatus(buf.readUtf(), buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt()));
        }
        int availableScore = buf.readVarInt();
        return new ConquestSyncPacket(points, ticketsA, ticketsB, active, state, mode, yourTeam, canAdmin, openScreen,
                attackerTeam, sectorIndex, sectorCount, attackerTickets, respawnWaveSecondsRemaining,
                callIns, availableScore);
    }

    public static void handle(ConquestSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
    }
}
