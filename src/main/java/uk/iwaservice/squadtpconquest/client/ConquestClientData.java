package uk.iwaservice.squadtpconquest.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side mirror of the conquest round, fed exclusively by S2C packets. */
public final class ConquestClientData {

    /** A spotted enemy's last known position (see {@link uk.iwaservice.squadtpconquest.network.SpotPacket}). */
    public record SpotEntry(String name, ResourceLocation dimension, BlockPos pos, long expiryGameTime) {}

    private static final Map<UUID, SpotEntry> spots = new HashMap<>();

    private static List<ConquestSyncPacket.PointStatus> points = List.of();
    private static int ticketsA;
    private static int ticketsB;
    private static boolean active;
    private static RoundState state = RoundState.WAITING;
    private static GameMode mode = GameMode.CONQUEST;
    private static Team yourTeam = Team.NEUTRAL;
    private static boolean canAdmin;
    private static int roundElapsedSeconds;
    private static List<ConquestScoreboardPacket.Entry> scoreboard = List.of();
    private static Team attackerTeam = Team.A;
    private static int sectorIndex;
    private static int sectorCount;
    private static int attackerTickets;
    private static int respawnWaveSecondsRemaining;
    private static List<ConquestSyncPacket.CallInStatus> callIns = List.of();
    private static int availableScore;
    private static List<ConquestSyncPacket.SquadStatus> joinableSquads = List.of();
    /** Incremented on every update; lets the GUI detect changes cheaply. */
    private static int revision;

    public static synchronized void apply(List<ConquestSyncPacket.PointStatus> newPoints,
                                          int newTicketsA, int newTicketsB, boolean newActive, RoundState newState,
                                          GameMode newMode, Team newYourTeam, boolean newCanAdmin,
                                          Team newAttackerTeam, int newSectorIndex, int newSectorCount,
                                          int newAttackerTickets, int newRespawnWaveSecondsRemaining,
                                          List<ConquestSyncPacket.CallInStatus> newCallIns, int newAvailableScore,
                                          List<ConquestSyncPacket.SquadStatus> newJoinableSquads) {
        points = List.copyOf(newPoints);
        ticketsA = newTicketsA;
        ticketsB = newTicketsB;
        active = newActive;
        state = newState;
        mode = newMode;
        yourTeam = newYourTeam;
        canAdmin = newCanAdmin;
        attackerTeam = newAttackerTeam;
        sectorIndex = newSectorIndex;
        sectorCount = newSectorCount;
        attackerTickets = newAttackerTickets;
        respawnWaveSecondsRemaining = newRespawnWaveSecondsRemaining;
        callIns = List.copyOf(newCallIns);
        availableScore = newAvailableScore;
        joinableSquads = List.copyOf(newJoinableSquads);
        revision++;
    }

    public static synchronized void applyScoreboard(int newRoundElapsedSeconds, List<ConquestScoreboardPacket.Entry> newEntries) {
        roundElapsedSeconds = newRoundElapsedSeconds;
        scoreboard = List.copyOf(newEntries);
        revision++;
    }

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized int getRoundElapsedSeconds() {
        return roundElapsedSeconds;
    }

    public static synchronized List<ConquestScoreboardPacket.Entry> getScoreboard() {
        return scoreboard;
    }

    public static synchronized List<ConquestSyncPacket.PointStatus> getPoints() {
        return points;
    }

    /** First point in server insertion order, or null if none exist yet. Used by simple single-point UI. */
    @Nullable
    public static synchronized ConquestSyncPacket.PointStatus getPoint(String name) {
        for (ConquestSyncPacket.PointStatus p : points) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    public static synchronized void addSpot(UUID target, String name, ResourceLocation dimension, BlockPos pos,
                                            long expiryGameTime) {
        spots.put(target, new SpotEntry(name, dimension, pos, expiryGameTime));
    }

    /** Drops expired spots. Returns true if anything was removed, so the caller knows to redraw. */
    public static synchronized boolean pruneExpiredSpots(long currentGameTime) {
        return spots.entrySet().removeIf(e -> e.getValue().expiryGameTime() <= currentGameTime);
    }

    public static synchronized Map<UUID, SpotEntry> getSpots() {
        return Map.copyOf(spots);
    }

    /** Called on logout: spot expiry is measured in absolute world time, meaningless across sessions/servers. */
    public static synchronized void clearSpots() {
        spots.clear();
    }

    public static synchronized int getTicketsA() {
        return ticketsA;
    }

    public static synchronized int getTicketsB() {
        return ticketsB;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized RoundState getState() {
        return state;
    }

    public static synchronized GameMode getMode() {
        return mode;
    }

    public static synchronized Team getYourTeam() {
        return yourTeam;
    }

    public static synchronized boolean canAdmin() {
        return canAdmin;
    }

    public static synchronized Team getAttackerTeam() {
        return attackerTeam;
    }

    public static synchronized int getSectorIndex() {
        return sectorIndex;
    }

    public static synchronized int getSectorCount() {
        return sectorCount;
    }

    public static synchronized int getAttackerTickets() {
        return attackerTickets;
    }

    public static synchronized int getRespawnWaveSecondsRemaining() {
        return respawnWaveSecondsRemaining;
    }

    public static synchronized List<ConquestSyncPacket.CallInStatus> getCallIns() {
        return callIns;
    }

    public static synchronized int getAvailableScore() {
        return availableScore;
    }

    public static synchronized List<ConquestSyncPacket.SquadStatus> getJoinableSquads() {
        return joinableSquads;
    }

    private ConquestClientData() {}
}
