package uk.iwaservice.squadtpconquest.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;
import uk.iwaservice.squadtpconquest.network.ConquestZonesPacket;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side mirror of the conquest round, fed exclusively by S2C packets. */
public final class ConquestClientData {

    /** A spotted enemy's last known position (see {@link uk.iwaservice.squadtpconquest.network.SpotPacket}). */
    public record SpotEntry(String name, ResourceLocation dimension, BlockPos pos, long expiryGameTime) {}

    /** A teammate's pinned location (see {@link uk.iwaservice.squadtpconquest.network.PinPacket}). */
    public record PinEntry(String placerName, ResourceLocation dimension, BlockPos pos, long expiryGameTime) {}

    /** One kill feed line (see {@link uk.iwaservice.squadtpconquest.network.KillFeedPacket}). */
    public record KillFeedEntry(String attackerName, String victimName, int distanceMeters, long expiryGameTime) {}

    private static final Map<UUID, SpotEntry> spots = new HashMap<>();
    /** Keyed by placer UUID — each player has at most one active pin. */
    private static final Map<UUID, PinEntry> pins = new HashMap<>();
    /** Oldest first; capped in {@link #addKillFeedEntry} so a burst of kills can't grow this unbounded. */
    private static final java.util.List<KillFeedEntry> killFeed = new java.util.ArrayList<>();
    private static final int MAX_KILL_FEED_ENTRIES = 5;

    private static List<ConquestSyncPacket.PointStatus> points = List.of();
    private static int ticketsA;
    private static int ticketsB;
    private static boolean active;
    private static RoundState state = RoundState.WAITING;
    private static GameMode mode = GameMode.CONQUEST;
    private static Team yourTeam = Team.NEUTRAL;
    private static boolean canAdmin;
    private static int roundElapsedSeconds;
    /** Client game time when {@link #roundElapsedSeconds} was received; the timer is extrapolated from it while the round runs. */
    private static long roundElapsedReceivedAt;
    private static ConquestZonesPacket zones = new ConquestZonesPacket(List.of(), List.of());
    private static List<ConquestScoreboardPacket.Entry> scoreboard = List.of();
    private static Team attackerTeam = Team.A;
    private static int sectorIndex;
    private static int sectorCount;
    private static int attackerTickets;
    private static int attackerTicketsMax;
    private static int tdmKillLimit;
    private static List<ConquestSyncPacket.CallInStatus> callIns = List.of();
    private static int availableScore;
    private static List<ConquestSyncPacket.SquadStatus> joinableSquads = List.of();
    private static List<ConquestSyncPacket.SdmSquadStatus> sdmSquads = List.of();
    private static int yourSdmSquad;
    private static int sdmKillLimit;
    private static int sdmWinner;
    /** Incremented on every update; lets the GUI detect changes cheaply. */
    private static int revision;

    public static synchronized void apply(List<ConquestSyncPacket.PointStatus> newPoints,
                                          int newTicketsA, int newTicketsB, boolean newActive, RoundState newState,
                                          GameMode newMode, Team newYourTeam, boolean newCanAdmin,
                                          Team newAttackerTeam, int newSectorIndex, int newSectorCount,
                                          int newAttackerTickets, int newAttackerTicketsMax, int newTdmKillLimit,
                                          List<ConquestSyncPacket.CallInStatus> newCallIns, int newAvailableScore,
                                          List<ConquestSyncPacket.SquadStatus> newJoinableSquads,
                                          List<ConquestSyncPacket.SdmSquadStatus> newSdmSquads, int newYourSdmSquad,
                                          int newSdmKillLimit, int newSdmWinner) {
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
        attackerTicketsMax = newAttackerTicketsMax;
        tdmKillLimit = newTdmKillLimit;
        callIns = List.copyOf(newCallIns);
        availableScore = newAvailableScore;
        joinableSquads = List.copyOf(newJoinableSquads);
        sdmSquads = List.copyOf(newSdmSquads);
        yourSdmSquad = newYourSdmSquad;
        sdmKillLimit = newSdmKillLimit;
        sdmWinner = newSdmWinner;
        revision++;
    }

    public static synchronized void applyScoreboard(int newRoundElapsedSeconds, List<ConquestScoreboardPacket.Entry> newEntries) {
        roundElapsedSeconds = newRoundElapsedSeconds;
        roundElapsedReceivedAt = gameTime();
        scoreboard = List.copyOf(newEntries);
        revision++;
    }

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized int getRoundElapsedSeconds() {
        return state == RoundState.IN_PROGRESS
                ? roundElapsedSeconds + (int) ((gameTime() - roundElapsedReceivedAt) / 20)
                : roundElapsedSeconds;
    }

    private static long gameTime() {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null ? level.getGameTime() : 0;
    }

    public static synchronized void applyZones(ConquestZonesPacket newZones) {
        zones = newZones;
    }

    public static synchronized ConquestZonesPacket getZones() {
        return zones;
    }

    /** Called on logout, so another server's zones aren't drawn from stale data. */
    public static synchronized void clearZones() {
        zones = new ConquestZonesPacket(List.of(), List.of());
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

    public static synchronized void addPin(UUID placer, String placerName, ResourceLocation dimension, BlockPos pos,
                                           long expiryGameTime) {
        pins.put(placer, new PinEntry(placerName, dimension, pos, expiryGameTime));
    }

    public static synchronized void removePin(UUID placer) {
        pins.remove(placer);
    }

    /** Drops expired pins. Returns true if anything was removed, so the caller knows to redraw. */
    public static synchronized boolean pruneExpiredPins(long currentGameTime) {
        return pins.entrySet().removeIf(e -> e.getValue().expiryGameTime() <= currentGameTime);
    }

    public static synchronized Map<UUID, PinEntry> getPins() {
        return Map.copyOf(pins);
    }

    /** Called on logout: pin expiry is measured in absolute world time, meaningless across sessions/servers. */
    public static synchronized void clearPins() {
        pins.clear();
    }

    public static synchronized void addKillFeedEntry(String attackerName, String victimName, int distanceMeters, long expiryGameTime) {
        killFeed.add(new KillFeedEntry(attackerName, victimName, distanceMeters, expiryGameTime));
        while (killFeed.size() > MAX_KILL_FEED_ENTRIES) {
            killFeed.remove(0);
        }
    }

    /** Drops expired kill feed entries. Returns true if anything was removed, so the caller knows to redraw. */
    public static synchronized boolean pruneExpiredKillFeed(long currentGameTime) {
        return killFeed.removeIf(e -> e.expiryGameTime() <= currentGameTime);
    }

    /** Oldest first. */
    public static synchronized List<KillFeedEntry> getKillFeed() {
        return List.copyOf(killFeed);
    }

    /** Called on logout: kill feed expiry is measured in absolute world time, meaningless across sessions/servers. */
    public static synchronized void clearKillFeed() {
        killFeed.clear();
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

    public static synchronized int getAttackerTicketsMax() {
        return attackerTicketsMax;
    }

    /** 0 means no kill limit (TDM runs to the time limit only). */
    public static synchronized int getTdmKillLimit() {
        return tdmKillLimit;
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

    /** Squad Deathmatch squads, sorted by kills descending then number; empty outside SQUAD_DM. */
    public static synchronized List<ConquestSyncPacket.SdmSquadStatus> getSdmSquads() {
        return sdmSquads;
    }

    /** The viewer's own Squad Deathmatch squad number; 0 = none. */
    public static synchronized int getYourSdmSquad() {
        return yourSdmSquad;
    }

    public static synchronized int getSdmKillLimit() {
        return sdmKillLimit;
    }

    /** Winning squad number once the round is ENDED; 0 = no winner (draw) or not Squad Deathmatch. */
    public static synchronized int getSdmWinner() {
        return sdmWinner;
    }

    private ConquestClientData() {}
}
